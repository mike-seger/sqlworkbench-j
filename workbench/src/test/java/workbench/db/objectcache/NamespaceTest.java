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
package workbench.db.objectcache;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class NamespaceTest
{

  @Test
  public void testKey()
  {
    Namespace key1 = new Namespace("public", null);
    Namespace key2 = new Namespace("PUBLIC", null);
    assertEquals(key1, key2);
    Namespace key3 = new Namespace("\"PUBLIC\"", null);
    assertFalse(key1.equals(key3));

    Namespace dbo1 = new Namespace("dbo", "some_db");
    Namespace dbo2 = new Namespace("dbo", "other_db");
    assertFalse(dbo1.equals(dbo2));

    Namespace dbo3 = new Namespace("dbo", "one_db");
    Namespace dbo4 = new Namespace("dbo", "one_db");
    assertTrue(dbo3.equals(dbo4));

    Namespace key4 = new Namespace("foo.bar.");
    assertEquals("foo", key4.getCatalog());
    assertEquals("bar", key4.getSchema());
  }


}
