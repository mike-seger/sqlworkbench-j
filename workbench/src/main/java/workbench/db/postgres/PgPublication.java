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

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PgPublication
  implements DbObject, Serializable
{
  public static final String OPTION_KEY_FILTER = "publicationFilter";
  public static final String TYPE_NAME = "PUBLICATION";
  private String name;
  private String comment;
  private boolean replicatesInserts;
  private boolean replicatesUpdates;
  private boolean replicatesTruncate;
  private boolean replicatesDeletes;
  private boolean publishViaRoot;

  private boolean includeAllTables;
  private boolean tablesInitialized;
  private final List<TableIdentifier> tables = new ArrayList<>();

  public PgPublication(String name)
  {
    this.name = name;
  }

  @Override
  public String getCatalog()
  {
    return null;
  }

  @Override
  public String getSchema()
  {
    return null;
  }

  @Override
  public String getObjectType()
  {
    return TYPE_NAME;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return name;
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return name;
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return name;
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    String source = "CREATE PUBLICATION " + SqlUtil.quoteObjectname(name);
    if (includeAllTables)
    {
      source += "\n  FOR ALL TABLES";
    }
    else
    {
      if (!tablesInitialized)
      {
        setTables(retrieveTables(con));
      }
      String indent = " ";
      if (tables.size() > 1)
      {
        indent = "\n  ";
      }
      String options = tables.stream().map(t -> getTableOptionSource(con, t)).collect(Collectors.joining("," + indent));
      source += "\nFOR TABLE" + indent + options;
    }
    String publish = getPublishOptions();
    if (publish != null)
    {
      source += "\n" + publish;
    }
    source += ";";
    return source;
  }

  private String getTableOptionSource(WbConnection con, TableIdentifier tbl)
  {
    String option = tbl.getTableExpression(con);
    String filter = tbl.getSourceOptions().getConfigSettings().get(OPTION_KEY_FILTER);
    if (StringUtil.isNotEmpty(filter))
    {
      option = option + " WHERE " + filter;
    }
    return option;
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return name;
  }

  @Override
  public String getComment()
  {
    return comment;
  }

  @Override
  public void setComment(String cmt)
  {
    this.comment = cmt;
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return "DROP PUBLICATION IF EXISTS " + SqlUtil.quoteObjectname(name);
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

  @Override
  public String getObjectName()
  {
    return name;
  }

  @Override
  public void setName(String name)
  {
    this.name = name;
  }

  public void setPublishViaPartitionRoot(boolean flag)
  {
    this.publishViaRoot = flag;
  }

  public void setReplicatesInserts(boolean replicatesInserts)
  {
    this.replicatesInserts = replicatesInserts;
  }

  public void setReplicatesUpdates(boolean replicatesUpdates)
  {
    this.replicatesUpdates = replicatesUpdates;
  }

  public void setReplicatesTruncate(boolean replicatesTruncate)
  {
    this.replicatesTruncate = replicatesTruncate;
  }

  public void setReplicatesDeletes(boolean replicatesDeletes)
  {
    this.replicatesDeletes = replicatesDeletes;
  }

  public void setIncludeAllTables(boolean includeAllTables)
  {
    this.includeAllTables = includeAllTables;
  }

  private boolean replicateAllDml()
  {
    return replicatesDeletes && replicatesInserts && replicatesUpdates && replicatesTruncate;
  }

  private String getPublishOptions()
  {
    String publish = "";
    if (!replicateAllDml())
    {
      int num = 0;
      if (replicatesInserts)
      {
        publish += "insert";
        num++;
      }
      if (replicatesUpdates)
      {
        if (num > 0) publish += ", ";
        publish += "update";
        num++;
      }
      if (replicatesDeletes)
      {
        if (num > 0) publish += ", ";
        publish += "delete";
        num++;
      }
      if (replicatesTruncate)
      {
        if (num > 0) publish += ", ";
        publish += "truncate";
        num++;
      }
      publish = "publish = '" + publish + "'";
    }

    if (publishViaRoot)
    {
      if (!publish.isEmpty()) publish += ", ";
      publish += "publish_via_partition_root = true";
    }
    if (publish.isEmpty()) return null;
    return "WITH (" + publish + ")";
  }

  public void setTables(List<TableIdentifier> pubTables)
  {
    this.tables.clear();
    if (CollectionUtil.isEmpty(pubTables)) return;
    this.tables.addAll(pubTables);
    this.tablesInitialized = true;
  }

  public List<TableIdentifier> retrieveTables(WbConnection connection)
  {
    PreparedStatement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    List<TableIdentifier> result = new ArrayList<>();

    String filterCol;

    if (JdbcUtils.hasMinimumServerVersion(connection, "15"))
    {
      filterCol = "       pg_get_expr(rel.prqual, rel.prrelid) as filter_clause, \n";
    }
    else
    {
      filterCol = "       null::text as filter_clause, \n";
    }

    String sql =
      "select t.relnamespace::regnamespace::text as schema_name, \n" +
      "       t.relname as table_name, \n" +
      filterCol +
      "       pg_catalog.obj_description(t.oid) as remarks \n" +
      "from pg_class t \n" +
      "  join pg_publication_rel rel on t.oid = rel.prrelid \n" +
      "  join pg_publication pub on pub.oid = rel.prpubid \n" +
      "where pub.pubname = ? \n" +
      "order by 1,2";

    LogMgr.logMetadataSql(new CallerInfo(){}, "publication tables", sql);

    try
    {
      sp = connection.setSavepoint();
      stmt = connection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, name);
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String schema = rs.getString("schema_name");
        String table = rs.getString("table_name");
        String remarks = rs.getString("remarks");
        String filter = rs.getString("filter_clause");
        TableIdentifier tbl = new TableIdentifier(schema, table);
        tbl.setComment(remarks);
        tbl.setNeverAdjustCase(true);
        if (filter != null)
        {
          tbl.getSourceOptions().addConfigSetting(OPTION_KEY_FILTER, filter);
        }
        result.add(tbl);
      }
      connection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "publication tables", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

}
