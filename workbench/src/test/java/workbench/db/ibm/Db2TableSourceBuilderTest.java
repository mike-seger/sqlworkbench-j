/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
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
package workbench.db.ibm;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DropType;
import workbench.db.IbmDb2Test;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.TableSourceBuilderFactory;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(IbmDb2Test.class)
public class Db2TableSourceBuilderTest
  extends WbTestCase
{

  public Db2TableSourceBuilderTest()
  {
    super("Db2TableSourceBuilderTest");
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    WbConnection con = Db2TestUtil.getDb2Connection();
    if (con == null) return;
    Db2TestUtil.cleanUpTestCase();
  }


  @Test
  public void testGenerateTableSource()
    throws Exception
  {
    WbConnection con = Db2TestUtil.getDb2Connection();
    if (con == null) fail("No connection available");
    String sql = "create table tbs_test (id integer);";
    TestUtil.executeScript(con, sql, false);
    TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
    List<TableIdentifier> tables = con.getMetadata().getTableList("TBS_TEST", null);
    String ddl = builder.getTableSource(tables.get(0), DropType.none, false);
    assertTrue(ddl.contains("IN USERSPACE1"));
  }

}
