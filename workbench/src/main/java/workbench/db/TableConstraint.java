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
package workbench.db;

import workbench.util.StringUtil;

/**
 * Represents a single table (check) constraint
 *
 * @author Thomas Kellerer
 */
public class TableConstraint
  extends ConstraintDefinition
  implements Comparable<TableConstraint>
{
  public static final String NAME_PLACEHOLDER = "%constraintname%";
  public static final String EXPR_PLACEHOLDER = "%expression%";

  private String expression;
  private String checkConstraintTemplate;

  public TableConstraint(String constraintName, String expr)
  {
    this(constraintName, expr, null);
  }

  public TableConstraint(String constraintName, String expr, String sqlTemplate)
  {
    super(constraintName);
    expression = StringUtil.isNotBlank(expr) ? expr.trim() : null;
    checkConstraintTemplate = StringUtil.trimToNull(sqlTemplate);
    setConstraintType(ConstraintType.Check);

    // this is for Postgres
    if (expression != null && expression.toLowerCase().startsWith("exclude"))
    {
      setConstraintType(ConstraintType.Exclusion);
    }
  }

  public String getExpression()
  {
    return expression;
  }

  @Override
  public int compareTo(TableConstraint other)
  {
    if (other == null) return -1;
    if (isSystemName() && other.isSystemName())
    {
      return StringUtil.compareStrings(this.expression, other.expression, false);
    }
    int c = StringUtil.compareStrings(this.getConstraintName(), other.getConstraintName(), true);
    if (c == 0)
    {
      c = StringUtil.compareStrings(this.expression, other.expression, false);
    }
    return c;
  }

  @Override
  public int hashCode()
  {
    int hash = 5;
    hash = 19 * hash + (this.expression != null ? this.expression.hashCode() : 0);
    return hash;
  }

  public boolean expressionIsEqual(TableConstraint other)
  {
    if (other == null) return false;
    return StringUtil.equalString(expression, other.expression);
  }

  @Override
  public boolean equals(Object other)
  {
    if (other instanceof TableConstraint)
    {
      return compareTo((TableConstraint)other) == 0;
    }
    return false;
  }

  @Override
  public String toString()
  {
    return getSql();
  }

  public String getSql()
  {
    if (this.checkConstraintTemplate != null)
    {
      return replaceTemplate();
    }

    StringBuilder result = new StringBuilder(50);

    if (StringUtil.isNotBlank(getConstraintName()) && !isSystemName())
    {
      result.append("CONSTRAINT ");
      result.append(getConstraintName());
      result.append(' ');
    }

    // Check if the returned expression already includes the CHECK keyword
    // PostgreSQL 9.0 supports "exclusion constraint which start with EXCLUDE
    // in that case the keyword CHECK may not be added either.
    if (!expression.toLowerCase().startsWith("check") && !expression.toLowerCase().startsWith("exclude"))
    {
      result.append("CHECK ");
    }
    result.append(expression);
    return result.toString();
  }

  private String replaceTemplate()
  {
    String sql = this.checkConstraintTemplate;

    String name = getConstraintName();
    if (StringUtil.isBlank(name))
    {
      sql = sql.replaceFirst("(?i)CONSTRAINT\\s+" + NAME_PLACEHOLDER, "");
    }
    else
    {
      sql = sql.replace(NAME_PLACEHOLDER, name);
    }
    sql = sql.replace(EXPR_PLACEHOLDER, expression);
    return sql;
  }
}
