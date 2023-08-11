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
package workbench.db.oracle;

import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class OracleIdentityOptionParserTest
{

  public OracleIdentityOptionParserTest()
  {
  }

  @Test
  public void testParseOptions()
  {
    String options = "START WITH: 1, INCREMENT BY: 1, MAX_VALUE: 9999999999999999999999999999, MIN_VALUE: 1, CYCLE_FLAG: N, CACHE_SIZE: 20, ORDER_FLAG: N";
    OracleIdentityOptionParser parser = new OracleIdentityOptionParser();
    Map<String, String> parsed = parser.parseOptions(options);
    assertEquals("1", parsed.get("START WITH"));
    assertEquals("1", parsed.get("INCREMENT BY"));
    assertEquals("9999999999999999999999999999", parsed.get("MAX_VALUE"));
    assertEquals("20", parsed.get("CACHE_SIZE"));

    String sql = parser.getIdentitySequenceOptions(options);
    assertNull(sql);
    String options2 = "START WITH: 42, INCREMENT BY: 1, MAX_VALUE: 9999999999999999999999999999, MIN_VALUE: 1, CYCLE_FLAG: N, CACHE_SIZE: 1, ORDER_FLAG: N";
    String sql2 = parser.getIdentitySequenceOptions(options2);
    assertEquals("START WITH 42 CACHE 1", sql2);
  }

}
