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
package workbench.db.postgres;

import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DbMetadata;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListCleaner;
import workbench.db.ObjectListDataStore;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresObjectListCleaner
  implements ObjectListCleaner
{

  public static final String CLEANUP_PARTITIONS_PROP = "partitions.tablelist.remove";
  private static final String[] BASE_TYPES = new String[] {"TABLE", "PARTITIONED TABLE"};

  public boolean removePartitions()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.postgresql." + CLEANUP_PARTITIONS_PROP, false);
  }

  @Override
  public void cleanupObjectList(WbConnection con, ObjectListDataStore result,
                                String catalogPattern, String schemaPattern, String objectNamePattern, String[] requestedTypes)
  {
    if (DbMetadata.typesIncluded(BASE_TYPES, requestedTypes) && removePartitions())
    {
      removePartitions(con, result);
    }
    if (con.getDbSettings().returnAccessibleTablesOnly())
    {
      removeInaccessible(con, result);
    }
  }

  private void removePartitions(WbConnection con, ObjectListDataStore result)
  {
    if (result.getRowCount() == 0) return;

    List<TableIdentifier> partitions = getAllPartitions(con);
    if (CollectionUtil.isEmpty(partitions)) return;

    int rowCount = result.getRowCount();
    for (int row = rowCount - 1; row >= 0; row --)
    {
      String schema = result.getSchema(row);
      String table = result.getObjectName(row);
      TableIdentifier tbl = new TableIdentifier(schema, table);
      if (TableIdentifier.findTableByNameAndSchema(partitions, tbl) != null)
      {
        LogMgr.logDebug(new CallerInfo(){}, "Removing: " + schema + "." + table);
        result.deleteRow(row);
      }
    }
  }

  private List<TableIdentifier> getAllPartitions(WbConnection conn)
  {
    List<TableIdentifier> partitions = new ArrayList<>();
    if (!JdbcUtils.hasMinimumServerVersion(conn, "11")) return partitions;

    String sql =
      "-- SQL Workbench/J \n" +
      "with recursive inh as ( \n" +
      "\n" +
      "  select i.inhrelid, i.inhparent\n" +
      "  from pg_catalog.pg_inherits i  \n" +
      "  where i.inhparent in (select partrelid from pg_partitioned_table)\n" +
      "\n" +
      "  union all \n" +
      "\n" +
      "  select i.inhrelid, i.inhparent\n" +
      "  from inh \n" +
      "    join pg_catalog.pg_inherits i on inh.inhrelid = i.inhparent\n" +
      ") \n" +
      "select c.relnamespace::regnamespace::text as partition_schema, \n" +
      "       c.relname as partition_name \n" +
      "from inh \n" +
      "  join pg_catalog.pg_class c on inh.inhrelid = c.oid";

    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    long start = System.currentTimeMillis();
    try
    {
      sp = conn.setSavepoint();

      stmt = conn.createStatementForQuery();
      rs = stmt.executeQuery(sql);

      LogMgr.logMetadataSql(new CallerInfo(){}, "table partitions", sql);

      while (rs.next())
      {
        String schema = rs.getString(1);
        String name = rs.getString(2);
        partitions.add(new TableIdentifier(schema, name));
      }

      conn.releaseSavepoint(sp);
    }
    catch (Exception ex)
    {
      conn.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "table partitions", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Reading all partitions took: " + duration + "ms");
    return partitions;
  }

  /**
   * Removes all tables (and table like objects) from the data store
   * where the current user does not have the SELECT privilege.
   *
   * @param conn the database connection
   * @param result the list of tables to check
   */
  public void removeInaccessible(WbConnection conn, ObjectListDataStore result)
  {
    if (result.getRowCount() == 0) return;

    long start = System.currentTimeMillis();

    String tableList = buildTableList(result);
    if (tableList.isEmpty()) return;

    final CallerInfo ci = new CallerInfo(){};

    String sql =
      "with table_list (schemaname, tablename) as (\n" +
      " values " + tableList  +
      "\n)\n" +
      "select s.nspname, t.relname\n" +
      "from pg_class t\n" +
      "  join pg_namespace s on s.oid = t.relnamespace\n" +
      "  join table_list l on (l.schemaname, l.tablename) = (s.nspname, t.relname) \n" +
      "where not has_table_privilege(t.oid, 'select')";

    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    List<TableIdentifier> noPrivs = new ArrayList<>();
    try
    {
      sp = conn.setSavepoint();

      stmt = conn.createStatementForQuery();
      rs = stmt.executeQuery(sql);

      LogMgr.logMetadataSql(ci, "table permissions", sql);

      while (rs.next())
      {
        String schema = rs.getString(1);
        String table = rs.getString(2);
        noPrivs.add(new TableIdentifier(schema,table));
      }
      conn.releaseSavepoint(sp);
    }
    catch (Exception ex)
    {
      noPrivs.clear();
      conn.rollback(sp);
      LogMgr.logMetadataError(ci, ex, "table permissions", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    int rowCount = result.getRowCount();
    List<String> removed = new ArrayList<>();
    for (int row=rowCount - 1; row >= 0; row --)
    {
      String name = result.getObjectName(row);
      String schema = result.getSchema(row);
      TableIdentifier tbl = new TableIdentifier(schema, name);
      if (TableIdentifier.findTableByNameAndSchema(noPrivs, tbl) != null)
      {
        result.deleteRow(row);
        removed.add(tbl.getTableExpression());
      }
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(ci, "The following tables were removed from the result because the current user has no select privilege: " + StringUtil.listToString(removed, "\n  ", false));
    LogMgr.logInfo(ci, "Checking table permissions took: " + duration + "ms");
  }

  private String buildTableList(ObjectListDataStore result)
  {
    final Set<String> types = CollectionUtil.caseInsensitiveSet("TABLE", "MATERIALIZED VIEW", "FOREIGN TABLE", "VIEW");
    StringBuilder list = new StringBuilder(result.getRowCount() * 60);

    try
    {
      int numTables = 0;

      for (int row=0; row < result.getRowCount(); row ++)
      {
        String type = result.getType(row);
        if (types.contains(type))
        {
          String table = SqlUtil.escapeQuotes(result.getObjectName(row));
          String schema = SqlUtil.escapeQuotes(result.getSchema(row));

          if (numTables > 0) list.append(',');
          if (numTables % 5 == 0) list.append("\n    ");

          numTables++;
          list.append("('" + schema + "','" + table + "')");
        }
      }
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not build table list", ex);
    }

    return list.toString();
  }

}
