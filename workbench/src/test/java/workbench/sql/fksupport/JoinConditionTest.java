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
package workbench.sql.fksupport;

import java.util.HashMap;
import java.util.Map;

import workbench.WbTestCase;
import workbench.resource.GeneratedIdentifierCase;

import workbench.db.QuoteHandler;

import workbench.util.TableAlias;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class JoinConditionTest
  extends WbTestCase
{

  public JoinConditionTest()
  {
    super("JoinConditionTest");
  }

  @Test
  public void testCondition()
  {
    TableAlias person = new TableAlias("person", "p", '.', '.');
    TableAlias address = new TableAlias("address", "a", '.', '.');

    Map<String, String> joinColumns = new HashMap<>();
    joinColumns.put("address_id", "id");
    JoinCondition cond = new JoinCondition(person, address, "fk_person_address", joinColumns);
    assertEquals("address(id) - person(address_id)", cond.toString());
    cond.setIdentifierCase(GeneratedIdentifierCase.asIs);
    cond.setKeywordCase(GeneratedIdentifierCase.upper);
    cond.setPreferUsingOperator(false);
    cond.setUseParentheses(false);

    String sql = cond.getJoinCondition(true, QuoteHandler.STANDARD_HANDLER);
    assertEquals("ON p.address_id = a.id", sql);

    sql = cond.getJoinCondition(false, QuoteHandler.STANDARD_HANDLER);
    assertEquals("p.address_id = a.id", sql);
    cond.setUseParentheses(true);
    sql = cond.getJoinCondition(true, QuoteHandler.STANDARD_HANDLER);
    assertEquals("ON (p.address_id = a.id)", sql);
  }


}
