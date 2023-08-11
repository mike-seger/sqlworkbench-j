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

import workbench.storage.ResultInfo;
import workbench.storage.RowDataContainer;
import workbench.storage.SqlLiteralFormatter;

/**
 *
 * @author Thomas Kellerer
 */
public class Db2MergeGenerator
  extends AnsiSQLMergeGenerator
{
  public Db2MergeGenerator()
  {
    super(new SqlLiteralFormatter(SqlLiteralFormatter.ANSI_DATE_LITERAL_TYPE));
  }

  @Override
  protected void generateStart(StringBuilder sql, RowDataContainer data, boolean withData)
  {
    TableIdentifier tbl = data.getUpdateTable();
    sql.append("MERGE INTO ");
    sql.append(tbl.getTableExpression(data.getOriginalConnection()));
    sql.append(" ut\nUSING TABLE (\n  VALUES\n    ");
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

}

