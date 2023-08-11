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
package workbench.db.ibm;


import java.util.Map;

import workbench.db.DefaultConnectionPropertiesReader;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;

/**
 * A ConnectionPropertiesReader implementation for DB2 for i.
 *
 * It will return the "job identifier" as returned by the JDBC driver.
 *
 * @author Thomas Kellerer
 */
public class Db2iConnectionInfoReader
  extends DefaultConnectionPropertiesReader
{
  private static final String PROP_NAME = "JobIdentifier";

  public Db2iConnectionInfoReader(Map<String, String> props)
  {
    super(CollectionUtil.combine(Map.of("getServerJobIdentifier", PROP_NAME), props));
  }

  @Override
  public Map<String, String> getConnectionProperties(WbConnection conn)
  {
    Map<String, String> props = super.getConnectionProperties(conn);
    if (conn != null && conn.getDbSettings().getBoolProperty("format.jobidentifier", true))
    {
      String id = props.get(PROP_NAME);
      props.put(PROP_NAME, formatJobIdentifier(id));
    }
    return props;
  }

  public String formatJobIdentifier(String id)
  {
    if (id == null) return id;
    if (id.contains("/")) return id;
    if (id.length() != 26) return id;

    String jobNumber = id.substring(16);
    String jobName = id.substring(0,10);
    String userName = id.substring(10,19);
    
    return  jobNumber.trim() + "/" + userName.trim() + "/" + jobName.trim();
  }

}
