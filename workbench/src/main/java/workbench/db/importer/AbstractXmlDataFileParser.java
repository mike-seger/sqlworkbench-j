/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.db.importer;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import workbench.interfaces.ImportFileParser;
import workbench.interfaces.JobErrorHandler;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;
import workbench.util.WbStringTokenizer;

import org.xml.sax.SAXException;

/**
 *
 * @author Thomas Kellerer
 */
public abstract class AbstractXmlDataFileParser
  extends AbstractImportFileParser
  implements ImportFileParser
{
  protected SAXParser saxParser;
  protected Object[] currentRow;
  protected int currentRowNumber = 1;
  protected final List<String> missingColumns = new ArrayList<>();
  protected String nullString;

  public AbstractXmlDataFileParser()
  {
    super();
    SAXParserFactory factory = SAXParserFactory.newInstance();
    try
    {
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
      factory.setValidating(false);
      saxParser = factory.newSAXParser();
    }
    catch (Exception e)
    {
      // should not happen!
      LogMgr.logError(new CallerInfo(){}, "Error creating XML parser", e);
    }
  }

  public AbstractXmlDataFileParser(File file)
  {
    this();
    this.inputFile = file;
  }

  public String getNullString()
  {
    return nullString;
  }

  public void setNullString(String nullString)
  {
    this.nullString = StringUtil.trimToNull(nullString);
  }

  @Override
  public String getColumns()
  {
    return StringUtil.listToString(this.importColumns, ',', false);
  }

  @Override
  public String getLastRecord()
  {
    return null;
  }

  @Override
  public Map<Integer, Object> getInputColumnValues(Collection<Integer> inputFileIndexes)
  {
    return null;
  }

  public void setColumns(String columnList)
    throws SQLException
  {
    if (StringUtil.isNotBlank(columnList))
    {
      WbStringTokenizer tok = new WbStringTokenizer(columnList, ",");
      List<ColumnIdentifier> columns = new ArrayList<>();
      while (tok.hasMoreTokens())
      {
        String col = tok.nextToken();
        if (StringUtil.isBlank(col)) continue;
        col = col.trim();
        if (col.length() == 0) continue;
        ColumnIdentifier ci = new ColumnIdentifier(col);
        columns.add(ci);
      }
      setColumns(columns);
    }
  }

  /**
   * Define the columns to be imported.
   */
  @Override
  public void setColumns(List<ColumnIdentifier> cols)
    throws SQLException
  {
    if (CollectionUtil.isNonEmpty(cols))
    {
      importColumns = ImportFileColumn.createList();
      for (int i=0; i < cols.size(); i++)
      {
        ColumnIdentifier id = cols.get(i);
        if (!id.getColumnName().equals(RowDataProducer.SKIP_INDICATOR))
        {
          ImportFileColumn ifc = new ImportFileColumn(id);
          ifc.setTargetIndex(i);
          importColumns.add(ifc);
        }
      }
    }
    else
    {
      this.importColumns = null;
    }
  }

  private boolean doIncludeCurrentRow()
  {
    if (currentRow == null) return false;

    for (int colIndex = 0; colIndex < currentRow.length; colIndex ++)
    {
      Object value = currentRow[colIndex];
      if (value != null)
      {
        String svalue = value.toString();
        if (isColumnFiltered(colIndex, svalue))
        {
          return false;
        }
      }
    }
    return true;
  }

  protected void clearRowData()
  {
    if (currentRow != null)
    {
      Arrays.fill(currentRow, null);
    }
  }

  protected void sendRowData()
    throws SAXException, Exception
  {
    if (this.receiver != null)
    {
      try
      {
        if (doIncludeCurrentRow())
        {
          this.receiver.processRow(this.currentRow);
        }
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error when sending row data to receiver", e);
        if (this.abortOnError)
        {
          this.hasErrors = true;
          throw e;
        }
        this.hasWarnings = true;
        if (this.errorHandler != null)
        {
          int choice = errorHandler.getActionOnError(this.currentRowNumber + 1, null, null, ExceptionUtil.getDisplay(e, false));
          if (choice == JobErrorHandler.JOB_ABORT) throw e;
          if (choice == JobErrorHandler.JOB_IGNORE_ALL)
          {
            this.abortOnError = false;
          }
        }

      }
    }
    if (this.cancelImport)
    {
      throw new ParsingInterruptedException();
    }
  }

  /**
   *  Returns the columns from the defined import columns that are not found in the import file.
   * 
   *  @see #setColumns(String)
   *  @see #setColumns(List)
   */
  public List<String> getMissingColumns()
  {
    return missingColumns;
  }


}
