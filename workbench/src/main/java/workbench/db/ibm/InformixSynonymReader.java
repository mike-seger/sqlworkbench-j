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
package workbench.db.ibm;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.SynonymReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 * A class to retrieve synonym definitions from an Informix database.
 *
 * @author Thomas Kellerer
 */
public class InformixSynonymReader
  implements SynonymReader
{

  /**
   * Returns an empty list, as the standard JDBC driver
   * alread returns synonyms in the getObjects() method.
   *
   * @return an empty list
   */
  @Override
  public List<TableIdentifier> getSynonymList(WbConnection con, String catalog, String owner, String namePattern)
    throws SQLException
  {
    return Collections.emptyList();
  }

  @Override
  public TableIdentifier getSynonymTable(WbConnection con, String catalog, String schema, String synonymName)
    throws SQLException
  {
    InformixSystemTables systemTables = new InformixSystemTables(catalog, con);

    String systables = systemTables.getSysTables();
    String syntable = systemTables.getSysSynonyms();

    String sql =
      "select bt.owner as table_schema, \n" +
      "       bt.tabname as table_name \n" +
      "from " + systables + " syn \n" +
      "  join " + syntable + " lnk on lnk.tabid = syn.tabid \n" +
      "  join " + systables + " bt on bt.tabid = lnk.btabid \n" +
      "and syn.tabname = ? \n" +
      "and syn.owner = ?";

    LogMgr.logMetadataSql(new CallerInfo(){}, "synonym table",  sql, synonymName, schema);

    TableIdentifier result = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = con.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, synonymName);
      stmt.setString(2, schema);

      rs = stmt.executeQuery();
      String table = null;
      String owner = null;
      if (rs.next())
      {
        owner = rs.getString(1);
        table = rs.getString(2);
        if (table != null)
        {
          result = new TableIdentifier(null, owner, table);
          result.setNeverAdjustCase(true);
        }
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "synonym table",  sql, synonymName, schema);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return result;
  }

}
