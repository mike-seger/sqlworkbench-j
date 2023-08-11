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
package workbench.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

/**
 * A class to create versioned backups of files up to a specified limit.
 *
 * @author Thomas Kellerer
 */
public class FileVersioner
{
  private char versionSeparator = '.'; // should be compatible with all file systems
  private final int maxVersions;
  private File backupDir;

  /**
   * Create a FileVersioner that saves the backup in the same
   * directory as the target file.
   *
   * @param maxCount max. number of backups to maintain
   */
  public FileVersioner(int maxCount)
  {
    this(maxCount, null, '.');
  }

  /**
   * Create a new FileVersioner that saves the backup files in a specific directory
   * <br/>
   * If the backup directory is not an absolute pathname, then it's
   * considered relative to the config directory.
   *
   * @param maxCount max. number of backups to maintain
   * @param dirName  the directory where to save the backup files
   * @param separator the character to put before the version number. Only the first character is used
   * @see Settings#getConfigDir()
   */
  public FileVersioner(int maxCount, File dir, char separator)
  {
    this.maxVersions = (maxCount > 0 ? maxCount : 5);
    if (dir != null)
    {
      backupDir = dir;
      if (!backupDir.isAbsolute())
      {
        backupDir = new File(Settings.getInstance().getConfigDir(), dir.getName());
      }
    }
    if (separator != 0)
    {
      versionSeparator = separator;
    }
  }

  /**
   * Create a versioned backup of the specified file.
   * <br/>
   * If the max. number of versions has not yet been reached for the given
   * file, this method will simply create a new version (highest number is the newest version).
   * <br/>
   * File versions will be appended to the input filename (myfile.txt -> myfile.txt.1).
   * <br/>
   * If the max. number of versions is reached, the oldest version (version #1) will
   * be deleted, and the other versions will be renamed (2 -> 1, 3 -> 2, and so on).
   * <br/>
   * Then the new version will be created.
   * <br>
   * The backup file will be stored in the directory specified in the constructor,
   * or the directory of the file that is backed up (if no backup directory was specified)
   *
   * @param toBackup the file to backup
   * @return the complete filename of the backup
   * @throws java.io.IOException
   */
  public File createBackup(File toBackup)
    throws IOException
  {
    if (!toBackup.exists()) return null;
    File backup = getNextBackupFile(toBackup);
    long start = System.currentTimeMillis();
    FileUtil.copy(toBackup, backup);
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Created file \"" +
      WbFile.getPathForLogging(backup.getAbsolutePath()) + "\" as a backup of \"" +
      WbFile.getPathForLogging(toBackup) + "\" in " + duration + "ms");
    return backup;
  }

  public File getNextBackupFile(File toBackup)
  {
    if (toBackup == null) return null;
    long start = System.currentTimeMillis();
    int nextVersion = findNextIndex(toBackup);
    File dir = getTargetDir(toBackup);
    if (dir == null)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not determine target directory for backup. Using current directory");
      dir = new File(".");
    }

    if (!dir.exists())
    {
      if (!dir.mkdirs())
      {
        LogMgr.logError(new CallerInfo(){}, "Could not create backup dir: " +
          WbFile.getPathForLogging(dir.getAbsolutePath()) + ", using directory: " +
          WbFile.getPathForLogging(toBackup.getParentFile().getAbsolutePath()), null);
        dir = toBackup.getParentFile();
      }
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Calculating next backup version for file \"" +
      WbFile.getPathForLogging(toBackup) + "\" took " + duration + "ms");
    return new File(dir, toBackup.getName() + versionSeparator + nextVersion);
  }

  private File getTargetDir(File target)
  {
    if (backupDir != null) return backupDir;
    return target.getAbsoluteFile().getParentFile();
  }

  private int findNextIndex(File target)
  {
    File targetDir = getTargetDir(target);
    if (!targetDir.exists()) return 1;

    String name = target.getName() + versionSeparator + "*";
    int maxVersion = 0;

    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(targetDir.toPath(), name))
    {
      for (Path p : dirStream)
      {
        String fname = p.getFileName().toString();
        int pos = fname.lastIndexOf(versionSeparator);
        if (pos < 0) continue;

        int version = StringUtil.getIntValue(fname.substring(pos + 1), -1);
        if (version > maxVersion)
        {
          maxVersion = version;
        }
      }
    }
    catch (Exception ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not determine highest version", ex);
    }

    if (maxVersion < maxVersions)
    {
      return maxVersion + 1;
    }

    slideVersions(target);
    return maxVersions;
  }

  public static String stripVersion(File f)
  {
    if (f == null) return null;
    return stripVersion(f.getName(), Settings.getInstance().getFileVersionDelimiter());
  }

  public static String stripVersion(String name, char versionSeparator)
  {
    if (name == null) return null;
    int idx = name.lastIndexOf(versionSeparator);
    if (idx > 0)
    {
      name = name.substring(0, idx);
    }
    return name;
  }

  public static int getFileVersion(File target, char versionSeparator)
  {
    if (target == null) return -1;
    String name = target.getName();
    int idx = name.lastIndexOf(versionSeparator);
    if (idx < 0) return -1;
    return StringUtil.getIntValue(name.substring(idx + 1), -1);
  }

  private void slideVersions(File target)
  {
    long start = System.currentTimeMillis();
    File dir = getTargetDir(target);
    String name = target.getName();

    File max = new File(dir, name + versionSeparator + '1');
    max.delete();

    for (int i = 2; i <= maxVersions; i++)
    {
      File old = new File(dir, name + versionSeparator + i);
      if (old.exists())
      {
        File newIndex = new File(dir, name + versionSeparator + (i - 1));
        old.renameTo(newIndex);
      }
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Adjusting backup versions for \"" +
      WbFile.getPathForLogging(target) + "\" took " + duration + "ms");
  }
}
