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
package workbench.gui.completion;

import java.util.ArrayList;
import java.util.List;

import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.objectcache.Namespace;

import workbench.util.SqlUtil;
import workbench.util.TableAlias;

import static workbench.gui.completion.BaseAnalyzer.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DeleteAnalyzer
  extends BaseAnalyzer
{
  public DeleteAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
  }

  @Override
  protected void checkContext()
  {
    this.context = -1;

    int wherePos = parsingUtil.getKeywordPosition("WHERE", sql);
    checkOverwrite();

    if ( wherePos == -1 || wherePos > -1 && cursorPos < wherePos)
    {
      context = CONTEXT_TABLE_LIST;
      this.namespaceForTableList = getNamespaceFromCurrentWord();
      if (isCurrentNameSpaceCatalog())
      {
        this.namespaceForTableList = new Namespace(null, namespaceForTableList.toString());
        context = CONTEXT_SCHEMA_LIST;
      }
    }
    else
    {
      // current cursor position is after the WHERE
      // so we'll need a column list
      context = CONTEXT_COLUMN_LIST;
      String table = SqlUtil.getDeleteTable(sql, catalogSeparator, dbConnection);
      if (table != null)
      {
        tableForColumnList = new TableIdentifier(table, catalogSeparator, SqlUtil.getSchemaSeparator(dbConnection));
        tableForColumnList.adjustCase(dbConnection);
        fkMarker = checkFkLookup();
      }
    }
  }

  @Override
  public List<TableAlias> getTables()
  {
    String table = SqlUtil.getDeleteTable(this.sql, this.catalogSeparator, dbConnection);
    TableAlias a = new TableAlias(table, SqlUtil.getCatalogSeparator(this.dbConnection), SqlUtil.getSchemaSeparator(dbConnection));
    List<TableAlias> result = new ArrayList<>(1);
    result.add(a);
    return result;
  }


}
