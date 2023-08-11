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
package workbench.util;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import workbench.WbManager;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

/**
 *
 * @author Thomas Kellerer
 */
public class ClasspathUtil
{
  public static final String EXT_DIR = "ext";
  private final File extDir;

  public ClasspathUtil()
  {
    this.extDir = getExtDir();
  }

  public List<File> checkLibsToMove()
  {
    final File jarFile = getJarFile();
    if (jarFile == null) return Collections.emptyList();

    final File jarDir = jarFile.getParentFile();
    if (jarDir == null)
    {
      return Collections.emptyList();
    }
    LogMgr.logDebug(new CallerInfo(){}, "Checking directory: " + jarDir + " for additional libraries");

    final List<File> cp = getClassPath();
    FileFilter ff = (File pathname) -> !pathname.equals(jarFile) && isExtJar(pathname, cp);
    File[] files = jarDir.listFiles(ff);

    if (files == null) return Collections.emptyList();

    return Arrays.asList(files);
  }

  public List<File> getExtLibs()
  {
    if (WbManager.isTest()) return Collections.emptyList();

    FileFilter ff = (File pathname) -> pathname.getName().toLowerCase().endsWith(".jar");
    File[] files = extDir.listFiles(ff);

    if (files == null)
    {
      // This can happen if run from within the IDE
      return getClassPath();
    }
    return Arrays.asList(files);
  }

  public boolean isExtDir(List<File> toCheck)
  {
    return (toCheck.size() == 1 && isExtDir(toCheck.get(0)));
  }

  public boolean isExtDir(File toCheck)
  {
    if (toCheck == null) return false;
    return toCheck.equals(extDir);
  }

  public boolean isInExtDir(List<File> libs)
  {
    for (File f : libs)
    {
      if (isInExtDir(f)) return true;
    }
    return false;
  }

  public boolean isInExtDir(File jarfile)
  {
    if (jarfile == null) return false;

    if (extDir.equals(jarfile))
    {
      return true;
    }
    // this is essentially a file created with new File("ext")
    if (EXT_DIR.equals(jarfile.getName()) && jarfile.getParent() == null) return true;

    if (jarfile.isAbsolute())
    {
      return extDir.equals(jarfile.getParentFile());
    }

    List<File> cp = getExtLibs();

    File realFile = null;
    if (jarfile.getParentFile() == null)
    {
      realFile = new File(extDir, jarfile.getName());
    }
    else
    {
      realFile = new File(new WbFile(jarfile).getFullPath());
    }
    return cp.contains(realFile);
  }

  public WbFile getExtDir()
  {
    // Allow an alternate "ext" dir from within the IDE
    String dir = System.getProperty("workbench.libs.extdir", null);
    if (StringUtil.isNotBlank(dir))
    {
      WbFile f = new WbFile(dir);
      if (f.exists())
      {
        return f;
      }
    }

    File jarFile = getJarFile();
    if (jarFile == null || jarFile.isDirectory())
    {
      // This can happen when running from within the IDE
      return new WbFile(".", EXT_DIR);
    }
    return new WbFile(jarFile.getParentFile(), EXT_DIR);
  }

  public List<File> getClassPath()
  {
    String path = System.getProperty("java.class.path");
    String[] files = path.split(System.getProperty("path.separator"));
    if (files == null) return Collections.emptyList();
    return Arrays.stream(files).map(name -> new File(name)).collect(Collectors.toList());
  }

  private boolean isExtJar(File jarFile, List<File> classpath)
  {
    if (jarFile == null) return false;
    if (jarFile.isDirectory()) return false;
    if (classpath.contains(jarFile)) return false;
    if (jarFile.getName().toLowerCase().endsWith(".jar"))
    {
      return !mightBeJDBCDriver(jarFile);
    }
    return false;
  }

  private boolean mightBeJDBCDriver(File file)
  {
    try (JarFile jar = new JarFile(file);)
    {
      ZipEntry drv = jar.getEntry("META-INF/services/java.sql.Driver");
      return drv != null;
    }
    catch (Throwable th)
    {
      return false;
    }
  }
  /**
   * Returns the location of the application's jar file.
   *
   * @return the file object denoting the running jar file.
   * @see #getJarPath()
   */
  public File getJarFile()
  {
    URL url = null;

    try
    {
      url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
    }
    catch (Throwable th)
    {
      // ignore
    }

    // This can happen when loading forms in the NetBeans GUI designer
    if (url == null || StringUtil.isBlank(url.getFile()))
    {
      return new File(".");
    }

    File f;
    try
    {
      // Sending the path through the URLDecoder is important
      // because otherwise a path with %20 will be created
      // if the directory contains spaces!
      String p = URLDecoder.decode(url.getFile(), "UTF-8");
      f = new File(p);
    }
    catch (Throwable e)
    {
      // Fallback, should not happen
      String p = url.getFile().replace("%20", " ");
      f = new File(p);
    }
    return f;
  }

  public File getJarDir()
  {
    File jarFile = getJarFile();
    if (jarFile == null || jarFile.getParentFile() == null) return new File("");
    return getJarFile().getParentFile();
  }

  /**
   * Returns the directory in which the application is installed.
   *
   * @return the full path to the jarfile
   * @see #getJarFile()
   */
  public String getJarPath()
  {
    File jarFile = getJarFile();
    if (jarFile == null || jarFile.getParentFile() == null) return ".";
    WbFile parent = new WbFile(jarFile.getParentFile());
    return parent.getFullPath();
  }
}
