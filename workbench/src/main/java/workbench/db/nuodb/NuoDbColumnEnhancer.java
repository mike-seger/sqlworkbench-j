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
package workbench.db.nuodb;


import java.sql.PreparedStatement;
import java.sql.ResultSet;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnDefinitionEnhancer;
import workbench.db.ColumnIdentifier;
import workbench.db.GeneratedColumnType;
import workbench.db.JdbcUtils;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 * A class to read additional column level information for a table.
 *
 * @author Thomas Kellerer
 */
public class NuoDbColumnEnhancer
  implements ColumnDefinitionEnhancer
{

  @Override
  public void updateColumnDefinition(TableDefinition table, WbConnection conn)
  {
    readIdentityColumns(table, conn);
  }

  private void readIdentityColumns(TableDefinition table, WbConnection conn)
  {
    PreparedStatement stmt = null;
    ResultSet rs = null;

    TableIdentifier tblId = table.getTable();

    String sql =
      "select field \n" +
      "from system.fields \n " +
      "where tablename = ? \n" +
      "and schema = ? \n" +
      "and generator_sequence is not null ";

    LogMgr.logMetadataSql(new CallerInfo(){}, "identity columns", sql);

    try
    {
      stmt = conn.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, tblId.getRawTableName());
      stmt.setString(2, tblId.getRawSchema());

      rs = stmt.executeQuery();
      while (rs.next())
      {
        String colname = rs.getString(1);
        ColumnIdentifier col = table.findColumn(colname);
        if (col != null)
        {
          col.setGeneratedExpression("GENERATED ALWAYS AS IDENTITY", GeneratedColumnType.identity);
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "identity columns", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }
}
