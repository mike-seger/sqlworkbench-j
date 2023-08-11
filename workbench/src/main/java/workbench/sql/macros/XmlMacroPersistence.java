/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2013, Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.sql.macros;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.FileUtil;
import workbench.util.FileVersioner;
import workbench.util.WbFile;
import workbench.util.WbPersistence;

/**
 * A class to load and save macro definitions to an XML file.
 *
 * @author Thomas Kellerer
 */
public class XmlMacroPersistence
  implements MacroPersistence
{
  public XmlMacroPersistence()
  {
    WbPersistence.makeTransient(MacroDefinition.class, "originalSourceFile");
  }

  @Override
  public void reload(List<MacroGroup> groups, File changed)
  {
    List<MacroGroup> newMacros = loadMacros(changed);
    if (newMacros.size() > 0)
    {
      groups.clear();
      groups.addAll(newMacros);
    }
  }

  @Override
  public List<MacroGroup> loadMacros(File sourceFile)
  {
    List<MacroGroup> groups = new ArrayList<>();
    try
    {
      WbPersistence reader = new WbPersistence(sourceFile.getAbsolutePath());
      Object o = reader.readObject();
      if (o instanceof List)
      {
        groups.addAll((List)o);
      }
      else
      {
        LogMgr.logWarning(new CallerInfo(){}, "Format of macro file " + WbFile.getPathForLogging(sourceFile) + " not supported!");
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load macros from: " + WbFile.getPathForLogging(sourceFile), ex);
    }
    return groups;
  }

  @Override
  public void saveMacros(WbFile sourceFile, List<MacroGroup> groups, boolean isModified)
  {
    boolean deleteBackup = !Settings.getInstance().getCreateMacroBackup();
    boolean restoreBackup = false;
    File backupFile = null;
    long start = System.currentTimeMillis();

    if (groups.size() == 0)
    {
      // If a file existed but all macros were deleted, then delete the original file.
      if (sourceFile.exists() && isModified)
      {
        backupFile = createBackup(sourceFile);
        sourceFile.delete();
        LogMgr.logDebug(new CallerInfo(){}, "All macros from " + sourceFile.getFullpathForLogging() + " were removed. Macro file deleted.");
      }
      else
      {
        LogMgr.logDebug(new CallerInfo(){}, "No macros defined, nothing to save");
      }
    }
    else
    {
      backupFile = createBackup(sourceFile);

      WbPersistence writer = new WbPersistence(sourceFile.getAbsolutePath());
      try
      {
        writer.writeObject(groups);
        long duration = System.currentTimeMillis() - start;
        LogMgr.logDebug(new CallerInfo(){}, "Saved " + groups.size() + " macros to " + sourceFile.getFullpathForLogging() + " in " + duration + "ms");
      }
      catch (Throwable th)
      {
        LogMgr.logError(new CallerInfo(){}, "Error saving macros to " + sourceFile.getFullPath(), th);
        restoreBackup = true;
      }

      if (backupFile != null)
      {
        if (restoreBackup)
        {
          LogMgr.logWarning(new CallerInfo(){}, "Restoring the old macro file from backup: " +
             WbFile.getPathForLogging(backupFile.getAbsolutePath()));
          FileUtil.copySilently(backupFile, sourceFile);
        }
        else if (deleteBackup)
        {
          LogMgr.logDebug(new CallerInfo(){}, "Deleting temporary backup file: " + WbFile.getPathForLogging(backupFile.getAbsolutePath()));
          backupFile.delete();
        }
      }
    }

  }

  private File createBackup(WbFile f)
  {
    if (f.isDirectory()) return null;

    if (Settings.getInstance().getCreateMacroBackup())
    {
      int maxVersions = Settings.getInstance().getMaxBackupFiles();
      File dir = Settings.getInstance().getBackupDir();
      char sep = Settings.getInstance().getFileVersionDelimiter();
      FileVersioner version = new FileVersioner(maxVersions, dir, sep);
      try
      {
        return version.createBackup(f);
      }
      catch (IOException e)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Error when creating backup for: " + f.getFullpathForLogging(), e);
      }
    }
    return f.makeBackup();
  }


}
