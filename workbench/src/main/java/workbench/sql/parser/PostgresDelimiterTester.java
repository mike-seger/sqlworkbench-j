/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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
package workbench.sql.parser;

import workbench.sql.DelimiterDefinition;
import workbench.sql.lexer.SQLToken;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresDelimiterTester
  implements DelimiterTester
{
  private SQLToken firstToken;
  private SQLToken lastToken;

  private DelimiterDefinition defaultDelimiter = DelimiterDefinition.STANDARD_DELIMITER;
  private final DelimiterDefinition copyDelimiter = new DelimiterDefinition("\\.");
  private final DelimiterDefinition dummyDelimiter = new DelimiterDefinition("_$wb$_end_body#");
  private boolean isCopy = false;
  private boolean isCopyFromStdin = false;
  private boolean isSQLBody = false;

  public PostgresDelimiterTester()
  {
  }

  @Override
  public void setDelimiter(DelimiterDefinition delim)
  {
    this.defaultDelimiter = delim;
  }

  @Override
  public boolean supportsMixedDelimiters()
  {
    return true;
  }

  @Override
  public void setAlternateDelimiter(DelimiterDefinition delimiter)
  {
  }

  @Override
  public void currentToken(SQLToken token, boolean isStartOfStatement)
  {
    if (token == null) return;
    if (token.isComment() || token.isWhiteSpace()) return;

    if (firstToken == null)
    {
      firstToken = token;
      isCopy = token.getText().equalsIgnoreCase("copy");
    }

    if (isCopy && token.getText().equalsIgnoreCase("stdin") && lastToken != null && lastToken.getText().equalsIgnoreCase("from"))
    {
      isCopyFromStdin = true;
    }
    else if (token.getContents().equals("BEGIN ATOMIC"))
    {
      isSQLBody = true;
    }
    else if (isSQLBody && token.getContents().equals("END") && isDelimiter(lastToken))
    {
      isSQLBody = false;
    }
    lastToken = token;
  }

  private boolean isDelimiter(SQLToken token)
  {
    if (token == null) return false;
    DelimiterDefinition def = defaultDelimiter == null ? DelimiterDefinition.STANDARD_DELIMITER : defaultDelimiter;
    return token.getText().equalsIgnoreCase(def.getDelimiter());
  }

  @Override
  public DelimiterDefinition getCurrentDelimiter()
  {
    if (isCopyFromStdin) return copyDelimiter;
    if (isSQLBody) return dummyDelimiter;
    if (defaultDelimiter != null) return defaultDelimiter;
    return DelimiterDefinition.STANDARD_DELIMITER;
  }

  @Override
  public void statementFinished()
  {
    firstToken = null;
    lastToken = null;
    isCopy = false;
    isCopyFromStdin = false;
  }

  @Override
  public boolean supportsSingleLineStatements()
  {
    return true;
  }

  @Override
  public boolean isSingleLineStatement(SQLToken token, boolean isStartOfLine)
  {
    if (token == null) return false;

    if (isStartOfLine && !token.isWhiteSpace())
    {
      String text = token.getText();
      char c = text.charAt(0);
      return c == '\\' || c == '@';
    }
    return false;
  }

  @Override
  public void lineEnd()
  {
  }

}
