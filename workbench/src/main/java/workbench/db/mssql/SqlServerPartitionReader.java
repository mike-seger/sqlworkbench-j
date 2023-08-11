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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.db.JdbcUtils;

/**
 *
 * @author Thomas Kellerer
 */
public class SqlServerPartitionReader
{
  private final WbConnection dbConnection;

  private final String retrieveFunctionSQL =
    "select pf.function_id, \n" +
    "       pf.name as function_name, \n" +
    "       pf.type_desc,\n" +
    "       pf.boundary_value_on_right,\n" +
    "       typ.name as parameter_type,\n" +
    "       r.parameter_id,\n" +
    "       r.boundary_id, \n" +
    "       case cast(SQL_VARIANT_PROPERTY(r.value, 'BaseType') as varchar(50))\n" +
    "          when 'date' then '''' + convert(varchar(20), r.value, 23) + ''''\n" +
    "          when 'timestamp' then '''' + convert(varchar(20), r.value, 121) + ''''\n" +
    "          when 'varchar' then '''' + cast(r.value as varchar(max)) + ''''\n" +
    "          else cast(r.value as varchar(max))\n" +
    "       end as boundary_value\n" +
    "from sys.partition_functions pf \n" +
    "  join sys.partition_parameters par on par.function_id = pf.function_id\n" +
    "  join sys.types typ on typ.system_type_id = par.system_type_id\n" +
    "  LEFT JOIN sys.partition_range_values AS r ON pf.function_id = r.function_id\n" +
    "where pf.is_system = 0 \n";

  private final String retrieveSchemesSQL =
    "select ps.name AS partition_scheme, \n" +
    "       fg.name AS file_group, \n" +
    "       f.name as function_name \n" +
    "from sys.partitions p \n" +
    "    join sys.indexes i  ON i.object_id = p.object_id AND i.index_id = p.index_id \n" +
    "    join sys.partition_schemes ps on ps.data_space_id = i.data_space_id \n" +
    "    join sys.partition_functions f on f.function_id = ps.function_id \n" +
    "    join sys.allocation_units au  ON au.container_id = p.hobt_id \n" +
    "    join sys.filegroups fg  ON fg.data_space_id = au.data_space_id \n";

  private final String orderSchemes = "order by p.partition_number";
  private final String orderFunctions = "order by pf.function_id, r.boundary_id";
  private final boolean supportsPartitioning;

  public SqlServerPartitionReader(WbConnection conn)
  {
    this.dbConnection = conn;
    this.supportsPartitioning = SqlServerUtil.supportsPartitioning(conn);
  }

  public List<PartitionFunction> getFunctions()
  {
    if (!supportsPartitioning) return Collections.emptyList();

    String sql = retrieveFunctionSQL + orderFunctions;
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    List<PartitionFunction> result = new ArrayList<>();
    final CallerInfo ci = new CallerInfo(){};
    int lastParameterId = -1;
    PartitionFunction func = null;
    String database = dbConnection.getCurrentCatalog();

    LogMgr.logMetadataSql(ci, "partition functions", sql);
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        int functionId = rs.getInt("function_id");
        String name = rs.getString("function_name");
        if (func == null || func.getFunctionId() != functionId)
        {
          result.add(func);
          func = new PartitionFunction(name);
          func.setFunctionId(functionId);
          func.setCatalog(database);
          lastParameterId = -1;
        }
        int parameterId = rs.getInt("parameter_id");
        String pType = rs.getString("parameter_type");
        if (parameterId != lastParameterId)
        {
          func.addParameter(pType);
          lastParameterId = parameterId;
        }
        String value = rs.getString("boundary_value");
        func.addValue(value);
      }
      if (func != null)
      {
        result.add(func);
      }
    }
    catch (Throwable th)
    {
      LogMgr.logMetadataError(ci, th, "partition functions", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return result;
  }

  public PartitionScheme getSchemeForTable(TableIdentifier tbl)
  {
    if (!supportsPartitioning || tbl == null) return null;
    String sql = retrieveSchemesSQL + "\n" +
      "where  p.object_id = object_id(?) \n" +
      orderSchemes;
    String tname = tbl.getTableExpression(dbConnection);
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    PartitionScheme result = null;
    final CallerInfo ci = new CallerInfo(){};

    LogMgr.logMetadataSql(ci, "table partition scheme", sql, tname);
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, tname);
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        if (result == null)
        {
          String scheme = rs.getString("partition_scheme");
          String func = rs.getString("function_name");
          result = new PartitionScheme(scheme, func);
          result.setCatalog(dbConnection.getCurrentCatalog());
        }
        result.addFileGroup(rs.getString("file_group"));
      }
    }
    catch (Throwable th)
    {
      LogMgr.logMetadataError(ci, th, "table partition scheme", sql, tname);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return result;
  }

  public PartitionFunction getFunctionForTable(TableIdentifier tbl)
  {
    if (!supportsPartitioning || tbl == null) return null;

    String sql = retrieveFunctionSQL +
      "  and pf.function_id IN (SELECT s.function_id\n" +
      "                         FROM sys.tables AS t  \n" +
      "                           JOIN sys.indexes AS i ON t.object_id = i.object_id  \n" +
      "                           JOIN sys.partition_schemes AS s ON i.data_space_id = s.data_space_id  \n" +
      "                          WHERE t.object_id = object_id(?)) \n"
      + orderFunctions;

    String tname = tbl.getTableExpression(dbConnection);
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    PartitionFunction func = null;
    final CallerInfo ci = new CallerInfo(){};
    int lastParameter = -1;

    LogMgr.logMetadataSql(ci, "table partition function", sql, tname);
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, tname);
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String funcName = rs.getString("function_name");
        if (func == null)
        {
          func = new PartitionFunction(funcName);
          String type = rs.getString("type_desc");
          boolean isRight = rs.getBoolean("boundary_value_on_right");
          if (isRight)
          {
            func.setTypeDef(type + " RIGHT");
          }
          else
          {
            func.setTypeDef(type + " LEFT");
          }
          int id = rs.getInt("function_id");
          func.setFunctionId(id);
          func.setCatalog(dbConnection.getCurrentCatalog());
        }
        int parameterId = rs.getInt("parameter_id");
        String pType = rs.getString("parameter_type");
        if (parameterId != lastParameter)
        {
          func.addParameter(pType);
          lastParameter = parameterId;
        }
        String value = rs.getString("boundary_value");
        func.addValue(value);
      }
    }
    catch (Throwable th)
    {
      LogMgr.logMetadataError(ci, th, "table partition function", sql, tname);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return func;
  }
}
