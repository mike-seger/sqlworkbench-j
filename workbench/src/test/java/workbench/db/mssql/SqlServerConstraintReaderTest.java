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

import java.sql.SQLException;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObjectFinder;
import workbench.db.MsSQLTest;
import workbench.db.ReaderFactory;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(MsSQLTest.class)
public class SqlServerConstraintReaderTest
  extends WbTestCase
{

  public SqlServerConstraintReaderTest()
  {
    super("SqlServerConstraintReaderTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    SQLServerTestUtil.initTestcase("SqlServerProcedureReaderTest");
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", conn);
    SQLServerTestUtil.dropAllObjects(conn);
    String sql =
      "create table sales \n" +
      "( \n" +
      "   pieces integer, \n" +
      "   single_price numeric(19,2), \n" +
      "   constraint positive_amount check (pieces > 0) \n" +
      ");\n" +
      "commit;\n" +
      "create table def_test ( id integer constraint inital_value default 1);\n" +
      "commit;";
    TestUtil.executeScript(conn, sql);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", conn);
    SQLServerTestUtil.dropAllObjects(conn);
  }

  @Test
  public void testReader()
    throws SQLException
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    assertNotNull("No connection available", conn);

    TableIdentifier tbl = new DbObjectFinder(conn).findTable(new TableIdentifier("sales"));
    assertNotNull(tbl);
    String source = tbl.getSource(conn).toString();
    assertNotNull(source);
    assertTrue(source.contains("CHECK ([pieces]>(0))"));
    assertTrue(source.contains("CONSTRAINT positive_amount"));

    SqlServerConstraintReader reader = (SqlServerConstraintReader)ReaderFactory.getConstraintReader(conn.getMetadata());
    assertTrue(reader.isSystemConstraintName("FK__child__base_id__70099B30"));
    assertTrue(reader.isSystemConstraintName("CK__check_test__id__2E3BD7D3"));
    assertTrue(reader.isSystemConstraintName("PK__child__3213D0856E2152BE"));
    assertFalse(reader.isSystemConstraintName("PK_child__100"));
    assertFalse(reader.isSystemConstraintName("fk_child_base"));
  }

  @Test
  public void testDefaultConstraintName()
    throws SQLException
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    assertNotNull("No connection available", conn);

    TableIdentifier tbl = new DbObjectFinder(conn).findTable(new TableIdentifier("def_test"));
    assertNotNull(tbl);
    String source = tbl.getSource(conn).toString();
    assertNotNull(source);
//    System.out.println(source);
    assertTrue(source.contains("CONSTRAINT inital_value"));
  }

}
