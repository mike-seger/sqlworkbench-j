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
import java.sql.Types;
import java.util.Map;
import java.util.TreeMap;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.ObjectListEnhancer;
import workbench.db.ObjectListDataStore;
import workbench.db.WbConnection;

import workbench.util.CaseInsensitiveComparator;

/**
 *
 * @author Thomas Kellerer
 */
public class SqlServerObjectListEnhancer
  implements ObjectListEnhancer
{

  static final String REMARKS_PROP_NAME = "remarks.propertyname";
  static final String REMARKS_PROP_DEFAULT = "MS_DESCRIPTION";

  @Override
  public void updateObjectList(WbConnection con, ObjectListDataStore result, String aCatalog, String aSchema, String objects, String[] requestedTypes)
  {
    if (con.getDbSettings().getBoolProperty("remarks.object.retrieve", true))
    {
      updateObjectRemarks(con, result, aCatalog, aSchema, objects, requestedTypes);
    }
  }

  protected void updateObjectRemarks(WbConnection con, ObjectListDataStore result, String catalog, String schema, String objects, String[] requestedTypes)
  {
    if (result == null) return;
    if (result.getRowCount() == 0) return;

    String object = null;
    if (result.getRowCount() == 1)
    {
      object = result.getObjectName(0);
      // no need to loop through all requested types if only a single object is requested
      requestedTypes = new String[] { result.getType(0)};
    }

    Map<String, String> remarks = readRemarks(con, schema, object, requestedTypes);

    for (int row=0; row < result.getRowCount(); row++)
    {
      String name = result.getObjectName(row);
      String objectSchema = result.getSchema(row);

      String remark = remarks.get(objectSchema + "." + name);
      if (remark != null)
      {
        result.setRemarks(row, remark);
      }
    }
  }

  public Map<String, String> readRemarks(WbConnection con, String schema, String object, String[] requestedTypes)
  {
    String propName = con.getDbSettings().getProperty(REMARKS_PROP_NAME, REMARKS_PROP_DEFAULT);
    String sql = null;

    if ("*".equals(schema))
    {
      schema = null;
    }

    if (SqlServerUtil.isSqlServer2005(con))
    {
      sql = "SELECT objtype, objname, cast(value as varchar(8000)) as value \n" +
      "FROM fn_listextendedproperty ('" + propName + "','schema', ?, ?, ";
    }
    else
    {
      sql = "SELECT objtype, objname, cast(value as varchar(8000)) as value \n" +
      "FROM ::fn_listextendedproperty ('" + propName + "','user', ?, ?, ";
    }

    if (object == null)
    {
      sql += "null, null, null)";
    }
    else
    {
      sql += "'" + object + "', null, null)";
    }

    if (requestedTypes == null)
    {
      requestedTypes = new String[] { "TABLE", "VIEW", "SYNONYM", "TYPE", "RULE" };
    }

    PreparedStatement stmt = null;
    ResultSet rs = null;

    if (schema == null)
    {
      schema = con.getMetadata().getCurrentSchema();
    }

    Map<String, String> remarks = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
    String type = null;
    try
    {
      for (String requestedType : requestedTypes)
      {
        type = requestedType;
        LogMgr.logMetadataSql(new CallerInfo(){}, "table remarks", sql, schema, type);
        stmt = con.getSqlConnection().prepareStatement(sql);
        if (schema == null)
        {
          stmt.setNull(1, Types.VARCHAR);
        }
        else
        {
          stmt.setString(1, schema);
        }
        stmt.setString(2, type);
        rs = stmt.executeQuery();
        while (rs.next())
        {
          String objectname = rs.getString(2);
          String remark = rs.getString(3);
          if (objectname != null && remark != null)
          {
            remarks.put(schema + "." + objectname.trim(), remark);
          }
        }
        JdbcUtils.closeResult(rs);
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "table remarks", sql, schema, type);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return remarks;
  }
}
