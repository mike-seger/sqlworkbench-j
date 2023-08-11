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

import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.IndexDefinition;
import workbench.db.TableIdentifier;

import workbench.sql.macros.MacroDefinition;

import workbench.util.CollectionUtil;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class QueryMacroParserTest
  extends WbTestCase
{

  public QueryMacroParserTest()
  {
    super("QueryMacroRunnerTest");
  }

  @Test
  public void testSimpleMacro()
  {
    String text = "select ${column}$, count(*) from ${table}$ group by ${column}$";
    MacroDefinition macro = new MacroDefinition("test", text);
    ColumnIdentifier c1 = new ColumnIdentifier("category");
    TableIdentifier tbl = new TableIdentifier("public", "product");
    QueryMacroParser parser = new QueryMacroParser(macro);
    assertTrue(parser.hasColumnPlaceholder());
    assertTrue(parser.hasTablePlaceholder());
    parser.setObject(tbl);
    parser.setColumn(CollectionUtil.arrayList(c1));

    String sql = parser.getSQL(null);
    String expected = "select category, count(*) from public.product group by category";
    Assert.assertEquals(expected, sql);
  }

  @Test
  public void testColumnReferences()
  {
    String text = "select ${column_1}$, count(distinct ${column_2}$) from ${table}$ group by ${column_1}$";
    MacroDefinition macro = new MacroDefinition("test", text);
    ColumnIdentifier c1 = new ColumnIdentifier("category");
    ColumnIdentifier c2 = new ColumnIdentifier("name");
    TableIdentifier tbl = new TableIdentifier("product");
    QueryMacroParser parser = new QueryMacroParser(macro);
    assertTrue(parser.hasColumnPlaceholder());

    parser.setObject(tbl);
    parser.setColumn(CollectionUtil.arrayList(c1,c2));

    String sql = parser.getSQL(null);
    String expected = "select category, count(distinct name) from product group by category";
    Assert.assertEquals(expected, sql);
  }

  @Test
  public void testColumnList()
  {
    String text = "select ${column_list}$ from ${table}$";
    MacroDefinition macro = new MacroDefinition("test", text);
    ColumnIdentifier c1 = new ColumnIdentifier("category");
    ColumnIdentifier c2 = new ColumnIdentifier("name");
    ColumnIdentifier c3 = new ColumnIdentifier("id");
    TableIdentifier tbl = new TableIdentifier("product");
    QueryMacroParser parser = new QueryMacroParser(macro);
    assertTrue(parser.hasColumnPlaceholder());
    parser.setObject(tbl);
    parser.setColumn(CollectionUtil.arrayList(c1,c2,c3));

    String sql = parser.getSQL(null);
    String expected = "select category, name, id from product";
    Assert.assertEquals(expected, sql);
  }

  @Test
  public void testIndex()
  {
    String text = "drop index ${index}$";
    MacroDefinition macro = new MacroDefinition("test", text);
    IndexDefinition idx = new IndexDefinition(new TableIdentifier("base_table"), "idx_foo");
    idx.setSchema("public");
    QueryMacroParser parser = new QueryMacroParser(macro);
    assertTrue(parser.hasIndexPlaceholder());
    parser.setObject(idx);

    String sql = parser.getSQL(null);
    String expected = "drop index public.idx_foo";
    Assert.assertEquals(expected, sql);
  }

}
