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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresUtilTest
{

  /**
   * Test of switchDatabaseURL method, of class PostgresUtil.
   */
  @Test
  public void testSwitchDatabaseURL()
  {
    assertEquals("jdbc:postgresql://localhost/postgres", PostgresUtil.switchDatabaseURL("jdbc:postgresql://localhost/test", "postgres"));

    assertEquals("jdbc:postgresql://localhost/postgres?autosave=true",
                  PostgresUtil.switchDatabaseURL("jdbc:postgresql://localhost/test?autosave=true", "postgres"));

    assertEquals("jdbc:postgresql://localhost:5544/postgres?autosave=true&prepareThreshold=42",
                  PostgresUtil.switchDatabaseURL("jdbc:postgresql://localhost:5544/test?autosave=true&prepareThreshold=42", "postgres"));
  }
}
