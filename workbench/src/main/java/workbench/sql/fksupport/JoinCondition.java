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
package workbench.sql.fksupport;

import java.util.Map;

import workbench.resource.GeneratedIdentifierCase;

import workbench.db.QuoteHandler;

import workbench.util.StringUtil;
import workbench.util.TableAlias;

/**
 *
 * @author Thomas Kellerer
 */
public class JoinCondition
{
  private final TableAlias fromTable;
  private final TableAlias toTable;
  private final String fkName;
  private final Map<String, String> joinColumns;
  private boolean preferUsingOperator = false;
  private boolean useParentheses = false;
  private GeneratedIdentifierCase keywordCase = GeneratedIdentifierCase.upper;
  private GeneratedIdentifierCase identifierCase = GeneratedIdentifierCase.asIs;

  public JoinCondition(TableAlias fromTable, TableAlias toTable, String fkName, Map<String, String> joinColumns)
  {
    this.fromTable = fromTable;
    this.toTable = toTable;
    this.fkName = fkName;
    this.joinColumns = joinColumns;
  }

  public void setUseParentheses(boolean useParentheses)
  {
    this.useParentheses = useParentheses;
  }

  /**
   * Set if the USING (column_name) operator should be preferred over an explicit JOIN
   * condition using col1 = col2 AND col3 = col4
   */
  public void setPreferUsingOperator(boolean useUsingOperator)
  {
    this.preferUsingOperator = useUsingOperator;
  }

  public TableAlias getFromTable()
  {
    return fromTable;
  }

  public TableAlias getToTable()
  {
    return toTable;
  }

  public String getFkName()
  {
    return fkName;
  }

  public void setKeywordCase(GeneratedIdentifierCase keywordCase)
  {
    this.keywordCase = keywordCase;
  }

  public void setIdentifierCase(GeneratedIdentifierCase identifierCase)
  {
    this.identifierCase = identifierCase;
  }

  public String getJoinCondition()
  {
    return getJoinCondition(true, QuoteHandler.STANDARD_HANDLER);
  }

  public String getJoinCondition(boolean includeOperator, QuoteHandler handler)
  {
    if (preferUsingOperator && canUseUSING())
    {
      return generateUSINGCondition(includeOperator, handler);
    }
    else
    {
      return generateJoinCondition(includeOperator, handler);
    }
  }

  private String generateUSINGCondition(boolean includeOperator, QuoteHandler handler)
  {
    StringBuilder result = new StringBuilder(joinColumns.size() * 20 + 10);
    boolean first = true;
    if (includeOperator)
    {
      result.append("USING ");
    }
    result.append("(");

    for (Map.Entry<String, String> entry : joinColumns.entrySet())
    {
      if (!first)
      {
        result.append(", ");
      }
      result.append(getColumnName(null, entry.getKey(), handler));
      first = false;
    }
    result.append(')');
    return result.toString();
  }

  private String generateJoinCondition(boolean includeOperator, QuoteHandler handler)
  {
    StringBuilder result = new StringBuilder(joinColumns.size() * 20 + 10);
    String kw = keywordCase == GeneratedIdentifierCase.upper ? " AND " : " and ";
    boolean first = true;
    if (includeOperator)
    {
      result.append("ON ");
    }
    if (useParentheses)
    {
      result.append("(");
    }
    for (Map.Entry<String, String> entry : joinColumns.entrySet())
    {
      if (!first)
      {
        result.append(kw);
      }
      result.append(getColumnName(fromTable, entry.getKey(), handler));
      result.append(" = ");
      result.append(getColumnName(toTable, entry.getValue(), handler));
      first = false;
    }
    if (useParentheses)
    {
      result.append(")");
    }
    return result.toString();
  }

  private String getColumnName(TableAlias table, String columnName, QuoteHandler quoteHandler)
  {
    if (StringUtil.isBlank(columnName)) return columnName;

    String result= quoteHandler.quoteObjectname(columnName);

    if (quoteHandler.isQuoted(result)) return result;

    switch (identifierCase)
    {
      case lower:
        result = columnName.toLowerCase();
        break;
      case upper:
        result = columnName.toUpperCase();
        break;
      default:
        result = columnName;
    }
    if (table != null)
    {
      result = table.getNameToUse() + "." + result;
    }
    return result;

  }
  private boolean canUseUSING()
  {
    for (Map.Entry<String, String> entry : joinColumns.entrySet())
    {
      if (!entry.getKey().equalsIgnoreCase(entry.getValue()))
      {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString()
  {
    StringBuilder result = new StringBuilder(80);
    StringBuilder targetColumns = new StringBuilder(40);
    result.append(toTable.getObjectName());
    result.append("(");
    targetColumns.append(fromTable.getObjectName());
    targetColumns.append("(");
    boolean first = true;
    for (Map.Entry<String, String> entry : joinColumns.entrySet())
    {
      if (!first)
      {
        result.append(",");
        targetColumns.append(",");
      }
      result.append(entry.getValue());
      targetColumns.append(entry.getKey());
    }

    result.append(") - ");
    targetColumns.append(")");
    result.append(targetColumns);
    return result.toString();
  }

}
