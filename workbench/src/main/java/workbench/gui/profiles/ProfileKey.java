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
package workbench.gui.profiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.util.CsvLineParser;
import workbench.util.QuoteEscapeType;
import workbench.util.StringUtil;

/**
 * A class to uniquely identify a {@link workbench.db.ConnectionProfile}
 *
 * @author Thomas Kellerer
 */
public class ProfileKey
{
  // the name of the profile
  private String name;

  // the path of the profile group
  private final List<String> groupPath = new ArrayList<>();

  /**
   * Create a new ProfileKey.
   *
   * The passed name can consist of the profile group and the profile name
   * the group needs to be enclosed in curly brackets, e.g:
   * <tt>{MainGroup}/HR Database</tt><br/>
   * The divividing slash is optional.
   *
   * @param pname the name (can include the profile group path) of the profile
   */
  public ProfileKey(String pname)
  {
    if (StringUtil.isBlank(pname)) throw new IllegalArgumentException("Name cannot be empty!");
    parseNameAndGroup(pname);
  }

  public ProfileKey(String pname, String path)
  {
    if (StringUtil.isBlank(pname)) throw new IllegalArgumentException("Name cannot be empty!");

    parseNameAndGroup(pname);

    // only parse the path if the name did not contain one
    if (StringUtil.isNotBlank(path))
    {
      List<String> newPath = parseGroupPath(path);
      if (groupPath.isEmpty())
      {
        groupPath.addAll(newPath);
      }
      else
      {
        if (!newPath.equals(groupPath))
        {
          throw new IllegalArgumentException("The profile name contained a different group path than the path parameter");
        }
      }
    }
  }

  /**
   * Create a new key based on a profile name and a group name.
   *
   * @param pname the name of the profile
   * @param groupPath the group path to which the profile belongs
   */
  public ProfileKey(String pname, List<String> groupPath)
  {
    if (StringUtil.isBlank(pname)) throw new IllegalArgumentException("Name cannot be empty!");

    this.name = pname.trim();
    if (groupPath != null)
    {
      this.groupPath.addAll(groupPath);
    }
  }

  private void parseNameAndGroup(String pname)
  {
    if (pname == null) return;

    String tname = pname.trim();
    if (tname.length() > 0 && tname.charAt(0) == '{')
    {
      int pos = tname.indexOf('}');
      if (pos < 0) throw new IllegalArgumentException("Missing closing } to define group name");
      int slashPos = tname.indexOf('/', pos + 1);
      if (slashPos < 0) slashPos = pos;
      this.name = tname.substring(slashPos + 1).trim();
      String path = tname.substring(1,pos).trim();
      this.groupPath.addAll(parseGroupPath(path));
    }
    else if (tname.indexOf('/') > -1)
    {
      int slashPos = tname.lastIndexOf('/');
      this.name = tname.substring(slashPos + 1).trim();
      String path = tname.substring(0,slashPos).trim();
      this.groupPath.addAll(parseGroupPath(path));
    }
    else
    {
      name = tname;
    }
  }

  public static String getGroupPathAsString(List<String> path)
  {
    return StringUtil.listToString(path, '/');
  }

  public static String getGroupPathEscaped(List<String> path)
  {
    if (path == null || path.isEmpty()) return "";

    StringBuilder result = new StringBuilder(path.size() * 10);
    for (String item : path)
    {
      if (result.length() > 0) result.append("/");
      boolean needsQuote = item.contains("/");
      if (needsQuote) result.append('"');
      if (item.contains("\""))
      {
        item = item.replace("\"", "\\\"");
      }
      result.append(item);
      if (needsQuote) result.append('"');
    }
    return result.toString();
  }

  public static List<String> parseGroupPath(String path)
  {
    if (StringUtil.isBlank(path)) return Collections.emptyList();
    CsvLineParser parser = new CsvLineParser('/', '"');
    parser.setQuoteEscaping(QuoteEscapeType.escape);
    path = StringUtil.removeLeading(path, '/');
    path = StringUtil.removeTrailing(path, '/');
    parser.setLine(path);
    return parser.getAllElements();
  }

  public String getName()
  {
    return name;
  }

  public List<String> getGroups()
  {
    return Collections.unmodifiableList(groupPath);
  }

  public String getGroupPath()
  {
    return getGroupPathAsString(groupPath);
  }

  @Override
  public String toString()
  {
    if (groupPath.isEmpty()) return name;
    return "{" + getGroupPath() + "}/" + name;
  }

  @Override
  public int hashCode()
  {
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object other)
  {
    if (other == null) return false;
    if (other instanceof ProfileKey)
    {
      ProfileKey key = (ProfileKey)other;
      if (key.getName() == null) return false;
      if (this.name.equals(key.getName()))
      {
        if (key.groupPath.isEmpty() || this.groupPath.isEmpty()) return true;
        return this.groupPath.equals(key.groupPath);
      }
    }
    return false;
  }

}
