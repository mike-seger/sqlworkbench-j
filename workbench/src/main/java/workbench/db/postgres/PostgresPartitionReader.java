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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * A class to read the partition definition of a Postgres table.
 *
 * Before any information is returned, {@link #readPartitionInformation()} has to be called.
 * @author Thomas Kellerer
 */
public class PostgresPartitionReader
{
  public static final String OPTION_KEY_STRATEGY = "partition_strategy";
  public static final String OPTION_KEY_EXPRESSION = "partition_expression";

  private final TableIdentifier table;
  private final WbConnection dbConnection;
  private String strategy;
  private String partitionExpression;
  private String partitionDefinition;
  private List<PostgresPartition> partitions;

  public PostgresPartitionReader(TableIdentifier table, WbConnection conn)
  {
    Objects.requireNonNull(table, "A table must be specified");
    this.table = table;
    this.dbConnection = conn;
  }

  public List<PostgresPartition> getTablePartitions()
  {
    if (partitions == null)
    {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(partitions);
  }

  public String getStrategy()
  {
    return strategy;
  }

  public String getPartitionExpression()
  {
    return partitionExpression;
  }

  public String getPartitionDefinition()
  {
    return partitionDefinition;
  }

  /**
   * Returns the CREATE TABLE statements for all partitions of the table.
   *
   * This requires that {@link #readPartitionInformation()} has been called.
   */
  public String getCreatePartitions()
  {
    if (partitions == null) return null;

    String baseTable = table.getTableExpression(dbConnection);

    StringBuilder result = new StringBuilder(partitions.size() * 100);
    for (PostgresPartition part : partitions)
    {
      result.append(generatePartitionDDL(part, baseTable, dbConnection));
      result.append(";\n\n");
    }
    return result.toString();
  }

  public static String generatePartitionDDL(PostgresPartition partition, String baseTable, WbConnection dbConnection)
  {
    if (partition == null) return null;

    TableIdentifier parent = partition.getParentTable();

    String tableOf = parent == null ? baseTable : parent.getTableExpression(dbConnection);

    TableIdentifier name = new TableIdentifier(partition.getSchema(), partition.getObjectName());
    String partSQL =
      "CREATE TABLE " + name.getTableExpression(dbConnection) + "\n" +
      "  PARTITION OF " + tableOf + "\n  " + partition.getDefinition();

    if (partition.getSubPartitionDefinition() != null)
    {
      partSQL +=
        "\n  PARTITION BY " + partition.getSubPartitionStrategy() + " " + partition.getSubPartitionDefinition();
    }

    return partSQL;
  }

  public List<PostgresPartition> loadTablePartitions()
  {
    if (this.partitions == null)
    {
      readPartitions();
    }
    return getTablePartitions();
  }

  public void readPartitionInformation()
  {
    readPartitioningDefinition();
    readPartitions();
  }

  private void readPartitions()
  {
    String sql =
      "-- SQL Workbench/J \n" +
      "with recursive inh as ( \n" +
      "\n" +
      "  select i.inhrelid, null::text as parent  \n" +
      "  from pg_catalog.pg_inherits i  \n" +
      "    join pg_catalog.pg_class cl on i.inhparent = cl.oid \n" +
      "  where cl.relnamespace = cast(? as regnamespace) \n" +
      "    and cl.relname = ? \n" +
      "  union all \n" +
      "\n" +
      "  select i.inhrelid, (i.inhparent::regclass)::text \n" +
      "  from inh \n" +
      "    join pg_catalog.pg_inherits i on (inh.inhrelid = i.inhparent) \n" +
      ") \n" +
      "select c.relname as partition_name, \n" +
      "       c.relnamespace::regnamespace::text as partition_schema,  \n" +
      "       pg_catalog.obj_description(c.oid, 'pg_class') as remarks, \n" +
      "       pg_catalog.pg_get_expr(c.relpartbound, c.oid, true) as partition_expression, " +
      "       (select string_agg(case when x.attnum = 0 then '<expr>' else att.attname end, ', ' order by x.idx) \n" +
      "        from unnest(p.partattrs) with ordinality as x(attnum, idx)\n" +
      "          left join pg_catalog.pg_attribute att \n" +
      "                 on att.attnum = x.attnum \n" +
      "                and att.attrelid = p.partrelid) as sub_part_cols,\n" +
      "       pg_catalog.pg_get_expr(p.partexprs, p.partrelid, true) as sub_part_expression, " +
      "       parent, \n" +
      "       case p.partstrat \n" +
      "         when 'l' then 'LIST' \n" +
      "         when 'r' then 'RANGE' \n" +
      "         when 'h' then 'HASH' \n" +
      "       end as sub_partition_strategy \n" +
      "from inh \n" +
      "  join pg_catalog.pg_class c on inh.inhrelid = c.oid \n" +
        "  left join pg_catalog.pg_partitioned_table p on p.partrelid = c.oid \n" +
      "order by c.relnamespace::regnamespace, c.relname";

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    partitions = new ArrayList<>();

    LogMgr.logMetadataSql(new CallerInfo(){}, "partitions", sql, table.getSchema(), table.getTableName());

    try
    {
      sp = dbConnection.setSavepoint();

      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getRawSchema());
      pstmt.setString(2, table.getRawTableName());
      rs = pstmt.executeQuery();

      while (rs.next())
      {
        String partName = rs.getString("partition_name");
        String schema = rs.getString("partition_schema");
        String partExpr = rs.getString("partition_expression");
        String subPartExpr = rs.getString("sub_part_expression");
        String subPartCols = rs.getString("sub_part_cols");
        String parent = rs.getString("parent");
        String remarks = rs.getString("remarks");
        PostgresPartition partition = new PostgresPartition(this.table, schema, partName);
        partition.setDefinition(partExpr);
        partition.setComment(remarks);
        partition.setPartitionStrategy(strategy);
        String subPartStrategy = rs.getString("sub_partition_strategy");
        if (subPartStrategy != null)
        {
          String expr = mergeSubPartitionExpression(subPartCols, subPartExpr);
          partition.setSubPartitionDefinition("(" + expr + ")");
          partition.setSubPartitionStrategy(subPartStrategy);
        }

        if (parent != null)
        {
          TableIdentifier p = new TableIdentifier(parent);
          partition.setParentTable(p);
        }
        partitions.add(partition);
      }

      dbConnection.releaseSavepoint(sp);
    }
    catch (Exception ex)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "partitions", sql, table.getSchema(), table.getTableName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
  }

  private static String mergeSubPartitionExpression(String columns, String expressions)
  {
    if (expressions == null) return columns;
    List<String> expressionList = StringUtil.stringToList(expressions, ",", true, true, true, true);
    if (CollectionUtil.isEmpty(expressionList)) return columns;
    for (int i=0; i < expressionList.size(); i++)
    {
      columns = columns.replaceFirst("<expr>", Matcher.quoteReplacement(expressionList.get(i)));
    }
    return columns;
  }

  private void readPartitioningDefinition()
  {
    if (table == null) return;

    String sql =
      "-- SQL Workbench/J \n" +
      "select p.partstrat, \n" +
      "       pg_catalog.pg_get_expr(p.partexprs, t.oid, true) as partition_expression, \n" +
      "       (select string_agg(case when x.attnum = 0 then '<expr>' else att.attname end, ', ' order by x.idx) \n" +
      "        from unnest(p.partattrs) with ordinality as x(attnum, idx)\n" +
      "          left join pg_catalog.pg_attribute att \n" +
      "                 on att.attnum = x.attnum \n" +
      "                and att.attrelid = p.partrelid) as partition_columns\n" +
      "from pg_catalog.pg_partitioned_table p\n" +
      "  join pg_catalog.pg_class t on t.oid = p.partrelid\n" +
      "where t.relnamespace = cast(? as regnamespace) \n" +
      "  and t.relname = ? ";

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    LogMgr.logMetadataSql(new CallerInfo(){}, "partitioning definition", sql, table.getSchema(), table.getTableName());

    try
    {
      sp = dbConnection.setSavepoint();

      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getRawSchema());
      pstmt.setString(2, table.getRawTableName());
      rs = pstmt.executeQuery();

      if (rs.next())
      {
        String expression = rs.getString("partition_expression");
        String cols = rs.getString("partition_columns");
        partitionExpression = mergeSubPartitionExpression(cols, expression);

        String strat = rs.getString("partstrat");
        switch (strat)
        {
          case "r":
            strategy = "RANGE";
            break;
          case "l":
            strategy = "LIST";
            break;
          case "h":
            strategy = "HASH";
            break;
          default:
            break;
        }
        partitionDefinition = "PARTITION BY " + strategy + " (" + partitionExpression + ")";
      }

      dbConnection.releaseSavepoint(sp);
    }
    catch (Exception ex)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "partitioning definition", sql, table.getSchema(), table.getTableName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
  }

  /**
   * Check if the given table is in fact a partition in Postgres 10.
   *
   * If it is a partition, the definition is returned, otherwise null
   *
   * @param table          the table to check
   * @param dbConnection   the connection to use
   * @return null if the table is not a parition, the definition otherwise
   */
  public static PostgresPartition getPartitionDefinition(TableIdentifier table, WbConnection dbConnection)
  {
    String sql =
      "-- SQL Workbench/J \n" +
      "select base.relnamespace::regnamespace::text as base_table_schema, \n" +
      "       base.relname as base_table, \n" +
      "       pg_catalog.pg_get_expr(c.relpartbound, c.oid, true) as partition_expression, \n" +
      "       pg_catalog.pg_get_expr(p.partexprs, p.partrelid, true) as sub_partition_expression, \n" +
      "       (select string_agg(case when x.attnum = 0 then '<expr>' else att.attname end, ', ' order by x.idx) \n" +
      "        from unnest(p.partattrs) with ordinality as x(attnum, idx)\n" +
      "          left join pg_catalog.pg_attribute att \n" +
      "                 on att.attnum = x.attnum \n" +
      "                and att.attrelid = p.partrelid) as sub_part_cols,\n" +
      "       case p.partstrat \n" +
      "         when 'l' then 'LIST' \n" +
      "         when 'r' then 'RANGE' \n" +
      "         when 'h' then 'HASH' \n" +
      "       end as sub_partition_strategy \n" +
      "from pg_catalog.pg_inherits i \n" +
      "  join pg_catalog.pg_class c on i.inhrelid = c.oid \n" +
      "  join pg_partitioned_table p on p.partrelid = i.inhrelid\n" +
      "  join pg_class base on base.oid = i.inhparent \n" +
      "where c.relnamespace = cast(? as regnamespace) \n" +
      "  and c.relname = ?";

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    LogMgr.logMetadataSql(new CallerInfo(){}, "partition information", sql, table.getSchema(), table.getTableName());

    PostgresPartition result = null;
    try
    {
      sp = dbConnection.setSavepoint();

      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getRawSchema());
      pstmt.setString(2, table.getRawTableName());
      rs = pstmt.executeQuery();

      if (rs.next())
      {
        String name = rs.getString("base_table");
        String schema = rs.getString("base_table_schema");
        TableIdentifier tbl = new TableIdentifier(schema, name);
        result = new PostgresPartition(table, table.getRawSchema(), table.getRawTableName());
        result.setParentTable(tbl);
        result.setDefinition(rs.getString("partition_expression"));
        result.setSubPartitionStrategy(rs.getString("sub_partition_strategy"));
        String subExpr = rs.getString("sub_partition_expression");
        String subCols = rs.getString("sub_part_cols");
        result.setSubPartitionDefinition(mergeSubPartitionExpression(subCols, subExpr));
      }

      dbConnection.releaseSavepoint(sp);
    }
    catch (Exception ex)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "partition information", sql, table.getSchema(), table.getTableName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return result;
  }

}
