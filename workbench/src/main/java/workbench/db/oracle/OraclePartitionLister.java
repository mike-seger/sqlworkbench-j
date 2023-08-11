/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.db.oracle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.PartitionLister;
import workbench.db.SubPartitionState;
import workbench.db.TableIdentifier;
import workbench.db.TablePartition;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class OraclePartitionLister
  extends OracleTablePartition
  implements PartitionLister
{
  private final WbConnection conn;

  public OraclePartitionLister(WbConnection conn)
  {
    super(conn, true);
    this.conn = conn;
  }

  @Override
  public List<TablePartition> getPartitions(TableIdentifier table)
  {
    List<TablePartition> result = new ArrayList<>();
    try
    {
      boolean isPartitioned = super.retrieveDefinition(table, conn);
      if (!isPartitioned) return null;

      SubPartitionState state = SubPartitionState.unknown;
      if (!this.hasSubPartitions())
      {
        state = SubPartitionState.none;
      }
      List<OraclePartitionDefinition> partitions = super.loadPartitions(table, conn);
      for (OraclePartitionDefinition oraPart : partitions)
      {
        TablePartition partition = new TablePartition();
        CharSequence definition = oraPart.getSource(true, 0, "");
        if (definition != null)
        {
          partition.setDefinition(definition.toString());
        }
        partition.setSubPartitionStrategy(oraPart.getSubPartitionType());
        partition.setSchema(table.getRawSchema());
        partition.setName(oraPart.getName());
        partition.setPartitionStrategy(oraPart.getType());
        partition.setSubPartitionState(state);
        result.add(partition);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load partitions",ex);
    }
    return result;
  }

  @Override
  public List<TablePartition> getSubPartitions(TableIdentifier baseTable, TablePartition partition)
  {
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    String sql =
        "-- SQL Workbench/J \n" +
        "select sub.subpartition_name, sub.high_value, sub.subpartition_position, tbl.subpartitioning_type \n" +
        "from all_tab_subpartitions sub \n" +
        "  join all_part_tables tbl on tbl.owner = sub.table_owner and tbl.table_name = sub.table_name \n" +
        "where sub.table_owner = ? \n" +
        "  and sub.table_name = ? \n" +
        "  and sub.partition_name = ? \n" +
        "order by sub.subpartition_position";

    List<TablePartition> result = new ArrayList<>();

    String mainPartName = SqlUtil.removeObjectQuotes(partition.getObjectName());
    long start = System.currentTimeMillis();
    try
    {
      pstmt = OracleUtils.prepareQuery(conn, sql);
      LogMgr.logMetadataSql(new CallerInfo(){}, "sub-partitions", sql, baseTable.getRawSchema(), baseTable.getRawTableName(), mainPartName);

      pstmt.setString(1, baseTable.getRawSchema());
      pstmt.setString(2, baseTable.getRawTableName());
      pstmt.setString(3, mainPartName);
      rs = pstmt.executeQuery();

      while (rs.next())
      {
        String name = rs.getString("subpartition_name");
        String value = rs.getString("high_value");
        int pos = rs.getInt("subpartition_position");
        String type = rs.getString("subpartitioning_type");
        OraclePartitionDefinition oraPart = new OraclePartitionDefinition(name, type, pos);
        oraPart.setIsSubpartition(true);
        oraPart.setPartitionValue(value);
        CharSequence source = oraPart.getSource(true, 0, "");
        TablePartition part = new TablePartition();
        if (source != null)
        {
          part.setDefinition(source.toString());
        }
        part.setIsSubPartition(true);
        part.setPartitionStrategy(type);
        part.setName(name);
        part.setSchema(baseTable.getRawSchema());
        result.add(part);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "sub-partitions", sql, baseTable.getRawSchema(), baseTable.getRawTableName(), mainPartName);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Retrieving sub partitions " + baseTable.getObjectName() + " took: " + duration + "ms");

    return result;
  }

  @Override
  public boolean supportsSubPartitions()
  {
    return true;
  }

}
