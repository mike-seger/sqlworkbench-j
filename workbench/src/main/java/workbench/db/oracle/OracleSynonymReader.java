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
package workbench.db.oracle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.JdbcUtils;
import workbench.db.SynonymReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 *
 * @author  Thomas Kellerer
 */
public class OracleSynonymReader
  implements SynonymReader
{

  @Override
  public List<TableIdentifier> getSynonymList(WbConnection con, String catalog, String owner, String namePattern)
    throws SQLException
  {
    // Nothing to do. The Oracle driver already returns the SYNONYMs in the getTables() call
    return Collections.emptyList();
  }

  @Override
  public TableIdentifier getSynonymTable(WbConnection con, String catalog, String owner, String synonym)
    throws SQLException
  {
    boolean readComments = con.getDbSettings().getBoolProperty("synonym.target.retrieve.comments", false);
    if (readComments)
    {
      readComments = OracleUtils.getRemarksReporting(con);
    }

    String sql =
      "-- SQL Workbench/J \n" +
      "SELECT s.synonym_name, s.table_owner, s.table_name, s.db_link, o.object_type, s.owner";

    if (readComments)
    {
      // the scalar sub-select seems to be way faster than an outer join
      // we don't need to lookup comments for Oracle objects
      // For some reasons, the sub-select sometimes fails with a "single-row subquery returns more than one row"
      // I could not identify the real cause, so adding a rownum <= 1 will prevent that in any case
      sql += ", case " +
             "    when o.owner IN ('PUBLIC','SYS','SYSTEM') then null " +
             "    else (select tc.comments from all_tab_comments tc where tc.table_name = o.object_name AND tc.owner = o.owner AND rownum <= 1) " +
             "  end as comments ";
    }

    // the outer join to all_objects is necessary to also see synonyms that point to no longer existing tables
    sql +=
      "\nFROM all_synonyms s \n" +
      "  LEFT JOIN all_objects o ON s.table_name = o.object_name AND s.table_owner = o.owner  \n";

    sql +=
      "WHERE ((s.synonym_name = ? AND s.owner = ?)  \n" +
      "    OR (s.synonym_name = ? AND s.owner = 'PUBLIC'))  \n" +
      "ORDER BY decode(s.owner, 'PUBLIC',9,1)";

    if (owner == null)
    {
      owner = con.getCurrentUser();
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "synonym table", sql, synonym, owner, synonym);

    PreparedStatement stmt = null;
    ResultSet rs = null;

    TableIdentifier result = null;
    try
    {
      stmt = OracleUtils.prepareQuery(con, sql);
      stmt.setString(1, synonym);
      stmt.setString(2, owner);
      stmt.setString(3, synonym);

      rs = stmt.executeQuery();
      if (rs.next())
      {
        String towner = rs.getString(2);
        String table = rs.getString(3);
        String dblink = rs.getString(4);
        String type = rs.getString(5);
        if (dblink != null) table = table + "@" + dblink;
        result = new TableIdentifier(null, towner, table);
        result.setNeverAdjustCase(true);
        result.setType(type);
        if (readComments)
        {
          String comment = rs.getString(6);
          result.setComment(comment);
        }
      }
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return result;
  }

  @Override
  public String getSynonymSource(WbConnection con, String catalog, String owner, String synonym)
    throws SQLException
  {
    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.synonym))
    {
      try
      {
        return DbmsMetadata.getDDL(con, "SYNONYM", synonym, owner);
      }
      catch (SQLException sql)
      {
        // logging already done
      }
    }

    TableIdentifier id = getSynonymTable(con, catalog, owner, synonym);
    StringBuilder result = new StringBuilder(200);
    String nl = Settings.getInstance().getInternalEditorLineEnding();
    if (supportsReplace(con))
    {
      result.append("CREATE OR REPLACE SYNONYM ");
    }
    else
    {
      result.append("CREATE SYNONYM ");
    }
    TableIdentifier syn = new TableIdentifier(owner, synonym);
    result.append(syn.getTableExpression(con));
    result.append(nl + "   FOR ");
    result.append(id.getTableExpression(con));
    result.append(';');
    result.append(nl);
    return result.toString();
  }

  @Override
  public boolean supportsReplace(WbConnection con)
  {
    return JdbcUtils.hasMinimumServerVersion(con, "10.0");
  }


}
