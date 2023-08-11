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
package workbench.db.ibm;

import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class Db2iConnectionInfoReaderTest
{

  public Db2iConnectionInfoReaderTest()
  {
  }

  @Test
  public void testFormat()
  {
    Db2iConnectionInfoReader reader = new Db2iConnectionInfoReader(Collections.emptyMap());
    String formatted = reader.formatJobIdentifier("QZDASOINITQUSER     147421");
    assertEquals("147421/QUSER/QZDASOINIT", formatted);
  }

}
