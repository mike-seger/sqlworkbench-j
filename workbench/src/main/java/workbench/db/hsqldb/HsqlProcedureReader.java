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
package workbench.db.hsqldb;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbMetadata;
import workbench.db.JdbcProcedureReader;
import workbench.db.JdbcUtils;
import workbench.db.ProcedureDefinition;
import workbench.db.RoutineType;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class HsqlProcedureReader
  extends JdbcProcedureReader
{
  public HsqlProcedureReader(WbConnection conn)
  {
    super(conn);
  }

  /**
   * HyperSQL marks all functions returned from getFunctions() with DatabaseMetaData.functionNoTable which is not helpful.
   */
  @Override
  public List<ProcedureDefinition> getTableFunctions(String catalogPattern, String schemaPattern, String namePattern)
    throws SQLException
  {
    catalogPattern = DbMetadata.cleanupWildcards(catalogPattern);
    schemaPattern = DbMetadata.cleanupWildcards(schemaPattern);
    namePattern = DbMetadata.cleanupWildcards(namePattern);

    List<ProcedureDefinition> result = new ArrayList<>();
    String sql =
      "select routine_catalog, \n" +
      "       routine_schema, \n" +
      "       routine_name \n" +
      "from information_schema.routines \n" +
      "where routine_type = 'FUNCTION' \n" +
      "  and data_type like 'ROW%'";

    StringBuilder condition = new StringBuilder(50);
    if (StringUtil.isNotBlank(catalogPattern))
    {
      condition.append("\n  AND ");
      SqlUtil.appendExpression(condition, "ROUTINE_CATALOG", catalogPattern, connection);
    }
    if (StringUtil.isNotBlank(schemaPattern))
    {
      condition.append("\n  AND ");
      SqlUtil.appendExpression(condition, "ROUTINE_SCHEMA", schemaPattern, connection);
    }
    if (StringUtil.isNotBlank(namePattern))
    {
      condition.append("\n  AND ");
      SqlUtil.appendExpression(condition, "ROUTINE_NAME", namePattern, connection);
    }
    sql += condition;
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      LogMgr.logMetadataSql(new CallerInfo(){}, "table functions", sql);

      stmt = connection.getSqlConnection().createStatement();
      rs = stmt.executeQuery(sql);

      while (rs.next())
      {
        String cat = rs.getString(1);
        String schema = rs.getString(2);
        String name = rs.getString(3);
        ProcedureDefinition def = new ProcedureDefinition(cat, schema, name, RoutineType.tableFunction, DatabaseMetaData.functionReturnsTable);
        result.add(def);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "table functions", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

}
