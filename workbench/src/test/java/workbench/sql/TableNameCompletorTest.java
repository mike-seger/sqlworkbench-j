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
package workbench.sql;

import java.util.ArrayList;
import java.util.List;

import workbench.WbTestCase;

import workbench.db.WbConnection;

import workbench.sql.syntax.SqlKeywordHelper;

import org.junit.Test;

import static org.junit.Assert.*;


/**
 *
 * @author Thomas Kellerer
 */
public class TableNameCompletorTest
  extends WbTestCase
{

  public TableNameCompletorTest()
  {
    super("TableNameCompletor");
  }

  @Test
  public void testDDLCompletion()
    throws Exception
  {
    WbConnection conn = getTestUtil().getHSQLConnection("completor");
    TableNameCompletor completor = new TableNameCompletor(conn);
    List candidates = new ArrayList<>();

    SqlKeywordHelper helper = new SqlKeywordHelper(conn.getDbId());
    List<String> kwlist = new ArrayList<>(helper.loadKeywordsFromFile("ddl_types.txt"));
    int pos = completor.complete("drop ", 5, candidates);
    assertTrue(pos > -1);
    String kw = (String)candidates.get(0);
    assertEquals(kw, kwlist.get(0));

    candidates.clear();
    pos = completor.complete("drop " + kw, 5 + kw.length(), candidates);
    assertTrue(pos > 0);
    kw = (String)candidates.get(0);
    assertEquals(kw, kwlist.get(1));

    candidates.clear();
    pos = completor.complete("drop " + kw, 5 + kw.length(), candidates);
    assertTrue(pos > 0);
    kw = (String)candidates.get(0);
    assertEquals(kw, kwlist.get(2));
  }

}
