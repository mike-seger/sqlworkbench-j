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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.DirectorySaveStrategy;
import workbench.resource.Settings;
import workbench.resource.StoreableKeyStroke;

import workbench.util.CollectionUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.WbProperties;

/**
 * A class to load macro definitions from a directory containing multiple SQL scripts.
 *
 * <p>Each sub-directory of the defined base directory is assumed to be a macro group.</p>
 *
 * <p>Files in the base directory are only loaded if there are no sub-directories.</p>
 *
 * <p>
 * Additional properties of the MacroGroup and MacroDefinition are stored and loaded from
 * a properties file ({@link #GROUP_INFO_FILE}) in the group's directory.
 * If the info file is not present, the default values for attributes of the group
 * or macro definitions are used.
 * </p>
 *
 * @author Thomas Kellerer
 */
public class DirectoryMacroPersistence
  implements MacroPersistence
{
  private static final String GROUP_INFO_FILE = "wb-macro-group.properties";
  private static final String GROUP_PREFIX = "group.info.";

  private static final String PROP_COUNT = "count";
  private static final String PROP_TOOLTIP = "tooltip";
  private static final String PROP_SORT_ORDER = "sortorder";
  private static final String PROP_SHORTCUT = "shortcut";
  private static final String PROP_NAME = "name";
  private static final String PROP_EXPAND = "expandWhileTyping";
  private static final String PROP_APPEND = "appendResults";
  private static final String PROP_DB_TREE = "dbTreeMacro";
  private static final String PROP_INCLUDE_IN_MENU = "includeInMenu";
  private static final String PROP_INCLUDE_IN_POPUP = "includeInPopup";

  private final Comparator<File> fileSorter = (File f1, File f2) -> f1.getName().compareToIgnoreCase(f2.getName());

  @Override
  public List<MacroGroup> loadMacros(File sourceDirectory)
  {
    List<MacroGroup> result = new ArrayList<>();
    if (sourceDirectory == null || !sourceDirectory.exists()) return result;

    if (!sourceDirectory.isDirectory())
    {
      throw new IllegalArgumentException("The provided file " + sourceDirectory + " is not a directory!");
    }

    File[] dirs = sourceDirectory.listFiles((File f) -> f != null && f.isDirectory());
    if (dirs.length == 0)
    {
      result.add(loadMacrosFromDirectory(new WbFile(sourceDirectory), 0));
    }
    else
    {
      Arrays.sort(dirs, fileSorter);
      int groupIndex = 0;

      for (File dir : dirs)
      {
        result.add(loadMacrosFromDirectory(new WbFile(dir), groupIndex++));
      }
    }
    return result;
  }

  @Override
  public void saveMacros(WbFile baseDirectory, List<MacroGroup> groups, boolean isModified)
  {
    if (baseDirectory == null || CollectionUtil.isEmpty(groups)) return;

    if (baseDirectory.exists() && !baseDirectory.isDirectory())
    {
      throw new IllegalArgumentException("The provided file " + baseDirectory + " is not a directory!");
    }
    if (!baseDirectory.exists())
    {
      baseDirectory.mkdirs();
    }

    int numGroups = groups.size();
    if (numGroups > 1)
    {
      // If macros from a single directory were loaded, but new groups
      // were added later, we need to delete the macros in the root direcctory.
      // That way, each group is in a separate directory and nothing is stored
      // in the base directory (to avoid confusion).
      FileUtil.deleteDirectoryContent(baseDirectory);
    }

    DirectorySaveStrategy saveStrategy = Settings.getInstance().getDirectoryBaseMacroStorageSaveStrategy();
    Set<File> activeGroupDirs = new HashSet<>();

    for (MacroGroup group : groups)
    {
      String groupName = group.getName();
      WbFile groupDir = numGroups == 1 ? new WbFile(baseDirectory) : new WbFile(baseDirectory, StringUtil.makeFilename(groupName, false));

      switch (saveStrategy)
      {
        case Flush:
          FileUtil.deleteDirectoryContent(groupDir);
          break;
        case Merge:
          if (group.getTotalSize() == 0)
          {
            File infoFile = new File(groupDir, GROUP_INFO_FILE);
            if (infoFile.exists())
            {
              infoFile.delete();
            }
          }
          break;
      }

      saveGroup(groupDir, group, saveStrategy);
      activeGroupDirs.add(groupDir);
    }

    // Now remove any directory that represents a group that is no longer there.
    for (File f : baseDirectory.listFiles())
    {
      if (f.isDirectory() && !activeGroupDirs.contains(f))
      {
        LogMgr.logDebug(new CallerInfo(){}, "Deleting no longer used macro group directory: " + WbFile.getPathForLogging(f));
        FileUtil.deleteDirectoryContent(f);
        f.delete();
      }
    }
  }

  @Override
  public void reload(List<MacroGroup> groups, File changed)
  {
    if (CollectionUtil.isEmpty(groups) || changed == null) return;
    if (changed.getName().equals(GROUP_INFO_FILE))
    {
      for (MacroGroup group : groups)
      {
        if (group.isGroupForInfoFile(changed))
        {
          readGroupInfo(group, changed);
        }
      }
    }
    else
    {
      reloadSingleMacro(groups, changed);
    }
  }

  private void reloadSingleMacro(List<MacroGroup> groups, File changed)
  {
    MacroDefinition macro = findMacroForFile(groups, changed);
    if (macro != null)
    {
      try
      {
        String content = FileUtil.readFile(changed, "UTF-8");
        macro.setText(content);
        LogMgr.logDebug(new CallerInfo(){}, "Reloaded definition for macro: " + macro.getName() + " from: " + WbFile.getPathForLogging(changed));
      }
      catch (Exception io)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not reload macro file: " + WbFile.getPathForLogging(changed), io);
      }
    }
    else
    {
      LogMgr.logWarning(new CallerInfo(){}, "No macro definition found for modified file: " + WbFile.getPathForLogging(changed));
    }
  }

  private MacroDefinition findMacroForFile(List<MacroGroup> groups, File toFind)
  {
    for (MacroGroup group : groups)
    {
      List<MacroDefinition> macros = group.getAllMacros();
      for (MacroDefinition macro : macros)
      {
        if (macro.getOriginalSourceFile() != null && toFind.equals(macro.getOriginalSourceFile()))
        {
          return macro;
        }
      }
    }
    return null;
  }

  private void saveGroup(File groupDir, MacroGroup group, DirectorySaveStrategy strategy)
  {
    if (!groupDir.exists())
    {
      groupDir.mkdirs();
    }

    for (MacroDefinition macro : group.getAllMacros())
    {
      saveMacro(groupDir, macro);
    }
    writeGroupInfo(groupDir, group);

    if (strategy == DirectorySaveStrategy.Merge)
    {
      List<MacroDefinition> deleted = group.getDeletedMacros();
      for (MacroDefinition def : deleted)
      {
        File toDelete = def.getOriginalSourceFile();
        if (toDelete == null)
        {
          toDelete = new File(groupDir, getFilename(def));
        }
        if (toDelete.exists())
        {
          toDelete.delete();
        }
      }
    }
  }

  private void writeGroupInfo(File directory, MacroGroup group)
  {
    List<MacroDefinition> allMacros = group.getAllMacros();

    WbProperties props = new WbProperties(2);
    props.setProperty(GROUP_PREFIX + PROP_INCLUDE_IN_MENU, group.isVisibleInMenu());
    props.setProperty(GROUP_PREFIX + PROP_INCLUDE_IN_POPUP, group.isVisibleInPopup());
    props.setProperty(GROUP_PREFIX + PROP_TOOLTIP, group.getTooltip());
    props.setProperty(GROUP_PREFIX + PROP_NAME, group.getName());
    props.setProperty(GROUP_PREFIX + PROP_COUNT, allMacros.size());

    for (MacroDefinition def : allMacros)
    {
      String key = makeKey(def);
      props.setProperty(key + PROP_INCLUDE_IN_MENU, def.isVisibleInMenu());
      props.setProperty(key + PROP_INCLUDE_IN_POPUP, def.isVisibleInPopup());
      props.setProperty(key + PROP_APPEND, def.isAppendResult());
      props.setProperty(key + PROP_EXPAND, def.getExpandWhileTyping());
      props.setProperty(key + PROP_DB_TREE, def.isDbTreeMacro());
      props.setProperty(key + PROP_NAME, def.getName());
      props.setProperty(key + PROP_TOOLTIP, def.getTooltip());
      props.setProperty(key + PROP_SORT_ORDER, def.getSortOrder());
      StoreableKeyStroke shortcut = def.getShortcut();
      if (shortcut != null)
      {
        props.setProperty(key + PROP_SHORTCUT, shortcut.toPropertiesString());
      }
    }

    WbFile info = new WbFile(directory, GROUP_INFO_FILE);
    try
    {
      props.saveToFile(info);
    }
    catch (IOException io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not write macro info file: " + info.getFullpathForLogging(), io);
    }
  }

  private void applyGroupInfo(File directory, MacroGroup group)
  {
    if (directory == null || group == null) return;

    WbFile infoFile = new WbFile(directory, GROUP_INFO_FILE);
    if (!infoFile.exists())
    {
      // Sort the macros by (file) name if no attributes are available
      group.sortByName();
      return;
    }
    readGroupInfo(group, infoFile);
  }

  private void readGroupInfo(MacroGroup group, File infoFile)
  {
    WbProperties props = new WbProperties(0);
    try
    {
      props.loadTextFile(infoFile, "UTF-8");
      group.setTooltip(props.getProperty(PROP_TOOLTIP));
      group.setVisibleInMenu(props.getBoolProperty(GROUP_PREFIX + PROP_INCLUDE_IN_MENU, group.isVisibleInMenu()));
      group.setVisibleInPopup(props.getBoolProperty(GROUP_PREFIX + PROP_INCLUDE_IN_POPUP, group.isVisibleInPopup()));
      group.setName(props.getProperty(GROUP_PREFIX + PROP_NAME, group.getName()));
      group.setSortOrder(props.getIntProperty(GROUP_PREFIX + PROP_SORT_ORDER, group.getSortOrder()));

      for (MacroDefinition def : group.getAllMacros())
      {
        String key = makeKey(def);
        def.setVisibleInMenu(props.getBoolProperty(key + PROP_INCLUDE_IN_MENU, def.isVisibleInMenu()));
        def.setVisibleInPopup(props.getBoolProperty(key + PROP_INCLUDE_IN_POPUP, def.isVisibleInPopup()));
        def.setAppendResult(props.getBoolProperty(key + PROP_APPEND, def.isAppendResult()));
        def.setExpandWhileTyping(props.getBoolProperty(key + PROP_EXPAND, def.getExpandWhileTyping()));
        def.setDbTreeMacro(props.getBoolProperty(key + PROP_DB_TREE, def.isDbTreeMacro()));
        def.setName(props.getProperty(key + PROP_NAME, def.getName()));
        def.setTooltip(props.getProperty(key + PROP_TOOLTIP, def.getTooltip()));
        def.setSortOrder(props.getIntProperty(key + PROP_SORT_ORDER, def.getSortOrder()));
        StoreableKeyStroke keystroke = StoreableKeyStroke.fromPropertiesString(props.getProperty(key + PROP_SHORTCUT));
        def.setShortcut(keystroke);
      }
      group.setGroupInfoFile(infoFile);
    }
    catch (Exception io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read info file for macro group " + group.getName() +
        " in directory: " + WbFile.getPathForLogging(infoFile.getParent()), io);
    }
  }

  private void saveMacro(File baseDirectory, MacroDefinition macro)
  {
    if (baseDirectory == null || macro == null) return;
    File original = macro.getOriginalSourceFile();
    WbFile target;
    if (original != null)
    {
      target = new WbFile(baseDirectory, original.getName());
    }
    else
    {
      target = new WbFile(baseDirectory, StringUtil.makeFilename(macro.getName(), false) + ".sql");
    }

    try
    {
      FileUtil.writeString(target, macro.getText());
    }
    catch (Exception io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not write macro file: " + target.getFullpathForLogging(), io);
    }
  }

  private MacroGroup loadMacrosFromDirectory(WbFile source, int groupIndex)
  {
    MacroGroup result = new MacroGroup();
    result.setName(source.getName());
    // will be overwritten if a group info file exists
    result.setSortOrder(groupIndex);
    File[] files = source.listFiles((File f) -> f != null && f.isFile() && f.getName().toLowerCase().endsWith(".sql"));
    for (File f : files)
    {
      WbFile wb = new WbFile(f);
      try
      {
        String content = FileUtil.readFile(f, "UTF-8");
        MacroDefinition def = new MacroDefinition(wb.getFileName(), content);
        def.setOriginalSourceFile(wb);
        result.addMacro(def);
      }
      catch (Exception io)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not read macro file: " + wb.getFullpathForLogging(), io);
      }
    }
    applyGroupInfo(source, result);
    return result;
  }

  private String getFilename(MacroDefinition macro)
  {
    if (macro.getOriginalSourceFile() != null)
    {
      return macro.getOriginalSourceFile().getName();
    }
    return StringUtil.makeFilename(macro.getName(), false) + ".sql";
  }

  private String makeKey(MacroDefinition macro)
  {
    String name;
    if (macro.getOriginalSourceFile() != null)
    {
      name = macro.getOriginalSourceFile().getFileName();
    }
    else
    {
      name = StringUtil.makeFilename(macro.getName(), false);
    }
    return "macro." + name.replace(' ', '_').replace('=', '-') + ".";
  }

}
