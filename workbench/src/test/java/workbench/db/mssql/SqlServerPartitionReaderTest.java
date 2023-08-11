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
package workbench.db.mssql;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ConnectionMgr;
import workbench.db.MsSQLTest;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(MsSQLTest.class)
public class SqlServerPartitionReaderTest
  extends WbTestCase
{

  public SqlServerPartitionReaderTest()
  {
    super("MSSQLPartitionReaderTest");
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    WbConnection con = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", con);
    SQLServerTestUtil.dropAllObjects(con);
    TestUtil.executeScript(con,
      "drop partition scheme my_part_scheme;\n" +
      "drop partition function my_part_function;\n" +
      "commit;");
    ConnectionMgr.getInstance().disconnect(con);
  }

  @Test
  public void testPartitionReader()
    throws Exception
  {
    WbConnection con = SQLServerTestUtil.getSQLServerConnection();
    if (!SqlServerUtil.supportsPartitioning(con)) return;

    String sql =
      "CREATE PARTITION FUNCTION my_part_function (int)  \n" +
      "    AS RANGE LEFT FOR VALUES (1, 100, 1000, 10000) ;  \n" +
      "\n" +
      "CREATE PARTITION SCHEME my_part_scheme\n" +
      "    AS PARTITION my_part_function\n" +
      "    all TO ([primary]);  \n" +
      "\n" +
      "CREATE TABLE my_part_table \n" +
      "(\n" +
      "  col1 int PRIMARY KEY, \n" +
      "  col2 varchar(10)\n" +
      ") \n" +
      "ON my_part_scheme (col1);";

    TestUtil.executeScript(con, sql, true);
    SqlServerPartitionReader reader = new SqlServerPartitionReader(con);
    TableIdentifier tbl = new TableIdentifier("my_part_table");
    PartitionFunction function = reader.getFunctionForTable(tbl);
    assertNotNull(function);
    assertEquals("RANGE LEFT", function.getTypeDef());
    assertEquals("int", function.getParameterString());

    PartitionScheme scheme = reader.getSchemeForTable(tbl);
    assertNotNull(scheme);
    assertEquals("my_part_function", scheme.getFunctionName());
    assertEquals(1, scheme.getFileGroups().size());
    assertTrue(scheme.getFileGroups().contains("[PRIMARY]"));

    SqlServerTableSourceBuilder builder = new SqlServerTableSourceBuilder(con);
    TableDefinition tblDef = con.getMetadata().getTableDefinitionReader().getTableDefinition(tbl, true);
    String source = builder.getTableSource(tblDef.getTable(), tblDef.getColumns());
    assertTrue(source.contains("ON my_part_scheme (col1);"));
    assertTrue(source.contains("my_part_function (int)"));
    assertTrue(source.contains("AS RANGE LEFT\n  FOR VALUES (1,100,1000,10000);"));
    assertTrue(source.contains("AS PARTITION my_part_function\n  ALL TO ([PRIMARY]);"));
  }

}
