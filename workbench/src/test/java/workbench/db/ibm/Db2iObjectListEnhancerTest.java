/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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
package workbench.db.ibm;

import java.sql.Types;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.ConnectionMgr;
import workbench.db.IbmDb2Test;
import workbench.db.ObjectListDataStore;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;
import static workbench.db.ibm.Db2iObjectListEnhancer.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(IbmDb2Test.class)
public class Db2iObjectListEnhancerTest
  extends WbTestCase
{

  public Db2iObjectListEnhancerTest()
  {
    super("Db2iObjectListEnhancerTest");
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    ConnectionMgr.getInstance().disconnectAll();
  }

  @Test
  public void testUpdateObjectList()
    throws Exception
  {
    WbConnection con = getTestUtil().getConnection();
    TestUtil.executeScript(con,
      "create schema qsys2;\n" +
      "create table qsys2.systables (table_name varchar(100), table_schema varchar(100), table_text varchar(100), system_table_name varchar(100));\n" +
      "insert into qsys2.systables values ('FOO', 'PUBLIC', 'Foo comment', 'FOO1');\n" +
      "insert into qsys2.systables values ('BAR', 'PUBLIC', 'Bar comment', 'BAR1');\n" +
      "commit;");

    Db2iObjectListEnhancer reader = new Db2iObjectListEnhancer();
    ObjectListDataStore tables = new ObjectListDataStore();
    ColumnIdentifier sysName = new ColumnIdentifier(SYSTEM_NAME_DS_COL, Types.VARCHAR, 50);
    tables.addColumn(sysName);

    int fooRow = tables.addRow();
    tables.setSchema(fooRow, "PUBLIC");
    tables.setCatalog(fooRow, "CAT");
    tables.setObjectName(fooRow, "FOO");

    int barRow = tables.addRow();
    tables.setSchema(barRow, "PUBLIC");
    tables.setCatalog(barRow, "CAT");
    tables.setObjectName(barRow, "BAR");

    reader.updateResult(con, tables, "PUBLIC", null, true);
    assertEquals("Foo comment", tables.getRemarks(fooRow));
    assertEquals("Bar comment", tables.getRemarks(barRow));
    assertEquals("BAR1", tables.getValueAsString(barRow, "SYSTEM_NAME"));
    assertEquals("FOO1", tables.getValueAsString(fooRow, "SYSTEM_NAME"));
  }

}
