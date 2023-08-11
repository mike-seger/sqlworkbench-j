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
package workbench.sql.wbcommands;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ConnectionMgr;
import workbench.db.WbConnection;

import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class WbRunResultTest
  extends WbTestCase
{

  public WbRunResultTest()
  {
    super("WbRunResultTest");
  }

  @Test
  public void testExecute()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection con = util.getHSQLConnection("quoteheader");
    try
    {
      TestUtil.executeScript(con,
        "create table run_result_test (id integer, some_value varchar(20)); \n" +
        "insert into run_result_test values (1, 'foo'), (2, 'bar'); \n" +
        "commit;");

      StatementRunner runner = new StatementRunner();
      runner.setConnection(con);

      StatementRunnerResult result = null;
      result = runner.runStatement(WbRunResult.VERB);
      assertTrue(result.isSuccess());
      assertNotNull(runner.getConsumer());
      result = runner.runStatement(
        "select 'delete from '||table_name||';'\n" +
        "from information_schema.tables\n" +
        "where table_name = 'RUN_RESULT_TEST'");
      assertTrue(result.isSuccess());
      assertNull(runner.getConsumer());
      int count = TestUtil.getNumberValue(con, "select count(*) from run_result_test");
      assertEquals(0, count);
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

}
