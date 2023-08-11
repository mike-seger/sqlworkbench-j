/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
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
import java.sql.Statement;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.PostgresDbTest;
import workbench.db.TriggerDefinition;
import workbench.db.WbConnection;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;
/**
 * This test requires that the wbjunit database user has the "superuser" role!
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class PostgresEventTriggerReaderTest
  extends WbTestCase
{

  public PostgresEventTriggerReaderTest()
  {
    super("PostgresEventTriggerReaderTest");
  }

  @Test
  public void testRetrieveTriggers()
    throws Exception
  {
    String script =
      "CREATE OR REPLACE FUNCTION dummy_trigger()\n" +
      "  RETURNS event_trigger\n" +
      "  LANGUAGE plpgsql\n" +
      "AS $$\n" +
      "BEGIN\n" +
      "END;\n" +
      "$$;\n" +
      "\n" +
      "CREATE EVENT TRIGGER dummy_event_trigger \n" +
      "   ON ddl_command_start WHEN TAG IN ('DROP FUNCTION')\n" +
      "   EXECUTE PROCEDURE dummy_trigger();\n";

    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (!isSuperUser(con))
    {
      System.out.println("*** Not running PostgresEventTriggerReaderTest because user \"" +
        con.getCurrentUser() + "\" is not a superuser!");
      return;
    }

    TestUtil.executeScript(con, script, true);

    PostgresEventTriggerReader reader = new PostgresEventTriggerReader();
    ObjectListDataStore ds = new ObjectListDataStore();
    String[] types = new String[] {PostgresEventTriggerReader.TYPE};
    reader.extendObjectList(con, ds, null, "public", "%", types);
    assertEquals(1, ds.getRowCount());
    TriggerDefinition trigger = ds.getUserObject(0, TriggerDefinition.class);
    assertNotNull(trigger);
    assertEquals("dummy_event_trigger", trigger.getObjectName());
    assertEquals(PostgresEventTriggerReader.TYPE, trigger.getTriggerType());
  }

  private boolean isSuperUser(WbConnection con)
  {
    if (con == null) return false;

    Statement stmt = null;
    ResultSet rs = null;
    boolean result = false;
    try
    {
      stmt = con.createStatementForQuery();
      rs = stmt.executeQuery(
        "select usesuper\n" +
        "from pg_user\n" +
        "where usename = current_user");
      if (rs.next())
      {
        result = rs.getBoolean(1);
      }
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }
}
