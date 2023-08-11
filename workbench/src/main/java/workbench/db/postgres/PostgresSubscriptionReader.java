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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresSubscriptionReader
  implements ObjectListExtender
{
  private Boolean hasPrivileges;

  @Override
  public List<String> supportedTypes()
  {
    return CollectionUtil.arrayList(PgSubscription.TYPE_NAME);
  }

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  @Override
  public boolean handlesType(String type)
  {
    return PgSubscription.TYPE_NAME.equalsIgnoreCase(type);
  }

  private List<PgSubscription> getSubscriptions(WbConnection connection, String namePattern)
  {
    List<PgSubscription> result = new ArrayList<>();

    checkPrivileges(connection);
    if (!hasPrivileges) return result;

    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    StringBuilder sql = new StringBuilder(
      "-- SQL Workbench/J \n" +
      "select s.subname, \n" +
      "       s.subconninfo, \n" +
      "       s.subpublications, \n" +
      "       s.subenabled, \n" +
      "       s.subslotname, \n" +
      "       s.subsynccommit, \n" +
      "       pg_catalog.obj_description(oid) as remarks \n" +
      "from pg_catalog.pg_subscription s \n" +
      "where subdbid = (select oid \n" +
      "                 from pg_catalog.pg_database \n" +
      "                 where datname = pg_catalog.current_database())");

    if (StringUtil.isNotBlank(namePattern))
    {
      sql.append("\n  and ");
      SqlUtil.appendExpression(sql, "s.subname", namePattern, connection);
    }

    sql.append("\norder by s.subname");

    LogMgr.logMetadataSql(new CallerInfo(){}, "subscriptions", sql);

    try
    {
      sp = connection.setSavepoint();
      stmt = connection.createStatementForQuery();
      rs = stmt.executeQuery(sql.toString());
      while (rs.next())
      {
        String name = rs.getString("subname");
        String remarks = rs.getString("remarks");
        PgSubscription sub = new PgSubscription(name);
        sub.setConnectionInfo(rs.getString("subconninfo"));
        sub.setEnabled(rs.getBoolean("subenabled"));
        sub.setSyncCommitEnabled(rs.getBoolean("subsynccommit"));
        sub.setSlotName(rs.getString("subslotname"));
        String[] pubs = JdbcUtils.getArray(rs, "subpublications", String[].class);
        sub.setPublications(Arrays.asList(pubs));
        sub.setComment(remarks);
        result.add(sub);
      }
      connection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "subscriptions", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public PgSubscription getObjectDefinition(WbConnection con, DbObject name)
  {
    List<PgSubscription> subs = getSubscriptions(con, name.getObjectName());
    if (subs.isEmpty()) return null;
    return subs.get(0);
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    if (object instanceof PgSubscription)
    {
      return getSource((PgSubscription)object);
    }
    PgSubscription sub = getObjectDefinition(con, object);
    return getSource(sub);
  }

  private String getSource(PgSubscription sub)
  {
    if (sub == null) return null;
    return sub.getSource();
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result, String aCatalog, String aSchema, String objects, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded(PgSubscription.TYPE_NAME, requestedTypes)) return false;
    List<PgSubscription> subs = getSubscriptions(con, objects);
    result.addObjects(subs);
    return subs.size() > 0;
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
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

  public List<TableIdentifier> getTables(WbConnection connection, DbObject publication)
  {
    List<TableIdentifier> result = new ArrayList<>();

    checkPrivileges(connection);
    if (!hasPrivileges) return result;

    PreparedStatement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    String sql =
      "SELECT c.relnamespace::regnamespace::text as table_schema, \n" +
      "       c.relname as table_name, \n" +
      "       pg_catalog.obj_description(c.oid) as remarks \n" +
      "FROM pg_catalog.pg_subscription s \n" +
      "  JOIN pg_catalog.pg_subscription_rel rel ON rel.srsubid = s.oid \n" +
      "  JOIN pg_catalog.pg_class c on c.oid = rel.srrelid \n" +
      "where s.subname = ? ";

    LogMgr.logMetadataSql(new CallerInfo(){}, "subscription tables", sql);

    try
    {
      sp = connection.setSavepoint();
      stmt = connection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, publication.getObjectName());
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String schema = rs.getString("table_schema");
        String table = rs.getString("table_name");
        String remarks = rs.getString("remarks");
        TableIdentifier tbl = new TableIdentifier(schema, table);
        tbl.setComment(remarks);
        tbl.setNeverAdjustCase(true);
        result.add(tbl);
      }
      connection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "subscription tables", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  private synchronized void checkPrivileges(WbConnection conn)
  {
    if (hasPrivileges != null) return;

    final String sql = "select has_table_privilege('pg_catalog.pg_subscription', 'select')";

    try
    {
      this.hasPrivileges = false;
      LogMgr.logDebug(new CallerInfo(){}, "Checking privileges for pg_catalog.pg_subscription");
      JdbcUtils.runQuery(conn, sql, false, (rs -> {if (rs.next()) this.hasPrivileges = rs.getBoolean(1);}));
      if (hasPrivileges == null || hasPrivileges == false)
      {
        LogMgr.logInfo(new CallerInfo(){}, "Can not display subscription information because uUser "
          + conn.getDisplayUser() + " does not have access to pg_catalog.pg_subscription");
      }
    }
    catch (SQLException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not check privileges for pg_catalog.pg_subscription", e);
      this.hasPrivileges = false;
    }
  }

}
