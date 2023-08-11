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
package workbench.db.postgres;

import java.util.Map;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ConnectionPropertiesReader;
import workbench.db.WbConnection;

import org.junit.Test;

import static org.junit.Assert.*;
/**
 *
 * @author Thomas Kellerer
 */
public class PostgresConnectionPropsReaderTest
  extends WbTestCase
{
  public PostgresConnectionPropsReaderTest()
  {
    super("TestPostgresConnectionProps");
  }

  @Test
  public void testReadProperties()
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    String pid = TestUtil.getSingleQueryValue(conn, "select pg_backend_pid()").toString();
    ConnectionPropertiesReader reader = ConnectionPropertiesReader.Fatory.getReader(conn);
    Map<String, String> props = reader.getConnectionProperties(conn);
    String pid2 = props.get("Backend PID");
    assertEquals(pid, pid2);
  }

}
