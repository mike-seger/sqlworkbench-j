/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.DbMetadata;
import workbench.db.DmlExpressionBuilder;
import workbench.db.DmlExpressionType;
import workbench.db.JdbcUtils;
import workbench.db.QuoteHandler;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.mssql.SqlServerUtil;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

import static workbench.db.DBID.*;

/**
 * A class to build the INSERT, "Upsert" or Insert/Ignore statements for a DataImporter.
 *
 * @author Thomas Kellerer
 */
public class ImportDMLStatementBuilder
{
  private final WbConnection dbConn;
  private final TableIdentifier targetTable;
  private final List<ColumnIdentifier> targetColumns;
  private List<ColumnIdentifier> keyColumns;
  private final Set<String> upsertRequiresPK = CollectionUtil.caseInsensitiveSet(DBID.Cubrid.getId(), DBID.MySQL.getId(), DBID.HANA.getId(), DBID.H2.getId(), DBID.SQL_Anywhere.getId(), DBID.SQLite.getId());
  private final Set<String> ignoreRequiresPK = CollectionUtil.caseInsensitiveSet(DBID.Cubrid.getId(), DBID.MySQL.getId(), DBID.SQL_Anywhere.getId(), DBID.SQLite.getId());

  private final Map<String, String> columnExpressions = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
  private OverrideIdentityType overrideIdentity;

  ImportDMLStatementBuilder(WbConnection connection, TableIdentifier target, List<ColumnIdentifier> columns, ColumnFilter filter, boolean adjustColumnNameCase)
  {
    dbConn = connection;
    targetTable = target;
    targetColumns = createColumnList(columns, filter, adjustColumnNameCase);
  }

  public void setOverrideStrategy(OverrideIdentityType strategy)
  {
    this.overrideIdentity = strategy;
  }

  public void setColumnExpressions(Map<String, String> expressions)
  {
    this.columnExpressions.clear();
    if (expressions != null)
    {
      this.columnExpressions.putAll(expressions);
    }
  }

