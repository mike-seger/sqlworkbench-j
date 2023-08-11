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
package workbench.db.firstsql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.AbstractConstraintReader;
import workbench.db.JdbcUtils;
import workbench.db.TableConstraint;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;

/**
 * Constraint reader for <a href="https://www.firstsql.com/">FirstSQL</a>
 *
 * @author Thomas Kellerer
 */
public class FirstSqlConstraintReader
  extends AbstractConstraintReader
{
  private final String SQL =
    "select ch.check_clause, ch.constraint_name \n" +
    "from definition_schema.syschecks ch,  \n" +
    "     definition_schema.sysconstraints cons \n" +
    "where cons.constraint_type = 'check' \n" +
    "  and cons.constraint_name = ch.constraint_name" +
    "  and cons.table_schema = ? \n" +
    "  and cons.table_name = ? ";

  public FirstSqlConstraintReader()
  {
    super("firstsqlj");
  }

  @Override
  public List<TableConstraint> getTableConstraints(WbConnection dbConnection, TableDefinition def)
  {
    PreparedStatement pstmt = null;
    ResultSet rs = null;

    LogMgr.logMetadataSql(new CallerInfo(){}, "table constraints", SQL);

    List<TableConstraint> result = CollectionUtil.arrayList();
    TableIdentifier table = def.getTable();
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(SQL);
      pstmt.setString(1, table.getSchema());
      pstmt.setString(2, table.getTableName());
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String constraint = rs.getString(1);
        String name = rs.getString(2);
        result.add(new TableConstraint(name, "(" + constraint + ")"));
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "table constraints", SQL);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return result;
  }

  @Override
  public String getColumnConstraintSql(TableIdentifier tbl)
  {
    return null;
  }

  @Override
  public String getTableConstraintSql(TableIdentifier tbl)
  {
    return SQL;
  }
}
