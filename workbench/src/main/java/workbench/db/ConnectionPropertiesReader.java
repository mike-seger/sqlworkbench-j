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

import java.util.Map;

import workbench.db.ibm.Db2iConnectionInfoReader;

/**
 * An interface that defines an API to read custom connection
 * properties from a JDBC connection.
 * <p>
 * The result is used to build the connection information
 * display in the UI.
 *
 * @author Thomas Kellerer
 */
public interface ConnectionPropertiesReader
{
  /**
   * Returns a map of DBMS specific connection properties.
   *
   * @param conn the connection
   *
   * @return a map with connection specific properties, never null
   */
  Map<String, String> getConnectionProperties(WbConnection conn);

  public static class Fatory
  {
    public static ConnectionPropertiesReader getReader(WbConnection conn)
    {
      if (conn == null) return null;
      DBID db = DBID.fromConnection(conn);
      Map<String, String> props = conn.getDbSettings().getDynamicInfoPropertiesMapping();

      if (db == DBID.DB2_ISERIES)
      {
        return new Db2iConnectionInfoReader(props);
      }

      return new DefaultConnectionPropertiesReader(props);
    }
  }
}
