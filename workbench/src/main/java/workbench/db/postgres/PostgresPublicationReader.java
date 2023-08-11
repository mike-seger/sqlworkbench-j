/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.db.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
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
 * A class to read the definition of a Postgres publication for logical replication.
 *
 * @author Thomas Kellerer
 */
public class PostgresPublicationReader
  implements ObjectListExtender
{
  public PostgresPublicationReader()
  {
  }

  @Override
  public List<String> supportedTypes()
  {
    return CollectionUtil.arrayList(PgPublication.TYPE_NAME);
  }

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  @Override
  public boolean handlesType(String type)
  {
    return PgPublication.TYPE_NAME.equalsIgnoreCase(type);
  }

  private List<PgPublication> getPublications(WbConnection connection, String namePattern)
  {
    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    List<PgPublication> result = new ArrayList<>();

    String truncateCol;
    if (JdbcUtils.hasMinimumServerVersion(connection, "11"))
    {
      truncateCol = "       pubtruncate, \n";
    }
    else
    {
      truncateCol = "       false as pubtruncate, \n";
    }

    String pubviarootCol;
    if (JdbcUtils.hasMinimumServerVersion(connection, "13"))
    {
      pubviarootCol = "       pubviaroot, \n";
    }
    else
    {
      pubviarootCol = "       false as pubviaroot, \n";
    }

    StringBuilder sql = new StringBuilder(
      "-- SQL Workbench/J \n" +
      "select pubname, \n" +
      "       puballtables, \n" +
      "       pubinsert, \n" +
      "       pubupdate, \n" +
      "       pubdelete, \n" +
      truncateCol +
      pubviarootCol +
      "       pg_catalog.obj_description(oid) as remarks \n" +
      "from pg_catalog.pg_publication ");

    if (StringUtil.isNotBlank(namePattern))
    {
      sql.append("\nwhere ");
      SqlUtil.appendExpression(sql, "pubname", namePattern, connection);
    }

    sql.append("\norder by pubname");

    LogMgr.logMetadataSql(new CallerInfo(){}, "publications", sql);

    try
    {
      sp = connection.setSavepoint();
      stmt = connection.createStatementForQuery();
      rs = stmt.executeQuery(sql.toString());
      while (rs.next())
      {
        String name = rs.getString("pubname");
        String remarks = rs.getString("remarks");
        PgPublication pub = new PgPublication(name);
        pub.setReplicatesDeletes(rs.getBoolean("pubdelete"));
        pub.setReplicatesInserts(rs.getBoolean("pubinsert"));
        pub.setReplicatesUpdates(rs.getBoolean("pubupdate"));
        pub.setReplicatesTruncate(rs.getBoolean("pubtruncate"));
        pub.setPublishViaPartitionRoot(rs.getBoolean("pubviaroot"));
        pub.setComment(remarks);
        result.add(pub);
      }
      connection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "publications", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    PgPublication pub = getObjectDefinition(con, object);
    if (pub == null) return null;
    return null;
  }

  @Override
  public PgPublication getObjectDefinition(WbConnection con, DbObject object)
  {
    List<PgPublication> publications = getPublications(con, object.getObjectName());
    if (publications == null || publications.isEmpty()) return null;
    return publications.get(0);
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    PgPublication pub = getObjectDefinition(con, object);
    if (pub == null) return null;
    try
    {
      return pub.getSource(con).toString();
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve publication source", ex);
      return null;
    }
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

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result, String aCatalog, String aSchema, String objects, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded(PgPublication.TYPE_NAME, requestedTypes)) return false;
    List<PgPublication> publications = getPublications(con, objects);
    result.addObjects(publications);
    return publications.size() > 0;
  }

  public List<TableIdentifier> getTables(WbConnection con, DbObject dbo)
  {
    PgPublication pub = getObjectDefinition(con, dbo);
    return pub.retrieveTables(con);
  }
}
