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
package workbench.db.h2database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.JdbcUtils;
import workbench.db.SequenceAdjuster;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;


/**
 * A class to sync the sequences related to the columns of a table with the current values of those columns.
 *
 * This is intended to be used after doing bulk inserts into the database.
 *
 * @author Thomas Kellerer
 */
public class H2SequenceAdjuster
  implements SequenceAdjuster
{
  public H2SequenceAdjuster()
  {
  }

  @Override
  public int adjustTableSequences(WbConnection connection, TableIdentifier table, boolean includeCommit)
    throws SQLException
  {
    int numAdjusted = 0;
    if (JdbcUtils.hasMinimumServerVersion(connection, "2.0"))
    {
      numAdjusted = syncIdentityColumns(connection, table);
    }
    else
    {
      Map<String, String> columns = getColumnSequences(connection, table);

      for (Map.Entry<String, String> entry : columns.entrySet())
      {
        syncSingleSequence(connection, table, entry.getKey(), entry.getValue());
      }
      numAdjusted = columns.size();
    }

    if (includeCommit && !connection.getAutoCommit())
    {
      connection.commit();
    }
    return numAdjusted;
  }

  private int syncIdentityColumns(WbConnection dbConnection, TableIdentifier table)
    throws SQLException
  {
    List<ColumnIdentifier> columns = dbConnection.getMetadata().getTableColumns(table, false);
    ColumnIdentifier identityCol = null;
    for (ColumnIdentifier col : columns)
    {
      if (col.isIdentityColumn())
      {
        identityCol = col;
        break;
      }
    }

    if (identityCol == null) return 0;

    Statement stmt = null;
    ResultSet rs = null;
    String ddl = null;

    String colName = identityCol.getColumnName(dbConnection);
    String tableName = table.getTableExpression(dbConnection);
    try
    {
      stmt = dbConnection.createStatement();

      long maxValue = -1;
      rs = stmt.executeQuery("select max(" + colName + ") from " + tableName);

      if (rs.next())
      {
        maxValue = rs.getLong(1) + 1;
        JdbcUtils.closeResult(rs);
      }

      if (maxValue > 0)
      {
        ddl = "alter table " + tableName + " alter column " + colName + " restart with " + Long.toString(maxValue);
        LogMgr.logDebug(new CallerInfo(){}, "Syncing identityc column using: " + ddl);
        stmt.execute(ddl);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could adjust identity column using:\n" + ddl, ex);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return 1;
  }

  private void syncSingleSequence(WbConnection dbConnection, TableIdentifier table, String column, String sequence)
    throws SQLException
  {
    Statement stmt = null;
    ResultSet rs = null;
    String ddl = null;

    try
    {
      stmt = dbConnection.createStatement();

      long maxValue = -1;
      rs = stmt.executeQuery("select max(" + column + ") from " + table.getTableExpression(dbConnection));

      if (rs.next())
      {
        maxValue = rs.getLong(1) + 1;
        JdbcUtils.closeResult(rs);
      }

      if (maxValue > 0)
      {
        ddl = "alter sequence " + sequence + " restart with " + Long.toString(maxValue);
        LogMgr.logDebug(new CallerInfo(){}, "Syncing sequence using: " + ddl);
        stmt.execute(ddl);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could adjust sequence using:\n" + ddl, ex);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  private Map<String, String> getColumnSequences(WbConnection dbConnection, TableIdentifier table)
  {
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    String sql =
      "select column_name,  \n" +
      "       column_default \n" +
      "from information_schema.columns \n" +
      "where table_name = ? \n" +
      " and table_schema = ? \n" +
      " and column_default like '(NEXT VALUE FOR%'";

    Map<String, String> result = new HashMap<>();
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getRawTableName());
      pstmt.setString(2, table.getRawSchema());

      LogMgr.logMetadataSql(new CallerInfo(){}, "column sequences", sql, table.getRawTableName(), table.getRawSchema());
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String column = rs.getString(1);
        String defValue = rs.getString(2);
        defValue = defValue.replace("NEXT VALUE FOR", "");
        if (defValue.startsWith("(") && defValue.endsWith(")"))
        {
          defValue = defValue.substring(1, defValue.length() -1 );
        }
        result.put(column, defValue);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "column sequences", sql, table.getRawTableName(), table.getRawSchema());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return result;
  }

}
