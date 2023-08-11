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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.WbConnection;

import workbench.util.CollectionUtil;

import workbench.db.JdbcUtils;

import workbench.util.StringUtil;

/**
 * Some utility functions for SQL Server.
 *
 * For the version information see: https://sqlserverbuilds.blogspot.de/
 *
 * @author Thomas Kellerer
 */
public class SqlServerUtil
{
  private static final String IS_ENTERPRISE_PROP = "isEnterprise";

  public static boolean isMicrosoftDriver(WbConnection conn)
  {
    if (conn == null) return false;
    String url = conn.getUrl();
    if (url == null) return false;
    return url.startsWith("jdbc:sqlserver:");
  }

  /**
   * Returns true if the connection is to a SQL Server 2019 or later.
   */
  public static boolean isSqlServer2019(WbConnection conn)
  {
    return JdbcUtils.hasMinimumServerVersion(conn, "15.0");
  }

  /**
   * Returns true if the connection is to a SQL Server 2017 or later.
   */
  public static boolean isSqlServer2017(WbConnection conn)
  {
    return JdbcUtils.hasMinimumServerVersion(conn, "14.0");
  }

  /**
   * Returns true if the connection is to a SQL Server 2016 or later.
   */
  public static boolean isSqlServer2016(WbConnection conn)
  {
    return JdbcUtils.hasMinimumServerVersion(conn, "13.0");
  }

  /**
   * Returns true if the connection is to a SQL Server 2014 or later.
   */
  public static boolean isSqlServer2014(WbConnection conn)
  {
    return JdbcUtils.hasMinimumServerVersion(conn, "12.0");
  }

  /**
   * Returns true if the connection is to a SQL Server 2012 or later.
   */
  public static boolean isSqlServer2012(WbConnection conn)
  {
    return JdbcUtils.hasMinimumServerVersion(conn, "11.0");
  }

  /**
   * Returns true if the connection is to a SQL Server 2008R2 or later.
   */
  public static boolean isSqlServer2008R2(WbConnection conn)
  {
    return JdbcUtils.hasMinimumServerVersion(conn, "10.5");
  }

  /**
   * Returns true if the connection is to a SQL Server 2008 or later.
   */
  public static boolean isSqlServer2008(WbConnection conn)
  {
    return JdbcUtils.hasMinimumServerVersion(conn, "10.0");
  }

  /**
   * Returns true if the connection is to a SQL Server 2005 or later.
   */
  public static boolean isSqlServer2005(WbConnection conn)
  {
    return JdbcUtils.hasMinimumServerVersion(conn, "9.0");
  }

  /**
   * Returns true if the current connection is to a SQL Server version that supports partitioning.
   */
  public static boolean supportsPartitioning(WbConnection conn)
  {
    if (conn == null) return false;
    if (!isSqlServer2016(conn)) return false;
    return isEnterprise(conn);
  }

  /**
   * Returns true if the current connection is to an enterprise (or developer) edition.
   */
  public static boolean isEnterprise(WbConnection conn)
  {
    if (conn == null) return false;

    String prop = conn.getSessionProperty(IS_ENTERPRISE_PROP);
    if (prop != null)
    {
      return StringUtil.stringToBool(prop);
    }

    String sql = "select cast(serverproperty('Edition') as varchar(100))";
    Statement stmt = null;
    ResultSet rs = null;
    boolean isEnterprise = false;
    try
    {
      stmt = conn.createStatement();
      rs = stmt.executeQuery(sql);
      if (rs.next())
      {
        String edition = rs.getString(1);
        if (StringUtil.isNotBlank(edition))
        {
          isEnterprise = edition.toLowerCase().contains("enterprise") || edition.toLowerCase().contains("developer");
        }
      }
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve edition using " + sql, ex);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    conn.setSessionProperty(IS_ENTERPRISE_PROP, Boolean.toString(isEnterprise));
    return isEnterprise;
  }

  /**
   * Returns true if the connection is to a SQL Server 2000 or later.
   */
  public static boolean isSqlServer2000(WbConnection conn)
  {
    return JdbcUtils.hasMinimumServerVersion(conn, "8.0");
  }

  public static void setLockTimeout(WbConnection conn, int millis)
  {
    Statement stmt = null;
    String sql = "SET LOCK_TIMEOUT " + Integer.toString(millis <= 0 ? -1 : millis );
    try
    {
      stmt = conn.createStatement();
      LogMgr.logInfo(new CallerInfo(){}, "Setting lock timeout: " + millis + "ms");
      stmt.execute(sql);
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not set lock timeout using: " + sql, ex);
    }
    finally
    {
      JdbcUtils.closeStatement(stmt);
    }
  }

  public static void changeDatabase(WbConnection conn, String dbName)
  {
    try
    {
      conn.getSqlConnection().setCatalog(dbName);
    }
    catch (SQLException ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not change database", ex);
    }
  }

  public static String getVersion(WbConnection conn)
  {
    if (conn.isBusy()) return null;

    Statement stmt = null;
    ResultSet rs = null;
    String version = null;

    try
    {
      stmt = conn.createStatement();
      rs = stmt.executeQuery("select @@version");
      if (rs.next())
      {
        String info = rs.getString(1);
        List<String> lines = StringUtil.getLines(info);
        if (CollectionUtil.isNonEmpty(lines))
        {
          version = StringUtil.trimToNull(lines.get(0));
        }
      }
    }
    catch (Throwable ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not retrieve database version using @@version" , ex);
      try
      {
        version = conn.getMetadata().getJdbcMetaData().getDatabaseProductVersion();
      }
      catch (Throwable th)
      {
        // ignore
      }
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return version;
  }
}
