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
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ConstraintDefinition;
import workbench.db.IndexDefinition;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.UniqueConstraintReader;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class InformixUniqueConstraintReader
  implements UniqueConstraintReader
{

  @Override
  public void readUniqueConstraints(TableIdentifier table, List<IndexDefinition> indexList, WbConnection con)
  {
    if (CollectionUtil.isEmpty(indexList))  return;
    if (con == null) return;

    long count = indexList.stream().filter(idx -> idx.isUnique()).count();
    if (count == 0) return;

    String catalog = table.getCatalog();
    InformixSystemTables systemTables = new InformixSystemTables(catalog, con);

    String sysTabs = systemTables.getSysTables();
    String sysCons = systemTables.getSysConstraints();

    String sql =
      "select c.idxname, t.owner, c.constrname \n" +
      "from " + sysCons + " c\n" +
      " join " + sysTabs + " t on t.tabid = c.tabid\n" +
      "where c.constrtype = 'U' \n" +
      "  and t.tabname = ?";

    LogMgr.logMetadataSql(new CallerInfo(){}, "unique constraints", sql);

    PreparedStatement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = con.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, table.getRawTableName());
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String idxName = rs.getString(1).trim();
        String idxSchema = rs.getString(2).trim();
        String consName = rs.getString(3).trim();
        IndexDefinition idx = IndexDefinition.findIndex(indexList, idxName, idxSchema);
        if (idx != null)
        {
          ConstraintDefinition cons = ConstraintDefinition.createUniqueConstraint(consName);
          idx.setUniqueConstraint(cons);
        }
      }
    }
    catch (SQLException se)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, se, "unique constraints", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

}
