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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class ObjectListLookup
{
  private final Map<Pair, Integer> lookup;
  private boolean caseSensitive = true;
  public ObjectListLookup(ObjectListDataStore ds)
  {
    this(ds, ds.getSchemaColumnName(), ds.getObjectColumnName());
  }

  public ObjectListLookup(ObjectListDataStore ds, String col1, String col2)
  {
    int count = ds.getRowCount();
    lookup = new HashMap<>(count);
    // Using the Pair class to store references to the names
    // avoids creating many new String objects just for the lookup
    for (int i=0; i < count; i++)
    {
      String s1 = ds.getValueAsString(i, col1);
      String s2 = ds.getValueAsString(i, col2);
      if (s1 == null || s2 == null) continue;
      Pair p = new Pair(s1, s2);
      lookup.put(p, i);
    }
  }

  public void setCaseSensitive(boolean flag)
  {
    this.caseSensitive = flag;
  }
  public int findObject(String s1, String s2)
  {
    Integer row = lookup.get(new Pair(s1, s2));
    if (row == null) return -1;
    return row;
  }

  private final class Pair
  {
    final String s1;
    final String s2;

    public Pair(String s1, String s2)
    {
      this.s1 = s1;
      this.s2 = s2;
    }

    @Override
    public int hashCode()
    {
      int hash = 7;
      hash = 67 * hash + Objects.hashCode(this.s1);
      hash = 67 * hash + Objects.hashCode(this.s2);
      return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final Pair other = (Pair)obj;
      if (StringUtil.compareStrings(this.s1, other.s1, caseSensitive) != 0) return false;
      return StringUtil.compareStrings(this.s2, other.s2, caseSensitive) == 0;
    }
  }
}
