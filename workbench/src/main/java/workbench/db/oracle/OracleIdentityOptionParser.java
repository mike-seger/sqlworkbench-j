/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.db.oracle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import workbench.util.StringUtil;

/**
 * A parser to convert the value stored in  ALL_TAB_IDENTITY_COLS.IDENTITY_OPTIONS to a valid SQL expression.
 * 
 * @author Thomas Kellerer
 */
public class OracleIdentityOptionParser
{
  private final Map<String, String> defaultOptions = new HashMap<>();

  public OracleIdentityOptionParser()
  {
    defaultOptions.put("START WITH", "1");
    defaultOptions.put("INCREMENT BY", "1");
    defaultOptions.put("MAX_VALUE", "9999999999999999999999999999");
    defaultOptions.put("MIN_VALUE", "1");
    defaultOptions.put("CYCLE_FLAG", "N");
    defaultOptions.put("CACHE_SIZE", "20");
    defaultOptions.put("ORDER_FLAG", "N");
    defaultOptions.put("KEEP_FLAG", "N");
  }

  /**
   * Parse the "identity options" string as stored in ALL_TAB_IDENTITY_COLS.IDENTITY_OPTIONS.
   *
   * @param options  the options as retrieved from the system catalog
   * @return a valid SQL expression to be used in a CREATE TABLE statement or null if all options are default.
   */
  public String getIdentitySequenceOptions(String options)
  {
    if (StringUtil.isBlank(options))
    {
      return null;
    }

    Map<String, String> seqOptions = parseOptions(options);

    String result = "";

    result = appendOption(result, "START WITH", "START WITH", seqOptions);
    result = appendOption(result, "MINVALUE", "MIN_VALUE", seqOptions);
    result = appendOption(result, "MAXVALUE", "MAX_VALUE", seqOptions);
    result = appendOption(result, "INCREMENT BY", "INCREMENT BY", seqOptions);
    result = appendFlag(result, "CYCLE_FLAG", "NOCYCLE", "CYCLE", seqOptions);
    result = appendOption(result, "CACHE", "CACHE_SIZE", seqOptions);
    result = appendFlag(result, "ORDER_FLAG", "NOORDER", "ORDER", seqOptions);
    // For 18c and later
    result = appendFlag(result, "KEEP_FLAG", "NOKEEP", "KEEP", seqOptions);

    return StringUtil.trimToNull(result);
  }

  protected Map<String, String> parseOptions(String options)
  {
    // "START WITH: 1, INCREMENT BY: 1, MAX_VALUE: 9999999999999999999999999999, MIN_VALUE: 1, CYCLE_FLAG: N, CACHE_SIZE: 20, ORDER_FLAG: N";
    List<String> elements = StringUtil.stringToList(options, ",", true, true, false, false);
    Map<String, String> result = new HashMap<>();
    for (String element : elements)
    {
      String[] items = element.split(":");
      if (items.length != 2) continue;
      result.put(items[0].trim(), items[1].trim());
    }
    return result;
  }

  private String appendOption(String input, String sqlOption, String key, Map<String, String> options)
  {
    String value = options.get(key);
    if (value == null) return input;

    String defaultValue = defaultOptions.get(key);

    if (value.equals(defaultValue)) return input;

    if (!input.isEmpty()) input += " ";
    input += sqlOption;
    if (value != null)
    {
      input += " " + value;
    }
    return input;
  }

  private String appendFlag(String input, String key, String noValue, String yesValue, Map<String, String> options)
  {
    String value = options.get(key);
    if (value == null) return input;
    String defaultValue = defaultOptions.get(key);
    if (value.equals(defaultValue)) return input;

    if (!input.isEmpty()) input += " ";
    if ("N".equals(value))
    {
      input += noValue;
    }
    else
    {
      input += yesValue;
    }
    return input;
  }

}
