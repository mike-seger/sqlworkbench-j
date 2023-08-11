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
package workbench.db;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ObjectListLookupTest
{

  public ObjectListLookupTest()
  {
  }

  @Test
  public void testSomeMethod()
  {
    ObjectListDataStore ds = new ObjectListDataStore();
    for (int i=0; i < 100000; i++)
    {
      int row = ds.addRow();
      ds.setObjectName(row, "TABLE_" + i);
      ds.setType(row, "TABLE");
      ds.setCatalog(row, "CATALOG");
      ds.setSchema(row, "PUBLIC");
    }

    long start = System.currentTimeMillis();
    ObjectListLookup lookup = new ObjectListLookup(ds);
    long duration = System.currentTimeMillis() - start;
    System.out.println("Creating lookup for "+ ds.getRowCount() + " objects took: " + duration + "ms");
    assertTrue(duration < 500);

    int row = lookup.findObject("PUBLIC", "TABLE_42");
    assertEquals(42, row);
    row = lookup.findObject("PUBLIC", "FOOBAR");
    assertEquals(-1, row);
  }

}
