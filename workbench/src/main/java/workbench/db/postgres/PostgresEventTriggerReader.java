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
package workbench.db.postgres;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.TriggerDefinition;
import workbench.db.TriggerListDataStore;
import workbench.db.TriggerReader;
import workbench.db.WbConnection;

import workbench.storage.DataStore;
import workbench.storage.SortDefinition;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read information about extensions from Postgres.
 *
 * @author Thomas Kellerer
 */
public class PostgresEventTriggerReader
  implements ObjectListExtender
{
  public static final String TYPE = "EVENT TRIGGER";

  @Override
  public TriggerDefinition getObjectDefinition(WbConnection connection, DbObject object)
  {
    return null;
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result,
                                  String catalog, String schema, String objectNamePattern, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded(TYPE, requestedTypes)) return false;

    TriggerListDataStore ds = new TriggerListDataStore();
    ds.removeColumn(TriggerReader.TRIGGER_SCHEMA_COLUMN);
    int count = retrieveEventTriggers(con, ds, objectNamePattern);
    for (int tr=0; tr < ds.getRowCount(); tr++)
    {
      int row = result.addRow();
      result.setObjectName(row, ds.getTriggerName(tr));
      result.setType(row, ds.getTriggerType(tr));
      result.setRemarks(row, ds.getRemarks(tr));
      result.getRow(row).setUserObject(ds.getUserObject(tr));
    }
    return count > 0;
  }

  public int retrieveEventTriggers(WbConnection dbConnection, TriggerListDataStore triggers, String namePattern)
  {
    String sql =
      "-- SQL Workbench/J \n" +
      "select evtname as trigger, \n" +
      "       evtevent as event, \n" +
      "       pg_catalog.obj_description(oid, 'pg_event_trigger') as remarks \n" +
      "FROM pg_catalog.pg_event_trigger";

    PreparedStatement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    if (namePattern != null)
    {
      sql += " \nWHERE evtname ";
      if (namePattern.contains("%"))
      {
        sql += " LIKE '" + SqlUtil.escapeQuotes(namePattern) + "'";
      }
      else
      {
        sql += " = '" + SqlUtil.escapeQuotes(namePattern) + "'";
      }
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "event triggers", sql);

    int triggerCount = 0;
    try
    {
      sp = dbConnection.setSavepoint();
      stmt = dbConnection.getSqlConnection().prepareStatement(sql);
      rs = stmt.executeQuery();

      while (rs.next())
      {
        triggerCount ++;
        String name = rs.getString(1);
        String event = rs.getString(2);
        String remarks = rs.getString(3);
        int row = triggers.addRow();
        triggers.setTriggerName(row, name);
        triggers.setTriggerType(row, "EVENT");
        triggers.setEvent(row, event);
        triggers.setRemarks(row, remarks);
        TriggerDefinition trg = new TriggerDefinition(null, null, name);
        trg.setComment(remarks);
        trg.setTriggerType(TYPE);
        triggers.getRow(row).setUserObject(trg);
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (Exception ex)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "event triggers", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (triggerCount > 0)
    {
      // sort the datastore again
      SortDefinition def = new SortDefinition();
      def.addSortColumn(0, true);
      triggers.sort(def);
    }
    triggers.resetStatus();

    return triggerCount;
  }

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  @Override
  public boolean handlesType(String type)
  {
    return StringUtil.equalStringIgnoreCase(TYPE, type);
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    return null;
  }

  @Override
  public List<String> supportedTypes()
  {
    return CollectionUtil.arrayList(TYPE);
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    PostgresTriggerReader reader = new PostgresTriggerReader(con);
    try
    {
      String source = reader.getEventTriggerSource(object.getObjectName());
      return source;
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve event trigger source", ex);
    }
    return null;

  }

  @Override
  public List<ColumnIdentifier> getColumns(WbConnection con, DbObject object)
  {
    return null;
  }

  @Override
  public boolean hasColumns()
  {
    return false;
  }
}
