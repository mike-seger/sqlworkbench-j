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

import workbench.db.ibm.Db2iTriggerReader;
import workbench.db.mssql.SqlServerTriggerReader;
import workbench.db.oracle.OracleTriggerReader;
import workbench.db.postgres.PostgresTriggerReader;

/**
 * A factory to create instances of TriggerReader.
 *
 * @author Thomas Kellerer
 */
public class TriggerReaderFactory
{
  public static TriggerReader createReader(WbConnection con)
  {
    if (con == null) return null;
    if (con.getMetadata() == null) return null;

    switch (DBID.fromConnection(con))
    {
      case Postgres:
        return new PostgresTriggerReader(con);
      case Oracle:
        return new OracleTriggerReader(con);
      case SQL_Server:
        return new SqlServerTriggerReader(con);
      case DB2_ISERIES:
        return new Db2iTriggerReader(con);
    }
    return new DefaultTriggerReader(con);
  }
}
