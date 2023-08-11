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
package workbench.db.oracle;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import workbench.WbTestCase;

import workbench.db.OracleTest;
import workbench.db.TriggerDefinition;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;
/**
 *
 * @author Thomas Kellerer
 */
@Category(OracleTest.class)
public class OracleTriggerReaderTest
  extends WbTestCase
{

  public OracleTriggerReaderTest()
  {
    super("OracleTriggerReaderTest");
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testListTriggers()
    throws SQLException
  {
    WbConnection conn = OracleTestUtil.getOracleConnection();
    assertNotNull("No Oracle connection available", conn);

    String tableDDL =
      "create table the_table\n" +
      "(\n" +
      "  id integer primary key,\n" +
      "  some_data int, \n" +
      "  updated_at timestamp\n" +
      ")";

    String trgDDL =
        "create or replace trigger the_table_trg\n" +
        "   before update or insert \n" +
        "   on the_table for each row\n" +
        "begin\n" +
        "  :new.updated_at := current_timestamp;\n" +
        "end;\n";

    try (Statement stmt = conn.createStatement();)
    {
      stmt.execute(tableDDL);
      stmt.execute(trgDDL);
    }

    OracleTriggerReader reader = new OracleTriggerReader(conn);
    List<TriggerDefinition> triggers = reader.getTriggerList(null, conn.getCurrentUser(), "THE_TABLE");
    assertEquals(1, triggers.size());
    assertEquals("THE_TABLE_TRG", triggers.get(0).getObjectName());
    assertEquals("THE_TABLE", triggers.get(0).getRelatedTable().getTableName());
  }

}
