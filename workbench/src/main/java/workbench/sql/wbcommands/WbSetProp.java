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
package workbench.sql.wbcommands;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import workbench.console.ConsoleSettings;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DbSettings;

import workbench.storage.DataStore;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class WbSetProp
  extends SqlCommand
{
  public static final String VERB = "WbSetProp";
  public static final String ALTERNATE_VERB = "WbSetConfig";
  public static final String SET_DB_CONFIG_VERB = "WbSetDbConfig";
  public static final String ARG_TYPE = "type";
  public static final String ARG_PROP = "property";
  public static final String ARG_VALUE = "value";
  private final Map<String, String> configMap = getAbbreviations();

  public WbSetProp()
  {
    super();
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(ARG_TYPE, CollectionUtil.arrayList("temp","default"));
    cmdLine.addArgument(ARG_PROP);
    cmdLine.addArgument(ARG_VALUE);
  }

  public static Map<String, String> getAbbreviations()
  {
    Map<String, String> map = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
    map.put("pager", ConsoleSettings.PROP_PAGER);
    map.put("nulldisplay", ConsoleSettings.PROP_NULL_STRING);
    map.put("nullstring", ConsoleSettings.PROP_NULL_STRING);
    map.put("varsuffix", Settings.PROPERTY_VAR_SUFFIX);
    map.put("varprefix", Settings.PROPERTY_VAR_PREFIX);
    map.put("debugmeta", "workbench.dbmetadata.debugmetasql");
    map.put("date_format", Settings.PROPERTY_DATE_FORMAT);
    map.put("ts_format", Settings.PROPERTY_DATETIME_FORMAT);
    map.put("time_format", Settings.PROPERTY_TIME_FORMAT);
    map.put("digits", Settings.PROPERTY_DECIMAL_DIGITS);
    map.put("dec_separator", Settings.PROPERTY_DECIMAL_SEP);
    map.put("dec_sep", Settings.PROPERTY_DECIMAL_SEP);
    map.put("showscriptfinish", "workbench.gui.sql.script.showtime");
    map.put("showendtime", "workbench.gui.sql.script.showtime");
    map.put("showfinishtime", "workbench.gui.sql.script.showtime");
    map.put("clearonrefresh", ConsoleSettings.PROP_CLEAR_SCREEN);
    map.put("logfileviewer", Settings.PROP_LOGFILE_VIEWER);
    map.put("displaysize", ConsoleSettings.PROP_MAX_DISPLAY_SIZE);
    return map;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = new StatementRunnerResult(sql);

    String verb = getParsingUtil().getSqlVerb(sql);
    boolean isConfig = verb.equalsIgnoreCase(ALTERNATE_VERB);
    boolean isDbConfig = verb.equalsIgnoreCase(SET_DB_CONFIG_VERB);

    if (isDbConfig)
    {
      isConfig = true;
    }

    String args = getCommandLine(sql);
    cmdLine.parse(args);

    if (cmdLine.hasArguments())
    {
      String type = cmdLine.getValue(ARG_TYPE, "temp");
      String prop = cmdLine.getValue(ARG_PROP);
      String value = null;
      int pos = prop != null ? prop.indexOf(':') : -1;
      if (pos < 0)
      {
        value = cmdLine.getValue(ARG_VALUE);
      }
      else if (prop != null)
      {
        value = prop.substring(pos + 1);
        prop = prop.substring(0, pos);
      }

      if (prop == null)
      {
        result.setFailure();
        result.addMessage("Property name required!");
      }
      else if (value == null && cmdLine.isArgNotPresent(ARG_VALUE))
      {
        String currentValue = Settings.getInstance().getProperty(prop, "");
        result.addMessage(prop + "=" + currentValue);
      }
      else
      {
        if ("default".equals(type) && prop.startsWith("workbench"))
        {
          Settings.getInstance().setProperty(prop, value);
          result.addMessage(prop  + " permanently set to "  + value);
        }
        else
        {
          Settings.getInstance().setTemporaryProperty(prop, value);
          result.addMessage(prop  + " set to "  + value);
        }
      }
    }
    else if (args.indexOf('=') > -1)
    {
      int pos = args.indexOf('=');
      String prop = args.substring(0, pos);
      String value;

      if (pos < args.length() - 1)
      {
        value = args.substring(pos + 1);
      }
      else
      {
        value = null;
      }

      if (value != null)
      {
        if (isDbConfig && !prop.startsWith("workbench") && currentConnection != null)
        {
          prop = "workbench.db." + currentConnection.getDbId() + "." + prop;
        }

        if (isConfig && prop.startsWith("workbench"))
        {
          Settings.getInstance().setProperty(prop, value);
          result.addMessage(prop  + " permanently set to \""  + value + "\"");
          LogMgr.logInfo(new CallerInfo(){}, "Changed configuration property: " + prop + "=" + value);
          Settings.getInstance().setCreatBackupOnSave(true);
        }
        else
        {
          Settings.getInstance().setTemporaryProperty(prop, value);
          result.addMessage(prop  + " set to \""  + value + "\"");
        }
      }
      else
      {
        // no value specified, remove property
        Settings.getInstance().removeProperty(prop);
        result.addMessage(prop  + " removed");
      }
    }
    else
    {
      DataStore ds = WbShowProps.getWbProperties(args);
      if (ds.getRowCount() > 0)
      {
        result.addDataStore(ds);
      }
    }
    result.setSuccess();
    return result;
  }

  private String getPropertyName(String shortName)
  {
    String propName = configMap.get(shortName);
    if (propName == null) propName = shortName;
    if (currentConnection != null)
    {
      return propName.replace(DbSettings.DBID_PLACEHOLDER, this.currentConnection.getDbId());
    }
    return propName;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }
  @Override
  public String getAlternateVerb()
  {
    return ALTERNATE_VERB;
  }

  @Override
  public Collection<String> getAllVerbs()
  {
    return CollectionUtil.arrayList(VERB, ALTERNATE_VERB, SET_DB_CONFIG_VERB);
  }

  @Override
  protected boolean isConnectionRequired()
  {
    return false;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
