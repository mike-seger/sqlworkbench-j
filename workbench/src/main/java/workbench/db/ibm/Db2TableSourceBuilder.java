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
package workbench.db.ibm;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.DropType;
import workbench.db.JdbcUtils;
import workbench.db.ObjectSourceOptions;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class Db2TableSourceBuilder
  extends TableSourceBuilder
{
  private final boolean checkHistoryTable;
  private final boolean readTablespaceInfo;
  private boolean useSystemProc = false;
  private final DBID dbid;

  public Db2TableSourceBuilder(WbConnection con)
  {
    super(con);
    dbid = DBID.fromConnection(dbConnection);
    checkHistoryTable = (dbid == DBID.DB2_LUW && JdbcUtils.hasMinimumServerVersion(con, "10.1"));
    readTablespaceInfo = (dbid == DBID.DB2_LUW && con.getDbSettings().getBoolProperty("table_options.retrieve", true));
    useSystemProc = Db2GenerateSQL.useGenerateSQLProc(dbConnection, Db2GenerateSQL.TYPE_TABLE);
  }

  @Override
  public void readTableOptions(TableIdentifier table, List<ColumnIdentifier> columns)
  {
    if (table == null) return;
    if (table.getSourceOptions().isInitialized()) return;

    readPeriods(table);
    readTablespaceInfo(table);
    table.getSourceOptions().setInitialized();
  }

  private void readPeriods(TableIdentifier table)
  {
    if (!checkHistoryTable) return;

    String sql =
      "select periodname, \n" +
      "       begincolname, \n" +
      "       endcolname, \n" +
      "       historytabschema, \n" +
      "       historytabname \n " +
      "from syscat.periods \n" +
      "where tabschema = ? \n" +
      "  and tabname = ? ";
    PreparedStatement stmt = null;
    ResultSet rs = null;

    String tablename = table.getTableName();
    String schema = table.getSchema();

    LogMgr.logMetadataSql(new CallerInfo(){}, "period definitions", sql, schema, tablename);

    try
    {
      stmt = this.dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, schema);
      stmt.setString(2, tablename);
      rs = stmt.executeQuery();

      if (rs.next())
      {
        String period = rs.getString(1);
        String begin = rs.getString(2);
        String end = rs.getString(3);
        String histSchema = rs.getString(4);
        String histTab = rs.getString(5);
        TableIdentifier histTable = new TableIdentifier(histSchema, histTab);

        ObjectSourceOptions options = table.getSourceOptions();

        String inline = "PERIOD " + period + " (" + begin + ", " + end + ")";
        options.setInlineOption(inline);

        String addSql =
          "ALTER TABLE " + table.getTableExpression(dbConnection) + "\n" +
          "  ADD VERSIONING USE HISTORY TABLE " + histTable.getTableExpression(dbConnection)  + ";\n";
        options.setAdditionalSql(addSql);
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "period definitions", sql, schema, tablename);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  private void readTablespaceInfo(TableIdentifier table)
  {
    if (!readTablespaceInfo) return;

    String sql =
      "select tbspace, index_tbspace, long_tbspace, \n" +
      "       property, \n" +
      "       compression, rowcompmode, \n" +
      "       datacapture, \n" +
      "       encoding_scheme, \n" +
      "       droprule \n" +
      "from syscat.tables \n" +
      "where tabschema = ? \n" +
      "  and tabname = ? ";
    PreparedStatement stmt = null;
    ResultSet rs = null;

    String tablename = table.getTableName();
    String schema = table.getSchema();

    LogMgr.logMetadataSql(new CallerInfo(){}, "table options", sql, schema, tablename);

    try
    {
      stmt = this.dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, schema);
      stmt.setString(2, tablename);
      rs = stmt.executeQuery();

      if (rs.next())
      {
        String tbSpace = rs.getString("TBSPACE");
        String idxSpace = StringUtil.trimToNull(rs.getString("INDEX_TBSPACE"));
        String longSpace = StringUtil.trimToNull(rs.getString("LONG_TBSPACE"));
        String props = rs.getString("PROPERTY");
        String compression = rs.getString("COMPRESSION");
        String rowMode = rs.getString("ROWCOMPMODE");
        String capture = rs.getString("DATACAPTURE");
        String encoding = rs.getString("ENCODING_SCHEME");
        String dropRule = rs.getString("DROPRULE");

        ObjectSourceOptions options = table.getSourceOptions();
        options.addConfigSetting("tablespace", tbSpace);
        String addSql = "IN " + dbConnection.getMetadata().quoteObjectname(tbSpace);

        if (idxSpace != null)
        {
          options.addConfigSetting("index_tablespace", idxSpace);
          addSql += " INDEX IN " + dbConnection.getMetadata().quoteObjectname(idxSpace);
        }
        if (longSpace != null)
        {
          options.addConfigSetting("long_tablespace", longSpace);
          addSql += " LONG IN " + dbConnection.getMetadata().quoteObjectname(idxSpace);
        }

        if (props != null && props.length() > 19)
        {
          String columnTable = props.substring(19,20);
          if ("Y".equals(columnTable))
          {
            options.addConfigSetting("organize", "column");
            addSql += "\nORGANIZE BY COLUMN";
          }
        }

        String compressDef = "";

        if ("R".equals(compression) || "B".equals(compression))
        {
          compressDef = "COMPRESS YES";
          if ("A".equals(rowMode))
          {
            options.addConfigSetting("compress", "adaptive");
            compressDef += " ADAPTIVE";
          }
          else if ("S".equals(rowMode))
          {
            options.addConfigSetting("compress", "static");
            compressDef += " STATIC";
          }
          if ("B".equals(compression))
          {
            compressDef += " VALUE COMPRESSION";
            options.addConfigSetting("value_compression", "y");
          }
          addSql += "\n" + compressDef;
        }

        if ("Y".equals(capture))
        {
          options.addConfigSetting("datacapture", "y");
          addSql += "\nDATA CAPTURE CHANGES";
        }

        if ("A".equals(encoding))
        {
          options.addConfigSetting("ccsid", "ascii");
          addSql += "\nCCSID ASCII";
        }
        if ("U".equals(encoding))
        {
          options.addConfigSetting("ccsid", "unicode");
          addSql += "\nCCSID UNICODE";
        }

        if ("R".equals(dropRule))
        {
          options.addConfigSetting("droprule", "restrict");
          addSql += "\nWITH RESTRICT ON DROP";
        }
        table.getSourceOptions().setTableOption(addSql);
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "table options", sql, schema, tablename);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  @Override
  public String getNativeTableSource(TableIdentifier table, DropType dropType)
  {
    if (useSystemProc)
    {
      return retrieveTableSource(table, dropType);
    }
    return super.getNativeTableSource(table, dropType);
  }

  @Override
  protected boolean shouldIncludeIndexInTableSource()
  {
    if (useSystemProc)
    {
      final String propIndexIncluded = "generate_sql.table.includes.index";
      if (dbConnection.getDbSettings().isPropertySet(propIndexIncluded))
      {
        boolean indexIncluded = dbConnection.getDbSettings().getBoolProperty(propIndexIncluded, false);
        return !indexIncluded;
      }

      // Starting with 7.4, GENERATE_SQL includes the indexes
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "7.4"))
      {
        return false;
      }
      return true;
    }
    return super.shouldIncludeIndexInTableSource();
  }

  @Override
  protected boolean shouldIncludeFKInTableSource()
  {
    if (useSystemProc)
    {
      return false;
    }
    return super.shouldIncludeFKInTableSource();
  }

  @Override
  protected boolean shouldIncludeGrantsInTableSource()
  {
    if (useSystemProc)
    {
      return false;
    }
    return super.shouldIncludeGrantsInTableSource();
  }

  @Override
  protected boolean shouldIncludeCommentInTableSource()
  {
    if (useSystemProc)
    {
      return false;
    }
    return super.shouldIncludeGrantsInTableSource();
  }

  public String retrieveTableSource(TableIdentifier tbl, DropType dropType)
  {
    if (tbl == null) return null;
    Db2GenerateSQL gen = new Db2GenerateSQL(dbConnection);
    gen.setGenerateRecreate(dropType != DropType.none);
    CharSequence source = gen.getTableSource(tbl.getRawSchema(), tbl.getRawTableName());
    return source == null ? null : source.toString();
  }

}
