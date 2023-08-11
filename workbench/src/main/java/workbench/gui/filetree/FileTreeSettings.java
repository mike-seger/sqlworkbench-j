/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer, Matthias Melzner
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
package workbench.gui.filetree;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import workbench.resource.Settings;

import workbench.gui.dbobjects.objecttree.ComponentPosition;

import workbench.util.CollectionUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;

/**
 * @author Matthias Melzner
 * @author Thomas Kellerer
 */
public class FileTreeSettings
{
  public static final String SETTINGS_PREFIX = "workbench.gui.filetree.";
  public static final String DIR_PREFIX = SETTINGS_PREFIX + ".default.dir";
  public static final String EXCLUDED_FILES_PROPERTY = SETTINGS_PREFIX + ".exclude.files";
  public static final String EXCLUDED_EXT_PROPERTY = SETTINGS_PREFIX + ".exclude.extensions";
  public static final String PROP_VISIBLE = "tree.visible";

  public static void setDefaultDirectoryNames(List<String> dirs)
  {
    Settings settings = Settings.getInstance();

    List<String> keys = settings.getKeysWithPrefix(DIR_PREFIX);
    for (String key : keys)
    {
      settings.setProperty(key, null);
    }

    if (CollectionUtil.isEmpty(dirs)) return;

    int index = 1;
    for (String dir : dirs)
    {
      if (StringUtil.isNotBlank(dir))
      {
        settings.setProperty(DIR_PREFIX + "." + index, dir);
        index++;
      }
    }
  }

  public static List<String> getDefaultDirectoryNames()
  {
    Settings settings = Settings.getInstance();
    List<String> keys = settings.getKeysWithPrefix(DIR_PREFIX);
    List<String> dirs = new ArrayList<>(keys.size());
    for (String key : keys)
    {
      String path = settings.getProperty(key, null);
      if (StringUtil.isNotBlank(path))
      {
        dirs.add(path);
      }
    }
    return dirs;
  }

  public static List<File> getDefaultDirectories()
  {
    List<File> dirs = new ArrayList<>();
    List<String> defaultDirectories = getDefaultDirectoryNames();
    for (String path : defaultDirectories)
    {
      if (StringUtil.isBlank(path)) continue;

      File f = FileUtil.getCanonicalFile(new File(path));
      if (f.exists() && !dirs.contains(f))
      {
        dirs.add(f);
      }
    }
    return dirs;
  }

  public static String getExcludedFiles()
  {
    return Settings.getInstance().getProperty(EXCLUDED_FILES_PROPERTY, null);
  }

  public static void setExcludedFiles(String list)
  {
    Settings.getInstance().setProperty(EXCLUDED_FILES_PROPERTY, StringUtil.trimToNull(list));
  }

  public static String getExcludedExtensions()
  {
    return Settings.getInstance().getProperty(EXCLUDED_EXT_PROPERTY, null);
  }

  public static void setExcludedExtensions(String list)
  {
    Settings.getInstance().setProperty(EXCLUDED_EXT_PROPERTY, StringUtil.trimToNull(list));
  }

  public static List<String> getFilesToExclude()
  {
    List<String> names = Settings.getInstance().getListProperty(EXCLUDED_FILES_PROPERTY, false, null);
    return names.stream().
            map(s -> StringUtil.trimToNull(s)).
            filter(s -> s != null).
            collect(Collectors.toList());
  }

  public static List<String> getExtensionsToExclude()
  {
    List<String> extensions = Settings.getInstance().getListProperty(EXCLUDED_EXT_PROPERTY, false, null);
    return extensions.
           stream().
           map(s -> StringUtil.trimToNull(s)).
           map(s -> StringUtil.removeLeading(s, '.')).
           filter(s -> s != null).
           collect(Collectors.toList());
  }

  public static boolean getUseSystemIcons()
  {
    // Using system icons in the file tree is much slower than fixed ones.
    return Settings.getInstance().getBoolProperty(SETTINGS_PREFIX + ".use.system.icons", false);
  }

  public static void setComponentPosition(ComponentPosition position)
  {
    if (position == null) return;
    Settings.getInstance().setProperty(SETTINGS_PREFIX + "position", position.name());
  }

  public static ComponentPosition getComponentPosition()
  {
    String pos = Settings.getInstance().getProperty(SETTINGS_PREFIX + "position", ComponentPosition.left.name());
    try
    {
      return ComponentPosition.valueOf(pos);
    }
    catch (Throwable th)
    {
      return ComponentPosition.left;
    }
  }

  public static void setClickOption(FileOpenMode option)
  {
    Settings.getInstance().setProperty(SETTINGS_PREFIX + "clickOption", option.name());
  }

  public static FileOpenMode getClickOption()
  {
    return Settings.getInstance().getEnumProperty(SETTINGS_PREFIX + "clickOption", FileOpenMode.sameTab);
  }
}
