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
package workbench.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.derby.DerbyViewGrantReader;
import workbench.db.firebird.FirebirdViewGrantReader;
import workbench.db.hsqldb.HsqlViewGrantReader;
import workbench.db.ibm.Db2ViewGrantReader;
import workbench.db.oracle.OracleViewGrantReader;
import workbench.db.postgres.PostgresViewGrantReader;

import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public abstract class ViewGrantReader
{

  public static ViewGrantReader createViewGrantReader(WbConnection conn)
  {
    DbMetadata meta = conn.getMetadata();
    DBID dbid = DBID.fromConnection(conn);

    switch (dbid)
    {
      case Oracle:
        return new OracleViewGrantReader();
      case Postgres:
      case H2:
      case MySQL:
      case MariaDB:
        return new PostgresViewGrantReader();
      case HSQLDB:
        return new HsqlViewGrantReader(conn);

      case SQL_Server:
        if (JdbcUtils.hasMinimumServerVersion(conn, "8.0"))
        {
          return new PostgresViewGrantReader();
        }
      case Firebird:
        return new FirebirdViewGrantReader();
      case DB2_LUW:
      case DB2_ISERIES:
      case DB2_ZOS:
        return new Db2ViewGrantReader(conn);
      case Derby:
        return new DerbyViewGrantReader();
    }
    return null;
  }

  public abstract String getViewGrantSql();

  public int getIndexForSchemaParameter()
  {
    return -1;
  }

  public int getIndexForCatalogParameter()
  {
    return -1;
  }

  public int getIndexForTableNameParameter()
  {
    return 1;
  }

  /**
   *  Return the GRANTs for the given view

   *  @return a List with TableGrant objects.
   */
  public Collection<GrantItem> getViewGrants(WbConnection dbConnection, TableIdentifier viewName)
  {
    Collection<GrantItem> result = new HashSet<>();

    String sql = this.getViewGrantSql();
    if (sql == null) return Collections.emptyList();

    LogMgr.logMetadataSql(new CallerInfo(){}, "view grants", sql);

    ResultSet rs = null;
    PreparedStatement stmt = null;
    try
    {
      TableIdentifier view = viewName.createCopy();
      view.adjustCase(dbConnection);

      stmt = dbConnection.getSqlConnection().prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
      int index = this.getIndexForSchemaParameter();
      if (index > 0) stmt.setString(index, view.getSchema());

      index = this.getIndexForCatalogParameter();
      if (index > 0) stmt.setString(index, view.getCatalog());

      index = this.getIndexForTableNameParameter();
      if (index > 0) stmt.setString(index, view.getTableName());

      rs = stmt.executeQuery();
      while (rs.next())
      {
        String to = rs.getString(1);
        String what = rs.getString(2);
        boolean grantable = StringUtil.stringToBool(rs.getString(3));
        GrantItem grant = new GrantItem(to, what, grantable);
        result.add(grant);
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "view grants", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;

  }

  /**
   *  Creates an SQL Statement which can be used to re-create the GRANTs on the
   *  given table.
   *
   *  @return SQL script to GRANT access to the table.
   */
  public StringBuilder getViewGrantSource(WbConnection dbConnection, TableIdentifier view)
  {
    Collection<GrantItem> grantList = this.getViewGrants(dbConnection, view);
    StringBuilder result = new StringBuilder(200);
    int count = grantList.size();

    // as several grants to several users can be made, we need to collect them
    // first, in order to be able to build the complete statements
    Map<String, List<String>> grants = new HashMap<>(count);

    for (GrantItem grant : grantList)
    {
      String grantee = grant.getGrantee();
      String priv = grant.getPrivilege();
      if (priv == null) continue;
      List<String> privs = grants.get(grantee);
      if (privs == null)
      {
        privs = new LinkedList<>();
        grants.put(grantee, privs);
      }
      privs.add(priv.trim());
    }
    for (Entry<String, List<String>> entry : grants.entrySet())
    {
      String grantee = entry.getKey();

      List<String> privs = entry.getValue();
      result.append("GRANT ");
      result.append(StringUtil.listToString(privs, ", ", false));
      result.append(" ON ");
      result.append(view.getTableExpression(dbConnection));
      result.append(" TO ");
      result.append(grantee);
      result.append(";\n");
    }
    return result;
  }
}
