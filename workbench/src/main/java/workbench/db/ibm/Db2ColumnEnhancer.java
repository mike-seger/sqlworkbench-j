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

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnDefinitionEnhancer;
import workbench.db.ColumnIdentifier;
import workbench.db.GeneratedColumnType;
import workbench.db.JdbcUtils;
import workbench.db.TableDefinition;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class Db2ColumnEnhancer
  implements ColumnDefinitionEnhancer
{

  @Override
  public void updateColumnDefinition(TableDefinition table, WbConnection conn)
  {
    updateComputedColumns(table, conn);
  }

  private void updateComputedColumns(TableDefinition table, WbConnection conn)
  {
    PreparedStatement stmt = null;
    ResultSet rs = null;

    String tablename = table.getTable().getTableName();
    String schema = table.getTable().getSchema();

    String columns =
      "SELECT c.colname, \n" +
      "       c.hidden, \n" +
      "       c.generated, \n" +
      "       c.text, \n" +
      "       a.start, \n" +
      "       a.increment, \n" +
      "       a.minvalue, \n" +
      "       a.maxvalue, \n" +
      "       a.cycle, \n" +
      "       a.cache, \n" +
      "       a.order";

    String from =
      "\nFROM syscat.columns c  \n" +
      "     LEFT JOIN syscat.colidentattributes a ON c.tabname = a.tabname AND c.tabschema = a.tabschema AND c.colname = a.colname \n" +
      "WHERE (c.generated <> ' ' or c.hidden <> ' ') \n" +
      "AND   c.tabname = ? \n" +
      "AND   c.tabschema = ? ";

    boolean checkHistory = false;

    if (JdbcUtils.hasMinimumServerVersion(conn, "10.1"))
    {
        columns += ", \n" +
          "       c.rowbegin, \n" +
          "       c.rowend, \n" +
          "       c.transactionstartid";
        checkHistory = true;
    }

    String sql = columns + from;

    LogMgr.logMetadataSql(new CallerInfo(){}, "column details", sql, tablename, schema);

    try
    {
      stmt = conn.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, tablename);
      stmt.setString(2, schema);
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String colname = rs.getString(1);
        String hidden = rs.getString(2);
        String gentype = rs.getString(3);
        String computedCol = rs.getString(4);
        BigDecimal start = rs.getBigDecimal(5);
        BigDecimal inc = rs.getBigDecimal(6);
        BigDecimal min = rs.getBigDecimal(7);
        BigDecimal max = rs.getBigDecimal(8);
        boolean cycle = StringUtil.stringToBool(rs.getString(9));
        Integer cache = rs.getInt(10);
        boolean order = StringUtil.stringToBool(rs.getString(11));

        String rowbegin = "N";
        String rowend = "N";
        String transid = "N";
        if (checkHistory)
        {
          rowbegin = rs.getString(12);
          rowend = rs.getString(13);
          transid = rs.getString(14);
        }
        ColumnIdentifier col = ColumnIdentifier.findColumnInList(table.getColumns(), colname);

        if (!gentype.equals(" "))
        {
          boolean isHistoryTCol= "Y".equals(rowbegin) || "Y".equals(rowend) || "Y".equals(transid);

          String expr = "GENERATED";

          if ("A".equals(gentype))
          {
            expr += " ALWAYS";
            col.setGeneratedColumnType(GeneratedColumnType.identity);
          }
          else
          {
            expr += " BY DEFAULT";
            col.setGeneratedColumnType(GeneratedColumnType.identity);
          }

          if (computedCol == null && !isHistoryTCol)
          {
            // IDENTITY column
            expr += " AS IDENTITY (" + Db2SequenceReader.buildSequenceDetails(false, start, min, max, inc, cycle, order, cache) + ")";
            col.setGeneratedExpression(expr, GeneratedColumnType.identity);
          }
          else if (isHistoryTCol)
          {
            if ("Y".equals(rowbegin))
            {
              expr += " AS ROW BEGIN";
            }
            else if ("Y".equals(rowend))
            {
              expr += " AS ROW END";
            }
            else if ("Y".equals(transid))
            {
              expr += " AS TRANSACTION START ID";
            }
            col.setGeneratedExpression(expr, GeneratedColumnType.generator);
          }
          else
          {
            expr += " " + computedCol;
            col.setGeneratedExpression(expr, GeneratedColumnType.computed);
          }
        }

        if ("I".equals(hidden))
        {
          col.setSQLOption("IMPLICITLY HIDDEN");
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "column details", sql, tablename, schema);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

  }

}
