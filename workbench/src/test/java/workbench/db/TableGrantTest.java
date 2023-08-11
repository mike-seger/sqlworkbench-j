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
package workbench.db;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class TableGrantTest
{

  @Test
  public void testCompareTo()
  {
    GrantItem g1 = new GrantItem("testuser", "DELETE", false);
    GrantItem g2 = new GrantItem("testuser", "DELETE", false);
    assertEquals("incorrect compareTo for equals objects", 0, g1.compareTo(g2));

    g1 = new GrantItem("testuser", "DELETE", true);
    g2 = new GrantItem("testuser", "DELETE", false);
    assertEquals("incorrect compareTo for equals objects", 1, g1.compareTo(g2));

  }

  @Test
  public void testEquals()
  {
    GrantItem g1 = new GrantItem("testuser", "DELETE", false);
    GrantItem g2 = new GrantItem("testuser", "DELETE", false);

    assertEquals("incorrect equals for equals objects", true, g1.equals(g2));

    g1 = new GrantItem("testuser", "DELETE", true);
    g2 = new GrantItem("testuser", "DELETE", false);

    assertEquals("incorrect equals for equals objects", false, g1.equals(g2));

    g1 = new GrantItem("someuser", "DELETE", false);
    g2 = new GrantItem("testuser", "DELETE", false);

    assertEquals("incorrect equals for equals objects", false, g1.equals(g2));

    g1 = new GrantItem("testuser", "INSERT", false);
    g2 = new GrantItem("testuser", "DELETE", false);

    assertEquals("incorrect equals for equals objects", false, g1.equals(g2));

    Set<GrantItem> grants = new HashSet<GrantItem>();
    g1 = new GrantItem("testuser", "DELETE", true);
    g2 = new GrantItem("testuser", "DELETE", false);
    grants.add(g1);
    grants.add(g2);
    assertEquals("Not all grants added", 2, grants.size());

    // This should not be added as it is equal to g2
    grants.add(new GrantItem("testuser", "DELETE", false));
    assertEquals("Not all grants added", 2, grants.size());
  }


}
