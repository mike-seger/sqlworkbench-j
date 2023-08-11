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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

/**
 * A ConnectionPropertiesReader implementation that uses a method from the JDBC
 * driver to return a single property.
 *
 * The method and property name are defined through the map
 * returned by {@link #getDriverProperties()}.
 *
 * @author Thomas Kellerer
 */
public class DefaultConnectionPropertiesReader
  implements ConnectionPropertiesReader
{

  private final Map<String, String> propertyMap = new HashMap<>();

  public DefaultConnectionPropertiesReader(Map<String, String> properties)
  {
    if (properties != null)
    {
      this.propertyMap.putAll(properties);
    }
  }

  @Override
  public Map<String, String> getConnectionProperties(WbConnection conn)
  {
    Map<String, String> result = new HashMap<>(1);
    if (conn == null || propertyMap.isEmpty()) return result;

    Connection sqlConn = conn.getSqlConnection();
    if (sqlConn == null) return result;

    for (Map.Entry<String, String> propDef : propertyMap.entrySet())
    {
      String method = propDef.getKey();
      String name = propDef.getValue();
      try
      {
        LogMgr.logDebug(new CallerInfo(){}, "Calling " + method + "()");
        Method getter = sqlConn.getClass().getMethod(method);
        if (getter != null)
        {
          Object prop = getter.invoke(sqlConn);
          if (prop != null)
          {
            result.put(name, prop.toString());
          }
        }
      }
      catch (Throwable th)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not call " + method + "()", th);
      }
    }
    return result;
  }

}
