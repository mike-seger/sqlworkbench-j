/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
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
package workbench.workspace;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.gui.sql.EditorHistory;

import workbench.sql.StatementHistory;

import workbench.util.CollectionUtil;
import workbench.util.EncodingUtil;
import workbench.util.WbProperties;

/**
 *
 * @author Thomas Kellerer
 */
public abstract class WorkspacePersistence
{
  public abstract void openForWriting()
    throws IOException;

  public abstract void openForReading()
    throws IOException;

  public abstract void close();

  public void saveProperties(String entryName, WbProperties props)
    throws IOException
  {
    if (CollectionUtil.isEmpty(props))
    {
      removeEntry(entryName);
      return;
    }
    OutputStream out = createOutputStream(entryName);
    try
    {
      props.save(out);
    }
    finally
    {
      closeEntryOutputStream(out);
    }
  }

  public abstract void removeEntry(String entryName);

  public WbProperties readProperties(String entryName)
  {
    WbProperties props = new WbProperties(null, 1);
    if (entryName == null)
    {
      return props;
    }

    InputStream in = null;
    try
    {
      in = createInputStream(entryName);
      // The InputStream can be null if the entry does not exist
      if (in != null)
      {
        props.load(in);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read property file: " + entryName, e);
    }
    finally
    {
      closeEntryInputStream(in);
    }
    return props;
  }

  public void saveEditorHistory(Map<Integer, EditorHistory> editorHistories)
    throws IOException
  {
    for (Map.Entry<Integer, EditorHistory> historyEntry : editorHistories.entrySet())
    {
      if (historyEntry.getValue() != null && historyEntry.getKey() != null)
      {
        OutputStream out = null;
        try
        {
          int index = historyEntry.getKey();
          String entryName = "WbStatements" + (index + 1) + ".txt";
          out = createOutputStream(entryName);
          historyEntry.getValue().writeToStream(out);
        }
        catch (IOException ex)
        {
          LogMgr.logError(new CallerInfo(){}, "Could not write editor history for tab index: " + historyEntry.getKey(), ex);
          throw ex;
        }
        finally
        {
          closeEntryOutputStream(out);
        }
      }
    }
  }

  public void readEditorHistory(int anIndex, EditorHistory history)
    throws IOException
  {
    InputStream in = null;
    try
    {
      in = createInputStream("WbStatements" + (anIndex + 1) + ".txt");
      if (in != null)
      {
        history.readFromStream(in);
      }
    }
    finally
    {
      closeEntryInputStream(in);
    }
  }

  public abstract List<String> getEntries();

  public void saveSQLExecutionHistory(int index, StatementHistory history)
    throws IOException
  {
    if (history.size() == 0) return;

    OutputStream out = null;
    try
    {
      String entryName = "SqlHistory" + (index + 1) + ".txt";
      out = createOutputStream(entryName);
      Writer writer = EncodingUtil.createWriter(out, "UTF-8");
      history.saveTo(writer);
      writer.flush();
    }
    finally
    {
      closeEntryOutputStream(out);
    }
  }

  public void readSQLExecutionHistory(int anIndex, StatementHistory executionHistory)
    throws IOException
  {
    InputStream in = createInputStream("SqlHistory" + (anIndex + 1) + ".txt");
    if (in != null)
    {
      try (BufferedReader reader = new BufferedReader(EncodingUtil.createReader(in, "UTF-8"), 2048))
      {
        executionHistory.readFrom(reader);
      }
      finally
      {
        closeEntryInputStream(in);
      }
    }
  }

  public abstract void restoreBackup(File backup)
    throws IOException;

  public abstract File createBackup();

  public abstract boolean isOutputValid();

  protected abstract OutputStream createOutputStream(String entryName)
    throws IOException;
  protected abstract void closeEntryOutputStream(OutputStream out);

  protected abstract InputStream createInputStream(String entryName)
    throws IOException;
  protected abstract void closeEntryInputStream(InputStream in);
}
