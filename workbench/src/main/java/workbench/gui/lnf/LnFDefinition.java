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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.resource.Settings;

import workbench.util.ClasspathUtil;
import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * The definition of a pluggable look and feel. It stores the classname
 * of the Look & Feel together with the library from which the class
 * should be loaded
 *
 * @author Thomas Kellerer
 */
public class LnFDefinition
  implements Comparable<LnFDefinition>
{
  public static final String FLATLAF_MAC_LIGHT_CLASS = "com.formdev.flatlaf.themes.FlatMacLightLaf";
  public static final String FLATLAF_MAC_DARK_CLASS = "com.formdev.flatlaf.themes.FlatMacDarkLaf";
  public static final String FLATLAF_LIGHT_CLASS = "com.formdev.flatlaf.FlatLightLaf";
  public static final String FLATLAF_DARK_CLASS = "com.formdev.flatlaf.FlatDarkLaf";
  public static final String FLATLAF_THEMED_CLASS = "com.formdev.flatlaf.IntelliJTheme";

  private String name;
  private String className;
  private List<String> liblist;
  private LnFType type;
  public static final String LNF_PATH_SEPARATOR = "$|$";
  private String themeFile;

  public LnFDefinition(String desc)
  {
    this(desc, null, null);
    this.type = LnFType.dynamic;
  }

  public LnFDefinition(String desc, String clazz)
  {
    this(desc, clazz, null);
    this.type = LnFType.builtIn;
  }

  public LnFDefinition(String desc, String clazz, List<String> libs)
  {
    this.name = desc;
    this.className = (clazz == null ? clazz : clazz.trim());
    this.setLibraries(libs);
  }

  private boolean isExtLibs(List<String> libs)
  {
    ClasspathUtil util = new ClasspathUtil();
    for (String lib : libs)
    {
      String fname = Settings.getInstance().replaceLibDirKey(lib);
      if (ClasspathUtil.EXT_DIR.equalsIgnoreCase(fname))
      {
        return true;
      }
      File f = new File(fname);
      if (!util.isInExtDir(f)) return false;
    }
    return true;
  }

  public File getThemeFile()
  {
    if (!getSupportsThemes()) return null;
    if (StringUtil.isBlank(themeFile)) return null;
    File f = new File(themeFile);
    if (f.exists()) return f;
    return null;
  }

  public String getThemeFileName()
  {
    return themeFile;
  }

  public void setThemeFileName(String themeFile)
  {
    this.themeFile = StringUtil.trimToNull(themeFile);
  }

  public boolean getSupportsThemes()
  {
    return FLATLAF_THEMED_CLASS.equals(className);
  }

  public boolean isExt()
  {
    return this.type == LnFType.ext;
  }

  public boolean isDynamic()
  {
    return this.type == LnFType.dynamic;
  }

  public boolean isBuiltIn()
  {
    return this.type == LnFType.builtIn;
  }

  public String debugString()
  {
    return name + ", class: " + className + ", type: " + this.type;
  }

  @Override
  public String toString()
  {
    return getName();
  }

  public String getName()
  {
    return name;
  }

  public String getClassName()
  {
    return className;
  }

  public void setName(String name)
  {
    this.name = name;
  }

  public void setClassName(String className)
  {
    this.className = className;
  }

  public List<String> getLibraries()
  {
    if (this.type == LnFType.builtIn)
    {
      return Collections.singletonList("rt.jar");
    }
    if (this.type == LnFType.ext)
    {
      ClasspathUtil util = new ClasspathUtil();
      return CollectionUtil.arrayList(util.getExtDir().getAbsolutePath());
    }
    if (CollectionUtil.isEmpty(liblist)) return new ArrayList<>(0);
    return Collections.unmodifiableList(liblist);
  }

  public void setLibraries(List<String> list)
  {
    if (list != null)
    {
      liblist = new ArrayList<>(list);
      if (isExtLibs(list))
      {
        this.type = LnFType.ext;
      }
      else if (liblist.size() == 1 && "rt.jar".equalsIgnoreCase(liblist.get(0)))
      {
        this.type = LnFType.builtIn;
      }
      else
      {
        this.type = LnFType.dynamic;
      }
    }
    else
    {
      liblist = null;
      type = LnFType.builtIn;
    }
  }

  public LnFDefinition createCopy()
  {
    return new LnFDefinition(getName(), getClassName(), liblist);
  }

  @Override
  public int compareTo(LnFDefinition o)
  {
    String cls = o.getClassName();
    return this.className.compareTo(cls);
  }

  @Override
  public boolean equals(Object o)
  {
    if (o instanceof LnFDefinition)
    {
      LnFDefinition other = (LnFDefinition)o;
      return this.className.equals(other.className);
    }
    if (o instanceof String)
    {
      return this.className.equals((String)o);
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    return this.className.hashCode();
  }

  public static LnFDefinition newExtLaf(String name, String className)
  {
    LnFDefinition lnf = new LnFDefinition(name, className);
    lnf.type = LnFType.ext;
    return lnf;
  }

}
