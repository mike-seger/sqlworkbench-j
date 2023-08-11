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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

import static workbench.util.SqlUtil.*;

/**
 * A class to read information about foreign servers from Postgres.
 *
 * @author Thomas Kellerer
 */
public class PostgresForeignServerReader
  implements ObjectListExtender
{

  public List<ForeignServer> getServerList(WbConnection connection, String namePattern)
  {
    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    List<ForeignServer> result = new ArrayList<>();
    String sql =
      "-- SQL Workbench/J \n" +
      "select s.srvname as name, \n" +
      "       s.srvtype as type,\n" +
      "       s.srvversion as version,\n" +
      "       s.srvoptions as options,\n" +
      "       w.fdwname as fdw\n" +
      "from pg_catalog.pg_foreign_server s\n" +
      "  join pg_catalog.pg_foreign_data_wrapper w on s.srvfdw = w.oid";

    if (StringUtil.isNotBlank(namePattern))
    {
      if (namePattern.contains("%"))
      {
        sql += "\nWHERE s.srvname like '";
        sql += SqlUtil.escapeUnderscore(namePattern, connection);
        sql += "' ";
        sql += getEscapeClause(connection, namePattern);
      }
      else
      {
        sql += "\nWHERE s.srvname = '";
        sql += SqlUtil.escapeQuotes(namePattern);
        sql += "' ";
      }
    }
    sql += "\norder by s.srvname";

    LogMgr.logMetadataSql(new CallerInfo(){}, "foreign servers", sql);

    try
    {
      sp = connection.setSavepoint();
      stmt = connection.createStatementForQuery();
      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        String name = rs.getString("name");
        String type = rs.getString("type");
        String version = rs.getString("version");
        String[] options = JdbcUtils.getArray(rs, "options", String[].class);
        String fdw = rs.getString("fdw");
        ForeignServer server = new ForeignServer(name);
        server.setVersion(version);
        server.setType(type);
        server.setFdwName(fdw);

        if (options != null)
        {
          Map<String, String> optionMap = new HashMap<>();
          for (String option : options)
          {
            String[] optValues = option.split("=");
            if (optValues.length == 2)
            {
              optionMap.put(optValues[0], optValues[1]);
            }
            server.setOptions(optionMap);
          }
        }
        result.add(server);
      }
      connection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "foreign servers", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public ForeignServer getObjectDefinition(WbConnection connection, DbObject object)
  {
    List<ForeignServer> rules = getServerList(connection, null);
    if (rules == null || rules.isEmpty()) return null;
    return rules.get(0);
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result,
                                  String catalog, String schema, String objectNamePattern, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded(ForeignServer.TYPE_NAME, requestedTypes)) return false;

    List<ForeignServer> servers = getServerList(con, objectNamePattern);
    if (servers.isEmpty()) return false;
    result.addObjects(servers);
    return true;
  }

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  @Override
  public boolean handlesType(String type)
  {
    return StringUtil.equalStringIgnoreCase(ForeignServer.TYPE_NAME, type);
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    return null;
  }

  @Override
  public List<String> supportedTypes()
  {
    return CollectionUtil.arrayList(ForeignServer.TYPE_NAME);
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    ForeignServer server = getObjectDefinition(con, object);
    if (server == null) return null;
    return server.getSource();
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
