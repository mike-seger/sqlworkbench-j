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

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.PostgresDbTest;
import workbench.db.WbConnection;

import workbench.sql.StatementRunnerResult;
import workbench.sql.parser.ParserType;
import workbench.sql.parser.ScriptParser;
import workbench.sql.wbcommands.WbDataDiff;

import workbench.util.FileUtil;
import workbench.util.SqlUtil;
import workbench.util.WbFile;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class PostgresDataDiffTest
  extends WbTestCase
{

  public PostgresDataDiffTest()
  {
    super("PostgresDataDiffTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    PostgresTestUtil.initTestCase("diff_test");
  }

  @After
  public void tearDown()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testNullKeys()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    String sql1 =
      "create schema one;\n" +
      "create table one.data (\n" +
      "   id_1 integer, " +
      "   id_2 integer, " +
      "   data integer " +
      ");\n" +
      "commit;\n";

    TestUtil.executeScript(conn, sql1);
    String sql2 = sql1.replace("one", "two");
    TestUtil.executeScript(conn, sql2);

    TestUtil.executeScript(conn,
      "insert into one.data (id_1, id_2, data) \n" +
      "values\n" +
      "(1, null, 42);" +
      "commit;");

    TestUtil.executeScript(conn,
      "insert into two.data (id_1, id_2, data) \n" +
      "values\n" +
      "(1, null, 4);" +
      "commit;");

    WbDataDiff diff = new WbDataDiff();
    diff.setConnection(conn);
    TestUtil util = getTestUtil();
    WbFile outFile1 = util.getFile("null_diff.sql");

    String diffSql =
      "WbDataDiff -referenceSchema=one -targetSchema=two -file='" + outFile1.getAbsolutePath() + "' " +
      "           -alternateKey='data=id_1,id_2' -encoding=UTF-8";

    StatementRunnerResult result = diff.execute(diffSql);
    assertTrue(result.isSuccess());
    assertTrue(outFile1.exists());
    WbFile updateFile = util.getFile("data_$update.sql");
    assertTrue(updateFile.exists());
    String content = FileUtil.readFile(updateFile, "UTF-8");
    String updateSql = SqlUtil.makeCleanSql(content, false, false, false, true, '"');
    updateSql = updateSql.replaceAll("\\s+", " ").toLowerCase();
    assertEquals("update two.data set data = 42 where id_1 = 1 and id_2 is null", updateSql);
  }

  @Test
  public void testStructDiff()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    String sql1 =
      "create schema one;\n" +
      "create type one.container as (x int, y int); \n" +
      "create table one.the_table (\n" +
      "   id integer not null primary key, " +
      "   data one.container\n " +
      ");\n" +
      "commit;\n";

    TestUtil.executeScript(conn, sql1);
    String sql2 = sql1.replace("one", "two");
    TestUtil.executeScript(conn, sql2);

    String insert1 =
      "insert into one.the_table (id, data) values \n" +
      "(1, row(4,2));\n" +
      "commit;";
    TestUtil.executeScript(conn, insert1);
    String insert2 =
      "insert into two.the_table (id, data) values \n" +
      "(1, row(4,2));\n" +
      "commit;";

    TestUtil.executeScript(conn, insert2);

    WbDataDiff diff = new WbDataDiff();
    diff.setConnection(conn);
    TestUtil util = getTestUtil();
    WbFile outFile1 = util.getFile("diff1.sql");

    String diffCmd =
      "WbDataDiff -referenceSchema=one -targetSchema=two -file='" + outFile1.getAbsolutePath() + "' " +
      "           -singleFile=true -encoding=UTF-8";

    StatementRunnerResult result = diff.execute(diffCmd);
    assertTrue(result.isSuccess());
    assertTrue(outFile1.exists());
    String script1 = FileUtil.readFile(outFile1, "UTF-8");
//    System.out.println(script1);
    assertTrue(script1.contains("-- No UPDATEs for the_table necessary"));
    assertTrue(script1.contains("-- No INSERTs for the_table necessary"));
    assertTrue(script1.contains("-- No DELETEs for the_table necessary"));

    TestUtil.executeScript(conn,
      "update two.the_table set data = row(42,0) where id = 1;\n" +
      "commit;");


    result = diff.execute(diffCmd);
    String script2 = FileUtil.readFile(outFile1, "UTF-8");
//    System.out.println(script2);
    assertTrue(script2.contains("UPDATE two.the_table"));
  }

  @Test
  public void testArrayDiff()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    String sql1 =
      "create schema one;\n" +
      "create table one.the_table (\n" +
      "   id integer not null primary key, " +
      "   data text[]\n " +
      ");\n" +
      "commit;\n";

    TestUtil.executeScript(conn, sql1);
    String sql2 = sql1.replace("one", "two");
    TestUtil.executeScript(conn, sql2);

    String insert1 =
      "insert into one.the_table (id, data) values \n" +
      "(1, array['one', 'two']);\n" +
      "commit;";
    TestUtil.executeScript(conn, insert1);
    String insert2 =
      "insert into two.the_table (id, data) values \n" +
      "(1, array['one', 'two']);\n" +
      "commit;";

    TestUtil.executeScript(conn, insert2);

    WbDataDiff diff = new WbDataDiff();
    diff.setConnection(conn);
    TestUtil util = getTestUtil();
    WbFile outFile1 = util.getFile("diff1.sql");

    String diffCmd =
      "WbDataDiff -referenceSchema=one -targetSchema=two -file='" + outFile1.getAbsolutePath() + "' " +
      "           -singleFile=true -encoding=UTF-8";

    StatementRunnerResult result = diff.execute(diffCmd);
    assertTrue(result.isSuccess());
    assertTrue(outFile1.exists());
    String script1 = FileUtil.readFile(outFile1, "UTF-8");
