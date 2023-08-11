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

import java.sql.Statement;

import workbench.AppArguments;
import workbench.TestUtil;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.sql.BatchRunner;

import workbench.util.ArgumentParser;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresTestUtil
{

  public static final String TEST_USER = "wbjunit";
  public static final String TEST_PWD = "wbjunit";
  public static final String TEST_DB = "wbjunit";
  public static final String TEST_HOST = "localhost";
  public static final String TEST_PORT = "5432";
  public static final String PROFILE_NAME = "WBJUnitPostgres";

  /**
   * Return a connection to a locally running PostgreSQL database
   */
  public static WbConnection getPostgresConnection()
  {
    return getPostgresConnection(getHostname(), TEST_DB, TEST_USER, TEST_PWD, getPort(), PROFILE_NAME);
  }

  public static String getPort()
  {
    String port = System.getProperty("WB_TEST_PORT", System.getenv("WB_TEST_PORT"));
    if (port == null)
    {
      port = TEST_PORT;
    }
    return port;
  }

  public static String getHostname()
  {
    String host = System.getProperty("WB_TEST_HOST", System.getenv("WB_TEST_HOST"));
    if (host == null)
    {
      host = TEST_HOST;
    }
    return host;
  }

  public static WbConnection getPostgresConnection(String hostName, String dbName, String username, String password, String port, String profileName)
  {
    try
    {
      WbConnection con = ConnectionMgr.getInstance().findConnection(PROFILE_NAME);
      if (con != null) return con;

      ArgumentParser parser = new AppArguments();
      parser.parse("-url='jdbc:postgresql://" + hostName + ":" + port + "/" + dbName + "' -username=" + username + " -password=" + password + " -driver=org.postgresql.Driver");
      ConnectionProfile prof = BatchRunner.createCmdLineProfile(parser);
      prof.setName(profileName);
      ConnectionMgr.getInstance().addProfile(prof);
      con = ConnectionMgr.getInstance().getConnection(prof, PROFILE_NAME);
      return con;
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not create Postgres connection", th);
      return null;
    }
  }

  public static void initTestCase(String schema)
    throws Exception
  {
    TestUtil util = new TestUtil(schema);
    util.prepareEnvironment();

    WbConnection con = getPostgresConnection();
    if (con == null) return;

    Statement stmt = null;
    try
    {
      stmt = con.createStatement();

      if (StringUtil.isBlank(schema))
      {
        schema = "junit";
      }
      else
      {
        schema = schema.toLowerCase();
      }

      dropAllObjects(con);

      stmt.execute("create schema "+ schema);
      stmt.execute("set session schema '" + schema + "'");
      con.commit();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      con.rollbackSilently();
    }
    finally
    {
      JdbcUtils.closeStatement(stmt);
    }
  }

  public static void cleanUpTestCase()
  {
    WbConnection con = getPostgresConnection();
    dropAllObjects(con);
    ConnectionMgr.getInstance().disconnectAll();
  }

  public static void dropAllObjects(WbConnection con)
  {
    if (con == null) return;

    Statement stmt = null;
    try
    {
      stmt = con.createStatement();
      stmt.execute("drop owned by wbjunit cascade");
      con.commit();
      con.getObjectCache().clear();
    }
    catch (Exception e)
    {
      con.rollbackSilently();
    }
    finally
    {
      JdbcUtils.closeStatement(stmt);
    }
  }
}
