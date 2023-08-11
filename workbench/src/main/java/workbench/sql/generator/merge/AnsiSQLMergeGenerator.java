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

/**
 *
 * @author Thomas Kellerer
 */
public class AnsiSQLMergeGenerator
  extends AbstractMergeGenerator
{
  public AnsiSQLMergeGenerator()
  {
    this(new SqlLiteralFormatter(SqlLiteralFormatter.ANSI_DATE_LITERAL_TYPE));
  }

  public AnsiSQLMergeGenerator(SqlLiteralFormatter formatter)
  {
    super(formatter);
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
    if (rowIndex > 0) sql.append(",\n    ");
    sql.append('(');
    appendValues(sql, info, row);
    sql.append(')');
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

  protected void generateStart(StringBuilder sql, RowDataContainer data, boolean withData)
  {
    TableIdentifier tbl = data.getUpdateTable();
    sql.append("MERGE INTO ");
    sql.append(tbl.getTableExpression(data.getOriginalConnection()));
    sql.append(" ut\nUSING (\n  VALUES\n    ");
    if (withData)
    {
      ResultInfo info = data.getResultInfo();
      for (int row=0; row < data.getRowCount(); row++)
      {
        if (row > 0) sql.append(",\n    ");
        sql.append('(');
        appendValues(sql, info, data.getRow(row));
        sql.append(')');
      }
    }
  }

  protected void appendJoin(StringBuilder sql, RowDataContainer data)
  {
    ResultInfo info = data.getResultInfo();
    sql.append("\n) AS md (");
    appendColumnNames(sql, info);

    sql.append(") ON (");
    int pkCount = 0;
    for (int col=0; col < info.getColumnCount(); col ++)
    {
      ColumnIdentifier colid = info.getColumn(col);
      if (!colid.isPkColumn()) continue;
      if (!includeColumn(colid)) continue;

      if (pkCount > 0)  sql.append(" AND ");
      String colName = quoteObjectname(colid.getColumnName());
      sql.append("ut.");
      sql.append(colName);
      sql.append(" = md.");
      sql.append(colName);
      pkCount ++;
    }
    sql.append(")");
  }

  protected void appendUpdate(StringBuilder sql, RowDataContainer data)
  {
    sql.append("\nWHEN MATCHED THEN UPDATE");
    ResultInfo info = data.getResultInfo();

    int colCount = 0;
    for (int col=0; col < info.getColumnCount(); col ++)
    {
      ColumnIdentifier id = info.getColumn(col);
      if (id.isPkColumn()) continue;
      if (!includeColumn(info.getColumn(col))) continue;

      if (colCount == 0) sql.append("\n     SET ");
      if (colCount > 0) sql.append(",\n         ");
      String name = quoteObjectname(id.getColumnName());
      sql.append(name);
      sql.append(" = md.");
      sql.append(name);
      colCount ++;
    }
  }

  protected void appendInsert(StringBuilder sql, RowDataContainer data)
  {
    sql.append("\nWHEN NOT MATCHED THEN\n  INSERT (");
    ResultInfo info = data.getResultInfo();
    StringBuilder cols = new StringBuilder(info.getColumnCount() * 10);
    int colNr = 0;
    for (int col=0; col < info.getColumnCount(); col ++)
    {
      if (!includeColumn(info.getColumn(col))) continue;

      if (colNr > 0)
      {
        sql.append(", ");
        cols.append(", ");
      }
      String colName = quoteObjectname(info.getColumn(col).getColumnName());
      sql.append(colName);
      cols.append("md.");
      cols.append(colName);
      colNr ++;
    }
    sql.append(")\n");
    sql.append("  VALUES (");
    sql.append(cols);
    sql.append(");\n");
  }

}

