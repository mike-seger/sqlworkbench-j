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

package workbench.sql;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.sql.wbcommands.WbHistory;

import workbench.util.CharacterRange;
import workbench.util.CollectionUtil;
import workbench.util.EncodingUtil;
import workbench.util.FixedSizeList;
import workbench.util.SqlParsingUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class StatementHistory
  extends FixedSizeList<String>
{

  public StatementHistory(int max)
  {
    super(max);
    setAllowDuplicates(true);
    doAppend(true);
  }

  public synchronized boolean add(String statement)
  {
    if (StringUtil.isEmpty(statement)) return false;

    String last = entries.size() > 0 ? entries.getLast() : "";
    if (last != null && last.equals(statement)) return false;

    String verb = SqlParsingUtil.getInstance(null).getSqlVerb(statement);
    if (verb.equalsIgnoreCase(WbHistory.VERB) || verb.equalsIgnoreCase(WbHistory.SHORT_VERB)) return false;

    return super.add(statement);
  }

  public List<String> getHistoryEntries()
  {
    return Collections.unmodifiableList(this.getEntries());
  }

  public String getHistoryEntry(int index)
  {
    return this.get(index);
  }

  public void readFrom(File f)
  {
    if (f == null || !f.exists()) return;

    try (BufferedReader reader = EncodingUtil.createBufferedReader(f, "UTF-8");)
    {
      readFrom(reader);
      LogMgr.logInfo(new CallerInfo(){}, "Loaded statement history from " + f.getAbsolutePath());
    }
    catch (IOException io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not save history", io);
    }
  }

  public void readFrom(BufferedReader reader)
    throws IOException
  {
    entries.clear();

    String line = reader.readLine();
    while (line != null)
    {
      line = StringUtil.decodeUnicode(line);
      this.append(line);
      line = reader.readLine();
    }
  }

  public void saveTo(File f)
  {
    if (f == null || !f.exists()) return;
    if (CollectionUtil.isEmpty(entries)) return;

    try (Writer writer = EncodingUtil.createWriter(f, "UTF-8", false);)
    {
      saveTo(writer);
      LogMgr.logInfo(new CallerInfo(){}, "Saved statement history to " + f.getAbsolutePath());
    }
    catch (IOException io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not save history", io);
    }
  }

  /**
   * The caller needs to close the Writer instance.
   *
   */
  public void saveTo(Writer writer)
    throws IOException
  {
    if (CollectionUtil.isEmpty(entries)) return;

    for (String sql : entries)
    {
      String line = StringUtil.escapeText(sql, CharacterRange.RANGE_CONTROL);
      writer.write(line);
      writer.write('\n');
    }

  }
}
