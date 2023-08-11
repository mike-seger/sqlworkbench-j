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
package workbench.gui.profiles;

import java.io.File;

import workbench.resource.Settings;

import workbench.util.ClasspathUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class MavenDownloadSettings {

  public static final String LAST_DIR_PROP = "workbench.driver.download.last.dir";

  public static void setLastDownloadDir(File dir)
  {
    if (dir != null)
    {
      Settings.getInstance().setProperty(LAST_DIR_PROP, dir.getAbsolutePath());
    }
  }

  public static File getDefaultDownloadDir()
  {
    String libDir = Settings.getInstance().getProperty(Settings.PROP_LIBDIR, null);
    String dir = Settings.getInstance().getProperty(LAST_DIR_PROP, libDir);
    if (dir != null)
    {
      File d = new File(dir);
      if (d.exists()) return d;
    }
    ClasspathUtil cp = new ClasspathUtil();
    File jarDir = cp.getJarDir();
    String dirName = "JDBCDrivers";
    WbFile dDir = new WbFile(jarDir, dirName);
    if (!dDir.exists())
    {
      dDir.mkdirs();
    }
    if (isWriteable(dDir)) return dDir;

    File configDir = Settings.getInstance().getConfigDir();
    WbFile fdir = new WbFile(configDir, dirName);
    if (!fdir.exists())
    {
      fdir.mkdirs();
    }
    if (isWriteable(fdir)) return fdir;

    WbFile extDir = cp.getExtDir();
    if (isWriteable(extDir)) return extDir;
    return null;
  }

  private static boolean isWriteable(File dir)
  {
    if (!dir.exists()) return false;
    WbFile f = new WbFile(dir, "test.wb");
    return f.canCreate();
  }

}
