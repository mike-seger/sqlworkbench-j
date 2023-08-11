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
package workbench.db.oracle;

import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.OracleTest;
import workbench.db.WbConnection;

import workbench.sql.StatementRunnerResult;
import workbench.sql.wbcommands.WbImport;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(OracleTest.class)
public class WbImportOracleTest
  extends WbTestCase
{

  private static final String TEST_ID = "wb_import_pg";

  public WbImportOracleTest()
  {
    super(TEST_ID);
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    OracleTestUtil.initTestCase();
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;
    String sql =
      "create table lookup (id integer primary key, data varchar(100));\n" +
      "create table data(\n" +
      "  id integer primary key, \n" +
      "  code integer references lookup, \n" +
      "  value varchar(50)\n" +
      ");\n" +
      "insert into lookup (id, data) values (1, 'one');\n" +
      "insert into lookup (id, data) values (2, 'two');\n" +
      "insert into lookup (id, data) values (3, 'three');\n" +
      "insert into data (id, code, value) values (1, 1, null);\n" +
      "insert into data (id, code, value) values (2, null, 'bla');\n" +
      "create table clob_test (id integer primary key, content clob);\n" +
      "commit;";
    TestUtil.executeScript(con, sql);

  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testUpsertWithConstantQuery()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull(con);

    TestUtil util = getTestUtil();
    WbImport cmd = new WbImport();
    cmd.setConnection(con);

    String data =
      "id,code_name,value\n"+
      "1,two,first value\n" +
      "2,one,second value\n" +
      "3,three,third value";

    File importFile = new File(util.getBaseDir(), "lookup_test.txt");
    TestUtil.writeFile(importFile, data, "ISO-8859-1");

    String sql =
      "WbImport -file=\"" + importFile.getAbsolutePath() + "\" -type=text \n" +
      "-table=data \n" +
      "-delimiter=, -mode=upsert \n" +
      "-importColumns=id,value \n" +
      "-constantValues='code=$@{select id from lookup where data = $2}'";

    StatementRunnerResult result = cmd.execute(sql);
    if (!result.isSuccess())
    {
      System.out.println(result.getMessages());
    }
    assertTrue(result.isSuccess());
    Number code = (Number)TestUtil.getSingleQueryValue(con, "select code from data where id=1");
    assertEquals(2, code.intValue());
    code = (Number)TestUtil.getSingleQueryValue(con, "select code from data where id=2");
    assertEquals(1, code.intValue());
    code = (Number)TestUtil.getSingleQueryValue(con, "select code from data where id=3");
    assertEquals(3, code.intValue());
  }

  @Test
  public void testTextClobImport()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull(con);
    TestUtil util = getTestUtil();
    File importFile = new File(util.getBaseDir(), "import_text_clob.txt");

    TestUtil.writeFile(importFile,
      "id\tcontent\n" +
      "1\ttext_data_r1_c2.data\n" +
      "2\ttext_data_r2_c2.data\n", "UTF-8");

    String[] data = {"This is a CLOB string to be put into row 1",
                     "This is a CLOB string to be put into row 2"};

    File datafile = new File(util.getBaseDir(), "text_data_r1_c2.data");
    TestUtil.writeFile(datafile, data[0]);

    datafile = new File(util.getBaseDir(), "text_data_r2_c2.data");
    TestUtil.writeFile(datafile, data[1]);

    WbImport importCmd = new WbImport();
    importCmd.setConnection(con);
    StatementRunnerResult result = importCmd.execute(
      "wbimport -encoding=utf8 -file='" + importFile.getAbsolutePath() + "' -clobIsFilename=true " +
      "         -type=text -header=true -continueonerror=false -table=clob_test"
    );

    assertEquals("Import failed: " + result.getMessages().toString(), result.isSuccess(), true);

    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery("select id, content from clob_test order by id");
    while (rs.next())
    {
      int id = rs.getInt(1);
      String content = rs.getString(2);
      assertEquals(data[id - 1], content);
    }
  }
}
