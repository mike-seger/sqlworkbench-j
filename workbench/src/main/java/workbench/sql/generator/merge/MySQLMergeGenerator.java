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

import workbench.storage.ColumnData;


import workbench.storage.ResultInfo;
import workbench.storage.RowData;
import workbench.storage.RowDataContainer;
import workbench.storage.SqlLiteralFormatter;

/**
 *
 * @author Thomas Kellerer
 */
public class MySQLMergeGenerator
  extends AbstractMergeGenerator
{
  public MySQLMergeGenerator()
  {
    super(new SqlLiteralFormatter("mysql"));
  }

  @Override
  public String generateMerge(RowDataContainer data)
  {
    StringBuilder result = new StringBuilder(100);
    generateInsert(result, data, true);
    generateUpdate(result, data);
    return result.toString();
  }

  @Override
  public String generateMergeStart(RowDataContainer data)
  {
    StringBuilder result = new StringBuilder(100);
    generateInsert(result, data, false);
    return result.toString();
  }

  @Override
  public String addRow(ResultInfo info, RowData row, long rowIndex)
  {
    StringBuilder result = new StringBuilder(100);
    appendValues(result, row, info, rowIndex);
    return result.toString();
  }

  @Override
  public String generateMergeEnd(RowDataContainer data)
  {
    StringBuilder result = new StringBuilder(100);
    generateUpdate(result, data);
    return result.toString();
  }

  private void generateUpdate(StringBuilder sql, RowDataContainer data)
  {
    ResultInfo info = data.getResultInfo();

    sql.append("\nON DUPLICATE KEY UPDATE\n");
    int colCount = 0;
    for (int col=0; col < info.getColumnCount(); col ++)
    {
      ColumnIdentifier id = info.getColumn(col);
      if (!includeColumn(id)) continue;
      if (id.isPkColumn()) continue;

      if (colCount > 0) sql.append(",\n");
      sql.append("  ");
      sql.append(info.getColumnName(col));
      sql.append(" = values(");
      sql.append(info.getColumnName(col));
      sql.append(')');
      colCount ++;
    }
    sql.append(';');
  }
  
  private void generateInsert(StringBuilder sql, RowDataContainer data, boolean withData)
  {
    TableIdentifier tbl = data.getUpdateTable();
    sql.append("INSERT INTO ");
    sql.append(tbl.getTableExpression(data.getOriginalConnection()));
    sql.append("\n  (");
    ResultInfo info = data.getResultInfo();

    int colNr = 0;
    for (int col=0; col < info.getColumnCount(); col ++)
    {
      if (!includeColumn(info.getColumn(col))) continue;

      if (colNr > 0) sql.append(", ");
      sql.append(info.getColumnName(col));
      colNr ++;
    }
    sql.append(")\nVALUES\n");
    if (withData)
    {
      for (int row=0; row < data.getRowCount(); row++)
      {
        appendValues(sql, data.getRow(row), info, row);
      }
    }
  }

  private void appendValues(StringBuilder sql, RowData rd, ResultInfo info, long rowNumber)
  {
    if (rowNumber > 0) sql.append(",\n");
    sql.append("  (");
    int colNr = 0;
    for (int col=0; col < info.getColumnCount(); col++)
    {
      if (!includeColumn(info.getColumn(col))) continue;
      if (col > 0) sql.append(',');
      ColumnData cd = new ColumnData(rd.getValue(col), info.getColumn(col));
      sql.append(formatter.getDefaultLiteral(cd));
      colNr ++;
    }
    sql.append(')');
  }
}
