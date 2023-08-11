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

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.PostgresDbTest;
import workbench.db.TriggerDefinition;
import workbench.db.TriggerReader;
import workbench.db.TriggerReaderFactory;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class PostgresTriggerReaderTest
  extends WbTestCase
{
  private static final String TEST_SCHEMA = "trgreadertest";

  public PostgresTriggerReaderTest()
  {
    super("PostgresTriggerReaderTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_SCHEMA);
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    TestUtil.executeScript(con, "create table some_table (id integer, some_data varchar(100));\n" +
      "commit;\n");

    String sql =
      "CREATE OR REPLACE FUNCTION my_trigger_func()  \n" +
      "RETURNS trigger AS  \n" +
      "$body$ \n" +
      "BEGIN \n" +
      "    if new.comment IS NULL then \n" +
      "        new.comment = 'n/a'; \n" +
      "    end if; \n" +
      "    RETURN NEW; \n" +
      "END; \n" +
      "$body$  \n" +
      "LANGUAGE plpgsql; \n" +
      "" +
      "\n" +
      "CREATE TRIGGER some_trg BEFORE UPDATE ON some_table \n" +
      "    FOR EACH ROW EXECUTE PROCEDURE my_trigger_func();\n";

    TestUtil.executeScript(con, sql);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testGetDependentSource()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TriggerReader reader = TriggerReaderFactory.createReader(con);
    assertTrue(reader instanceof PostgresTriggerReader);
    List<TriggerDefinition> triggers = reader.getTriggerList(null, TEST_SCHEMA, "some_table");
    assertEquals(1, triggers.size());

    TriggerDefinition trg = triggers.get(0);
    assertEquals("some_trg", trg.getObjectName());

    String sql = trg.getSource(con, false).toString();
    assertTrue(sql.startsWith("CREATE TRIGGER some_trg"));

    sql = reader.getDependentSource(null, TEST_SCHEMA, trg.getObjectName(), trg.getRelatedTable()).toString();
    assertNotNull(sql);
    assertTrue(sql.contains("CREATE OR REPLACE FUNCTION trgreadertest.my_trigger_func()"));
  }

  @Test
  public void testMultiTrigger()
    throws Exception
  {
    String sql =
      "create schema s1;\n" +
      "create schema s2;\n" +
      "create schema f1;\n" +
      "create table s1.t1(id int);\n" +
      "create table s2.t2(id int);\n" +

      "CREATE OR REPLACE FUNCTION f1.some_trigger_func()  \n" +
      "RETURNS trigger AS  \n" +
      "$body$ \n" +
      "BEGIN \n" +
      "    RETURN NEW; \n" +
      "END; \n" +
      "$body$  \n" +
      "LANGUAGE plpgsql; \n" +
      "\n" +
      "CREATE TRIGGER some_trg BEFORE UPDATE ON s1.t1\n" +
      "    FOR EACH ROW EXECUTE PROCEDURE f1.some_trigger_func(); \n" +
      "CREATE TRIGGER some_trg BEFORE UPDATE ON s2.t2\n" +
      "    FOR EACH ROW EXECUTE PROCEDURE f1.some_trigger_func(); \n" +
      "commit;";

    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil.executeScript(con, sql);

    TriggerReader reader = TriggerReaderFactory.createReader(con);

    List<TriggerDefinition> t1Triggers = reader.getTriggerList(null, "s1", "t1");
    assertEquals(1, t1Triggers.size());
    String src1 = reader.getTriggerSource(t1Triggers.get(0), false);
    String expected1 =
      "CREATE TRIGGER some_trg BEFORE UPDATE\n" +
      "  ON s1.t1 FOR EACH ROW\n" +
      "  EXECUTE FUNCTION f1.some_trigger_func();";
    assertEquals(expected1, src1);

    List<TriggerDefinition> t2Triggers = reader.getTriggerList(null, "s2", "t2");
    assertEquals(1, t2Triggers.size());
    String src2 = reader.getTriggerSource(t2Triggers.get(0), false);
    String expected2 =
      "CREATE TRIGGER some_trg BEFORE UPDATE\n" +
      "  ON s2.t2 FOR EACH ROW\n" +
      "  EXECUTE FUNCTION f1.some_trigger_func();";
    assertEquals(expected2, src2);
  }
}