  /**
   * Returns true if a "native" insert ignore is supported.
   *
   * For some DBMS (e.g. DB2 or HSQLDB) we use a MERGE statement without the "WHEN MATCHED" part to
   * simulate an insertIgnore mode.
   *
   * However when insert/update is used by the DataImporter it will try to use an insertIgnore statement
   * followed by an UPDATE statement if available.
   *
   * @return if the DBMS has a native insertIgnore mode (rather than simulating one using a MERGE)
   */
  boolean hasNativeInsertIgnore()
  {
    switch (DBID.fromConnection(dbConn))
    {
      case Oracle:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "11.2");
      case Postgres:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "9.5");
      case SQLite:
        return true;
      case SQL_Anywhere:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "10.0");
    }
    return false;
  }

  public static boolean supportsOverrideIdentity(WbConnection dbConn)
  {
    if (dbConn == null) return false;
    return (DBID.Postgres.isDB(dbConn) && JdbcUtils.hasMinimumServerVersion(dbConn, "9.5"));
  }

  /**
   * Returns true if the DBMS supports an "insert ignore" kind of statement.
   *
   * This is slightly different to {@link #hasNativeInsertIgnore()} which is a bit more restrictive.
   * For SQL Server or DB2 {@link #createInsertIgnore()} will create a <tt>MERGE</tt> statement
   * without an "WHEN MATCHED" clause, which might be less efficient than a "native" insert ignore statement.
   *
   * @param dbConn the DBMS to check
   * @return true if the DBMS supports some kine of "Insert but ignore unique key violations" statement.
   */
  public static boolean supportsInsertIgnore(WbConnection dbConn)
  {
    if (dbConn == null) return false;

    switch (DBID.fromConnection(dbConn))
    {
      case Postgres:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "9.5");
      case Oracle:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "11.2");
      case SQL_Server:
        return SqlServerUtil.isSqlServer2008(dbConn);
      case DB2_LUW:
      case Cubrid:
      case MySQL:
      case MariaDB:
      case SQLite:
        return true;
      case HSQLDB:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "2.0");
      case DB2_ZOS:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "10.0");
      case SQL_Anywhere:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "10.0");
    }

    return false;
  }


  /**
   * Returns true if the DBMS supports an "UPSERT" (insert, if exists, then update) kind of statement.
   *
   * Some DBMS have a native "upsert" statement, for other DMBS (e.g. Oracle or SQL Server) a MERGE statement will be used.
   *
   * @param dbConn the DBMS to check
   * @return true if the DBMS supports some kine of "upsert" statement.
   */
  public static boolean supportsUpsert(WbConnection dbConn)
  {
    if (dbConn == null) return false;

    switch (DBID.fromConnection(dbConn))
    {
      case Oracle:
      case DB2_LUW:
      case Cubrid:
      case HANA:
      case SQLite:
      case H2:
      case MySQL:
      case MariaDB:
        return true;
      case Postgres:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "9.5");
      case Firebird:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "2.1");
      case DB2_ZOS:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "10.0");
      case SQL_Server:
        return SqlServerUtil.isSqlServer2008(dbConn);
      case HSQLDB:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "2.0");
      case SQL_Anywhere:
        return JdbcUtils.hasMinimumServerVersion(dbConn, "10.0");
    }
    return false;
  }


  /**
   * Returns true if the current DBMS supports an "UPSERT" statement.
   *
   * If no key columns have been defined, false is returned.
   *
   * Some DBMS als require a primary key constraint to be defined (rather than just a unique constraint).
   *
   * @return
   * @see #setKeyColumns(java.util.List)
   * @see #supportsUpsert(workbench.db.WbConnection)
   */
  boolean supportsUpsert()
  {
    if (upsertRequiresPK.contains(dbConn.getDbId()))
    {
      boolean hasPK = hasRealPK();
      if (!hasPK)
      {
        LogMgr.logInfo(new CallerInfo(){}, "Cannot use upsert without a primary key.");
      }
      return hasPK;
    }
    return CollectionUtil.isNonEmpty(getKeyColumns()) && supportsUpsert(this.dbConn);
  }


  /**
   * Verifies if the given ImportMode is supported by the current DBMS.
   *
   * Some modes require a real primary key constraint. Any mode involving updating rows
   * will require the primary key (or unique) columns to be defined before calling this method.
   *
   * @param mode  the mode to check
   * @return true if the mode is supported
   *
   * @see #setKeyColumns(java.util.List)
   */
  boolean isModeSupported(ImportMode mode)
  {
    switch (mode)
    {
      case insertIgnore:
        if (ignoreRequiresPK.contains(dbConn.getDbId()))
        {
          boolean hasPK = hasRealPK();
          if (!hasPK)
          {
            LogMgr.logInfo(new CallerInfo(){}, "Cannot use insertIgnore without a primary key.");
          }
          return hasPK;
        }
        return supportsInsertIgnore(dbConn);
      case insertUpdate:
      case updateInsert:
      case upsert:
        return supportsUpsert();
    }
    return false;
  }

  /**
   * Define the key columns to be used for updating rows.
   *
   * The columns do not have to match the primary key of the table, but for some DBMS and import modes this is required
   * (e.g. MySQL can not do an InsertIgnore if no primary key is defined)
   *
   * @param keys   the columns to be used as a unique key
   */
  void setKeyColumns(List<ColumnIdentifier> keys)
  {
    keyColumns = new ArrayList<>(2);
    if (keys == null)
    {
      return;
    }
    for (ColumnIdentifier col : keys)
    {
      keyColumns.add(col.createCopy());
    }
  }

  private QuoteHandler getQuoteHandler()
  {
    return SqlUtil.getQuoteHandler(dbConn);
  }

  private String getInsertString()
  {
    if (dbConn == null) return null;
    return dbConn.getDbSettings().getInsertForImport();
  }

  /**
   * Creates a plain INSERT statement.
   *
   * The alternate insert statement can be used to enable special DBMS features.
   * e.g. enabling direct path inserts for Oracle using: <tt>INSERT /&#42;+ append &#42;/ INTO</tt>
   *
   * @param columnConstants  constant value definitions for some columns, may be null
   * @param insertSqlStart   an alternate insert statement, may be null.
   * @return a SQL statement suitable used for a PreparedStatement
   */
  String createInsertStatement(ConstantColumnValues columnConstants, String insertSqlStart)
  {
    DmlExpressionBuilder builder = DmlExpressionBuilder.Factory.getBuilder(dbConn);
    StringBuilder text = new StringBuilder(targetColumns.size() * 50);
    StringBuilder parms = new StringBuilder(targetColumns.size() * 20);

    String sql = StringUtil.firstNonBlank(insertSqlStart, getInsertString(), "INSERT INTO");
    text.append(sql);
    text.append(' ');
    text.append(targetTable.getFullyQualifiedName(dbConn));
    text.append(" (");

    QuoteHandler quoter = getQuoteHandler();

    int colIndex = 0;
    for (int i=0; i < getColCount(); i++)
    {
      ColumnIdentifier col = this.targetColumns.get(i);

      if (colIndex > 0)
      {
        text.append(',');
        parms.append(',');
      }

      String colname = col.getDisplayName();
      colname = quoter.quoteObjectname(colname);
      text.append(colname);

      String expr = columnExpressions.get(colname);
      if (StringUtil.isNotBlank(expr))
      {
        parms.append(expr);
      }
      else
      {
        parms.append(builder.getDmlExpression(col, DmlExpressionType.Import));
      }
      colIndex ++;
    }

    if (columnConstants != null)
    {
      int cols = columnConstants.getColumnCount();
      for (int i=0; i < cols; i++)
      {
        text.append(',');
        text.append(columnConstants.getColumn(i).getColumnName());
        parms.append(',');
        if (columnConstants.isFunctionCall(i))
        {
          parms.append(columnConstants.getFunctionLiteral(i));
        }
        else
        {
          parms.append('?');
        }
      }
    }
    text.append(")\n");
    appendOverride(text);
    text.append("VALUES \n(");
    text.append(parms);
    text.append(')');

    return text.toString();
  }

  private void appendOverride(StringBuilder text)
  {
    if (overrideIdentity == null) return;
    switch (overrideIdentity)
    {
      case System:
        text.append("OVERRIDING SYSTEM VALUE \n");
        break;
      case User:
        text.append("OVERRIDING USER VALUE \n");
        break;
    }
  }

  String createInsertIgnore(ConstantColumnValues columnConstants, String insertSqlStart)
  {
    switch (DBID.fromConnection(dbConn))
    {
      case Postgres:
        if (JdbcUtils.hasMinimumServerVersion(dbConn, "9.5"))
        {
          return createPostgresUpsert(columnConstants, insertSqlStart, true);
        }
      case MySQL:
      case MariaDB:
        return createMySQLUpsert(columnConstants, insertSqlStart, true);
      case Cubrid:
        return createMySQLUpsert(columnConstants, insertSqlStart, true);
      case Oracle:
        return createOracleInsertIgnore(columnConstants);
      case HSQLDB:
        return createHSQLUpsert(columnConstants, true);
      case DB2_LUW:
        return createDB2LuWUpsert(columnConstants, true);
      case DB2_ZOS:
        return createDB2zOSUpsert(columnConstants, true);
      case SQL_Server:
        return createSqlServerUpsert(columnConstants, true);
      case SQLite:
        return createInsertStatement(columnConstants, "INSERT OR IGNORE ");
      case SQL_Anywhere:
        return createSQLAnywhereStatement(columnConstants, true);
    }
    return null;
  }

  String createUpsertStatement(ConstantColumnValues columnConstants, String insertSqlStart)
  {
    switch (DBID.fromConnection(dbConn))
    {
      case Postgres:
        if (JdbcUtils.hasMinimumServerVersion(dbConn, "9.5"))
        {
          return createPostgresUpsert(columnConstants, insertSqlStart, false);
        }
      case MySQL:
      case MariaDB:
        return createMySQLUpsert(columnConstants, insertSqlStart, false);
      case H2:
        return createH2Upsert(columnConstants);
      case HSQLDB:
        return createHSQLUpsert(columnConstants, false);
      case Firebird:
        return createFirebirdUpsert(columnConstants);
      case DB2_LUW:
        return createDB2LuWUpsert(columnConstants, false);
      case DB2_ZOS:
        return createDB2zOSUpsert(columnConstants, false);
      case HANA:
        return createHanaUpsert(columnConstants);
      case Cubrid:
        return createMySQLUpsert(columnConstants, null, false);
      case Oracle:
        return createOracleMerge(columnConstants);
      case SQL_Server:
        return createSqlServerUpsert(columnConstants, false);
      case SQLite:
        return createInsertStatement(columnConstants, "INSERT OR REPLACE ");
      case SQL_Anywhere:
        return createSQLAnywhereStatement(columnConstants, false);
    }
    return null;
  }

  private String createSQLAnywhereStatement(ConstantColumnValues columnConstants, boolean useIgnore)
  {
    String insert = createInsertStatement(columnConstants, null);
    if (useIgnore)
    {
      insert = insert.replace(") VALUES (", ") ON EXISTING SKIP VALUES (");
    }
    else
    {
      insert = insert.replace(") VALUES (", ") ON EXISTING UPDATE VALUES (");
    }
    return insert;
  }

  protected String createOracleInsertIgnore(ConstantColumnValues columnConstants)
  {
    String start = "INSERT /*+ IGNORE_ROW_ON_DUPKEY_INDEX (";
    start += targetTable.getRawTableName() + " (";
    List<ColumnIdentifier> keyCols = getKeyColumns();
    for (int i=0; i < keyCols.size(); i++)
    {
      if (i > 0) start += ",";
      String colname = keyCols.get(i).getDisplayName();
      start += colname;
    }
    start += ")) */ INTO ";
    return createInsertStatement(columnConstants, start);
  }

  private String createHanaUpsert(ConstantColumnValues columnConstants)
  {
    if (CollectionUtil.isEmpty(getKeyColumns())) return null;

    String insert = createInsertStatement(columnConstants, "UPSERT ");
    insert += " WITH PRIMARY KEY";
    return insert;
  }

  protected String createPostgresUpsert(ConstantColumnValues columnConstants, String insertSqlStart, boolean useIgnore)
  {
    if (CollectionUtil.isEmpty(getKeyColumns())) return null;

    String insert = createInsertStatement(columnConstants, insertSqlStart);

    insert += "\nON CONFLICT";

    if (useIgnore)
    {
      insert += "\nDO NOTHING";
      return insert;
    }

    QuoteHandler quoter = getQuoteHandler();
    List<ColumnIdentifier> keyCols = getKeyColumns();

    insert += " (";
    for (int i=0; i < keyCols.size(); i++)
    {
      if (i > 0) insert += ",";
      String colname = keyCols.get(i).getDisplayName();
      colname = quoter.quoteObjectname(colname);
      insert += colname;
    }

    insert += ")\nDO UPDATE\n  SET ";

    int colCount = 0;
    List<ColumnIdentifier> keys = getKeyColumns();
    for (int i=0; i < targetColumns.size(); i++)
    {
      ColumnIdentifier col = targetColumns.get(i);
      if (keys.contains(col) ) continue;
      if (colCount > 0) insert += ",\n      ";
      String colname = quoter.quoteObjectname(col.getDisplayName());
      insert += colname + " = EXCLUDED." + colname;
      colCount ++;
    }

    if (columnConstants != null)
    {
      for (int i=0; i < columnConstants.getColumnCount(); i++)
      {
        ColumnIdentifier col = columnConstants.getColumn(i);
        if (keys.contains(col)) continue;
        if (colCount > 0) insert += ",\n      ";
        String colname = quoter.quoteObjectname(col.getDisplayName());
        insert += colname + " = EXCLUDED." + colname;
        colCount++;
      }
    }

    return insert;
  }

  protected String createH2Upsert(ConstantColumnValues columnConstants)
  {
    String insert = createInsertStatement(columnConstants, null);
    insert = insert.replace("INSERT INTO", "MERGE INTO");
    return insert;
  }

  protected String createFirebirdUpsert(ConstantColumnValues columnConstants)
  {
    String insert = createInsertStatement(columnConstants, null);
    insert = insert.replace("INSERT INTO", "UPDATE OR INSERT INTO");

    QuoteHandler quoter = getQuoteHandler();
    List<ColumnIdentifier> keyCols = getKeyColumns();
    if (CollectionUtil.isNonEmpty(keyCols))
    {
      insert += "\nMATCHING (";
      for (int i = 0; i < keyCols.size(); i++)
      {
        if (i > 0) insert += ",";
        String colname = keyCols.get(i).getDisplayName();
        colname = quoter.quoteObjectname(colname);
        insert += colname;
      }
      insert += ")";
    }
    return insert;
  }

  protected String createHSQLUpsert(ConstantColumnValues columnConstants, boolean insertOnly)
  {
    return createStandardMerge(columnConstants, insertOnly, "USING ");
  }

  protected String createDB2LuWUpsert(ConstantColumnValues columnConstants, boolean insertOnly)
  {
    return createStandardMerge(columnConstants, insertOnly, "USING TABLE");
  }

  protected String createDB2zOSUpsert(ConstantColumnValues columnConstants, boolean insertOnly)
  {
    return createStandardMerge(columnConstants, insertOnly, "USING ");
  }

  protected String createSqlServerUpsert(ConstantColumnValues columnConstants, boolean insertOnly)
  {
    return createStandardMerge(columnConstants, insertOnly, "USING ") + ";";
  }

  protected String createStandardMerge(ConstantColumnValues columnConstants, boolean insertOnly, String usingKeyword)
  {
    StringBuilder text = new StringBuilder(targetColumns.size() * 50);

    text.append("MERGE INTO ");
    text.append(targetTable.getFullyQualifiedName(dbConn));
    text.append(" AS tg\n" + usingKeyword + "(\n  VALUES (");

    QuoteHandler quoter = getQuoteHandler();

    int colIndex = 0;
    for (int i=0; i < getColCount(); i++)
    {
      if (colIndex > 0) text.append(',');

      if (columnConstants != null && columnConstants.isFunctionCall(i))
      {
        text.append(columnConstants.getFunctionLiteral(i));
      }
      else
      {
        text.append('?');
      }
      colIndex ++;
    }
    text.append(")\n) AS vals (");
    colIndex = 0;
    for (int i=0; i < getColCount(); i++)
    {
      if (colIndex > 0) text.append(',');
      String colname = targetColumns.get(i).getDisplayName();
      colname = quoter.quoteObjectname(colname);
      text.append(colname);
      colIndex ++;
    }
    text.append(")\n  ON ");
    colIndex = 0;

    List<ColumnIdentifier> keyCols = getKeyColumns();
    for (int i=0; i < keyCols.size(); i++)
    {
      if (colIndex > 0) text.append(" AND ");
      String colname = keyCols.get(i).getDisplayName();
      colname = quoter.quoteObjectname(colname);
      text.append("tg.");
      text.append(colname);
      text.append(" = vals.");
      text.append(colname);
      colIndex ++;
    }
    appendMergeMatchSection(text, insertOnly, columnConstants);
    return text.toString();
  }

  private void appendMergeMatchSection(StringBuilder text, boolean insertOnly, ConstantColumnValues columnConstants)
  {
    QuoteHandler quoter = getQuoteHandler();
    int colIndex = 0;

    if (!insertOnly)
    {
      text.append("\nWHEN MATCHED THEN UPDATE\n  SET ");
      for (int i=0; i < getColCount(); i++)
      {
        ColumnIdentifier col = this.targetColumns.get(i);
        if (isKeyColumn(col)) continue;

        String colname = targetColumns.get(i).getDisplayName();
        colname = quoter.quoteObjectname(colname);

        if (colIndex > 0) text.append(",\n      ");
        text.append("tg.");
        text.append(colname);
        text.append(" = vals.");
        text.append(colname);
        colIndex ++;
      }

      if (columnConstants != null)
      {
        int colCount = columnConstants.getColumnCount();
        for (int i=0; i < colCount; i++)
        {
          ColumnIdentifier col = columnConstants.getColumn(i);
        if (isKeyColumn(col)) continue;

        String colname = col.getColumnName();
        colname = quoter.quoteObjectname(colname);

        if (colIndex > 0) text.append(",\n      ");
        text.append("tg.");
        text.append(colname);
        text.append(" = vals.");
        text.append(colname);
        colIndex ++;
        }
      }
    }

    StringBuilder insertCols = new StringBuilder(targetColumns.size() * 20);
    StringBuilder valueCols = new StringBuilder(targetColumns.size() * 20);

    colIndex = 0;
    for (int i=0; i < getColCount(); i++)
    {
      String colname = targetColumns.get(i).getDisplayName();
      colname = quoter.quoteObjectname(colname);

      if (colIndex > 0)
      {
        insertCols.append(", ");
        valueCols.append(", ");
      }
      insertCols.append(colname);
      valueCols.append("vals.");
      valueCols.append(colname);
      colIndex ++;
    }

    if (columnConstants != null)
    {
      int colCount = columnConstants.getColumnCount();
      for (int i = 0; i < colCount; i++)
      {
        ColumnIdentifier col = columnConstants.getColumn(i);

        String colname = col.getColumnName();
        colname = quoter.quoteObjectname(colname);

        if (colIndex > 0)
        {
          insertCols.append(", ");
          valueCols.append(", ");
        }
        insertCols.append(colname);
        valueCols.append("vals.");
        valueCols.append(colname);
        colIndex++;
      }
    }

    text.append("\nWHEN NOT MATCHED THEN INSERT\n  (");
    text.append(insertCols);
    text.append(")\nVALUES\n  (");
    text.append(valueCols);
    text.append(")");
  }

  protected String createOracleMerge(ConstantColumnValues columnConstants)
  {
    StringBuilder text = new StringBuilder(targetColumns.size() * 50);

    text.append("MERGE INTO ");
    text.append(targetTable.getFullyQualifiedName(dbConn));
    text.append(" tg\n USING (\n  SELECT ");

    QuoteHandler quoter = getQuoteHandler();

    for (int i=0; i < targetColumns.size(); i++)
    {
      if (i > 0) text.append(',');
      text.append('?');
      String colname = targetColumns.get(i).getDisplayName();
      text.append(" AS ");
      text.append(quoter.quoteObjectname(colname));
    }

    if (columnConstants != null)
    {
      int colCount = columnConstants.getColumnCount();
      for (int i=0; i < colCount; i++)
      {
        ColumnIdentifier col = columnConstants.getColumn(i);
        text.append(',');
        if (columnConstants.isFunctionCall(i))
        {
          text.append(columnConstants.getFunctionLiteral(i));
        }
        else
        {
          text.append('?');
        }
        String colname = col.getColumnName();
        text.append(" AS ");
        text.append(quoter.quoteObjectname(colname));
      }
    }
    text.append(" FROM DUAL\n) vals ON (");

    int colIndex = 0;
    List<ColumnIdentifier> keyCols = getKeyColumns();
    for (int i=0; i < keyCols.size(); i++)
    {
      if (colIndex > 0) text.append(" AND ");
      String colname = keyCols.get(i).getDisplayName();
      colname = quoter.quoteObjectname(colname);
      text.append("tg.");
      text.append(colname);
      text.append(" = vals.");
      text.append(colname);
      colIndex ++;
    }
    text.append(')');
    appendMergeMatchSection(text, false, columnConstants);
    return text.toString();
  }

  private boolean isKeyColumn(ColumnIdentifier col)
  {
    if (col.isPkColumn()) return true;
    List<ColumnIdentifier> keyCols = getKeyColumns();

    if (keyCols != null)
    {
      return keyCols.contains(col);
    }
    return false;
  }

  private String createMySQLUpsert(ConstantColumnValues columnConstants, String insertSqlStart, boolean useIgnore)
  {
    String insert = createInsertStatement(columnConstants, insertSqlStart);
    QuoteHandler quoter = getQuoteHandler();

    insert += "\nON DUPLICATE KEY UPDATE \n  ";
    if (useIgnore)
    {
      // Just add a dummy update for one column
      // Apparently this is more efficient and stable than using insert ... ignore
      String colname = targetColumns.get(0).getDisplayName();
      colname = quoter.quoteObjectname(colname);
      insert += " " + colname + " = " + colname;
    }
    else
    {
      for (int i=0; i < targetColumns.size(); i++)
      {
        if (i > 0) insert += ",\n  ";
        String colname = targetColumns.get(i).getDisplayName();
        colname = quoter.quoteObjectname(colname);
        insert += colname + " = VALUES(" + colname + ")";
      }
    }
    return insert;
  }

  private List<ColumnIdentifier> getKeyColumns()
  {
    if (CollectionUtil.isEmpty(keyColumns)) return getPKColumns();
    return keyColumns;
  }

  private List<ColumnIdentifier> getPKColumns()
  {
    List<ColumnIdentifier> keys = new ArrayList<>(5);
    for (ColumnIdentifier col : targetColumns)
    {
      if (col.isPkColumn())
      {
        keys.add(col);
      }
    }
    return keys;
  }

  private boolean hasRealPK()
  {
    List<ColumnIdentifier> keyCols = getKeyColumns();
    if (CollectionUtil.isEmpty(keyCols)) return false;
    for (ColumnIdentifier col : keyCols)
    {
      if (!col.isPkColumn()) return false;
    }
    return true;
  }

  private int getColCount()
  {
    if (targetColumns == null) return 0;
    return targetColumns.size();
  }

  private List<ColumnIdentifier> createColumnList(List<ColumnIdentifier> columns, ColumnFilter filter, boolean adjustCase)
  {
    DbMetadata meta = dbConn != null ? dbConn.getMetadata() : null;
    QuoteHandler quoter = getQuoteHandler();

    List<ColumnIdentifier> newCols = new ArrayList<>(columns.size());
    for (ColumnIdentifier col : columns)
    {
      if (filter != null && filter.ignoreColumn(col)) continue;
      ColumnIdentifier copy = col.createCopy();
      if (adjustCase)
      {
        String colname = quoter.removeQuotes(copy.getColumnName());
        if (meta != null)
        {
          colname = meta.adjustObjectnameCase(colname);
        }
        copy.setColumnName(colname);
      }
      newCols.add(copy);
    }
    return newCols;
  }

}

