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
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresSequenceGrantReader
  implements SequenceGrantReader
{
  private final String baseSQL =
    "-- SQL Workbench/J \n" +
    "SELECT array_to_string(cl.relacl, ',') as acl \n" +
    "FROM pg_class cl \n" +
    " JOIN pg_namespace nsp on cl.regnamespace = nsp.oid \n" +
    "WHERE nsp.nspname = ? \n" +
    "  AND cl.relname = ? ";

  @Override
  public String getSequenceGrants(WbConnection conn, SequenceDefinition sequence)
  {
    String acl = null;
    if (sequence.isPropertySet(PostgresSequenceReader.PROP_ACL))
    {
      acl = (String)sequence.getSequenceProperty(PostgresSequenceReader.PROP_ACL);
    }
    else
    {
      acl = retrieveACL(conn, sequence);
      sequence.setSequenceProperty(PostgresSequenceReader.PROP_ACL, acl);
    }

    if (StringUtil.isNotBlank(acl))
    {
      PgACLParser parser = new PgACLParser(acl);
      return parser.getSQL(sequence.getObjectExpression(conn), "SEQUENCE");
    }
    return null;
  }

  private String retrieveACL(WbConnection conn, SequenceDefinition sequence)
  {
    ResultSet rs = null;
    String acl = null;

    LogMgr.logMetadataSql(new CallerInfo(){},  "sequence grant", baseSQL,
      sequence.getSchema(), sequence.getSequenceName());

    try (PreparedStatement pstmt = conn.getSqlConnection().prepareStatement(baseSQL);)
    {
      pstmt.setString(1, SqlUtil.removeObjectQuotes(sequence.getSchema()));
      pstmt.setString(2, SqlUtil.removeObjectQuotes(sequence.getSequenceName()));

      rs = pstmt.executeQuery();
      if (rs.next())
      {
        acl = rs.getString("grantee");
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
    return acl;
  }
}
