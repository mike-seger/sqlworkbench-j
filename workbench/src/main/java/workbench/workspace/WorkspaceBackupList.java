/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import workbench.resource.Settings;

import workbench.util.FileVersioner;

/**
 * A class to list all backups of a specific workspace.
 *
 * @author Thomas Kellerer
 */
public class WorkspaceBackupList
{
  private File backupDir;
  private File workspace;

  public WorkspaceBackupList(File workspace)
  {
    this(workspace, new File(Settings.getInstance().getBackupDirName()));
  }

  public WorkspaceBackupList(File workspace, File backupDir)
  {
    this.backupDir = backupDir;
    this.workspace = workspace;
  }

  public List<File> getBackups()
  {
    return getBackups(Settings.getInstance().getFileVersionDelimiter());
  }

  public List<File> getBackups(char versionDelimiter)
  {
    if (this.backupDir == null || !this.backupDir.exists() || !this.backupDir.isDirectory())
    {
      return Collections.emptyList();
    }

    List<File> result = new ArrayList<>(Settings.getInstance().getMaxBackupFiles());

    String baseName = workspace.getName();

    String[] files = this.backupDir.list((File dir, String name) ->
    {
      name = FileVersioner.stripVersion(name, versionDelimiter);
      return name.equalsIgnoreCase(baseName);
    });

    if (files == null) return result;
    for (String name : files)
    {
      File backup = new File(backupDir, name);
      result.add(backup);
    }
    sortFiles(result, versionDelimiter);
    return result;
  }

  private void sortFiles(List<File> files, char versionDelimiter)
  {
    Comparator<File> comp = (File o1, File o2) ->
    {
      int v1 = FileVersioner.getFileVersion(o1, versionDelimiter);
      int v2 = FileVersioner.getFileVersion(o2, versionDelimiter);
      return v2 - v1;
    };
    Collections.sort(files, comp);
  }

}
