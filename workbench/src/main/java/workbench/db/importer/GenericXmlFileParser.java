/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 *
 */
package workbench.db.importer;

import java.io.File;
import java.io.Reader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbObjectFinder;
import workbench.db.TableIdentifier;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.FileUtil;
import workbench.util.MessageBuffer;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An ImportFileParser for generic XML formats.
 *
 * The parser can be configured in two ways:
 * <ul>
 *   <li>A tag containing the column values as attributes for each row, e.g.:<br/>
 *     <pre>&lt;data&gt;&lt;row id="42" name="Foo"/&gt;&lt;data&gt;</pre>
 *     The mapping between attributes and columns can be configured through: {@link #setRowAttributeMap(String, Map)}. <br/><br/>
 *     This is the default mode, assuming the attribute names match the column names (similar to a header in a text file)
 *   </li>
 *   <br/>
 *   <li>A tag containing multiple nested tags for each column value, e.g.<br/>
 *     <pre>&lt;data&gt;&lt;row&gt;&lt;id&gt;42&lt;/id&gt;&lt;name&gt;Foo&lt;/name&gt;&lt;/row&gt;&lt;/data&gt;</pre>
 *     This mode is configured through {@link #setRowAndColumnTags(String, Map)}.
 *   </li>
 * </ul>
 *
 * @author  Thomas Kellerer
 */
public class GenericXmlFileParser
  extends AbstractXmlDataFileParser
{
  private boolean ignoreCurrentRow;
  private StringBuilder chars;
  /**
   * The name of the tag that defines a row.
   */
  private String rowTag;
  private boolean attributesAreColumns = true;
  /**
   * If columns are stored in tags, this map contains the mapping between the column tag and the real columns
   * If columns are stored in attributes, this map contains the mapping between tag attribues and the real columns
  */
  private Map<String, String> columnMap;

  private DefaultHandler handler = new SaxHandler();

  public GenericXmlFileParser()
  {
    super();
  }

  public GenericXmlFileParser(File file)
  {
    super();
  }

  /**
   * Define the name of the XML tag that starts a row including the attributes of that tag, that
   * represent the column values.
   *
   * @param tag      the tag that contains a single row
   * @param columns  the mapping between the XML attribute of the row tag and the target column. The key is the XML attribute
   */
  public void setRowAttributeMap(String tag, Map<String, String> columns)
  {
    this.rowTag = tag;
    this.attributesAreColumns = true;
    this.columnMap = createColMap();
    if (columns != null)
    {
      this.columnMap.putAll(columns);
    }
  }

  /**
   * Define the name of the XML tag that starts a row including the nested tags that contain the column values.
   *
   * @param tag      the tag that contains a single row
   * @param columns  the nested tags inside the row tag that contain the column values. The key is the nested tag name.
   */
  public void setRowAndColumnTags(String rowTag, Map<String, String> columns)
  {
    this.rowTag = rowTag;
    this.attributesAreColumns = false;
    this.columnMap = createColMap();
    if (columns != null)
    {
      this.columnMap.putAll(columns);
    }
  }

  private Map<String, String> createColMap()
  {
    return new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
  }

  private List<ColumnIdentifier> getMappedColumns()
    throws SQLException
  {
    List<ColumnIdentifier> mappedColumns = new ArrayList<>();
    if (CollectionUtil.isEmpty(columnMap)) return mappedColumns;

    List<ColumnIdentifier> tableColumns = getTargetTable().getColumns();
    for (String column : columnMap.values())
    {
      ColumnIdentifier realCol = ColumnIdentifier.findColumnInList(tableColumns, column);
      if (realCol != null)
      {
        mappedColumns.add(realCol);
      }
    }
    return mappedColumns;
  }

  protected void initializeColumns()
    throws Exception
  {
    if (this.importColumns != null)
    {
      checkTargetColumns(createTargetTableId());
    }
    else if (CollectionUtil.isNonEmpty(columnMap))
    {
      setColumns(getMappedColumns());
    }
    else
    {
      if (attributesAreColumns)
      {
        setColumns(getColumnsFromFile());
      }
      else
      {
        setColumns(getTargetTable().getColumns());
      }
    }

    if (CollectionUtil.isEmpty(columnMap) && CollectionUtil.isNonEmpty(importColumns))
    {
      columnMap = createColMap();
      for (ImportFileColumn col : importColumns)
      {
        if (col.getTargetIndex() > -1)
        {
          String colName = col.getColumn().getColumnName();
          columnMap.put(colName, colName);
        }
      }
    }
  }


  /**
   * Check if all columns defined for the import (through the table definition
   * as part of the XML file, or passed by the user on the command line) are
   * actually available in the target table.
   * For this all columns of the target table are retrieved from the database,
   * and each column that has been defined through setColumns() is checked
   * whether it exists there. Columns that are not found are dropped from
   * the list of import columns
   * If continueOnError == true, a warning is added to the messages. Otherwise
   * an Exception is thrown.
   */
  protected void checkTargetColumns(TableIdentifier tbl)
    throws SQLException
  {
    if (this.connection == null) return;
    if (tbl == null) return;

    DbObjectFinder finder = new DbObjectFinder(this.connection);
    if (!finder.tableExists(tbl))
    {
      if (this.receiver.getCreateTarget())
      {
        LogMgr.logDebug(new CallerInfo(){}, "Table " + tbl.getTableName() + " not found, but receiver will create it. Skipping column check...");
        return;
      }
      else
      {
        String msg = ResourceMgr.getFormattedString("ErrTargetTableNotFound", tbl.getTableName());
        this.messages.append(msg);
        this.messages.appendNewLine();
        throw new SQLException("Table '" + tbl.getTableName() + "' not found!");
      }
    }
    List<ColumnIdentifier> tableCols = this.connection.getMetadata().getTableColumns(tbl);
    List<ImportFileColumn> validCols = ImportFileColumn.createList();
    missingColumns.clear();

    int targetIndex = 0;
    for (ColumnIdentifier column : getTargetColumns())
    {
      int i = tableCols.indexOf(column);

      if (i != -1)
      {
        // Use the column definition retrieved from the database to make sure
        // we are using the correct data types when converting the input (String) values.
        // This is also important to get the quoting of column names correct
        ColumnIdentifier tc = tableCols.get(i);
        ImportFileColumn ic = new ImportFileColumn(tc);
        ic.setTargetIndex(targetIndex);
        validCols.add(ic);
        targetIndex++;
      }
      else
      {
        String errorColumn = column.getColumnName();
        missingColumns.add(errorColumn);
        String msg = ResourceMgr.getFormattedString("ErrImportColumnNotFound", errorColumn, getSourceFilename(), tbl.getTableExpression());
        this.messages.append(msg);
        this.messages.appendNewLine();
        if (this.abortOnError)
        {
          this.hasErrors = true;
          throw new SQLException("Column " + errorColumn + " not found in target table");
        }
        else
        {
          this.hasWarnings = true;
          LogMgr.logWarning(new CallerInfo(){}, msg);
        }
      }
    }
    this.importColumns = validCols;
  }

  @Override
  public List<ColumnIdentifier> getColumnsFromFile()
  {
    try
    {
      fileHandler.setMainFile(this.inputFile, getEncoding());
      GenericXMLColumnDetector detector = new GenericXMLColumnDetector(fileHandler.getMainFileReader(), rowTag, attributesAreColumns);
      return getColumnsFromFile(detector);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not detect columns from xml file for row tag <" + rowTag + ">", ex);
    }
    finally
    {
      FileUtil.closeQuietely(fileHandler);
    }
    return null;
  }

  private List<ColumnIdentifier> getColumnsFromFile(GenericXMLColumnDetector detector)
    throws Exception
  {
    final boolean useColumnMap = CollectionUtil.isNonEmpty(columnMap);
    List<ColumnIdentifier> tableColumns = new ArrayList<>(0);
    if (!useColumnMap)
    {
      tableColumns = getTargetTable().getColumns();
    }
    List<String> attributes = detector.getColumns();
    List<ColumnIdentifier> columns = new ArrayList<>(attributes.size());

    for (String att : attributes)
    {
      if (useColumnMap)
      {
        String columnName = columnMap.get(att);
        if (StringUtil.isBlank(columnName))
        {
          columns.add(new ColumnIdentifier(att));
        }
        else
        {
          columns.add(new ColumnIdentifier(columnName));
        }
      }
      else
      {
        ColumnIdentifier col = ColumnIdentifier.findColumnInList(tableColumns, att);
        if (col != null)
        {
          columns.add(col);
        }
        // This is needed in case the attributes are written in different case than the columns
        columnMap.put(att, col.getColumnName());
      }
    }
    return columns;
  }

  @Override
  public String getEncoding()
  {
    return (StringUtil.isEmpty(this.encoding) ? "UTF-8" : this.encoding);
  }

  @Override
  protected void processOneFile()
    throws Exception
  {
    // Re-initialize the reader in case we are reading from a ZIP archive
    // because readTableDefinition() can change the file handler
    this.fileHandler.setMainFile(this.inputFile, getEncoding());

    blobDecoder.setBaseDir(inputFile.getParentFile());

    if (!sharedMessages) this.messages = new MessageBuffer();
    initializeColumns();
    this.sendTableDefinition();
    Reader in = null;

    try
    {
      in = this.fileHandler.getMainFileReader();
      InputSource source = new InputSource(in);
      saxParser.parse(source, handler);
      filesProcessed.add(inputFile);
      this.receiver.tableImportFinished();
    }
    catch (ParsingInterruptedException e)
    {
      if (this.regularStop)
      {
        this.receiver.tableImportFinished();
      }
      else
      {
        this.hasErrors = true;
      }
    }
    catch (ParsingConverterException pce)
    {
      // already logged and added to the messages
      this.receiver.tableImportError();
      this.hasErrors = true;
      throw pce;
    }
    catch (Exception e)
    {
      String msg = "Error during parsing of data row: " + (this.currentRowNumber) + ", message: " + ExceptionUtil.getDisplay(e);
      LogMgr.logWarning(new CallerInfo(){}, msg);
      this.hasErrors = true;
      this.messages.append(msg);
      this.messages.appendNewLine();
      this.receiver.tableImportError();
      throw e;
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
  }

  @Override
  protected void resetForFile()
  {
    super.resetForFile();
    ignoreCurrentRow = false;
    importColumns = null;
    targetTable = null;
  }

  @Override
  protected void clearRowData()
  {
    super.clearRowData();
    this.chars = null;
  }

  /**
   *  Creates the approriate column data object and puts it into currentRow[currentColIndex].
   */
  private void buildColumnData(String columnName, String value)
    throws ParsingConverterException
  {
    int colIndex = getColumnIndex(columnName);
    this.currentRow[colIndex] = null;

    if (!this.receiver.shouldProcessNextRow()) return;

    ColumnIdentifier colId = this.importColumns.get(colIndex).getColumn();

    int type = colId.getDataType();

    if (trimValues && !SqlUtil.isBlobType(type))
    {
      value = value.trim();
    }

    if (nullString != null && nullString.equals(value))
    {
      value = null;
    }
    if (this.valueModifier != null)
    {
      value = this.valueModifier.modifyValue(colId, value);
    }

    try
    {
      if (receiver.isColumnExpression(colId.getColumnName()))
      {
        this.currentRow[colIndex] = value;
      }
      else if (SqlUtil.isBlobType(type))
      {
        this.currentRow[colIndex] = blobDecoder.decodeBlob(value, getBlobMode(colId.getColumnName()));
      }
      else
      {
        this.currentRow[colIndex] = this.converter.convertValue(value, type);
      }
    }
    catch (Exception e)
    {
      String msg = ResourceMgr.getString("ErrConvertError");
      msg = StringUtil.replace(msg, "%type%", SqlUtil.getTypeName(colId.getDataType()));
      msg = StringUtil.replace(msg, "%column%", colId.getColumnName());
      msg = StringUtil.replace(msg, "%error%", e.getMessage());
      msg = StringUtil.replace(msg, "%value%", value);
      msg = StringUtil.replace(msg, "%row%", Integer.toString(this.currentRowNumber));

      this.messages.append(msg);
      this.messages.appendNewLine();

      if (this.abortOnError)
      {
        LogMgr.logError(new CallerInfo(){}, msg, e);
        this.hasErrors = true;
        throw new ParsingConverterException();
      }
      else
      {
        this.messages.append(ResourceMgr.getString("ErrConvertWarning"));
        this.hasWarnings = true;
        LogMgr.logWarning(new CallerInfo(){}, msg, null);
      }
    }
  }

  private List<ColumnIdentifier> getTargetColumns()
  {
    if (importColumns == null) return new ArrayList<>();

    return this.importColumns.
      stream().
      filter(col -> col.getTargetIndex() > -1).
      map(col -> col.getColumn()).collect(Collectors.toList());
  }

  private void sendTableDefinition()
    throws SQLException
  {
    try
    {
      TableIdentifier tbl = createTargetTableId();
      List<ColumnIdentifier> cols = getTargetColumns();
      this.receiver.setTargetTable(tbl, cols, inputFile);
      this.currentRow = new Object[cols.size()];
    }
    catch (SQLException e)
    {
      this.currentRow = null;
      this.hasErrors = true;
      throw e;
    }
  }

  private void parseAttributes(Attributes attrs)
    throws ParsingConverterException
  {
    for (Map.Entry<String, String> entry : columnMap.entrySet())
    {
      String attrName = entry.getKey();
      String colName = entry.getValue();
      String value = attrs.getValue(attrName);
      buildColumnData(colName, value);
    }
  }

  private class SaxHandler
    extends DefaultHandler
  {
    private SaxHandler()
    {
      super();
    }

    @Override
    public void startDocument()
      throws SAXException
    {
      Thread.yield();
      if (cancelImport) throw new ParsingInterruptedException();
    }

    @Override
    public void endDocument()
      throws SAXException
    {
      Thread.yield();
      if (cancelImport)
      {
        throw new ParsingInterruptedException();
      }
    }

    @Override
    public void startElement(String namespaceURI, String sName, String qName, Attributes attrs)
      throws SAXException
    {
      Thread.yield();
      if (cancelImport)
      {
        throw new ParsingInterruptedException();
      }

      if (qName.equals(rowTag))
      {
        // row definition ended, start a new row
        clearRowData();
        if (attributesAreColumns)
        {
          parseAttributes(attrs);
        }
      }
      else if (columnMap.containsKey(qName))
      {
        chars = new StringBuilder();
      }
    }

    @Override
    public void endElement(String namespaceURI, String sName, String qName)
      throws SAXException
    {
      if (cancelImport)
      {
        throw new ParsingInterruptedException();
      }

      if (qName.equals(rowTag))
      {
        if (!receiver.shouldProcessNextRow())
        {
          receiver.nextRowSkipped();
        }
        else
        {
          if (!ignoreCurrentRow)
          {
            try
            {
              sendRowData();
            }
            catch (Exception e)
            {
              // don't need to log the error as sendRowData() has already done that.
              if (abortOnError)
              {
                throw new ParsingInterruptedException();
              }
            }
          }
        }
        ignoreCurrentRow = false;
        currentRowNumber++;
      }
      else if (columnMap.containsKey(qName))
      {
        buildColumnData(columnMap.get(qName), chars.toString());
        chars = null;
      }
    }

    @Override
    public void characters(char[] buf, int offset, int len)
      throws SAXException
    {
      Thread.yield();
      if (cancelImport)
      {
        throw new ParsingInterruptedException();
      }

      if (chars != null)
      {
        chars.append(buf, offset, len);
      }
    }

    /** Only implemented to have even more possibilities for cancelling the import */
    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
      throws SAXException
    {
      Thread.yield();
      if (cancelImport)
      {
        throw new ParsingInterruptedException();
      }
    }

    @Override
    public void processingInstruction(String target, String data)
      throws SAXException
    {
      Thread.yield();
      if (cancelImport)
      {
        throw new ParsingInterruptedException();
      }
    }

    @Override
    public void error(SAXParseException e)
      throws SAXParseException
    {
      String msg = "XML Parse error in line=" + e.getLineNumber() + ", row number: " + (currentRowNumber);
      LogMgr.logError(new CallerInfo(){}, msg, e);
      ignoreCurrentRow = true;
    }

    @Override
    public void fatalError(SAXParseException e)
      throws SAXParseException
    {
      String msg = "Fatal XML parse error in line=" + e.getLineNumber() + ", row number: " + (currentRowNumber) + "\nRest of file will be ignored!";
      LogMgr.logError(new CallerInfo(){}, msg, e);
      ignoreCurrentRow = true;
    }

    // dump warnings too
    @Override
    public void warning(SAXParseException err)
      throws SAXParseException
    {
      messages.append(ExceptionUtil.getDisplay(err));
      messages.appendNewLine();
      if (cancelImport)
      {
        throw err;
      }
    }

  }

}
