/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.sql.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLToken;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class SelectExtractor
{
  private final String originalSQL;
  private final int cursorPos;
  private int startPos = -1;
  private int endPos = -1;
  private final SQLLexer lexer;

  public SelectExtractor(SQLLexer lexer, String sql, int pos)
  {
    this.originalSQL = sql;
    this.cursorPos = pos;
    this.lexer = lexer;
    parseSQL();
  }

  private void parseSQL()
  {
    Set<String> unionKeywords = CollectionUtil.caseInsensitiveSet("UNION", "UNION ALL", "MINUS", "INTERSECT", "EXCEPT", "EXCEPT ALL");
    startPos = -1;
    endPos = -1;

    try
    {
      lexer.setInput(originalSQL);

      SQLToken t = lexer.getNextToken(false, false);
      if (t == null) return;

      SQLToken lastToken = null;

      int lastStart = 0;
      int lastEnd = 0;
      String verb = t.getContents();

      // Will contain each "union" token to find the start and end of each sub-statement
      List<SQLToken> unionStarts = new ArrayList<>();

      int bracketCount = 0;
      boolean inSubselect = false;
      boolean checkForInsertSelect = verb.equals("INSERT") || verb.equals("CREATE") || verb.equals("CREATE OR REPLACE");

      while (t != null)
      {
        final String value = t.getContents();

        if ("(".equals(value))
        {
          bracketCount++;
          if (bracketCount == 1) lastStart = t.getCharBegin();
        }
        else if (")".equals(value))
        {
          bracketCount--;
          if (inSubselect && bracketCount == 0)
          {
            lastEnd = t.getCharBegin();
            if (lastStart <= cursorPos && cursorPos <= lastEnd)
            {
              startPos = lastStart  + 1;
              endPos = lastEnd;
              return;
            }
          }
          if (bracketCount == 0)
          {
            inSubselect = false;
            lastStart = 0;
            lastEnd = 0;
          }
        }
        else if (bracketCount == 0 && checkForInsertSelect && value.equals("SELECT"))
        {
          if (this.cursorPos >= t.getCharBegin())
          {
            startPos = t.getCharBegin();
            return;
          }
        }
        else if (bracketCount == 0 && unionKeywords.contains(value))
        {
          unionStarts.add(t);
        }

        if (bracketCount == 1 && lastToken != null && lastToken.getContents().equals("(") && value.equals("SELECT"))
        {
          inSubselect = true;
        }

        lastToken = t;
        t = lexer.getNextToken(false, false);
      }

      if (unionStarts.size() > 0)
      {
        int index = 0;
        int lastPos = 0;
        while (index < unionStarts.size())
        {
          int unionStart = unionStarts.get(index).getCharBegin();
          if (lastPos <= cursorPos && cursorPos <= unionStart)
          {
            endPos = lastEnd;
            startPos = lastStart + 1;
            return;
          }
          lastPos = startPos;
          index++;
        }
        // check last union
        int unionStart = unionStarts.get(unionStarts.size() - 1).getCharEnd();
        if (cursorPos >= unionStart)
        {
          startPos = unionStart;
          endPos = -1;
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when checking sub-select", e);
    }
  }

  public int getRealSelectStart()
  {
    return startPos;
  }

  public int getRealSelectEnd()
  {
    return endPos;
  }

  public int getRelativeCursorPosition()
  {
    if (startPos > -1)
    {
      return cursorPos - startPos - 1;
    }
    return cursorPos;
  }

  public String getRealQuery()
  {
    if (startPos > -1 && endPos > -1)
    {
      return originalSQL.substring(startPos, endPos);
    }
    else if (startPos > -1)
    {
      return originalSQL.substring(startPos);
    }
    return originalSQL;
  }
}
