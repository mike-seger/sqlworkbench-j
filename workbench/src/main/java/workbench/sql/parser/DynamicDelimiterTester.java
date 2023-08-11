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

import java.util.Set;

import workbench.sql.DelimiterDefinition;
import workbench.sql.lexer.SQLToken;
import workbench.sql.wbcommands.WbDelimiter;

import workbench.util.CollectionUtil;

/**
 *
 * @author Alfred Porter
 */
public class DynamicDelimiterTester
  implements DelimiterTester
{

  private final DelimiterDefinition currentDelimiter = new DelimiterDefinition(";");
  private DelimiterDefinition alternateDelimiter;
  private boolean isDelimiterCommand;
  private final Set<String> delimiterCommands = CollectionUtil.caseInsensitiveSet(WbDelimiter.ALTERNATE_VERB, WbDelimiter.VERB, "SET TERM");

  public DynamicDelimiterTester()
  {
  }

  @Override
  public boolean supportsMixedDelimiters()
  {
    return true;
  }

  @Override
  public void setAlternateDelimiter(DelimiterDefinition delimiter)
  {
    if (delimiter == null)
    {
      this.alternateDelimiter = null;
    }
    else
    {
      this.alternateDelimiter = delimiter.createCopy();
    }
  }

  @Override
  public void setDelimiter(DelimiterDefinition delimiter)
  {
    currentDelimiter.setDelimiter(delimiter.getDelimiter());
  }

  public boolean isDelimiterCommand(SQLToken token, boolean isStartOfLineOrStatement)
  {
    return WbDelimiter.VERB.equalsIgnoreCase(token.getText()) ||
          (isStartOfLineOrStatement && token.isReservedWord() && delimiterCommands.contains(token.getText()));
  }

  @Override
  public void currentToken(SQLToken token, boolean isStartOfStatement)
  {
    if (token == null) return;
    if (token.isComment() || token.isWhiteSpace()) return;

    if (isDelimiterCommand)
    {
      currentDelimiter.setDelimiter(token.getText());
    }

    if (isStartOfStatement)
    {
      isDelimiterCommand = isDelimiterCommand(token, isStartOfStatement);
    }
  }

  @Override
  public DelimiterDefinition getCurrentDelimiter()
  {
    // if an alternate delimiter was explicitly set,  use that
    if (alternateDelimiter != null)
    {
      return alternateDelimiter;
    }
    return currentDelimiter;
  }

  @Override
  public void statementFinished()
  {
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

    // Don't check for @ inside code that was started with non-standard delimiter
    if (currentDelimiter.isNonStandard()) return false;

    if (isStartOfLine && !token.isWhiteSpace())
    {
      String text = token.getText();
      char c = text.charAt(0);
      return c == '@' || isDelimiterCommand(token, isStartOfLine);
    }
    return false;
  }

  @Override
  public void lineEnd()
  {
    isDelimiterCommand = false;
  }

}
