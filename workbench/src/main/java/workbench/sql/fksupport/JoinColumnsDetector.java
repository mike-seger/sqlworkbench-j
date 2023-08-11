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
package workbench.sql.fksupport;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import workbench.resource.GeneratedIdentifierCase;
import workbench.resource.Settings;

import workbench.db.DependencyNode;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.TableAlias;

/**
 *
 * @author Thomas Kellerer
 */
public class JoinColumnsDetector
{
  private final TableAlias joinTable;
  private final TableAlias joinedTable;
  private final WbConnection connection;
  private boolean preferUsingOperator;
  private boolean alwaysUseParentheses = false;
  private GeneratedIdentifierCase keywordCase;
  private GeneratedIdentifierCase identifierCase;

  public JoinColumnsDetector(WbConnection dbConnection, TableAlias mainTable, TableAlias childTable)
  {
    this.joinTable = mainTable;
    this.joinedTable = childTable;
    this.connection = dbConnection;
    this.preferUsingOperator = Settings.getInstance().getJoinCompletionPreferUSING();
    this.identifierCase = Settings.getInstance().getFormatterIdentifierCase();
    this.keywordCase =  Settings.getInstance().getFormatterKeywordsCase();
  }

  public void setAlwaysUseParentheses(boolean alwaysUseParentheses)
  {
    this.alwaysUseParentheses = alwaysUseParentheses;
  }

  public void setPreferUsingOperator(boolean flag)
  {
    this.preferUsingOperator = flag;
  }

  public void setKeywordCase(GeneratedIdentifierCase kwCase)
  {
    this.keywordCase = kwCase;
  }

  public void setIdentifierCase(GeneratedIdentifierCase idCase)
  {
    this.identifierCase = idCase;
  }

  /**
   * Return a map for columns to be joined.
   * <br/>
   * The key will be the PK column, the value the FK column. If either the joinTable or the joined table
   * (see constructor) is not found, an empty map is returned;
   *
   * @return the mapping for the PK/FK columns
   * @throws SQLException
   */
  public List<JoinCondition> getJoinConditions()
    throws SQLException
  {
    TableIdentifier realJoinTable = connection.getObjectCache().getOrRetrieveTable(joinTable.getTable());
    TableIdentifier realJoinedTable = connection.getObjectCache().getOrRetrieveTable(joinedTable.getTable());

    if (realJoinTable == null || realJoinedTable == null)
    {
      return Collections.emptyList();
    }

    List<JoinCondition> conditions = getJoinConditions(realJoinTable, joinTable, realJoinedTable, joinedTable);
    if (conditions.isEmpty())
    {
      conditions = getJoinConditions(realJoinedTable, joinedTable, realJoinTable, joinTable);
    }
    return conditions;
  }

  private List<JoinCondition> getJoinConditions(TableIdentifier table1, TableAlias alias1, TableIdentifier table2, TableAlias alias2)
    throws SQLException
  {
    List<JoinCondition> result = new ArrayList<>();

    List<DependencyNode> refTables = connection.getObjectCache().getReferencedTables(table2);

    for (DependencyNode node : refTables)
    {
      if (node.getTable().equals(table1))
      {
        Map<String, String> colMap = node.getColumns();

        JoinCondition condition = new JoinCondition(alias1, alias2, node.getFkName(), colMap);
        condition.setIdentifierCase(identifierCase);
        condition.setKeywordCase(keywordCase);
        condition.setPreferUsingOperator(preferUsingOperator);
        condition.setUseParentheses(alwaysUseParentheses);
        result.add(condition);
      }
    }
    return result;
  }
}
