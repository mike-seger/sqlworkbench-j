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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import workbench.db.GrantItem;

import workbench.util.StringUtil;

/**
 * A class to pass an ACL string from Postgres.
 *
 * @author Thomas Kellerer
 */
public class PgACLParser
{
  private static final Map<Character, String> PRIV_MAP = new HashMap<>();
  static
  {
    PRIV_MAP.put('r', "SELECT");
    PRIV_MAP.put('a', "INSERT");
    PRIV_MAP.put('w', "UPDATE");
    PRIV_MAP.put('d', "DELETE");
    PRIV_MAP.put('D', "TRUNCATE");
    PRIV_MAP.put('x', "REFERENCES");
    PRIV_MAP.put('t', "TRIGGER");
    PRIV_MAP.put('C', "CREATE");
    PRIV_MAP.put('c', "CONNECT");
    PRIV_MAP.put('t', "TEMPORARY");
    PRIV_MAP.put('X', "EXECUTE");
    PRIV_MAP.put('U', "USAGE");
  }

  private final String aclString;

  public PgACLParser(String acl)
  {
    this.aclString = acl;
  }

  /**
   * Returns grant items per grantee.
   *
   * The key of the map is the grantee.
   * @return
   */
  public Map<String, List<GrantItem>> getPrivileges()
  {
    List<String> items = StringUtil.stringToList(aclString, ",", false, false, false, false);
    Map<String, List<GrantItem>> grants = new HashMap<>();

    for (String item : items)
    {
      String grantee = getGrantee(item);
      String grantor = getGrantor(item);
      if (grantee.equals(grantor)) continue;
      int start = item.indexOf("=");
      int end = item.indexOf("/");
      String privs = item.substring(start+1, end);

      for (int i=0; i < privs.length(); i++)
      {
        boolean grantOption = false;
        char code = privs.charAt(i);
        String privilege = PRIV_MAP.get(code);
        if (i < privs.length() - 1 && privs.charAt(i+1) == '*')
        {
          grantOption = true;
          i++;
        }
        GrantItem gi = new GrantItem(grantee, privilege, grantOption);
        List<GrantItem> granteeItems = grants.get(grantee);
        if (granteeItems == null)
        {
          granteeItems = new ArrayList<>();
          grants.put(grantee, granteeItems);
        }
        granteeItems.add(gi);
      }
    }
    return grants;
  }

  public String getSQL(String objectName, String objectType)
  {
    Map<String, List<GrantItem>> grants = getPrivileges();
    StringBuilder result = new StringBuilder();
    for (Map.Entry<String, List<GrantItem>> entry : grants.entrySet())
    {
      // We need to generate on GRANT statement for those privileges
      // that where granted without the "GRANT OPTION"
      // and one for those granted "WITH GRANT OPTION" as that
      // is an option to the GRANT command, not for each privilege.
      // The alternative would be a single GRANT statement per
      // privilege which I find a bit too much
      List<GrantItem> plain = entry.getValue().stream().filter(g -> !g.isGrantable()).collect(Collectors.toList());
      if (plain.size() > 0)
      {
        CharSequence sql = getSingleGranteeSQL(plain, objectName, objectType, entry.getKey(), false);
        if (sql.length() > 0)
        {
          if (result.length() > 0)
          {
            result.append("\n");
          }
          result.append(sql);
        }
      }

      List<GrantItem> grantable = entry.getValue().stream().filter(g -> g.isGrantable()).collect(Collectors.toList());
      if (grantable.size() > 0)
      {
        CharSequence sql = getSingleGranteeSQL(grantable, objectName, objectType, entry.getKey(), true);
        if (sql.length() > 0)
        {
          if (result.length() > 0)
          {
            result.append("\n");
          }
          result.append(sql);
        }
      }
    }
    return result.toString();
  }

  private CharSequence getSingleGranteeSQL(List<GrantItem> grants,
                                           String objectName, String objectType,
                                           String grantee, boolean withGrantOption)
  {
    StringBuilder result = new StringBuilder();
      result.append("GRANT ");
      String privs = grants.stream().
        map(g -> g.getPrivilege()).
        collect(Collectors.joining(", "));
      result.append(privs);
      result.append(" ON ");
      result.append(objectType);
      result.append(" ");
      result.append(objectName);
      result.append(" TO ");
      result.append(grantee);
      if (withGrantOption)
      {
        result.append(" WITH GRANT OPTION");
      }
      result.append(";");
    return result;
  }

  private String getGrantor(String item)
  {
    if (StringUtil.isBlank(item)) return "";
    int pos = item.indexOf("/");
    if (pos > -1)
    {
      return item.substring(pos + 1);
    }
    return null;
  }

  private String getGrantee(String item)
  {
    if (StringUtil.isBlank(item)) return "";
    int pos = item.indexOf("=");
    if (pos > -1)
    {
      return item.substring(0, pos);
    }
    return null;
  }

}
