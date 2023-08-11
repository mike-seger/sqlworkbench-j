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
package workbench.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.WbConnection;

import workbench.gui.completion.SelectAllMarker;
import workbench.gui.completion.StatementContext;

import workbench.util.StringUtil;

import jline.Completor;

/**
 * A JLine Completor that loads up the tables and columns in the DB
 *
 * @author Jeremiah Spaulding, Thomas Kellerer
 */
public class TableNameCompletor
  implements Completor
{
  /**
   * The connection for which this completor works.
   */
  private final WbConnection connection;

  private String currentPrefix;
  private List<String> currentList;
  private int nextCycleIndex;

  /**
   * Simple constructor called only by BatchRunner
   *
   * @param stmnt
   */
  public TableNameCompletor(WbConnection conn)
  {
    this.connection = conn;
  }

  public boolean isSameConnection(WbConnection conn)
  {
    if (conn == null) return false;
    if (this.connection == null) return false;
    if (conn == this.connection) return true;

    if (conn.getUrl().equals(this.connection.getUrl()))
    {
      if (conn.getCurrentUser().equals(this.connection.getCurrentUser())) return true;
    }
    return false;
  }

  /**
   * This is called with the buffer to get completion candidates when tab is hit
   *
   * @param buffer
   * @param candidates
   * @param cursor
   */
  @Override
  public int complete(String buffer, int cursor, List candidates)
  {
    if (connection == null || connection.isClosed())
    {
      return -1;
    }
    LogMgr.logTrace(new CallerInfo(){}, "Completer called with cursor position=" + cursor + ", statement: "+ buffer);

    StatementContext ctx = new StatementContext(connection, buffer, cursor);
    if (!ctx.isStatementSupported())
    {
      reset();
      return -1;
    }

    String searchToken;

    String word = ctx.getAnalyzer().getCurrentWord();
    if (word != null && connection.getMetadata().isKeyword(word))
    {
      searchToken = "";
    }
    else
    {
      searchToken = word;
    }

    int start = cursor;
    while (start > 0 && !Character.isWhitespace(buffer.charAt(start - 1)))
    {
      start--;
    }

    String prefix = buffer.substring(0, start);

    if (currentPrefix != null && currentPrefix.equals(prefix) && currentList != null)
    {
      if (nextCycleIndex >= currentList.size()) //back to the beginning
      {
        nextCycleIndex = 0;
      }

      candidates.add(currentList.get(nextCycleIndex++));
      return prefix.length();
    }

    List<Object> data = ctx.getData();
    if (data == null || data.isEmpty())
    {
      reset();
      return -1;
    }

    if (!data.isEmpty() && data.get(0) instanceof SelectAllMarker)
    {
      data.remove(0);
    }
    // The "data" list can contain different DbObject instances
    // We just convert everything to a string here
    List<String> names = data.stream().
                              filter(o -> o != null).
                              map(Object::toString).
                              collect(Collectors.toList());


    if (StringUtil.isBlank(searchToken))
    {
      // if no search word is available, add all matches.
      currentList = new ArrayList<>();
      currentList.addAll(names);
    }
    else
    {
      currentList = names.stream().
                          filter(x -> startsWithIgnoreCase(x, searchToken)).
                          collect(Collectors.toList());
    }

    if (currentList.isEmpty())
    {
      reset();
      return -1;
    }

    currentPrefix = prefix;
    nextCycleIndex = 0;
    candidates.add(currentList.get(nextCycleIndex++));
    return prefix.length();
  }

  private boolean startsWithIgnoreCase(String input, String compare)
  {
    if (input == null || compare == null) return false;
    return input.toLowerCase().startsWith(compare.toLowerCase());
  }

  private void reset()
  {
    currentPrefix = null;
    nextCycleIndex = -1;
  }
}
