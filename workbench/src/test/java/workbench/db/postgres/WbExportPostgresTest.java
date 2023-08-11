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
import workbench.db.WbConnection;

import workbench.sql.StatementRunner;

import workbench.util.StringUtil;
import workbench.util.WbFile;

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
public class WbExportPostgresTest
  extends WbTestCase
{

  private static final String TEST_ID = "wb_export_pg";

  public WbExportPostgresTest()
  {
    super(TEST_ID);
  }


  @BeforeClass
  public static void setUp()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_ID);
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    TestUtil.executeScript(con,
      "create table ranges (product_id integer, start_date date, end_date date, start_time time);\n" +
      "insert into ranges (product_id, start_date, end_date, start_time) " +
      "values " +
      " (1, '-infinity', date '2009-12-31', time '20:00'), \n" +
      " (1, date '2010-01-01', date '2011-12-31', time '19:00'), \n" +
      " (1, date '2012-01-01', 'infinity', time '18:00'); \n" +
      "commit;\n"
      );
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testExportInfinity()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    StatementRunner runner = getTestUtil().createConnectedStatementRunner(con);

    WbFile output = new WbFile(getTestUtil().getBaseDir(), "ranges.txt");
    runner.runStatement("WbExport -file='" + output.getAbsolutePath() + "' -type=text -header=false -type=text -dateFormat='yyyy-MM-dd' -timeFormat='HH:mm'");
    runner.runStatement("select start_date, end_date, start_time from ranges order by start_date");
    assertTrue(output.exists());
    List<String> lines = TestUtil.readLines(output);
    assertEquals(3, lines.size());
    List<String> elements = StringUtil.stringToList(lines.get(0), "\t");
    assertEquals(3, elements.size());
    assertEquals("-infinity", elements.get(0));
    assertEquals("2009-12-31", elements.get(1));
    assertEquals("20:00", elements.get(2));

    elements = StringUtil.stringToList(lines.get(1), "\t");
    assertEquals(3, elements.size());
    assertEquals("2010-01-01", elements.get(0));
    assertEquals("2011-12-31", elements.get(1));
    assertEquals("19:00", elements.get(2));

    elements = StringUtil.stringToList(lines.get(2), "\t");
    assertEquals(3, elements.size());
    assertEquals("2012-01-01", elements.get(0));
    assertEquals("infinity", elements.get(1));
    assertEquals("18:00", elements.get(2));
  }

}
