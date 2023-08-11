/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.sql.generator.merge;

import java.util.ArrayList;
import java.util.List;

import workbench.db.ColumnIdentifier;
import workbench.db.QuoteHandler;

import workbench.storage.ColumnData;
import workbench.storage.ResultInfo;
import workbench.storage.RowData;
import workbench.storage.SqlLiteralFormatter;

/**
 *
 * @author Thomas Kellerer
 */
public abstract class AbstractMergeGenerator
  implements MergeGenerator
{
  protected final List<ColumnIdentifier> columns = new ArrayList<>();
  protected final SqlLiteralFormatter formatter;
  protected QuoteHandler quoteHandler = QuoteHandler.STANDARD_HANDLER;

  protected AbstractMergeGenerator(SqlLiteralFormatter formatter)
  {
    this.formatter = formatter == null ? new SqlLiteralFormatter(SqlLiteralFormatter.ANSI_DATE_LITERAL_TYPE) : formatter;
  }

  @Override
  public void setQuoteHandler(QuoteHandler handler)
  {
    this.quoteHandler = handler == null ? QuoteHandler.STANDARD_HANDLER : handler;
  }

  @Override
  public void setColumns(List<ColumnIdentifier> columns)
  {
    this.columns.clear();
    if (columns != null)
    {
      this.columns.addAll(columns);
    }
  }

  protected String quoteObjectname(String column)
  {
    return quoteHandler.quoteObjectname(column);
  }

  protected boolean includeColumn(ColumnIdentifier column)
  {
    if (this.columns.isEmpty()) return true;
    return this.columns.contains(column);
  }

  protected void appendColumnNames(StringBuilder sql, ResultInfo info)
  {
    int colNr = 0;
    for (int col=0; col < info.getColumnCount(); col ++)
    {
      if (!includeColumn(info.getColumn(col))) continue;

      if (colNr > 0) sql.append(", ");
      sql.append(quoteObjectname(info.getColumnName(col)));
      colNr ++;
    }
  }

  protected void appendValues(StringBuilder sql, ResultInfo info, RowData rd)
  {
    int colNr = 0;
    for (int col=0; col < info.getColumnCount(); col++)
    {
      if (!includeColumn(info.getColumn(col))) continue;

      if (colNr > 0) sql.append(", ");
      ColumnData cd = new ColumnData(rd.getValue(col), info.getColumn(col));
      sql.append(formatter.getDefaultLiteral(cd));
      colNr ++;
    }
  }

}
