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
package workbench.db.oracle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DataTypeResolver;
import workbench.db.DbMetadata;
import workbench.db.DbSettings;
import workbench.db.GeneratedColumnType;
import workbench.db.JdbcTableDefinitionReader;
import workbench.db.JdbcUtils;
import workbench.db.PkDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * A class to retrieve meta-information from an Oracle database.
 *
 * It fixes some problems with incorrectly returned data types.
 *
 * We will use our own statement only if the Oracle version is 9i or later and
 * if at least one of the following configuration properties are set:
 * <ul>
 *  <li>workbench.db.oracle.fixcharsemantics</li>
 *  <li>workbench.db.oracle.fixnvarchartype</li>
 * </ul>
 *
 * Additionally if the config property <tt>workbench.db.oracle.fixdatetype</tt> is
 * set to true, DATE columns will always be mapped to Timestamp objects when
 * retrieving data (see {@link OracleUtils#getMapDateToTimestamp(workbench.db.WbConnection) }
 * and {@link OracleDataTypeResolver#fixColumnType(int, java.lang.String)}
 *
 * @author Thomas Kellerer
 */
public class OracleTableDefinitionReader
  extends JdbcTableDefinitionReader
{
  private final OracleDataTypeResolver oraTypes;
  private final boolean is12c;
  private final boolean is23c;
  private final boolean isOracle8;
  private final String currentUser;

  public OracleTableDefinitionReader(WbConnection conn, OracleDataTypeResolver resolver)
  {
    super(conn);

    currentUser = conn.getCurrentUser();
    is12c = JdbcUtils.hasMinimumServerVersion(dbConnection, "12.1");
    is23c = JdbcUtils.hasMinimumServerVersion(dbConnection, "23.0");
    isOracle8 = JdbcUtils.hasMinimumServerVersion(dbConnection, "8.0");

    // The incorrectly reported search string escape bug was fixed with 11.2
    // The 11.1 and earlier drivers do not report the correct escape character and thus
    // escaping in DbMetadata doesn't return anything if the username (schema) contains an underscore
    if (!JdbcUtils.hasMiniumDriverVersion(conn.getSqlConnection(), "11.2")
      && Settings.getInstance().getBoolProperty("workbench.db.oracle.fixescapebug", true)
      && Settings.getInstance().getProperty("workbench.db.oracle.searchstringescape", null) == null)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Old Oracle JDBC driver detected. Turning off wildcard handling for objects retrieval to work around driver bug");
      System.setProperty("workbench.db.oracle.metadata.retrieval.wildcards", "false");
      System.setProperty("workbench.db.oracle.escape.searchstrings", "false");
    }
    oraTypes = resolver;
  }

  private boolean useOwnSql()
  {
    boolean fixNVarchar = Settings.getInstance().getBoolProperty("workbench.db.oracle.fixnvarchartype", true);
    boolean checkCharSemantics = Settings.getInstance().getBoolProperty("workbench.db.oracle.fixcharsemantics", true);

    // new property that controls using our own SQL
    boolean useOwnSQL = Settings.getInstance().getBoolProperty("workbench.db.oracle.tablecolumns.custom_sql", fixNVarchar || checkCharSemantics);
    return isOracle8 && useOwnSQL;
  }

  @Override
  public List<ColumnIdentifier> getTableColumns(TableIdentifier table, DataTypeResolver typeResolver)
    throws SQLException
  {
    if (!useOwnSql())
    {
      return super.getTableColumns(table, typeResolver);
    }

    PkDefinition primaryKey = table.getPrimaryKey();
    Set<String> pkColumns = CollectionUtil.caseInsensitiveSet();

    if (primaryKey != null)
    {
      pkColumns.addAll(primaryKey.getColumns());
    }

    DbSettings dbSettings = dbConnection.getDbSettings();
    DbMetadata dbmeta = dbConnection.getMetadata();
    String schema = StringUtil.trimQuotes(table.getSchema());
    String tablename = StringUtil.trimQuotes(table.getTableName());

    List<ColumnIdentifier> columns = new ArrayList<>();

    ResultSet rs = null;
    PreparedStatement pstmt = null;

    boolean hasIdentity = false;

    long start = System.currentTimeMillis();

    try
    {
      pstmt = prepareColumnsStatement(schema, tablename);
      rs = pstmt.executeQuery();

      while (rs != null && rs.next())
      {
        String colName = rs.getString("COLUMN_NAME");
        int sqlType = rs.getInt("DATA_TYPE");
        String typeName = rs.getString("TYPE_NAME");
        ColumnIdentifier col = new ColumnIdentifier(dbmeta.quoteObjectname(colName), oraTypes.fixColumnType(sqlType, typeName));

        int size = rs.getInt("COLUMN_SIZE");
        if (rs.wasNull())
        {
          size = Integer.MAX_VALUE;
        }
        int digits = rs.getInt("DECIMAL_DIGITS");
        if (rs.wasNull()) digits = -1;

        String remarks = rs.getString("REMARKS");
        String defaultValue = rs.getString("COLUMN_DEF");
        if (defaultValue != null && dbSettings.trimDefaults())
        {
          defaultValue = defaultValue.trim();
        }

        int position = rs.getInt("ORDINAL_POSITION");

        String nullable = rs.getString("IS_NULLABLE");
        String byteOrChar = rs.getString("CHAR_USED");

        OracleDataTypeResolver.CharSemantics charSemantics = oraTypes.getDefaultCharSemantics();

        if (StringUtil.isEmpty(byteOrChar))
        {
          charSemantics = oraTypes.getDefaultCharSemantics();
        }
        else if ("B".equals(byteOrChar.trim()))
        {
          charSemantics = OracleDataTypeResolver.CharSemantics.Byte;
        }
        else if ("C".equals(byteOrChar.trim()))
        {
          charSemantics = OracleDataTypeResolver.CharSemantics.Char;
        }

        String identity = rs.getString("IDENTITY_COLUMN");
        boolean isIdentity = "YES".equalsIgnoreCase(identity);
        hasIdentity = hasIdentity || isIdentity;

        String virtual = rs.getString("VIRTUAL_COLUMN");
        boolean isVirtual = StringUtil.stringToBool(virtual);
        String display = oraTypes.getSqlTypeDisplay(typeName, sqlType, size, digits, charSemantics);

        col.setDbmsType(display);
        col.setIsPkColumn(pkColumns.contains(colName));
        col.setIsNullable("YES".equalsIgnoreCase(nullable));

        if (isVirtual && sqlType != Types.OTHER)
        {
          String exp = "GENERATED ALWAYS AS (" + defaultValue + ")";
          col.setGeneratedExpression(exp, GeneratedColumnType.computed);
        }
        else
        {
          String defOnNull = rs.getString("DEFAULT_ON_NULL");
          String defOnNullUpd = rs.getString("DEFAULT_ON_NULL_UPD");

          if (is23c && "YES".equals(defOnNull) && "YES".equals(defOnNullUpd))
          {
            col.setDefaultClause("DEFAULT ON NULL FOR INSERT AND UPDATE");
          }
          else if ("YES".equalsIgnoreCase(defOnNull))
          {
            col.setDefaultClause("DEFAULT ON NULL");
          }

          col.setDefaultValue(defaultValue);
        }
        col.setComment(remarks);
        col.setColumnSize(size);
        col.setDecimalDigits(digits);
        col.setPosition(position);
        if (isIdentity)
        {
          col.setGeneratedColumnType(GeneratedColumnType.identity);
        }
        columns.add(col);
      }
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    if (columns.size() > 0 && table.getType() == null)
    {
      table.setType("TABLE");
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Retrieving table columns for " + table.getTableExpression() + " took " + duration + "ms");

    if (hasIdentity)
    {
      retrieveIdentityColumns(columns, schema, tablename);
    }

    return columns;
  }

  private void retrieveIdentityColumns(List<ColumnIdentifier> columns, String owner, String table)
  {
    boolean useUserTables = OracleUtils.useUserSpecificCatalogs(currentUser, owner);

    String sql =
      "-- SQL Workbench/J \n" +
      "select column_name,  generation_type,  identity_options\n";

    if (useUserTables)
    {
      sql +=
        "from user_tab_identity_cols \n" +
        "where table_name = ? ";
    }
    else
    {
      sql +=
        "from all_tab_identity_cols \n" +
        "where table_name = ? \n" +
        "  and owner = ? ";
    }

    ResultSet rs = null;
    PreparedStatement pstmt = null;

    LogMgr.logMetadataSql(new CallerInfo(){}, "identity columns", sql, table, owner);

    long start = System.currentTimeMillis();

    OracleIdentityOptionParser parser = new OracleIdentityOptionParser();
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table);
      if (!useUserTables)
      {
        pstmt.setString(2, owner);
      }
      rs = pstmt.executeQuery();

      while (rs.next())
      {
        String column = rs.getString("column_name");
        ColumnIdentifier col = ColumnIdentifier.findColumnInList(columns, column);
        if (col != null)
        {
          String type = rs.getString("GENERATION_TYPE");
          String exp = "GENERATED " + type + " AS IDENTITY";
          String options = rs.getString("IDENTITY_OPTIONS");
          String addOptions = parser.getIdentitySequenceOptions(options);
          if (StringUtil.isNotBlank(addOptions))
          {
            exp += " " + addOptions;
          }
          col.setGeneratedExpression(exp, GeneratedColumnType.identity);
          col.setDefaultValue(null);
        }
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "identity columns", sql, table, owner);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }

    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Retrieving identity column information for " + owner + "." + table + " took " + duration + "ms");
  }

  public static String getSelectForColumnInfo(WbConnection conn, String alias)
  {
    String sql =
            getDecodeForDataType("tttt.data_type", OracleUtils.getMapDateToTimestamp(conn)) + " AS data_type, \n" +
      "     tttt.data_type AS type_name,  \n" +
      "     decode(tttt.data_type, 'VARCHAR', tttt.char_length, \n" +
      "                         'VARCHAR2', tttt.char_length, \n" +
      "                         'NVARCHAR', tttt.char_length, \n" +
      "                         'NVARCHAR2', tttt.char_length, \n" +
      "                         'CHAR', tttt.char_length, \n" +
      "                         'NCHAR', tttt.char_length, \n" +
      "                         'NUMBER', tttt.data_precision, \n" +
      "                         'FLOAT', tttt.data_precision, \n" +
      "                         'REAL', tttt.data_precision, \n" +
      "            tttt.data_length) AS column_size,  \n" +
      "     case \n" +
      "        when tttt.data_type = 'NUMBER' and tttt.data_precision is null then coalesce(tttt.data_scale,-127) \n" +
      "        else tttt.data_scale \n" +
      "     end AS decimal_digits,  \n" +
      "     DECODE(tttt.nullable, 'N', 'NO', 'YES') AS is_nullable ";
    return sql.replace("tttt", alias);

  }
  public static String getDecodeForDataType(String colname, boolean mapDateToTimestamp)
  {
      return
      "     DECODE(" + colname + ", \n" +
      "            'CHAR', " + Types.CHAR + ", \n" +
      "            'VARCHAR2', " + Types.VARCHAR + ", \n" +
      "            'NVARCHAR2', " + Types.NVARCHAR + ", \n" +
      "            'NCHAR', " + Types.NCHAR + ", \n" +
      "            'NUMBER', " + Types.DECIMAL + ", \n" +
      "            'LONG', " + Types.LONGVARCHAR + ", \n" +
      "            'DATE', " + (mapDateToTimestamp ? Types.TIMESTAMP : Types.DATE) + ", \n" +
      "            'RAW', " + Types.VARBINARY + ", \n" +
      "            'LONG RAW', " + Types.LONGVARBINARY + ", \n" +
      "            'BLOB', " + Types.BLOB + ", \n" +
      "            'CLOB', " + Types.CLOB + ", \n" +
      "            'NCLOB', " + Types.NCLOB + ", \n" +
      "            'ROWID', " + Types.ROWID + ", \n" +
      "            'BFILE', -13, \n" +
      "            'FLOAT', " + Types.FLOAT + ", \n" +
      "            'TIMESTAMP(6)', " + Types.TIMESTAMP + ", \n" +
      "            'TIMESTAMP(6) WITH TIME ZONE', -101, \n" +
      "            'TIMESTAMP(6) WITH LOCAL TIME ZONE', -102, \n" +
      "            'INTERVAL YEAR(2) TO MONTH', -103, \n" +
      "            'INTERVAL DAY(2) TO SECOND(6)', -104, \n" +
      "            'BINARY_FLOAT', 100, \n" +
      "            'BINARY_DOUBLE', 101, " + Types.OTHER + ")";
  }

  private PreparedStatement prepareColumnsStatement(String schema, String table)
    throws SQLException
  {
    boolean useUserTables = OracleUtils.useUserSpecificCatalogs(currentUser, schema);

    // Oracle 9 and above reports a wrong length if NLS_LENGTH_SEMANTICS is set to char
    // this statement fixes this problem and also removes the usage of LIKE
    // to speed up the retrieval.
    final String sql1 =
      "-- SQL Workbench/J \n" +
      "SELECT " + OracleUtils.getCacheHint() + " t.column_name AS column_name,  \n" +
            getSelectForColumnInfo(dbConnection, "t") + ", \n" +
            "     " + (is12c ? "t.identity_column" : " 'NO' AS IDENTITY_COLUMN") + ", \n" +
      "     " + (is12c ? "t.default_on_null" : " 'NO' AS DEFAULT_ON_NULL") + ", \n" +
      "     " + (is23c ? "t.default_on_null_upd" : " 'N/A' AS DEFAULT_ON_NULL_UPD") + ", \n";

    String sql2 =
      "     t.data_default AS column_def,  \n" +
      "     t.char_used, \n" +
      "     t.column_id AS ordinal_position,   \n";

    boolean includeVirtualColumns = JdbcUtils.hasMinimumServerVersion(dbConnection, "11.0");
    if (includeVirtualColumns)
    {
      // for some reason XMLTYPE columns and "table type" columns are returned with virtual_column = 'YES'
      // which seems like a bug in all_tab_cols....
      sql2 +=
        "     case \n" +
        "          when data_type <> 'XMLTYPE' and DATA_TYPE_OWNER is null THEN t.virtual_column \n" +
        "          else 'NO' \n" +
        "      end as virtual_column ";
    }
    else
    {
      sql2 +=
        "     null as virtual_column ";
    }

    if (includeVirtualColumns)
    {
      sql2 += (useUserTables ? "\nFROM user_tab_cols t" : "\nFROM all_tab_cols t");
    }
    else
    {
      sql2 += (useUserTables ? "\nFROM user_tab_columns t" : "\nFROM all_tab_columns t");
    }

    String where = "\nWHERE t.table_name = ? \n";
    if (!useUserTables)
    {
      where += "  AND t.owner = ? \n";
    }

    if (includeVirtualColumns)
    {
      where += "  AND t.hidden_column = 'NO' ";
    }
    final String comment_join = (useUserTables ?
        "\n  LEFT JOIN user_col_comments c ON t.table_name = c.table_name AND t.column_name = c.column_name" :
        "\n  LEFT JOIN all_col_comments c ON t.owner = c.owner AND t.table_name = c.table_name AND t.column_name = c.column_name");
    final String order = "\nORDER BY t.column_id";

    final String sql_comment = sql1 + "     c.comments AS remarks, \n" + sql2 + comment_join + where + order;
    final String sql_no_comment = sql1 + "       null AS remarks, \n" + sql2 + where + order;

    String sql;

    if (OracleUtils.getRemarksReporting(dbConnection))
    {
      sql = sql_comment;
    }
    else
    {
      sql = sql_no_comment;
    }

    // if the table name refers to a DBLink, we need to query the
    // catalog tables from the DBLink, not from the current connection
    int pos = table != null ? table.indexOf('@') : -1;

    if (pos > 0)
    {
      String dblink = table.substring(pos);
      table = table.substring(0, pos);
      sql = StringUtil.replace(sql, "all_tab_columns", "all_tab_columns" + dblink);
      sql = StringUtil.replace(sql, "all_col_comments", "all_col_comments" + dblink);
      String dblinkOwner = this.getDbLinkTargetSchema(dblink.substring(1), schema);
      if (StringUtil.isEmpty(schema) && !StringUtil.isEmpty(dblinkOwner))
      {
        schema = dblinkOwner;
      }
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "table columns", sql, table, schema);

    try
    {
      PreparedStatement stmt = OracleUtils.prepareQuery(dbConnection, sql);
      stmt.setString(1, table);
      if (!useUserTables)
      {
        stmt.setString(2, schema);
      }
      return stmt;
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "table columns", sql, table, schema);
      throw ex;
    }
  }

  private String getDbLinkTargetSchema(String dblink, String owner)
  {
    String sql;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    String linkOwner = null;

    // check if DB Link name contains a domain
    // If yes, use the link name directly
    if (dblink.indexOf('.') > 0)
    {
      sql = "SELECT /* SQLWorkbench */ username FROM all_db_links WHERE db_link = ? AND (owner = ? or owner = 'PUBLIC')";
    }
    else
    {
      // apparently Oracle stores all DB Links with the default domain
      // appended. I did not find a reliable way to retrieve the domain
      // name, so I'm using a like to retrieve the definition
      // hoping that there won't be two dblinks with the same name
      // but different domains
      sql = "SELECT /* SQLWorkbench */ username FROM all_db_links WHERE db_link like ? AND (owner = ? or owner = 'PUBLIC')";
      dblink += ".%";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "DBLINK target schema", sql);
    try
    {
      synchronized (dbConnection)
      {
        stmt = OracleUtils.prepareQuery(dbConnection, sql);
        stmt.setString(1, dblink);
        stmt.setString(2, owner);
        rs = stmt.executeQuery();
        if (rs.next())
        {
          linkOwner = rs.getString(1);
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "DBLINK target schema", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return linkOwner;
  }

}
