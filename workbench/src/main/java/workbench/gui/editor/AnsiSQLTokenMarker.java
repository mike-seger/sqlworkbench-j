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
package workbench.gui.editor;

import java.util.Collection;

import workbench.sql.syntax.SqlKeywordHelper;
import workbench.sql.wbcommands.CommandTester;


/**
 * @author Thomas Kellerer
 */
public class AnsiSQLTokenMarker
  extends SQLTokenMarker
{
  public AnsiSQLTokenMarker()
  {
    super();
    initKeywordMap();
  }

  private void addKeywordList(Collection<String> words, byte anId)
  {
    if (words == null) return;

    for (String keyword : words)
    {
      if (!keywords.containsKey(keyword))
      {
        keywords.add(keyword.toUpperCase().trim(),anId);
      }
    }
  }

  public void setIsMicrosoft(boolean flag)
  {
    isMicrosoft = flag;
  }

  public void setIsMySQL(boolean flag)
  {
    isMySql = flag;
  }

  public void initKeywordMap()
  {
    keywords = new KeywordMap(true, 150);
    addKeywords();
    addWbCommands();
    addDataTypes();
    addSystemFunctions();
    addOperators();
  }

  public void addOperator(String operator)
  {
    this.keywords.add(operator, Token.OPERATOR);
  }

  public void initKeywordMap(Collection<String> keyWords, Collection<String> dataTypes, Collection<String> operators, Collection<String> functions)
  {
    keywords = new KeywordMap(true, 150);
    addKeywordList(keyWords, Token.KEYWORD1);
    addWbCommands();
    addKeywordList(dataTypes, Token.DATATYPE);
    addKeywordList(functions, Token.KEYWORD3);
    addKeywordList(operators, Token.OPERATOR);
  }

  private void addWbCommands()
  {
    CommandTester tester = new CommandTester();
    for (String verb : tester.getCommands())
    {
      if (!"@".equals(verb))
      {
        keywords.add(verb, Token.KEYWORD2);
      }
    }
  }

  private void addKeywords()
  {
    SqlKeywordHelper helper = new SqlKeywordHelper();
    addKeywordList(helper.getKeywords(), Token.KEYWORD1);
  }

  private void addDataTypes()
  {
    SqlKeywordHelper helper = new SqlKeywordHelper();
    addKeywordList(helper.getDataTypes(), Token.DATATYPE);
  }

  private void addSystemFunctions()
  {
    SqlKeywordHelper helper = new SqlKeywordHelper();
    addKeywordList(helper.getSqlFunctions(), Token.KEYWORD3);
  }

  private void addOperators()
  {
    SqlKeywordHelper helper = new SqlKeywordHelper();
    addKeywordList(helper.getOperators(), Token.OPERATOR);
  }
}
