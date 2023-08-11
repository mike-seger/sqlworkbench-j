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
package workbench.gui.completion;

import java.awt.Toolkit;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbSearchPath;
import workbench.db.IndexDefinition;
import workbench.db.IndexReader;
import workbench.db.ObjectNameSorter;
import workbench.db.ProcedureDefinition;
import workbench.db.QuoteHandler;
import workbench.db.SequenceDefinition;
import workbench.db.SequenceReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.objectcache.DbObjectCache;
import workbench.db.objectcache.Namespace;

import workbench.sql.lexer.SQLToken;
import workbench.sql.syntax.SqlKeywordHelper;

import workbench.util.CollectionUtil;
import workbench.util.SelectColumn;
import workbench.util.SqlParsingUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.TableAlias;

/**
 * Base class to analyze a SQL statement to find out what kind and which
 * objects should be included in the auto-completion window
 *
 * @author Thomas Kellerer
 */
public abstract class BaseAnalyzer
{
  public static final String QUALIFIER_DELIM = "\\?*=<>!/{}\\%'(),:;";
  public static final String WORD_DELIM = QUALIFIER_DELIM + "@";
  public static final String SELECT_WORD_DELIM = WORD_DELIM + ".";

  public static final int NO_CONTEXT = -1;

  /**
   *  Context value to list the available tables
   */
  public static final int CONTEXT_TABLE_LIST = 1;

  /**
   *  Context value to list the columns for a table
   */
  public static final int CONTEXT_COLUMN_LIST = 2;

  /**
   * Context value to list the tables that are available in the FROM list
   */
  public static final int CONTEXT_FROM_LIST = 3;

  public static final int CONTEXT_TABLE_OR_COLUMN_LIST = 4;

  /**
   * Context value to list keywords available at this point
   */
  public static final int CONTEXT_KW_LIST = 5;

  /**
   * Context value to list parameters for WB commands
   */
  public static final int CONTEXT_WB_PARAMS = 6;

  /**
   * Context value to list all workbench commands
   */
  public static final int CONTEXT_WB_COMMANDS = 7;

  /**
   * Context value to list values for a specific command parameter
   */
  public static final int CONTEXT_WB_PARAMVALUES = 8;

  public static final int CONTEXT_SYNTAX_COMPLETION = 9;
  public static final int CONTEXT_STATEMENT_PARAMETER = 10;
  public static final int CONTEXT_SCHEMA_LIST = 11;
  public static final int CONTEXT_CATALOG_LIST = 12;
  public static final int CONTEXT_SEQUENCE_LIST = 13;
  public static final int CONTEXT_INDEX_LIST = 14;
  public static final int CONTEXT_VIEW_LIST = 15;
  public static final int CONTEXT_VALUE_LIST = 16;
  public static final int CONTEXT_PROCEDURE_LIST = 17;

  private final SelectAllMarker allColumnsMarker = new SelectAllMarker();
  private List<String> typeFilter;
  protected String keywordFile;
  protected WbConnection dbConnection;
  protected final String sql;
  protected final String verb;
  protected final int cursorPos;
  protected int context;
  protected TableIdentifier tableForColumnList;
  protected Namespace namespaceForTableList;
  protected boolean addAllMarker;
  protected List elements;
  protected String title;
  private boolean overwriteCurrentWord;
  protected boolean appendDot;
  private String columnPrefix;
  protected BaseAnalyzer parentAnalyzer;
  protected char catalogSeparator;
  protected char schemaSeparator;
  protected SqlParsingUtil parsingUtil;
  protected SelectFKValueMarker fkMarker;

