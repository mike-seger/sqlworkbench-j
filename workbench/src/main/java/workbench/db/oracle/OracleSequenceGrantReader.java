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
package workbench.db.oracle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.SequenceDefinition;
import workbench.db.SequenceGrantReader;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class OracleSequenceGrantReader
  implements SequenceGrantReader
{
  private final String baseSQL =
    "-- SQL Workbench/J \n" +
    "select grantor, grantee, table_schema, table_name, privilege, grantable \n" +
    "from all_tab_privs \n" +
    "where type = 'SEQUENCE' \n" +
    "  and table_schema = ? \n" +
    "  and table_name = ?";

  @Override
  public String getSequenceGrants(WbConnection conn, SequenceDefinition sequence)
  {
    StringBuilder result = new StringBuilder(100);
    ResultSet rs = null;
    String name = sequence.getObjectExpression(conn);

    LogMgr.logMetadataSql(new CallerInfo(){},  "sequence grant", baseSQL,
      sequence.getSchema(), sequence.getSequenceName());

    try (PreparedStatement pstmt = conn.getSqlConnection().prepareStatement(baseSQL);)
    {
      pstmt.setString(1, SqlUtil.removeObjectQuotes(sequence.getSchema()));
      pstmt.setString(2, SqlUtil.removeObjectQuotes(sequence.getSequenceName()));

      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String grantee = rs.getString("grantee");
        String priv = rs.getString("privilege");
        String grantable = rs.getString("grantable");
        String sql = "GRANT " + priv + " ON " + name + " TO " + grantee;
        if ("YES".equalsIgnoreCase(grantable))
        {
          sql += " WITH GRANT OPTION";
        }
        sql += ";";
        if (result.length() > 0)
        {
          result.append('\n');
        }
        result.append(sql);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "sequence grant", baseSQL,
        sequence.getSchema(), sequence.getSequenceName());
    }
    finally
    {
      JdbcUtils.close(rs);
    }
    return result.toString();
  }

}
