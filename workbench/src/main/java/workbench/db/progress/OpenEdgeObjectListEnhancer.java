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
package workbench.db.progress;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.TreeMap;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DBID;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListEnhancer;
import workbench.db.ObjectListDataStore;
import workbench.db.WbConnection;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.StringUtil;


/**
 *
 * @author Thomas Kellerer
 */
public class OpenEdgeObjectListEnhancer
  implements ObjectListEnhancer
{

  @Override
  public void updateObjectList(WbConnection con, ObjectListDataStore result, String aCatalog, String aSchema, String objects, String[] requestedTypes)
  {
    if (Settings.getInstance().getBoolProperty("workbench.db." + DBID.OPENEDGE.getId() + ".remarks.object.retrieve", false))
    {
      updateObjectRemarks(con, result, aCatalog, aSchema, objects);
    }
  }

  protected void updateObjectRemarks(WbConnection con, ObjectListDataStore result, String catalog, String schema, String objects)
  {
    if (result == null) return;
    if (result.getRowCount() == 0) return;

    String object = null;
    if (result.getRowCount() == 1)
    {
      object = result.getObjectName(0);
    }

    Map<String, String> remarks = readRemarks(con, schema, object);

    for (int row = 0; row < result.getRowCount(); row++)
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

  public Map<String, String> readRemarks(WbConnection con, String schema, String object)
  {

    PreparedStatement stmt = null;
    ResultSet rs = null;

    if (schema == null)
    {
      schema = con.getMetadata().getCurrentSchema();
    }

    int schemaIndex = -1;
    int tableIndex = -1;

    String sql =
      "select owner, tbl, description \n" +
      "from sysprogress.systables_full \n" +
      "where description is not null \n" +
      "  and description <> '' \n" +
      "  and tbltype = 'T' \n";

    if (schema != null)
    {
      schemaIndex = 1;
      sql += "and owner = ? \n";
    }

    if (object != null)
    {
      tableIndex = (schemaIndex == -1 ? 1 : 2);
      sql += " and tbl = ? ";
    }

    Map<String, String> remarks = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);

    long start = System.currentTimeMillis();
    try
    {
      LogMgr.logMetadataSql(new CallerInfo(){}, "table remarks", sql, schema, object);
      stmt = con.getSqlConnection().prepareStatement(sql);
      if (schemaIndex > 0) stmt.setString(schemaIndex, schema);
      if (tableIndex > 0) stmt.setString(tableIndex, object);

      rs = stmt.executeQuery();
      while (rs.next())
      {
        String objectSchema = rs.getString(1);
        String objectName = rs.getString(2);
        String remark = rs.getString(3);
        if (objectName != null && StringUtil.isNotEmpty(remark))
        {
          remarks.put(objectSchema + "." + objectName.trim(), remark);
        }
      }
      JdbcUtils.closeResult(rs);
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "table remarks", sql, schema, object);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Retrieving table remarks took: " + duration + "ms");
    return remarks;
  }
}
