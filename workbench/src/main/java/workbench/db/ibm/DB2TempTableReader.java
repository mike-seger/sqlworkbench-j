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
package workbench.db.ibm;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbMetadata;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListAppender;
import workbench.db.ObjectListDataStore;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 * An ObjectListAppender for DB2/LUW to read temporary tables.
 *
 * The DB2 JDBC driver does not return created global temporary tables. This ObjectListAppender retrieves them
 * from syscat.tables and adds them to the regular list of tables.
 *
 * @author Thomas Kellerer
 */
public class DB2TempTableReader
  implements ObjectListAppender
{
  private static final String TABLE_TYPE = "GLOBAL TEMPORARY";

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result,
                                  String catalog, String schema, String objects, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded("TABLE", requestedTypes)) return false;

    String sql =
      "select tabschema, \n" +
      "       tabname, \n" +
      "       remarks \n" +
      "from syscat.tables \n" +
      "where type = 'G' ";

    int schemaIndex = 0;
    int tableIndex = 0;
    if (schema != null)
    {
      if (schema.indexOf('%') > -1)
      {
        sql += "\n  and tabschema like ? ";
      }
      else
      {
        sql += "\n  and tabschema = ? ";
      }
      schemaIndex = 1;
    }

    if (objects != null)
    {
      if (objects.indexOf('%') > -1)
      {
        sql += "\n  and tabname like ? ";
      }
      else
      {
        sql += "\n  and tabname = ? ";
      }
      tableIndex = schemaIndex + 1;
    }

    sql += "\nfor read only";

    LogMgr.logMetadataSql(new CallerInfo(){}, "temp tables", sql, schema, objects);

    PreparedStatement stmt = null;
    ResultSet rs = null;
    int count = 0;

    try
    {
      stmt = con.getSqlConnection().prepareStatement(sql);
      if (schemaIndex > 0)
      {
        stmt.setString(schemaIndex, schema);
      }
      if (tableIndex > 0)
      {
        stmt.setString(tableIndex, objects);
      }
      rs = stmt.executeQuery();
      while (rs.next())
      {
        int row = result.addRow();
        String tabSchema = rs.getString(1);
        String tabName = rs.getString(2);
        String remarks = rs.getString(3);
        TableIdentifier tbl = new TableIdentifier(null, tabSchema, tabName);
        tbl.setType("TABLE");
        tbl.setNeverAdjustCase(true);
        tbl.setComment(remarks);
        tbl.getSourceOptions().setTypeModifier(TABLE_TYPE);
        tbl.getSourceOptions().setInitialized();
        result.addDbObject(tbl);
        count ++;
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "temp tables", sql, schema, objects);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return count > 0;
  }

}
