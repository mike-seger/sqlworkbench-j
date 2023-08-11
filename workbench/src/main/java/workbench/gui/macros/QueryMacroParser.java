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
package workbench.gui.macros;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import workbench.db.ColumnIdentifier;
import workbench.db.DbObject;
import workbench.db.IndexDefinition;
import workbench.db.QuoteHandler;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.sql.macros.MacroDefinition;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class QueryMacroParser
{
  public static final String COLUMN_PLACEHOLDER = "${column}$";
  public static final String COLUMN_LIST_PLACEHOLDR = "${column_list}$";
  public static final String TABLE_PLACEHOLDER = "${table}$";
  public static final String TABLE_NAME_PLACEHOLDER = "${table_name}$";
  public static final String SCHEMA_NAME_PLACEHOLDER = "${schema_name}$";
  public static final String DB_NAME_PLACEHOLDER = "${db_name}$";
  public static final String CATALOG_NAME_PLACEHOLDER = "${catalog_name}$";
  public static final String INDEX_NAME_PLACEHOLDER = "${index_name}$";
  public static final String INDEX_PLACEHOLDER = "${index}$";

  private final Pattern NUMBERED_COLUMN_PATTERN = Pattern.compile("\\$\\{column_[0-9]+\\}\\$");
  private final MacroDefinition macro;
  private final List<ColumnIdentifier> columns = new ArrayList<>();
  private DbObject object;

  public QueryMacroParser(MacroDefinition macro)
  {
    this.macro = macro;
  }

  public void setColumn(List<ColumnIdentifier> newColumns)
  {
    this.columns.clear();
    if (newColumns != null)
    {
      this.columns.addAll(newColumns);
    }
  }

  public void setObject(DbObject tbl)
  {
    this.object = tbl;
  }

  public String getSQL(WbConnection conn)
  {
    String sql = macro.getText();

    QuoteHandler handler = SqlUtil.getQuoteHandler(conn);
    sql = sql.replace(COLUMN_LIST_PLACEHOLDR, getColumnList(conn));
    if (columns.size() == 1)
    {
      sql = sql.replace(COLUMN_PLACEHOLDER, columns.get(0).getColumnName(conn));
    }

    if (object == null) return sql;

    if (object instanceof TableIdentifier)
    {
      TableIdentifier table = (TableIdentifier)object;
      if (sql.contains(TABLE_PLACEHOLDER))
      {
        sql = sql.replace(TABLE_PLACEHOLDER, table.getFullyQualifiedName(conn));
      }
      sql = sql.replace(TABLE_NAME_PLACEHOLDER, quoteName(table.getRawTableName(), handler));
      sql = sql.replace(SCHEMA_NAME_PLACEHOLDER, quoteName(table.getRawSchema(), handler));
      sql = sql.replace(DB_NAME_PLACEHOLDER, quoteName(table.getRawCatalog(), handler));
      sql = sql.replace(CATALOG_NAME_PLACEHOLDER, quoteName(table.getRawCatalog(), handler));
    }
    else if (object instanceof IndexDefinition)
    {
      if (sql.contains(INDEX_PLACEHOLDER))
      {
        sql = sql.replace(INDEX_PLACEHOLDER, object.getFullyQualifiedName(conn));
      }
      sql = sql.replace(INDEX_NAME_PLACEHOLDER, object.getObjectName(conn));
      sql = sql.replace(SCHEMA_NAME_PLACEHOLDER, quoteName(object.getSchema(), handler));
      sql = sql.replace(DB_NAME_PLACEHOLDER, quoteName(object.getCatalog(), handler));
      sql = sql.replace(CATALOG_NAME_PLACEHOLDER, quoteName(object.getCatalog(), handler));
    }

    sql = replaceColumns(sql, conn);
    return sql;
  }

  public boolean hasTablePlaceholder()
  {
    String text = this.macro.getText();
    if (StringUtil.isBlank(text)) return false;
    return text.contains(TABLE_NAME_PLACEHOLDER) || text.contains(TABLE_PLACEHOLDER);
  }

  public boolean hasIndexPlaceholder()
  {
    String text = this.macro.getText();
    if (StringUtil.isBlank(text)) return false;
    return text.contains(INDEX_NAME_PLACEHOLDER) || text.contains(INDEX_PLACEHOLDER);
  }

  public boolean hasColumnPlaceholder()
  {
    String text = this.macro.getText();
    if (StringUtil.isBlank(text)) return false;

    return text.contains(COLUMN_PLACEHOLDER) ||
           text.contains(COLUMN_LIST_PLACEHOLDR) ||
           NUMBERED_COLUMN_PATTERN.matcher(text).find();
  }

  private String quoteName(String name, QuoteHandler handler)
  {
    if (StringUtil.isBlank(name)) return "";
    return handler.quoteObjectname(name);
  }

  private String replaceColumns(String sql, WbConnection conn)
  {
    for (int i=0; i < columns.size(); i++)
    {
      String placeholder = "${column_" + (i + 1) + "}$";
      sql = sql.replace(placeholder, columns.get(i).getColumnName(conn));
    }
    return sql;
  }

  private String getColumnList(WbConnection conn)
  {
    if (columns.isEmpty()) return "";

    return columns.stream().
      map(c -> c.getColumnName(conn)).
      collect(Collectors.joining(", "));
  }
}
