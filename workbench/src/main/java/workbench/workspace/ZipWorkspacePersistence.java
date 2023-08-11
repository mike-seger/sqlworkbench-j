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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.FileUtil;
import workbench.util.WbFile;
import workbench.util.ZipUtil;

/**
 * An implementation of WorkpacePersistence that saves a workspace into ZIP archive.
 *
 * @author Thomas Kellerer
 */
public class ZipWorkspacePersistence
  extends WorkspacePersistence
{
  private ZipOutputStream zout;
  private ZipFile archive;
  private FileLock writeLock;

  private final String filename;
  private final Map<String, Long> lastCRC = new HashMap<>();
  private ZipEntry lastEntry;

  public ZipWorkspacePersistence(String filename)
  {
    this.filename = filename;
  }

  @Override
  public void openForWriting()
    throws IOException
  {
    OutputStream out = null;
    File f = new File(filename);
    try
    {
      lastCRC.clear();
      FileOutputStream fout = new FileOutputStream(f);
      writeLock = fout.getChannel().lock();
      if (writeLock == null)
      {
        FileUtil.closeQuietely(fout);
        throw new IOException("Could not obtain a lock on " + filename);
      }
      zout = new ZipOutputStream(fout);
      zout.setLevel(Settings.getInstance().getIntProperty("workbench.workspace.compression", 5));
      zout.setComment("SQL Workbench/J Workspace file");
    }
    catch (Exception e)
    {
      FileUtil.closeQuietely(writeLock, zout, out);
      if (e instanceof IOException)
      {
        throw (IOException)e;
      }
      throw new IOException("Could not open ZIP file", e);
    }
  }

  @Override
  public void openForReading()
    throws IOException
  {
    try
    {
      this.zout = null;
      this.archive = new ZipFile(filename);
    }
    catch (Exception ex)
    {
      FileUtil.closeQuietely(archive);
      LogMgr.logDebug(new CallerInfo(){}, "Could not open workspace file " + filename, ex);
      if (ex instanceof IOException)
      {
        throw (IOException)ex;
      }
      throw new IOException(ex);
    }
  }

  @Override
  public void close()
  {
    FileUtil.closeQuietely(writeLock, zout, archive);
    zout = null;
    archive = null;
  }

  @Override
  protected OutputStream createOutputStream(String entryName)
    throws IOException
  {
    lastEntry = new ZipEntry(entryName);
    zout.putNextEntry(lastEntry);
    return zout;
  }

  @Override
  protected void closeEntryOutputStream(OutputStream out)
  {
    try
    {
      if (lastEntry != null)
      {
        lastCRC.put(lastEntry.getName(), lastEntry.getCrc());
        lastEntry = null;
      }
      zout.closeEntry();
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not close current ZIP entry", ex);
    }
  }

  @Override
  protected InputStream createInputStream(String entryName)
    throws IOException
  {
    lastEntry = archive.getEntry(entryName);
    if (lastEntry == null)
    {
      return null;
    }
    return archive.getInputStream(lastEntry);
  }

  @Override
  protected void closeEntryInputStream(InputStream in)
  {
    lastEntry = null;
    try
    {
      if (in != null) in.close();
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not close current ZIP entry", ex);
    }
  }

  @Override
  public void removeEntry(String entryName)
  {
    // nothing to do for ZIP files
  }

  @Override
  public List<String> getEntries()
  {
    List<String> entries = new ArrayList<>();
    if (archive == null) return entries;
    Enumeration<? extends ZipEntry> zipEntries = archive.entries();
    while (zipEntries.hasMoreElements())
    {
      ZipEntry entry = zipEntries.nextElement();
      entries.add(entry.getName());
    }
    return entries;
  }

  @Override
  public boolean isOutputValid()
  {
    return ZipUtil.isValid(new File(this.filename), lastCRC);
  }

  @Override
  public File createBackup()
  {
    return FileUtil.createBackup(new WbFile(filename));
  }

  @Override
  public void restoreBackup(File backup)
    throws IOException
  {
    FileUtil.copy(backup, new File(filename));
  }

}
