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
package workbench.db.mysql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.TableDefinition;
import workbench.db.WbConnection;

import workbench.db.JdbcUtils;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class MySQLColumnCollationReader
{

  public void readCollations(TableDefinition table, WbConnection conn)
  {
    String defaultCharacterSet = null;
    String defaultCollation = null;
    Statement info = null;
    ResultSet rs = null;
    String defaultCollationSql = "show variables where variable_name in ('collation_database', 'character_set_database')";
    try
    {
      LogMgr.logMetadataSql(new CallerInfo(){}, "default collation", defaultCollationSql);
      info = conn.createStatement();
      rs = info.executeQuery(defaultCollationSql);
      while (rs.next())
      {
        String name = rs.getString(1);
        String value = rs.getString(2);
        if ("character_set_database".equals(name))
        {
          defaultCharacterSet = value;
        }
        if ("collation_database".equals(name))
        {
          defaultCollation = value;
        }
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "default collation", defaultCollationSql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, info);
    }

    // In MySQL 5.7 show variables is no longer available to regular users
    // in that case both defaults will be null --> don't check anything
    if (defaultCharacterSet == null && defaultCollation == null) return;

    PreparedStatement stmt = null;

    HashMap<String, String> collations = new HashMap<>(table.getColumnCount());
    HashMap<String, String> expressions = new HashMap<>(table.getColumnCount());
    String sql =
      "SELECT column_name, \n" +
      "       character_set_name, \n" +
      "       collation_name \n" +
      "FROM information_schema.columns \n" +
      "WHERE table_name = ? \n" +
      "AND   table_schema = ? ";

    LogMgr.logMetadataSql(new CallerInfo(){}, "column collation", sql);

    try
    {
      stmt = conn.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, table.getTable().getTableName());
      stmt.setString(2, table.getTable().getCatalog());
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String colname = rs.getString(1);
        String charset = rs.getString(2);
        String collation = rs.getString(3);
        String expression = null;
        if (isNonDefault(collation, defaultCollation) && isNonDefault(charset, defaultCharacterSet))
        {
          expression = "CHARSET " + charset + " COLLATE " + collation;
        }
        else if (isNonDefault(collation, defaultCollation))
        {
          expression = "COLLATE " + collation;
        }
        else if (isNonDefault(charset, defaultCharacterSet))
        {
          expression = "CHARSET " + charset;
        }

        if (expression != null)
        {
          expressions.put(colname, expression);
        }
        if (collation != null)
        {
          collations.put(colname, collation);
        }
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "column collation", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    for (ColumnIdentifier col : table.getColumns())
    {
      String expression = expressions.get(col.getColumnName());
      if (expression != null)
      {
        String dataType = col.getDbmsType() + " " + expression;
        col.setDbmsType(dataType);
      }
      String collation = collations.get(col.getColumnName());
      if (collation != null)
      {
        col.setCollation(collation);
      }
    }
  }

  private boolean isNonDefault(String value, String defaultValue)
  {
    if (defaultValue == null) return false;
    if (StringUtil.isEmpty(value)) return false;
    return !value.equals(defaultValue);
  }

}