  public BaseAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    this.dbConnection = conn;
    this.sql = statement;
    this.parsingUtil = SqlParsingUtil.getInstance(conn);
    this.verb = parsingUtil.getSqlVerb(sql);
    this.cursorPos = cursorPos;
    this.catalogSeparator = SqlUtil.getCatalogSeparator(this.dbConnection);
    this.schemaSeparator = SqlUtil.getSchemaSeparator(this.dbConnection);
  }

  /**
   * To be implemented by specialized analyzers that might need more complex logic
   * when the user selects an entry from the popup list.
   *
   * @param selectedObject
   * @return the value to paste or null if the standard behaviour should be used
   */
  public String getPasteValue(Object selectedObject)
  {
    return null;
  }

  public QuoteHandler getQuoteHandler()
  {
    return SqlUtil.getQuoteHandler(dbConnection);
  }

  public WbConnection getConnection()
  {
    return dbConnection;
  }

  public String getWordDelimiters()
  {
    return SELECT_WORD_DELIM;
  }

  public boolean needsCommaForMultipleSelection()
  {
    return true;
  }

  public boolean allowMultiSelection()
  {
    return true;
  }

  /**
   * For testing purposes only!
   * @param newSeparator
   */
  void setCatalogSeparator(char newSeparator)
  {
    this.catalogSeparator = newSeparator;
  }

  public String getSqlVerb()
  {
    return this.verb;
  }

  public String getAnalyzedSql()
  {
    return this.sql;
  }

  public int getCursorPosition()
  {
    return this.cursorPos;
  }

  public char quoteCharForValue(String value)
  {
    return 0;
  }

  public boolean isWbParam()
  {
    return false;
  }

  /**
   * checks if the value selected by the user should be changed according to the upper/lowercase settings.
   *
   * @return true - check the "paste case"
   *         false - use the selected vale "as is"
   */
  public boolean convertCase()
  {
    return true;
  }

  /**
   * Set a prefix for columns that are added.
   * If this value is set, any column that the user
   * selects, will be prefixed with this string (plus a dot)
   * This is used when the FROM list in a SELECT statement
   * contains more than one column
   */
  protected void setColumnPrefix(String prefix)
  {
    this.columnPrefix = prefix;
  }

  public String getColumnPrefix()
  {
    return this.columnPrefix;
  }

  protected void setOverwriteCurrentWord(boolean flag)
  {
    this.overwriteCurrentWord = flag;
  }

  public boolean appendDotToSelection()
  {
    return this.appendDot;
  }

  public boolean isKeywordList()
  {
    return this.context == CONTEXT_KW_LIST || this.context == CONTEXT_SYNTAX_COMPLETION;
  }

  public boolean getOverwriteCurrentWord()
  {
    return this.overwriteCurrentWord;
  }

  public Namespace getNamespaceForTableList()
  {
    return namespaceForTableList;
  }

  protected Namespace getCurrentNamespaceToUse()
  {
    if (dbConnection == null) return null;

    if (!dbConnection.getDbSettings().useCurrentNamespaceForCompletion())
    {
      return null;
    }

    boolean supportsSchemas = dbConnection.getDbSettings().supportsSchemas();
    boolean supportsCatalogs = dbConnection.getDbSettings().supportsCatalogs();

    if (!supportsSchemas && supportsCatalogs)
    {
      // No schemas supported (e.g. MySQL) pretend a catalog is the same thing
      return new Namespace(null, this.dbConnection.getMetadata().getCurrentCatalog());
    }

    if (dbConnection.getDbSettings().useFullSearchPathForCompletion())
    {
      return null;
    }

    String schema = this.dbConnection.getCurrentSchema();
    String catalog = this.dbConnection.getCurrentCatalog();

    if (supportsCatalogs && supportsSchemas)
    {
      return new Namespace(schema, catalog);
    }
    else if (!supportsSchemas)
    {
      return new Namespace(null, catalog);
    }

    List<String> schemas = DbSearchPath.Factory.getSearchPathHandler(dbConnection).getSearchPath(dbConnection, schema);
    if (schemas.isEmpty())
    {
      // DBMS does not have a search path, so use the current schema
      return new Namespace(schema, null);
    }
    else if (schemas.size() == 1)
    {
      return new Namespace(schemas.get(0), null);
    }
    return null;
  }

  protected boolean isCurrentNameSpaceCatalog()
  {
    if (this.namespaceForTableList == null) return false;
    if (this.namespaceForTableList.hasCatalogAndSchema()) return false;
    if (this.dbConnection == null) return false;

    if (dbConnection.getDbSettings().supportsCatalogs() && dbConnection.getDbSettings().supportsSchemas())
    {
      String schema = namespaceForTableList.getSchema();
      String catalog = namespaceForTableList.getCatalog();
      String toCheck = StringUtil.coalesce(schema, catalog);
      List<String> allCatalogs = dbConnection.getMetadata().getAllCatalogs();
      for (String dbCat : allCatalogs)
      {
        if (SqlUtil.objectNamesAreEqual(dbCat, toCheck))
        {
          return true;
        }
      }
    }
    return false;
  }
  protected Namespace getNamespaceFromCurrentWord()
  {
//    String word = getCurrentWord();
    String name = getQualifierLeftOfCursor();
    Namespace key = Namespace.fromExpression(dbConnection, name);
    if (!key.isValid())
    {
      return getCurrentNamespaceToUse();
    }
    return key;
  }

  public void retrieveObjects()
  {
    // reset current state
    this.elements = null;
    this.context = NO_CONTEXT;
    this.typeFilter = null;
    this.keywordFile = null;
    this.fkMarker =  null;

    checkOverwrite();
    this.addAllMarker = false;

    // this should not be done in the constructor as the
    // sub-classes might need to do important initializations during initialization
    // and before checkContext is called
    this.checkContext();
    this.buildResult();
  }

  public String getTitle()
  {
    return this.title;
  }

  public List getData()
  {
    return this.elements;
  }

  protected abstract void checkContext();

  // For use with hierarchical Analyzers (so that a child
  // analyzer can ask its parent directly for a table list
  // This should be overwritten by any Analyzer supporting
  // Sub-Selects
  protected List<TableAlias> getTables()
  {
    return Collections.emptyList();
  }

  public void setParent(BaseAnalyzer analyzer)
  {
    this.parentAnalyzer = analyzer;
  }

  protected int getPos(int pos)
  {
    if (pos == -1) return Integer.MAX_VALUE;
    return pos;
  }

  protected boolean between(int toTest, int start, int end)
  {
    return (toTest > getPos(start) && toTest < getPos(end));
  }

  public int getContext()
  {
    return context;
  }

  protected String contextToString()
  {
    switch (context)
    {
      case CONTEXT_COLUMN_LIST:
        return "CONTEXT_COLUMN_LIST";
      case CONTEXT_FROM_LIST:
        return "CONTEXT_FROM_LIST";
      case CONTEXT_KW_LIST:
        return "CONTEXT_KW_LIST";
      case CONTEXT_TABLE_LIST:
        return "CONTEXT_TABLE_LIST";
      case CONTEXT_TABLE_OR_COLUMN_LIST:
        return "CONTEXT_TABLE_LIST";
      case CONTEXT_WB_COMMANDS:
        return "CONTEXT_WB_COMMANDS";
      case CONTEXT_WB_PARAMS:
        return "CONTEXT_WB_PARAMS";
      case CONTEXT_WB_PARAMVALUES:
        return "CONTEXT_WB_PARAMVALUES";
      case CONTEXT_SYNTAX_COMPLETION:
        return "CONTEXT_SYNTAX_COMPLETION";
      case CONTEXT_STATEMENT_PARAMETER:
        return "CONTEXT_STATEMENT_PARAMETER";
    }
    return Integer.toString(context);
  }

  @SuppressWarnings("unchecked")
  protected void buildResult()
  {
    if (context == CONTEXT_TABLE_OR_COLUMN_LIST && tableForColumnList != null)
    {
      if (!retrieveColumns())
      {
        retrieveTables();
      }
    }
    else if (context == CONTEXT_TABLE_LIST)
    {
      if (this.elements == null) retrieveTables();
    }
    else if (context == CONTEXT_VIEW_LIST)
    {
      if (this.elements == null) retrieveViews();
    }
    else if (context == CONTEXT_COLUMN_LIST)
    {
      if (this.elements == null) retrieveColumns();
    }
    else if (context == CONTEXT_KW_LIST)
    {
      this.title = ResourceMgr.getString("LblCompletionListKws");
      this.elements = readKeywords();
    }
    else if (context == CONTEXT_SCHEMA_LIST)
    {
      if (namespaceForTableList != null)
      {
        this.title = namespaceForTableList + ".*";
      }
      else
      {
        this.title = StringUtil.capitalize(dbConnection.getMetadata().getSchemaTerm());
      }
      retrieveSchemas();
    }
    else if (context == CONTEXT_CATALOG_LIST)
    {
      this.title = StringUtil.capitalize(dbConnection.getMetadata().getCatalogTerm());
      retrieveCatalogs();
    }
    else if (context == CONTEXT_SEQUENCE_LIST)
    {
      retrieveSequences();
    }
    else if (context == CONTEXT_INDEX_LIST)
    {
      retrieveIndexes();
    }
    else if (context == CONTEXT_WB_COMMANDS)
    {
      this.title = ResourceMgr.getString("LblCompletionListWbCmd");
    }
    else if (context == CONTEXT_SYNTAX_COMPLETION || context == CONTEXT_STATEMENT_PARAMETER)
    {
      this.title = parsingUtil.getSqlVerb(sql);
    }
    else if (elements == null)
    {
      // no proper sql found
      this.elements = Collections.emptyList();
      this.title = null;
      Toolkit.getDefaultToolkit().beep();
    }

    if (this.addAllMarker && this.elements != null)
    {
      this.elements.add(0, this.allColumnsMarker);
    }
  }

  private List<String> readKeywords()
  {
    if (this.keywordFile == null) return null;
    SqlKeywordHelper helper = new SqlKeywordHelper(dbConnection == null ? null : dbConnection.getDbId());
    Set<String> kwlist = helper.loadKeywordsFromFile(keywordFile);
    return new ArrayList<>(kwlist);
  }

  @SuppressWarnings("unchecked")
  protected void retrieveSchemas()
  {
    List<String> schemas = null;
    if (namespaceForTableList != null && namespaceForTableList.getCatalog() != null && dbConnection.getMetadata().supportsCatalogForGetSchemas())
    {
      schemas = dbConnection.getMetadata().getSchemas(dbConnection.getSchemaFilter(), namespaceForTableList.getCatalog());
    }
    else
    {
      schemas = dbConnection.getMetadata().getSchemas();
    }
    this.elements = new ArrayList(schemas);
  }

  @SuppressWarnings("unchecked")
  protected void retrieveCatalogs()
  {
    List<String> catalogs = dbConnection.getMetadata().getAllCatalogs();
    this.elements = new ArrayList(catalogs);
  }

  @SuppressWarnings("unchecked")
  protected void retrieveIndexes()
  {
    IndexReader reader = dbConnection.getMetadata().getIndexReader();
    title = "Index";
    if (namespaceForTableList != null)
    {
      namespaceForTableList.adjustCase(dbConnection);
    }
    List<IndexDefinition> indexes = reader.getIndexes(namespaceForTableList.getCatalog(), namespaceForTableList.getSchema(), null, null);
    this.elements = new ArrayList(indexes.size());
    for (IndexDefinition idx : indexes)
    {
      if (!idx.isPrimaryKeyIndex())
      {
        idx.setDisplayName(idx.getObjectExpression(dbConnection) + " (" + idx.getBaseTable().getTableExpression(dbConnection) + ")");
        elements.add(idx);
      }
    }
  }

  @SuppressWarnings("unchecked")
  protected void retrieveSequences()
  {
    SequenceReader reader = dbConnection.getMetadata().getSequenceReader();
    if (reader == null) return;
    try
    {
      title = StringUtil.capitalize(reader.getSequenceTypeName());
      if (namespaceForTableList != null)
      {
        namespaceForTableList.adjustCase(dbConnection);
      }
      List<SequenceDefinition> sequences = reader.getSequences(namespaceForTableList.getCatalog(), namespaceForTableList.getSchema(), null);
      elements = new ArrayList(sequences);
    }
    catch (SQLException se)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve sequences", se);
    }
  }

  @SuppressWarnings("unchecked")
  protected void retrieveViews()
  {
    DbObjectCache cache = this.dbConnection.getObjectCache();
    Set<TableIdentifier> tables = cache.getTables(namespaceForTableList, dbConnection.getDbSettings().getViewTypes());
    if (namespaceForTableList == null)
    {
      this.title = ResourceMgr.getString("LblCompletionListTables");
    }
    else
    {
      this.title = namespaceForTableList + ".*";
    }
    this.elements = new ArrayList(tables);
  }


  @SuppressWarnings("unchecked")
  protected void retrieveTables()
  {
    DbObjectCache cache = this.dbConnection.getObjectCache();
    Set<TableIdentifier> tables = cache.getTables(namespaceForTableList, typeFilter);
    if (namespaceForTableList == null)
    {
      this.title = ResourceMgr.getString("LblCompletionListTables");
    }
    else
    {
      this.title = namespaceForTableList + ".*";
    }
    this.elements = new ArrayList(tables);
    if (this.dbConnection.getDbSettings().getIncludeTableFunctionsForTableCompletion())
    {
      List<ProcedureDefinition> functions = cache.getTableFunctions(namespaceForTableList);
      this.elements.addAll(functions);
      if (functions.size() > 0 && GuiSettings.getCompletionSortFunctionsWithTables())
      {
        Collections.sort(elements, new ObjectNameSorter(true));
      }
    }
  }

  protected List<ColumnIdentifier> retrieveColumnsForTable(TableIdentifier table)
  {
    if (table== null) return null;
    if (dbConnection == null) return null;

    DbObjectCache cache = this.dbConnection.getObjectCache();
    TableIdentifier toCheck = table.createCopy();

    // first try the table directly, only if it isn't found, resolve synonyms.
    // By doing this we avoid retrieving a synonym base table if the table is already in the cache
    List<ColumnIdentifier> cols = cache.getColumns(toCheck);

    if (cols == null && dbConnection.getMetadata().supportsSynonyms())
    {
      toCheck = cache.getSynonymTable(tableForColumnList);
      cols = cache.getColumns(toCheck);
    }

    if (CollectionUtil.isNonEmpty(cols) && GuiSettings.getSortCompletionColumns())
    {
      // the cache returns an unmodifieable list!
      cols = new ArrayList<>(cols);
      Collections.sort(cols);
    }
    return cols;
  }

  @SuppressWarnings("unchecked")
  protected boolean retrieveColumns()
  {
    if (tableForColumnList == null) return false;
    if (this.dbConnection == null) return false;
    DbObjectCache cache = this.dbConnection.getObjectCache();

    // first try the table directly, only if it isn't found, resolve synonyms.
    // By doing this we avoid retrieving a synonym base table if the table is already in the cache
    List<ColumnIdentifier> cols = retrieveColumnsForTable(tableForColumnList);

    if (cols != null && cols.size() > 0)
    {
      if (cache.supportsSearchPath())
      {
        TableIdentifier tbl = cache.getTable(tableForColumnList);
        this.title = (tbl == null ? tableForColumnList.getTableName() : tbl.getTableExpression()) + ".*";
      }
      else
      {
        this.title = tableForColumnList.getTableName() + ".*";
      }
      this.elements = new ArrayList(cols.size() + 1);
      this.elements.addAll(cols);

      if (fkMarker != null)
      {
        if (GuiSettings.showSelectFkValueAtTop())
        {
          elements.add(0, fkMarker);
        }
        else
        {
          elements.add(fkMarker);
        }
      }
    }
    return (elements == null ? false : (elements.size() > 0));
  }

  protected void setTableTypeFilter(Collection<String> filter)
  {
    this.typeFilter = new ArrayList<>(filter);
  }

  public String getQualifierLeftOfCursor()
  {
    int len = this.sql.length();
    int start = this.cursorPos - 1;

    if (this.cursorPos > len)
    {
      start = len - 1;
    }

    char c = this.sql.charAt(start);
    //if (Character.isWhitespace(c)) return null;

    // if no dot is present, then the current word is not a qualifier (e.g. a table name or alias)
    if (c != schemaSeparator) return null;

    String word = getCurrentWord();
    return StringUtil.removeTrailing(word, schemaSeparator);
  }

  public boolean isColumnList()
  {
    return this.context == CONTEXT_COLUMN_LIST;
  }

  public String getCurrentWord()
  {
    return StringUtil.getWordLeftOfCursor(this.sql, cursorPos, WORD_DELIM);
  }

  protected void checkOverwrite()
  {
    String currentWord = getCurrentWord();
    if (StringUtil.isEmpty(currentWord))
    {
      setOverwriteCurrentWord(false);
    }
    else
    {
      char separator = SqlUtil.getCatalogSeparator(this.dbConnection);
      setOverwriteCurrentWord(currentWord.charAt(currentWord.length() - 1) != separator);
    }
  }

  protected SelectFKValueMarker checkFkLookup()
  {
    SQLToken prev = SqlUtil.getOperatorBeforeCursor(sql, cursorPos);
    if (prev == null) return null;
    int pos = prev.getCharBegin() - 1;
    String col = StringUtil.getWordLeftOfCursor(sql, pos, " ");

    if (col != null)
    {
      SelectColumn scol = new SelectColumn(col);
      String column = scol.getObjectName();

      // getOperatorBeforeCursor() only returns operators and IN, ANY, ALL
      // if the token is not an operator it's an IN, ANY, ALL condition
      // which would allow multiple values to be selected.
      boolean multiValueFkSelect = !prev.isOperator();
      TableIdentifier fkTable = this.tableForColumnList;
      String tbl =  scol.getColumnTable();
      if (tbl != null)
      {
        List<TableAlias> tblList = getTables();
        for (TableAlias alias : tblList)
        {
          if (tbl.equalsIgnoreCase(alias.getNameToUse()))
          {
            fkTable = alias.getTable();
          }
        }
      }
      return new SelectFKValueMarker(column, fkTable, multiValueFkSelect);
    }
    return null;
  }
  // Used by JUnit tests
  TableIdentifier getTableForColumnList()
  {
    return tableForColumnList;
  }


}
