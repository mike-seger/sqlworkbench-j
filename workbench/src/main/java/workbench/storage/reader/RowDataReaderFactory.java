/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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
package workbench.storage.reader;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DBID;
import workbench.db.DbSettings;
import workbench.db.WbConnection;

import workbench.storage.ResultInfo;

/**
 *
 * @author Thomas Kellerer
 */
public class RowDataReaderFactory
{
  public static RowDataReader createReader(ResultInfo info, WbConnection conn)
  {
    DBID id = DBID.fromConnection(conn);
    switch (id)
    {
      case Oracle:
        try
        {
          return new OracleRowDataReader(info, conn);
        }
        catch (Exception cnf)
        {
          LogMgr.logWarning(new CallerInfo(){}, "Could not create OracleRowDataReader. Probably the Oracle specific classes are not available", cnf);
        }
      case Postgres:
        return new PostgresRowDataReader(info, conn);
      case SQL_Server:
        return new SqlServerRowDataReader(info, conn);
      case SQLite:
        DbSettings dbs = conn.getDbSettings();
        if (dbs != null && dbs.useSQLiteDataReader())
        {
          LogMgr.logDebug(new CallerInfo()
          {
          }, "Using SQLiteDataReader that returns columns with errors as a string");
          return new SQLiteRowDataReader(info, conn);
        }
    }
    return new RowDataReader(info, conn);
  }
}
