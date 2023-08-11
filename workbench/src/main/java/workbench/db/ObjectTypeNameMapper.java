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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import workbench.util.StringUtil;

/**
 * A class to map object type names returned by a driver to a "SQL" type.
 * <p>
 * This is mainly needed for H2 2.x as it returns "BASE TABLE" as the
 * type of a TABLE.
 *
 * The mapper is initialized by the list defined through {@link DbSettings#getObjectTypeNameMap()}.
 *
 * @author Thomas Kellerer
 */
public class ObjectTypeNameMapper
{
  private final Map<String, String> nameMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public ObjectTypeNameMapper(DbSettings dbs)
  {
    this(dbs.getObjectTypeNameMap());

    // Merge the map with the old (single) property that contains a list of
    // alternate names for "TABLE"
    Set<String> tableNames = dbs.getTableTypeSynonyms();
    for (String type : tableNames)
    {
      nameMap.put(type, "TABLE");
    }
  }

  public ObjectTypeNameMapper(List<String> typeMap)
  {
    if (typeMap != null)
    {
      for (String entry : typeMap)
      {
        if (StringUtil.isBlank(entry)) continue;
        String[] names = entry.trim().split(":");
        if (StringUtil.isNoneBlank(names) && names.length == 2)
        {
          nameMap.put(names[0], names[1].toUpperCase());
        }
      }
    }
  }

  public String mapInternalNameToSQL(String internalName)
  {
    return nameMap.getOrDefault(internalName, internalName);
  }

}
