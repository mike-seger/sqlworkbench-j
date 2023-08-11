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
import java.util.Comparator;
import java.util.List;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.ClasspathUtil;
import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

import static workbench.gui.lnf.LnFDefinition.*;

/**
 * Retrieve and store LnF definitions in the the Settings object.
 *
 * @author Thomas Kellerer
 */
public class LnFManager
{
  private List<LnFDefinition> lnfList;

  public LnFManager()
  {
  }

  public void removeDefinition(LnFDefinition lnf)
  {
    if (lnf == null) return;
    if (!lnf.isBuiltIn())
    {
      getLnFList().remove(lnf);
    }
  }

  public LnFDefinition getCurrentLnF()
  {
    LookAndFeel lnf = UIManager.getLookAndFeel();
    String lnfClass = lnf.getClass().getName();
    if (lnfClass.startsWith(FLATLAF_THEMED_CLASS))
    {
      lnfClass = LnFDefinition.FLATLAF_THEMED_CLASS;
    }
    return findLookAndFeel(lnfClass);
  }

  public int addDefinition(LnFDefinition lnf)
  {
    List<LnFDefinition> list = getLnFList();
    list.add(lnf);
    return list.size() - 1;
  }

  public void saveLookAndFeelDefinitions()
  {
    Settings set = Settings.getInstance();
    removeLnFEntries();
    int lnfCount = 0;
    for (LnFDefinition lnf : getLnFList())
    {
      if (lnf.isDynamic())
      {
        String libs = StringUtil.listToString(lnf.getLibraries(), LnFDefinition.LNF_PATH_SEPARATOR, false);
        set.setProperty("workbench.lnf." + lnfCount + ".classpath", libs);
        set.setProperty("workbench.lnf." + lnfCount + ".class", lnf.getClassName());
        set.setProperty("workbench.lnf." + lnfCount + ".name", lnf.getName());
        lnfCount++;
      }
      else if (lnf.isExt())
      {
        set.setProperty("workbench.lnf." + lnfCount + ".class", lnf.getClassName());
        set.setProperty("workbench.lnf." + lnfCount + ".name", lnf.getName());
        set.setProperty("workbench.lnf." + lnfCount + ".classpath", ClasspathUtil.EXT_DIR);
        set.setProperty("workbench.lnf." + lnfCount + ".theme", lnf.getThemeFileName());
        lnfCount++;
      }
    }
    set.setProperty("workbench.lnf.count", lnfCount);
  }

  private void removeLnFEntries()
  {
    Settings set = Settings.getInstance();
    int count = set.getIntProperty("workbench.lnf.count", 0);
    for (int i = 0; i < count; i++)
    {
      set.removeProperty("workbench.lnf." + i + ".classpath");
      set.removeProperty("workbench.lnf." + i + ".class");
      set.removeProperty("workbench.lnf." + i + ".name");
    }
  }

  public boolean isFlatLafLibPresent()
  {
    ClasspathUtil util = new ClasspathUtil();
    List<File> libs = util.getExtLibs();
    for (File f : libs)
    {
      if (f.getName().toLowerCase().contains("flatlaf"))
      {
        return true;
      }
    }
    return false;
  }

