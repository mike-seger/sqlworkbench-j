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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 *
 * @author Thomas Kellerer
 */
public class ProfileGroupMap
  extends TreeMap<List<String>, List<ConnectionProfile>>
{
  public ProfileGroupMap(List<ConnectionProfile> profiles)
  {
    super(createComparator());
    for (ConnectionProfile profile : profiles)
    {
      List<String> groups = profile.getGroups();
      List<ConnectionProfile> l = computeIfAbsent(groups, s -> new ArrayList<ConnectionProfile>());
      l.add(profile);
    }
  }

  private static Comparator<List<String>> createComparator()
  {
    return (List<String> o1, List<String> o2) ->
    {
      for (int i = 0; i < Math.min(o1.size(), o2.size()); i++)
      {
        int c = o1.get(i).compareToIgnoreCase(o2.get(i));
        if (c != 0)
        {
          return c;
        }
      }
      return Integer.compare(o1.size(), o2.size());
    };
  }

}
