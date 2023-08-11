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

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 * A class to read information about a partitioned table in Oracle
 *
 * @author Thomas Kellerer
 */
public class OracleTablePartition
  extends AbstractOraclePartition
{

  public OracleTablePartition(WbConnection conn)
  {
    super(conn, true);
    isIndex = false;
  }

  protected OracleTablePartition(WbConnection conn, boolean retrieveCompression)
  {
    super(conn, retrieveCompression);
    isIndex = false;
  }

  @Override
  protected String getRetrieveColumnsSql()
  {
    return
      "-- SQL Workbench/J \n" +
      "select column_name, \n" +
      "       column_position \n" +
      "from all_part_key_columns \n" +
      "where object_type = 'TABLE' \n" +
      "  and owner = ? \n" +
      "  and name = ? \n" +
      "order by column_position \n";
  }

  @Override
  protected String getRetrievePartitionDefinitionSql()
  {
    return
      "-- SQL Workbench/J \n" +
      "select owner,  \n" +
      "       table_name, \n" +
      "       partitioning_type,  \n" +
      "       partition_count, \n" +
      "       partitioning_key_count, \n" +
      "       subpartitioning_type, \n" +
      "       subpartitioning_key_count, \n" +
      "       def_subpartition_count, " +
      "       " + (supportsIntervals ? "interval" : "null as interval") + ", \n" +
      "       " + (supportsRefPartitions ? "ref_ptn_constraint_name" : "null as ref_ptn_constraint_name") + ", \n" +
      "       def_tablespace_name \n " +
      "from all_part_tables pt \n" +
      "where pt.owner = ? \n" +
      "  and pt.table_name = ? ";
  }

  @Override
  protected String getRetrievePartitionsSql()
  {
    if (useCompression)
    {
      return
        "-- SQL Workbench/J \n" +
        "SELECT partition_name,  \n" +
        "       high_value,  \n" +
        "       partition_position, \n" +
        "       subpartition_count, \n" +
        "       compression \n" +
        "FROM all_tab_partitions \n" +
        "WHERE table_owner = ?  \n" +
        "  AND table_name = ? \n" +
        "ORDER BY partition_position";
    }
    return
      "-- SQL Workbench/J \n" +
      "SELECT partition_name,  \n" +
      "       high_value,  \n" +
      "       partition_position, \n" +
      "       subpartition_count \n" +
      "FROM all_tab_partitions \n" +
      "WHERE table_owner = ?  \n" +
      "  AND table_name = ? \n" +
      "ORDER BY partition_position";
  }

  @Override
  protected String getRetrieveSubColumnsSql()
  {
    return
    "-- SQL Workbench/J \n" +
    "select name, \n" +
    "       object_type, \n" +
    "       column_name, \n" +
    "       column_position \n" +
    "from all_subpart_key_columns \n" +
    "where owner = ? \n" +
    "  and name = ? \n" +
    "order by column_position";
  }

  @Override
  protected String getRetrieveSubPartitionsSql()
  {
    if (useCompression)
    {
      return
        "-- SQL Workbench/J \n" +
        "select partition_name,  \n" +
        "       subpartition_name,  \n" +
        "       high_value, \n" +
        "       subpartition_position, \n" +
        "       compression \n" +
        "from all_tab_subpartitions \n" +
        "where table_owner = ?  \n" +
        "  and table_name = ?  \n" +
        "order by subpartition_position";
    }
    return
      "-- SQL Workbench/J \n" +
      "select partition_name,  \n" +
      "       subpartition_name,  \n" +
      "       high_value, \n" +
      "       subpartition_position \n" +
      "from all_tab_subpartitions \n" +
      "where table_owner = ?  \n" +
      "  and table_name = ?  \n" +
      "order by subpartition_position";
  }

  @Override
  protected void retrieveSubPartitions(DbObject object, WbConnection conn)
    throws SQLException
  {
    this.retrieveSubPartitionTemplates(object, conn);
    if (this.templateSubPartitions.isEmpty())
    {
      super.retrieveSubPartitions(object, conn);
    }
    for (OraclePartitionDefinition part : partitions)
    {
      part.setHasTemplateSubPartitions(this.templateSubPartitions.size() > 0);
      part.setDefaultSubPartitionCount(defaultSubpartitionCount);
    }
  }

  private void retrieveSubPartitionTemplates(DbObject object, WbConnection conn)
    throws SQLException
  {
    this.templateSubPartitions.clear();

    if (!JdbcUtils.hasMinimumServerVersion(conn, "11.2")) return;

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    String sql =
      "select tpl.subpartition_name,  \n" +
      "       tpl.high_bound, \n" +
      "       tpl.subpartition_position, \n" +
      "       tpl.compression \n" +
      "from all_subpartition_templates tpl\n" +
      "where tpl.user_name = ? \n" +
      "  and tpl.table_name = ?";

    long start = System.currentTimeMillis();
    TableIdentifier tbl = (TableIdentifier)object;
    try
    {
      pstmt = OracleUtils.prepareQuery(conn, sql);
      LogMgr.logMetadataSql(new CallerInfo(){}, "sub-partition templates", sql, object.getSchema(), object.getObjectName());

      pstmt.setString(1, tbl.getRawSchema());
      pstmt.setString(2, tbl.getRawTableName());
      rs = pstmt.executeQuery();

      while (rs.next())
      {
        String subPart = rs.getString("SUBPARTITION_NAME");
        String value = rs.getString("HIGH_BOUND");
        int position = rs.getInt("SUBPARTITION_POSITION");
        String compress = rs.getString("COMPRESSION");
        OraclePartitionDefinition subPartition = new OraclePartitionDefinition(subPart, subType, position);
        subPartition.setPartitionValue(value);
        subPartition.setCompressOption(compress);
        subPartition.setIsSubpartition(true);
        this.templateSubPartitions.add(subPartition);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "sub-partition templates", sql, object.getSchema(), object.getObjectName());
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Retrieving sub partition templates " + object.getObjectName() + " took: " + duration + "ms");
  }
}
