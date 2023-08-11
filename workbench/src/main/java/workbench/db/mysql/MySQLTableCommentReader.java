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
package workbench.db.mysql;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ObjectListEnhancer;
import workbench.db.WbConnection;

import workbench.util.CaseInsensitiveComparator;

import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class MySQLTableCommentReader
  implements ObjectListEnhancer
{

  public static boolean retrieveComments()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.mysql.tablecomments.retrieve", false);
  }

  @Override
  public void updateObjectList(WbConnection con, ObjectListDataStore result, String aCatalog, String aSchema, String objects, String[] requestedTypes)
  {
    if (retrieveComments())
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
    }

    Map<String, String> remarks = readRemarks(con, catalog, object, requestedTypes);

    for (int row=0; row < result.getRowCount(); row++)
    {
      String tblName = result.getObjectName(row);
      String tblSchema = result.getCatalog(row);
      String remark = remarks.get(getNameKey(tblSchema, tblName));
      if (remark != null && !remark.equals("VIEW"))
      {
        result.setRemarks(row, remark);
      }
    }
  }

  private String getNameKey(String schema, String objectname)
  {
    if (schema != null && objectname != null)
    {
      return schema.trim() + "." + objectname.trim();
    }
    else if (objectname != null)
    {
      return objectname.trim();
    }
    return null;
  }

  public Map<String, String> readRemarks(WbConnection con, String catalog, String object, String[] requestedTypes)
  {
    String sql = "select table_schema, table_name, table_comment from information_schema.tables";

    boolean whereAdded = false;

    if (StringUtil.isNotBlank(object))
    {
      sql += " WHERE table_name = '" + object + "'";
      whereAdded = true;
    }

    if (StringUtil.isNotBlank(catalog))
    {
      if (whereAdded) sql += " AND ";
      else sql += " WHERE ";

      sql += " table_schema = '" + catalog + "'";
      whereAdded = true;
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "remarks", sql);

    Statement stmt = null;
    ResultSet rs = null;

    Map<String, String> remarks = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
    try
    {
        stmt = con.createStatement();
        rs = stmt.executeQuery(sql);
        while (rs.next())
        {
          String schema = rs.getString(1);
          String objectname = rs.getString(2);
          String remark = rs.getString(3);
          if (objectname != null && StringUtil.isNotBlank(remark))
          {
            remarks.put(getNameKey(schema, objectname), remark);
          }
        }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "remarks", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return remarks;
  }
}
