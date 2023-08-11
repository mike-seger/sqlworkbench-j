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
package workbench.sql.generator.merge;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;
import workbench.storage.RowDataContainer;
import workbench.storage.SqlLiteralFormatter;

import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class Postgres95MergeGenerator
  extends AbstractMergeGenerator
{
  public Postgres95MergeGenerator()
  {
    super(new SqlLiteralFormatter(SqlLiteralFormatter.ANSI_DATE_LITERAL_TYPE));
  }

  @Override
  public String generateMergeStart(RowDataContainer data)
  {
    TableIdentifier tbl = data.getUpdateTable();
    StringBuilder result = new StringBuilder(100);
    result.append("INSERT INTO " + tbl.getTableExpression() + "\n  (");
    ResultInfo info = data.getResultInfo();
    appendColumnNames(result, info);
    result.append(")\nVALUES\n");
    return result.toString();
  }

  @Override
  public String addRow(ResultInfo info, RowData row, long rowIndex)
  {
    StringBuilder result = new StringBuilder(100);
    if (rowIndex > 0) result.append(",\n");
    appendValues(result, info, row);
    return result.toString();
  }

  @Override
  public String generateMergeEnd(RowDataContainer data)
  {
    String sql = "\nON CONFLICT (";
    ResultInfo info = data.getResultInfo();
    int colNr = 0;
    for (ColumnIdentifier col : info.getColumnList())
    {
      if (!col.isPkColumn()) continue;
      if (colNr > 0) sql += ", ";
      sql += SqlUtil.quoteObjectname(col.getColumnName());
      colNr ++;
    }
    sql += ") DO UPDATE\n  SET ";
    colNr = 0;
    for (ColumnIdentifier col : info.getColumnList())
    {
      if (col.isPkColumn()) continue;
      if (!includeColumn(col)) continue;

      if (colNr > 0) sql += ",\n      ";
      String colname = SqlUtil.quoteObjectname(col.getColumnName());
      sql += colname + " = EXCLUDED." + colname;
      colNr ++;
    }
    return sql;
  }

  @Override
  public String generateMerge(RowDataContainer data)
  {
    StringBuilder sql = new StringBuilder(data.getRowCount() * 20);
    sql.append(generateMergeStart(data));
    for (int rowNr=0; rowNr < data.getRowCount(); rowNr++)
    {
      RowData row = data.getRow(rowNr);
      if (rowNr > 0) sql.append(",\n");
      sql.append("  (");
      appendValues(sql, data.getResultInfo(), row);
      sql.append(')');
    }
    sql.append(generateMergeEnd(data));
    return sql.toString();
  }

}
