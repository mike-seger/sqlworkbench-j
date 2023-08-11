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
public class Firebird21MergeGenerator
  extends AnsiSQLMergeGenerator
{
  public Firebird21MergeGenerator()
  {
    super(new SqlLiteralFormatter(SqlLiteralFormatter.ANSI_DATE_LITERAL_TYPE));
  }

  @Override
  public String generateMergeStart(RowDataContainer data)
  {
    StringBuilder result = new StringBuilder(100);
    generateStart(result, data, false);
    return result.toString();
  }

  @Override
  public String addRow(ResultInfo info, RowData row, long rowIndex)
  {
    StringBuilder sql = new StringBuilder(100);
    if (rowIndex > 0) sql.append("\n  UNION ALL\n");
    appendValues(sql, info, row, rowIndex == 0);
    return sql.toString();
  }

  @Override
  public String generateMergeEnd(RowDataContainer data)
  {
    StringBuilder sql = new StringBuilder(data.getRowCount());
    appendJoin(sql, data);
    appendUpdate(sql, data);
    appendInsert(sql, data);
    return sql.toString();
  }

  @Override
  public String generateMerge(RowDataContainer data)
  {
    StringBuilder sql = new StringBuilder(data.getRowCount());

    generateStart(sql, data, true);
    appendJoin(sql, data);
    appendUpdate(sql, data);
    appendInsert(sql, data);
    return sql.toString();
  }

  @Override
  protected void generateStart(StringBuilder sql, RowDataContainer data, boolean withData)
  {
    TableIdentifier tbl = data.getUpdateTable();
    sql.append("MERGE INTO ");
    sql.append(tbl.getTableExpression(data.getOriginalConnection()));
    sql.append(" ut\nUSING (\n");
    if (withData)
    {
      ResultInfo info = data.getResultInfo();
      for (int row=0; row < data.getRowCount(); row++)
      {
        if (row > 0) sql.append("\n  UNION ALL\n");
        appendValues(sql, info, data.getRow(row), row == 0);
      }
    }
  }

  protected void appendValues(StringBuilder sql, ResultInfo info, RowData rd, boolean useAlias)
  {
    sql.append("  SELECT ");

    int colNr = 0;
    for (int col=0; col < info.getColumnCount(); col++)
    {
      if (!includeColumn(info.getColumn(col))) continue;

      if (colNr > 0) sql.append(", ");
      ColumnData cd = new ColumnData(rd.getValue(col), info.getColumn(col));
      sql.append(formatter.getDefaultLiteral(cd));
      if (useAlias)
      {
        sql.append(" AS ");
        sql.append(info.getColumnName(col));
      }
      colNr ++;
    }
    sql.append(" FROM rdb$database");
  }

}

