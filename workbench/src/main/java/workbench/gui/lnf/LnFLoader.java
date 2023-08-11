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
package workbench.gui.lnf;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.ClasspathUtil;

/**
 * A class to load a defined Look & Feel at runtime.
 *
 * @author Thomas Kellerer
 */
public class LnFLoader
{
  private LnFDefinition lnfDef;

  public LnFLoader(LnFDefinition definition)
  {
    this.lnfDef = definition;
  }

  public boolean isAvailable()
  {
    if (this.lnfDef.isBuiltIn()) return true;
    if (this.lnfDef.isExt()) return true;

    try
    {
      ClassLoader loader = createLoader();
      String resName = this.lnfDef.getClassName().replace('.', '/') + ".class";
      URL cl = loader.getResource(resName);
      return (cl != null);
    }
    catch (Exception e)
    {
      return false;
    }
  }

  private ClassLoader createLoader()
    throws MalformedURLException
  {
    List<String> liblist = this.lnfDef.getLibraries();
    if (liblist != null && !lnfDef.isExt())
    {
      URL[] url = new URL[liblist.size()];
      for (int i=0; i < liblist.size(); i++)
      {
        String fname = Settings.getInstance().replaceLibDirKey(liblist.get(i));
        File f = new File(fname);
        if (!f.isAbsolute())
        {
          ClasspathUtil cp = new ClasspathUtil();
          f = new File(cp.getJarPath(), liblist.get(i));
        }
        url[i] = f.toURI().toURL();
      }
      ClassLoader loader = new URLClassLoader(url, this.getClass().getClassLoader());
      return loader;
    }
    else
    {
      // no libraries, assume it's on the classpath
      return null;
    }
  }

  public Class<? extends LookAndFeel> loadClass()
    throws ClassNotFoundException
  {
    return loadClass(this.lnfDef.getClassName(), true);
  }

  public Class<? extends LookAndFeel> loadClass(String lnfClassName, boolean logVersion)
    throws ClassNotFoundException
  {
    Class lnfClass = null;
    try
    {
      ClassLoader loader = createLoader();
      if (loader != null)
      {
        // Tell the LNF class which classloader to use!
        // This is important otherwise, the LnF will not
        // initialize correctly
        UIManager.getDefaults().put("ClassLoader", loader);
        Thread.currentThread().setContextClassLoader(loader);
        lnfClass = loader.loadClass(lnfClassName);
      }
      else
      {
        // If no library is specified we assume the class
        // is available through the system classloader
        // My tests showed that the property is not set initially
        // so I assume this means "use system classloader"
        UIManager.getDefaults().put("ClassLoader", null);
        lnfClass = Class.forName(lnfClassName);
      }

      if (logVersion)
      {
        Package pkg = lnfClass.getPackage();
        String version = pkg.getImplementationVersion();
        if (version != null)
        {
          version = ", version: " + version;
        }
        else
        {
          version = "";
        }
        LogMgr.logInfo(new CallerInfo(){}, "Loaded look and feel: " + lnfClass.getSimpleName() + version);
      }
    }
    catch (Exception e)
    {
      throw new ClassNotFoundException("Could not load class " + lnfClassName,e);
    }
    return lnfClass;
  }

  public LookAndFeel getLookAndFeel(String className)
    throws ClassNotFoundException
  {
    try
    {
      Class<? extends LookAndFeel> lnf = loadClass(className, false);
      return lnf.getDeclaredConstructor().newInstance();
    }
    catch (Exception e)
    {
      throw new ClassNotFoundException("Could not load class " + this.lnfDef.getClassName(),e);
    }
  }

  public LookAndFeel getLookAndFeel()
    throws ClassNotFoundException
  {
    try
    {
      Class<? extends LookAndFeel> lnf = loadClass();
      return lnf.getDeclaredConstructor().newInstance();
    }
    catch (Exception e)
    {
      throw new ClassNotFoundException("Could not load class " + this.lnfDef.getClassName(),e);
    }
  }
}