//    System.out.println(script1);
    assertTrue(script1.contains("-- No UPDATEs for the_table necessary"));
    assertTrue(script1.contains("-- No INSERTs for the_table necessary"));
    assertTrue(script1.contains("-- No DELETEs for the_table necessary"));

    TestUtil.executeScript(conn,
      "update two.the_table set data = array['fourtytwo'] where id = 1;\n" +
      "commit;");


    result = diff.execute(diffCmd);
    assertTrue(result.isSuccess());
    assertTrue(outFile1.exists());
    String script2 = FileUtil.readFile(outFile1, "UTF-8");

    // remove comments and other noise
    script2 = SqlUtil.makeCleanSql(script2, true);

    ScriptParser parser = new ScriptParser(script2, ParserType.Postgres);

    int size = parser.getSize();
    // UPDATE and COMMIT
    assertEquals(2, size);

    String expected =
      "UPDATE two.the_table \n" +
      "   SET data = '{one,two}' \n" +
      "WHERE id = 1";
    assertEquals(expected, parser.getCommand(0));
    assertEquals("COMMIT", parser.getCommand(1));
  }

  @Test
  public void testDiff()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    String sql1 =
      "create schema one;\n" +
      "create table one.the_table (\n" +
      "   id integer not null primary key, " +
      "   data varchar(100)\n " +
      ");\n" +
      "commit;\n";

    TestUtil.executeScript(conn, sql1);
    String sql2 = sql1.replace("one", "two");
    TestUtil.executeScript(conn, sql2);

    String insert1 =
      "insert into one.the_table (id, data) values \n" +
      "(1, 'Something \u00bb else'), (2, 'par d\u00e9faul 50 m\u00b2/room'), (3, 'details \u00bb de l''INSEE (annè)');\n" +
      "commit;";
//    System.out.println(insert1);

    TestUtil.executeScript(conn, insert1);
    String insert2 = insert1.replace("one", "two");
    TestUtil.executeScript(conn, insert2);

    WbDataDiff diff = new WbDataDiff();
    diff.setConnection(conn);
    TestUtil util = getTestUtil();
    WbFile outFile1 = util.getFile("diff1.sql");

    String diff1 =
      "WbDataDiff -referenceSchema=one -targetSchema=two -file='" + outFile1.getAbsolutePath() + "' " +
      "           -singleFile=true -encoding=UTF-8";

    StatementRunnerResult result = diff.execute(diff1);
    assertTrue(result.isSuccess());
    assertTrue(outFile1.exists());

    String script1 = FileUtil.readFile(outFile1, "UTF-8");
//    System.out.println(script1);
    assertTrue(script1.contains("-- No UPDATEs for the_table necessary"));
    assertTrue(script1.contains("-- No INSERTs for the_table necessary"));
    assertTrue(script1.contains("-- No DELETEs for the_table necessary"));

    TestUtil.executeScript(conn,
      "update two.the_table set data = '50m\u00b2/room' where id = 2;\n" +
      "update two.the_table set data = 'Arthur''s House' where id = 3;\n" +
      "commit; ");

    WbFile outFile2 = util.getFile("diff2.sql");
    String diff2 =
      "WbDataDiff -referenceSchema=one -targetSchema=two -file='" + outFile2.getAbsolutePath() + "' " +
      "           -singleFile=true -encoding=UTF-8";

    StatementRunnerResult result2 = diff.execute(diff2);
    assertTrue(result2.isSuccess());
    assertTrue(outFile2.exists());

    String script2 = FileUtil.readFile(outFile2, "UTF-8");
//    System.out.println(script2);
    assertFalse(script2.contains("WHERE id = 1"));
    assertTrue(script2.contains("WHERE id = 2"));
    assertTrue(script2.contains("WHERE id = 3"));
  }


}
