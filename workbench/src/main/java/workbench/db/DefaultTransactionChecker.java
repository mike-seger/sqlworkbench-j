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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class DefaultTransactionChecker
  implements TransactionChecker
{
  private final String query;

  public DefaultTransactionChecker(String sql)
  {
    query = StringUtil.trimToNull(sql);
  }

  @Override
  public boolean hasUncommittedChanges(WbConnection con)
  {
    if (con == null) return false;
    if (con.isClosed()) return false;
    if (con.getAutoCommit()) return false;
    if (query == null) return false;

    Savepoint sp = null;
    ResultSet rs = null;
    Statement stmt = null;
    int count = 0;

    final CallerInfo ci = new CallerInfo(){};
    try
    {
      long start = System.currentTimeMillis();
      if (con.getDbSettings().useSavePointForTransactionCheck())
      {
        sp = con.setSavepoint(ci);
      }
      stmt = con.createStatementForQuery();
      rs = stmt.executeQuery(query);
      if (rs.next())
      {
        count = rs.getInt(1);
      }
      con.releaseSavepoint(sp, ci);
      long duration = System.currentTimeMillis() - start;
      LogMgr.logDebug(ci, "Checking for pending transactions took " + duration + "ms");
    }
    catch (SQLException sql)
    {
      LogMgr.logWarning(ci, "Could not retrieve transaction state", sql);
      con.rollback(sp);
    }
    catch (Throwable th)
    {
      LogMgr.logDebug(ci, "Error when retrieving transaction state", th);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return count > 0;
  }

}
