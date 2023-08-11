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
package workbench.db.mssql;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DependencyNode;
import workbench.db.IndexDefinition;
import workbench.db.ObjectSourceOptions;
import workbench.db.PkDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;
import workbench.db.sqltemplates.TemplateHandler;

import workbench.db.JdbcUtils;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class SqlServerTableSourceBuilder
  extends TableSourceBuilder
{
  public static final String CLUSTERED_PLACEHOLDER = "%clustered_attribute%";
  private static final String OPTION_KEY_SCHEME = "partition_scheme";
  private static final String OPTION_KEY_COLUMNS = "partition_columns";

  public SqlServerTableSourceBuilder(WbConnection con)
  {
    super(con);
  }

  @Override
  public CharSequence getPkSource(TableIdentifier table, PkDefinition pk, boolean forInlineUse, boolean useFQN)
  {
    CharSequence pkSource = super.getPkSource(table, pk, forInlineUse, useFQN);
    if (StringUtil.isEmpty(pkSource))
    {
      return pkSource;
    }

    String sql = pkSource.toString();
    String indexType = null;
    if (pk.getPkIndexDefinition() != null)
    {
      String type = pk.getPkIndexDefinition().getIndexType();
      if ("NORMAL".equals(type))
      {
        indexType = "NONCLUSTERED";
      }
      else
      {
        indexType = "CLUSTERED";
      }
    }

    if (indexType == null)
    {
      sql = TemplateHandler.removePlaceholder(sql, CLUSTERED_PLACEHOLDER, true);
    }
    else
    {
      sql = TemplateHandler.replacePlaceholder(sql, CLUSTERED_PLACEHOLDER, indexType, true);
    }
    return sql;
  }

  @Override
  protected String getAdditionalFkSql(TableIdentifier table, DependencyNode fk, String template)
  {
    if (!fk.isValidated())
    {
      template = template.replace("%nocheck%", "WITH NOCHECK ");
    }
    else
    {
      template = template.replace("%nocheck%", "");
    }

    if (!fk.isEnabled())
    {
      template += "\nALTER TABLE " + table.getObjectExpression(dbConnection) + " NOCHECK CONSTRAINT " + dbConnection.getMetadata().quoteObjectname(fk.getFkName());
    }
    return template;
  }

  @Override
  public String getAdditionalTableInfo(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList)
  {
    if (SqlServerUtil.isSqlServer2012(dbConnection) && !table.getSourceOptions().isInitialized())
    {
      return readExtendeStats(table);
    }
    return null;
  }

  @Override
  public void readTableOptions(TableIdentifier table, List<ColumnIdentifier> columns)
  {
    if (SqlServerUtil.supportsPartitioning(dbConnection))
    {
      readPartitionDefinition(table);
    }
  }

  private void readPartitionDefinition(TableIdentifier table)
  {
    String getPartitionInfo =
      "SELECT ps.name as partition_scheme,\n" +
      "       c.name AS column_name\n" +
      "FROM sys.tables AS t   \n" +
      "  JOIN sys.indexes AS i ON t.object_id = i.object_id AND i.type <= 1 \n" +
      "  JOIN sys.partition_schemes AS ps ON ps.data_space_id = i.data_space_id \n" +
      "  JOIN sys.index_columns AS ic \n" +
      "    ON ic.object_id = i.object_id   \n" +
      "   AND ic.index_id = i.index_id   \n" +
      "   AND ic.partition_ordinal >= 1\n" +
      "  JOIN sys.columns AS c \n" +
      "    ON t.object_id = c.object_id   \n" +
      "   AND ic.column_id = c.column_id   \n" +
      "WHERE t.object_id = object_id(?)\n" +
      "ORDER BY ic.partition_ordinal";

    final CallerInfo ci = new CallerInfo(){};
    PreparedStatement pstmt = null;
    ResultSet rs = null;

    String tname = table.getTableExpression(dbConnection);
    LogMgr.logMetadataSql(ci, "partition info", getPartitionInfo, tname);
    String scheme = null;
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(getPartitionInfo);
      pstmt.setString(1, tname);
      rs = pstmt.executeQuery();
      int colNr = 0;
      String columns = "";
      while (rs.next())
      {
        if (colNr == 0)
        {
          scheme = rs.getString(1);
        }
        else
        {
          columns += ",";
        }
        columns += rs.getString(2);
        colNr ++;
      }
      if (scheme != null && columns != null)
      {
        table.getSourceOptions().setTableOption("ON " + scheme + " (" + columns + ")");
        table.getSourceOptions().addConfigSetting(OPTION_KEY_SCHEME, scheme);
        table.getSourceOptions().addConfigSetting(OPTION_KEY_COLUMNS, columns);
        readPartitionSchemeAndFunction(table);

        table.getSourceOptions().setInitialized();
      }
    }
    catch (Throwable th)
    {
      LogMgr.logMetadataError(ci, th, "partition info", getPartitionInfo, tname);
    }
  }

  private void readPartitionSchemeAndFunction(TableIdentifier table)
  {
    SqlServerPartitionReader reader = new SqlServerPartitionReader(dbConnection);
    PartitionFunction function = reader.getFunctionForTable(table);
    if (function != null)
    {
      table.getSourceOptions().appendAdditionalSql("-- partitioning details");
      table.getSourceOptions().appendAdditionalSql(function.getSource());
    }
    PartitionScheme scheme = reader.getSchemeForTable(table);
    if (scheme != null)
    {
      table.getSourceOptions().appendAdditionalSql("\n" + scheme.getSource());
    }
  }

  private String readExtendeStats(TableIdentifier table)
  {
    if (table == null) return null;

    String sql =
      "select st.name as stats_name, c.name as column_name, st.has_filter, st.filter_definition\n" +
      "from sys.stats st\n" +
      "  join sys.stats_columns sc on sc.stats_id = st.stats_id and sc.object_id = st.object_id\n" +
      "  join sys.columns c on c.column_id = sc.column_id and c.object_id = st.object_id\n" +
      "where st.user_created = 1 \n" +
      "  and st.object_id = object_id(?) \n" +
      "order by st.stats_id, sc.stats_column_id";

    ResultSet rs = null;
    PreparedStatement pstmt = null;

    StringBuilder result = new StringBuilder();

    String tableName = table.getTableExpression(dbConnection);
    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logMetadataSql(ci, "column statistics", sql, tableName);

    ObjectSourceOptions option = table.getSourceOptions();

    String lastStat = null;
    String currentFilter = null;
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getFullyQualifiedName(dbConnection));
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String statName = rs.getString(1);
        String colName = rs.getString(2);
        String filter = rs.getString(4);
        if (lastStat == null || !lastStat.equals(statName))
        {
          if (result.length() > 0)
          {
            result.append(")");
            if (currentFilter != null)
            {
              result.append("\nWHERE ");
              result.append(currentFilter);
            }
            result.append(";\n\n");
          }
          result.append("CREATE STATISTICS ");
          result.append(dbConnection.getMetadata().quoteObjectname(statName));
          result.append("\n  ON ");
          result.append(tableName);
          result.append("(");
          result.append(colName);
          currentFilter = filter;
          lastStat = statName;
        }
        else
        {
          result.append(",");
          result.append(colName);
        }
      }
      if (result.length() > 0)
      {
        result.append(")");
        if (currentFilter != null)
        {
          result.append("\nWHERE ");
          result.append(currentFilter);
        }
        result.append(";");
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(ci, e, "column statistics", sql, tableName);
      result = null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    if (result.length() > 0)
    {
      option.addConfigSetting("column_statistics", result.toString());
    }

    if (result.length() == 0) return null;
    return "\n" + result.toString();
  }
}