  private synchronized List<LnFDefinition> getLnFList()
  {
    if (lnfList != null) return lnfList;
    long start = System.currentTimeMillis();

    lnfList = new ArrayList<>();
    Settings set = Settings.getInstance();

    boolean lightConfigured = false;
    boolean darkConfigured = false;
    boolean themedConfigure = false;
    boolean macLightConfigured = false;
    boolean macDarkConfigured = false;

    int count = set.getIntProperty("workbench.lnf.count", 0);
    for (int i = 0; i < count; i++)
    {
      String clz = set.getProperty("workbench.lnf." + i + ".class", "");
      if (clz == null) continue;

      if (!lightConfigured && clz.equals(FLATLAF_LIGHT_CLASS))
      {
        lightConfigured = true;
      }
      if (!macLightConfigured && clz.equals(FLATLAF_MAC_LIGHT_CLASS))
      {
        macLightConfigured = true;
      }
      if (!macDarkConfigured && clz.equals(FLATLAF_MAC_DARK_CLASS))
      {
        macDarkConfigured = true;
      }
      if (!darkConfigured && clz.equals(FLATLAF_DARK_CLASS))
      {
        darkConfigured = true;
      }
      if (!themedConfigure && clz.equals(FLATLAF_THEMED_CLASS))
      {
        themedConfigure = true;
      }
      String name = set.getProperty("workbench.lnf." + i + ".name", clz);
      String libs = set.getProperty("workbench.lnf." + i + ".classpath", "");
      String theme = set.getProperty("workbench.lnf." + i + ".theme", null);

      if (libs.equals(ClasspathUtil.EXT_DIR))
      {
        LnFDefinition lnf = LnFDefinition.newExtLaf(name, clz);
        lnf.setThemeFileName(theme);
        LogMgr.logDebug(new CallerInfo(){}, "Found Look & Feel: " + lnf.debugString());
        lnfList.add(lnf);
      }
      else
      {
        List<String> liblist = null;
        if (libs.contains(LnFDefinition.LNF_PATH_SEPARATOR))
        {
          liblist = StringUtil.stringToList(libs, LnFDefinition.LNF_PATH_SEPARATOR);
        }
        else
        {
          liblist = StringUtil.stringToList(libs, StringUtil.getPathSeparator());
        }

        if (CollectionUtil.isNonEmpty(liblist))
        {
          LnFDefinition lnf = new LnFDefinition(name, clz, liblist);
          LogMgr.logDebug(new CallerInfo(){}, "Found Look & Feel: " + lnf.debugString());
          lnfList.add(lnf);
        }
      }
    }

    if (isFlatLafLibPresent())
    {
      if (!lightConfigured)
      {
        LnFDefinition light = LnFDefinition.newExtLaf("FlatLaf Light", FLATLAF_LIGHT_CLASS);
        lnfList.add(light);
        LogMgr.logDebug(new CallerInfo(){}, "Added " + light.debugString());
      }

      if (!darkConfigured)
      {
        LnFDefinition dark = LnFDefinition.newExtLaf("FlatLaf Dark", FLATLAF_DARK_CLASS);
        lnfList.add(dark);
        LogMgr.logDebug(new CallerInfo(){}, "Added " + dark.debugString());
      }

      if (!macLightConfigured)
      {
        LnFDefinition light = LnFDefinition.newExtLaf("MacOS FlatLaf Light", FLATLAF_MAC_LIGHT_CLASS);
        lnfList.add(light);
        LogMgr.logDebug(new CallerInfo(){}, "Added " + light.debugString());
      }

      if (!macDarkConfigured)
      {
        LnFDefinition dark = LnFDefinition.newExtLaf("MacOS FlatLaf Dark", FLATLAF_MAC_DARK_CLASS);
        lnfList.add(dark);
        LogMgr.logDebug(new CallerInfo(){}, "Added " + dark.debugString());
      }

      if (!themedConfigure)
      {
        LnFDefinition themed = LnFDefinition.newExtLaf("FlatLaf Themed", FLATLAF_THEMED_CLASS);
        lnfList.add(themed);
        LogMgr.logDebug(new CallerInfo(){}, "Added " + themed.debugString());
      }
    }

    // The Liquid Look & Feel "installs" itself as a System L&F and if
    // activated is returned in getInstalledLookAndFeels(). To avoid
    // a duplicate entry we check this before adding a "system" look and feel
    LookAndFeelInfo[] systemLnf = UIManager.getInstalledLookAndFeels();

    for (LookAndFeelInfo lnfInfo : systemLnf)
    {
      LnFDefinition lnf = new LnFDefinition(lnfInfo.getName(), lnfInfo.getClassName());
      if (!lnfList.contains(lnf))
      {
        lnfList.add(lnf);
      }
    }

    Comparator<LnFDefinition> nameComp = (LnFDefinition first, LnFDefinition second) -> StringUtil.compareStrings(first.getName(), second.getName(), true);
    lnfList.sort(nameComp);

    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Checking look & feels took: " + duration + "ms");

    return lnfList;
  }

  /**
   * returns all LnFs defined in the system. This is
   * a combined list of built-in LnFs and user-defined
   * LnFs
   *
   * @return all available Look and Feels
   * @see workbench.gui.lnf.LnFDefinition#isBuiltInLnF()
   */
  public List<LnFDefinition> getAvailableLookAndFeels()
  {
    return Collections.unmodifiableList(getLnFList());
  }

  public boolean isRegistered(String className)
  {
    return findLookAndFeel(className) != null;
  }

  public LnFDefinition findLookAndFeel(String className)
  {
    if (className == null) return null;

    for (LnFDefinition lnf : getLnFList())
    {
      if (lnf.getClassName().equals(className))
      {
        return lnf;
      }
    }
    return null;
  }
}
