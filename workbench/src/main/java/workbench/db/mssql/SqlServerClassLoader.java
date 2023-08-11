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
package workbench.db.mssql;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.net.URLClassLoader;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.ClasspathUtil;
import workbench.util.PlatformHelper;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A URL classloader that implements findLibrary() to find DLLs needed by the driver.
 *
 * This is mainly intended for SQL Servers's DLL that is needed for integreated security,
 * but can also be used for other drivers that need to load a DLL.
 *
 * @author Thomas Kellerer
 */
public class SqlServerClassLoader
  extends URLClassLoader
{
  private final File jardir;

  public SqlServerClassLoader(File jardir, URL[] urls, ClassLoader cl)
  {
    super(urls, cl);
    if (jardir.isDirectory())
    {
      this.jardir = jardir;
    }
    else
    {
      this.jardir = jardir.getParentFile();
    }
  }

  @Override
  protected String findLibrary(String libname)
  {
    String path = super.findLibrary(libname);
    if (path != null || libname == null)
    {
      return path;
    }

    // Does this even exists for anything else than Windows?
    String ext = PlatformHelper.isWindows() ? ".dll" : ".so";
    File dlldir = searchLibrary(libname);
    WbFile f = new WbFile(dlldir, libname + ext);
    if (f != null) return f.getFullPath();

    return null;
  }

  private File searchLibrary(String dllName)
  {
    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logInfo(ci, "Native library \"" + dllName + "\" requested.");
    ClasspathUtil cp = new ClasspathUtil();

    String ext = ".dll";
    String dllFile = null;

    // not sure if this is really needed, but doesn't hurt either
    if (PlatformHelper.isWindows())
    {
      dllName = dllName.toLowerCase();
      dllFile = dllName + ext;
    }
    else
    {
      ext = ".so";
      dllFile = dllName + ext;
    }

    WbFile lib = new WbFile(jardir, dllFile);
    if (lib.exists())
    {
      LogMgr.logInfo(ci, "Found library \"" + dllFile + "\" in: " + jardir);
      return jardir;
    }

    // This is the directory layout that is created when extracting the downloaded ZIP file
    boolean is64Bit = System.getProperty("os.arch").equals("amd64");
    String archDir = is64Bit ? "x64" : "x86";

    WbFile authDir = new WbFile(jardir, "auth\\" + archDir);

    if (!authDir.exists())
    {
      // newer builds of the driver put the jar files into a sub-directory
      authDir = new WbFile(jardir, "..\\auth\\" + archDir);
    }
    WbFile dll = new WbFile(authDir, dllFile);
    if (dll.exists())
    {
      LogMgr.logInfo(ci, "Found library \"" + dllFile + "\" in: " + authDir);
      return authDir;
    }

    // Now check the directory where SQL Workbench's JAR file is located
    File f = new File(cp.getJarDir(), dllFile);
    if (f.exists())
    {
      LogMgr.logInfo(ci, "Found library \"" + dllFile + "\" in: " + cp.getJarDir());
      return cp.getJarDir();
    }

    // Then check the ext dir
    f = new File(cp.getExtDir(), dllFile);
    if (f.exists())
    {
      LogMgr.logInfo(ci, "Found library \"" + dllFile + "\" in: " + cp.getExtDir());
      return cp.getExtDir();
    }

    // Nothing found, search the library path
    String libPath = System.getProperty("java.library.path");
    LogMgr.logDebug(ci, "Searching for \"" + dllFile + "\" on library.path: " + libPath);
    String separator = StringUtil.quoteRegexMeta(File.pathSeparator);
    String[] pathElements = libPath.split(separator);
    for (String dir : pathElements)
    {
      File fdir = new File(dir);
      if (dllExists(fdir, dllName, ext))
      {
        LogMgr.logInfo(ci, "Found library " + dllName + " in: \"" + fdir + "\"");
        return fdir;
      }
    }
    return null;
  }

  private boolean dllExists(File dir, String dllName, String ext)
  {
    if (dir == null) return false;
    if (dllName == null) return false;
    FilenameFilter filter = (File sdir, String name) -> name != null && name.toLowerCase().contains(dllName) && name.toLowerCase().endsWith(ext);
    String[] files = dir.list(filter);
    if (files == null || files.length == 0) return false;
    return true;
  }

}
