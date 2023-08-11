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

import java.sql.SQLException;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ReadOnlyChanger;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresReadOnlyChanger
  implements ReadOnlyChanger
{
  @Override
  public void setReadOnly(WbConnection conn, boolean flag)
    throws SQLException
  {
    if (conn == null) return;

    if (conn.getAutoCommit())
    {
      // When autocommit is enabled, the read only state can only
      // be changed throug a SQL statement
      String sql;
      if (flag)
      {
        sql = conn.getDbSettings().getSetReadOnlySQL();
      }
      else
      {
        sql = conn.getDbSettings().getSetReadWriteSQL();
      }
      if (StringUtil.isNotBlank(sql))
      {
        LogMgr.logInfo(new CallerInfo(){}, "Setting connection to " + (flag ? "read only" : "read/write") + " using: " + sql);
      }
    }
    else
    {
      // when autocommit is turned off
      // this can only be changed through the setReadOnly() call
      // and it can't be done if a transaction is active
      conn.rollbackSilently();
      conn.getSqlConnection().setReadOnly(flag);
    }
  }

}
