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
package workbench.db.vertica;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.db.JdbcUtils;


/**
 *
 * This class retrieves information about projections from Vertica.
 *
 * @author Tatiana Saltykova
 */
public class VerticaProjectionReader
{
  private WbConnection dbConnection;

  public VerticaProjectionReader()
  {
  }

  public void setConnection(WbConnection conn)
  {
    dbConnection = conn;
  }

  public DataStore getProjectionList(TableIdentifier table)
    throws SQLException
  {
    // Views can't have projections
    if ("VIEW".equals(table.getType())) return null;

    String sql =
      "SELECT projection_basename as basename, \n" +
      "       decode(is_super_projection,true,'super') as is_super, \n" +
      "       decode(is_segmented,true,'segmented',false,'replicated') as is_segmented, \n" +
      "       decode(is_prejoin,true,'prejoin') as is_prejoin, \n" +
      "       decode(is_up_to_date,true,'up to date',false,'stale') as is_up_to_date, \n" +
      "       decode(has_statistics,true,'has stats',false,'no stats') as has_statistics, \n"+
      "       verified_fault_tolerance, \n" +
      "       count(*) \n" +
      "FROM projections \n" +
      "WHERE anchor_table_name = ? \n" +
      "  AND projection_schema = ? \n" +
      "group by 1,2,3,4,5,6,7 \n" +
      "ORDER BY 2, projection_basename";

    LogMgr.logMetadataSql(new CallerInfo(){}, "projections", sql);

    PreparedStatement projectionStatement = null;
    ResultSet rs = null;
    try
    {
      projectionStatement = this.dbConnection.getSqlConnection().prepareStatement(sql);
      projectionStatement.setString(1, table.getTableName());
      projectionStatement.setString(2, table.getSchema());
      rs = projectionStatement.executeQuery();
      DataStore ds = new DataStore(rs, true);
      return ds;
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "projections", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, projectionStatement);
    }

    return null;
  }

  public DataStore getProjectionCopies(String projectionBasename, String schema)
    throws SQLException
  {
    String sql =
      "SELECT p.projection_name as name, \n" +
      "       decode(p.node_name,null,ps.node_name,'',ps.node_name,p.node_name) as node, \n" +
      "       p.created_epoch, \n" +
      "       ps.projection_column_count as columns, \n" +
      "       ps.ros_count as ROSes, \n" +
      "       ps.wos_row_count as wos_rows, \n" +
      "       ps.ros_row_count as ros_rows, \n" +
      "       round(ps.wos_used_bytes/1024/1024,2) as wos_mb, \n" +
      "       round(ps.ros_used_bytes/1024/1024,2) as ros_mb \n" +
      "FROM projections p \n" +
      "  left outer join projection_storage ps using (projection_id) \n" +
      "WHERE decode(p.node_name,null,ps.node_name,'',ps.node_name,p.node_name) = ps.node_name \n" +
      "  and p.projection_basename = ? \n" +
      "  and p.projection_schema = ? \n" +
      "ORDER BY p.projection_name, node ";

    LogMgr.logMetadataSql(new CallerInfo(){}, "projection copies", sql);

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try
    {
      pstmt = this.dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, projectionBasename);
      pstmt.setString(2, schema);
      rs = pstmt.executeQuery();
      DataStore ds = new DataStore(rs, true);
      return ds;
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "projection copies", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return null;
  }

  public DataStore getProjectionColumns(String projectionName, String schema)
    throws SQLException
  {
    String sql =
      "SELECT pc.column_position, \n" +
      "       pc.projection_column_name, \n" +
      "       decode(pc.projection_column_name,pc.table_column_name,'',pc.table_column_name) as table_column_name, \n" +
      "       pc.sort_position, \n" +
      "       pc.data_type, \n" +
      "       pc.encoding_type, \n" +
      "       pc.access_rank, \n" +
      "       pc.group_id, \n" +
      "       round(sum(used_bytes)/1024/1024,2) as mb_total, \n" +
      "       sum(ros_count) as ros_total, \n" +
      "       round(avg(used_bytes)/1024/1024,2) as mb_per_node, \n" +
      "       avg(ros_count) as ros_per_node, \n" +
      "       pc.statistics_type, \n" +
      "       pc.statistics_updated_timestamp \n" +
      "FROM projection_columns pc \n" +
      " left outer join column_storage ps using (projection_id,column_id) \n" +
      "WHERE pc.projection_id = (select projection_id FROM projections \n" +
      "                          WHERE projection_basename = ? \n" +
      "                            and projection_schema = ? \n" +
      "                            limit 1) \n" +
      "GROUP BY 1,2,3,4,5,6,7,8,13,14 \n" +
      "ORDER BY pc.column_position ";

    LogMgr.logMetadataSql(new CallerInfo(){}, "projection columns", sql);

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, projectionName);
      pstmt.setString(2, schema);
      rs = pstmt.executeQuery();
      DataStore ds = new DataStore(rs, true);
      return ds;
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "projection columns", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return null;
  }

}
