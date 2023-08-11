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
package workbench.sql.macros;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import workbench.interfaces.MacroChangeListener;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;
import workbench.resource.ShortcutManager;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.FileWatcherFactory;
import workbench.util.WbFile;

/**
 * Manages loading and saving of macros.
 *
 * <p>Two storage formats are supported:</p>
 * <ul>
 *  <li>a single XML file, implemented through {@link XmlMacroPersistence}</li>
 *  <li>SQL scripts stored in a directory, implemented through {@link DirectoryMacroPersistence}</li>
 * </ul>
 *
 * @author Thomas Kellerer
 */
public class MacroStorage
  implements PropertyChangeListener
{
  private final Object lock = new Object();
  private final Map<String, MacroDefinition> allMacros = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
  private final List<MacroGroup> groups = new ArrayList<>();

  private boolean modified = false;
  private String currentFilter;
  private final List<MacroChangeListener> changeListeners = new ArrayList<>(1);
  private WbFile sourceFile;

  public MacroStorage()
  {
  }

  public MacroStorage(WbFile toLoad)
  {
    this();
    sourceFile = toLoad;
    loadMacros();
  }

  public synchronized void loadNewFile(WbFile toLoad)
  {
    if (toLoad == null) return;

    if (sourceFile == null || !sourceFile.equals(toLoad))
    {
      FileWatcherFactory.getInstance().removeChangeListener(sourceFile, this);
      sourceFile = toLoad;
      loadMacros();
      fireMacroListChanged();
    }
  }

  public synchronized MacroDefinition getMacro(String key)
  {
    return allMacros.get(key);
  }

  public File getCurrentFile()
  {
    return sourceFile;
  }

  public String getCurrentMacroFilename()
  {
    if (sourceFile == null) return null;
    return sourceFile.getFullPath();
  }

  public void removeGroup(MacroGroup group)
  {
    groups.remove(group);
    modified = true;
  }

  public synchronized int getSize()
  {
    int size = 0;
    for (MacroGroup group : groups)
    {
      size += group.getSize();
    }
    return size;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    File f = (File)evt.getNewValue();
    LogMgr.logDebug(new CallerInfo(){}, "MacroStorage file changed: " + f.getAbsolutePath());
    if (Settings.getInstance().getWatchMacroFiles())
    {
      synchronized (this.lock)
      {
        MacroPersistence persistence = createPersistence();
        persistence.reload(groups, f);
        fireMacroListChanged();
      }
    }
  }

  public void addChangeListener(MacroChangeListener aListener)
  {
    synchronized (lock)
    {
      this.changeListeners.add(aListener);
    }
  }

  public void removeChangeListener(MacroChangeListener aListener)
  {
    synchronized (lock)
    {
      this.changeListeners.remove(aListener);
    }
  }

  public synchronized void copyFrom(MacroStorage source)
  {
    synchronized (lock)
    {
      this.allMacros.clear();
      this.groups.clear();
      for (MacroGroup group : source.groups)
      {
        groups.add(group.createCopy());
      }
      currentFilter = null;
      modified = source.isModified();
      updateMap();
    }
    fireMacroListChanged();
  }

  public MacroStorage createCopy()
  {
    MacroStorage copy = new MacroStorage();
    for (MacroGroup group : groups)
    {
      copy.groups.add(group.createCopy());
    }
    copy.updateMap(false);
    copy.resetModified();
    return copy;
  }

  /**
   * Saves the macros to a new file or directory.
   *
   * The contents of the file they were loaded from, will be unaffected
   * (effectively creating a copy of the current file).
   *
   * After saving the macros to a new file, getCurrentFile() will return the new file name.
   *
   * This method can also be used to convert between the XML storage and directory based storage.
   *
   * @param file
   * @see #saveMacros()
   */
  public void saveMacros(WbFile file)
  {
    FileWatcherFactory.getInstance().removeChangeListener(sourceFile, this);
    sourceFile = file;
    saveMacros();
  }

  /**
   * Saves the macros to the file they were loaded from.
   *
   * This will also reset the modified flag.
   *
   * @see #isModified()
   */
  public void saveMacros()
  {
    if (sourceFile == null) return;

    String savedFilter = currentFilter;
    FileWatcherFactory.getInstance().suspendWatcher(sourceFile);

    MacroPersistence persistence = createPersistence();
    synchronized (lock)
    {
      if (currentFilter != null)
      {
        resetFilter();
      }

      persistence.saveMacros(sourceFile, getGroups(), modified);

      if (savedFilter != null)
      {
        applyFilter(savedFilter);
      }
      resetModified();
    }
    FileWatcherFactory.getInstance().continueWatcher(sourceFile);
  }

  private boolean sourceIsFile()
  {
    if (sourceFile == null) return false;

    if (sourceFile.exists())
    {
      return sourceFile.isFile();
    }
    return "xml".equalsIgnoreCase(sourceFile.getExtension());
  }

  private MacroPersistence createPersistence()
  {
    if (sourceIsFile())
    {
      return new XmlMacroPersistence();
    }
    return new DirectoryMacroPersistence();
  }

  private void fireMacroListChanged()
  {
    synchronized (lock)
    {
      for (MacroChangeListener listener : this.changeListeners)
      {
        if (listener != null)
        {
          listener.macroListChanged();
        }
      }
    }
  }

  public void sortGroupsByName()
  {
    synchronized (lock)
    {
      Comparator<MacroGroup> comp = (MacroGroup o1, MacroGroup o2) ->
      {
        if (o1 == null && o2 == null) return 0;
        if (o1 == null) return -1;
        if (o2 == null) return 1;
        return o1.getName().compareToIgnoreCase(o2.getName());
      };
      groups.sort(comp);
      modified = true;
    }

  }
  public void applySort()
  {
    synchronized (lock)
    {
      groups.sort(new Sorter());
      for (int i=0; i < groups.size(); i++)
      {
        groups.get(i).setSortOrder(i);
        groups.get(i).applySort();
      }
      modified = true;
    }
  }

  private void updateMap()
  {
    updateMap(true);
  }

  private void updateMap(boolean fireEvent)
  {
    boolean shortcutChanged = false;
    allMacros.clear();
    for (MacroGroup group : groups)
    {
      Collection<MacroDefinition> macros = group.getMacros();
      for (MacroDefinition macro : macros)
      {
        allMacros.put(macro.getName(), macro);
        shortcutChanged = shortcutChanged || macro.isShortcutChanged();
      }
    }
    if (shortcutChanged && fireEvent)
    {
      ShortcutManager.getInstance().fireShortcutsChanged();
    }
  }

  /**
   * Loads the macros from the previously set source.
   *
   * @see XmlMacroPersistence
   * @see DirectoryMacroPersistence
   * @see #createPersistence()
   */
  private void loadMacros()
  {
    if (sourceFile == null)
    {
      LogMgr.logDebug(new CallerInfo(){}, "No macro file specified. No Macros loaded");
      return;
    }

    if (!sourceFile.exists())
    {
      LogMgr.logDebug(new CallerInfo(){}, "Macro file " + sourceFile.getFullpathForLogging() + " not found. No Macros loaded");
      return;
    }

    MacroPersistence persistence = createPersistence();
    try
    {
      synchronized (lock)
      {
        List<MacroGroup> macros = persistence.loadMacros(sourceFile);
        groups.clear();
        groups.addAll(macros);
        applySort();
        updateMap();
        resetModified();
        LogMgr.logDebug(new CallerInfo(){}, "Loaded " + getSize() + " macros from " + sourceFile.getAbsolutePath());
      }
      if (Settings.getInstance().getWatchMacroFiles())
      {
        FileWatcherFactory.getInstance().registerWatcher(sourceFile, this);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error loading macro file", e);
    }

  }

  public synchronized void moveMacro(MacroDefinition macro, MacroGroup newGroup)
  {
    moveMacro(macro, newGroup, true);
  }

  private void moveMacro(MacroDefinition macro, MacroGroup newGroup, boolean notifyListeners)
  {
    MacroGroup currentGroup = findMacroGroup(macro.getName());

    if (currentGroup != null && currentGroup.equals(newGroup)) return;
    if (currentGroup != null)
    {
      currentGroup.removeMacro(macro);
    }
    newGroup.addMacro(macro);

    this.modified = true;
    if (notifyListeners)
    {
      this.fireMacroListChanged();
    }
  }

  public MacroGroup findMacroGroup(String macroName)
  {
    synchronized (lock)
    {
      for (MacroGroup group : groups)
      {
        List<MacroDefinition> macros = group.getMacros();
        for (MacroDefinition macro : macros)
        {
          if (macro.getName().equalsIgnoreCase(macroName))
          {
            return group;
          }
        }
      }
    }
    return null;
  }

  public void addMacro(MacroGroup group, MacroDefinition macro)
  {
    synchronized (lock)
    {
      allMacros.put(macro.getName(), macro);
      if (!containsGroup(group.getName()))
      {
        addGroup(group);
      }
      moveMacro(macro, group, false);
      macro.setSortOrder(group.getSize() + 1);
      this.modified = true;
    }
    this.fireMacroListChanged();
  }

  public void removeMacro(MacroDefinition toDelete)
  {
    synchronized (lock)
    {
      MacroDefinition macro = allMacros.remove(toDelete.getName());
      for (MacroGroup group : groups)
      {
        group.removeMacro(macro);
      }
      this.modified = true;
    }
    this.fireMacroListChanged();
  }

  public void addMacro(String groupName, String key, String text)
  {
    MacroDefinition def = new MacroDefinition(key, text);
    synchronized (lock)
    {
      boolean added = false;
      if (groupName != null)
      {
        for (MacroGroup group : groups)
        {
          if (group.getName().equalsIgnoreCase(groupName))
          {
            group.addMacro(def);
            added = true;
          }
        }
        if (!added)
        {
          MacroGroup group = new MacroGroup(groupName);
          group.addMacro(def);
          groups.add(group);
        }
      }
      else
      {
        groups.get(0).addMacro(def);
      }
      updateMap();
      this.modified = true;
    }
    this.fireMacroListChanged();
  }

  public boolean containsGroup(String groupName)
  {
    synchronized (lock)
    {
      for (MacroGroup group : groups)
      {
        if (group.getName().equalsIgnoreCase(groupName)) return true;
      }
      return false;
    }
  }

  public void addGroup(MacroGroup group)
  {
    synchronized (lock)
    {
      if (!groups.contains(group))
      {
        int newIndex = 1;
        if (groups.size() > 0)
        {
          newIndex = groups.get(groups.size() - 1).getSortOrder() + 1;
        }
        group.setSortOrder(newIndex);
        groups.add(group);
        applySort();
        modified = true;
      }
    }
  }

  /**
   * Return all visible macro groups.
   *
   * Returns only groups that have isVisibleInMenu() == true and
   * contain at least one macro with isVisibleInMenu() == true
   *
   * @see MacroGroup#getVisibleMacroSize()
   */
  public List<MacroGroup> getVisibleGroups()
  {
    List<MacroGroup> result = new ArrayList<>(groups.size());
    synchronized (lock)
    {
      for (MacroGroup group : groups)
      {
        if (group.isVisibleInMenu() && group.getVisibleMacroSize() > 0)
        {
          result.add(group);
        }
      }
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Returns all macros to be used in the DbTree.
   *
   * @see MacroDefinition#isDbTreeMacro()
   */
  public List<MacroDefinition> getDbTreeMacros()
  {
    List<MacroDefinition> result = allMacros.values().
      stream().
      filter(m -> m.isDbTreeMacro()).
      collect(Collectors.toList());
    result.sort(new Sorter());

    return result;
  }

  public List<MacroGroup> getGroups()
  {
    synchronized (lock)
    {
      return Collections.unmodifiableList(groups);
    }
  }

  public void resetModified()
  {
    synchronized (lock)
    {
      this.modified = false;
      for (MacroGroup group : groups)
      {
        group.resetModified();
      }
    }
  }

  public boolean isModified()
  {
    synchronized (lock)
    {
      if (this.modified) return true;
      for (MacroGroup group : groups)
      {
        if (group.isModified()) return true;
      }
    }
    return false;
  }

  public void clearAll()
  {
    synchronized (lock)
    {
      this.resetFilter();
      this.allMacros.clear();
      this.groups.clear();
      this.modified = true;
    }
    this.fireMacroListChanged();
  }

  @Override
  public String toString()
  {
    return allMacros.size() + " macros";
  }

  public void resetFilter()
  {
    for (MacroGroup group : groups)
    {
      group.resetFilter();
    }
    this.currentFilter = null;
    updateMap();
  }

  public void applyFilter(String filter)
  {
    this.currentFilter = filter;
    for (MacroGroup group : groups)
    {
      group.applyFilter(filter);
    }
    updateMap();
  }

  public boolean isFiltered()
  {
    return currentFilter != null;
  }

  @Override
  public int hashCode()
  {
    return sourceFile.hashCode();
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final MacroStorage other = (MacroStorage)obj;
    return Objects.equals(this.sourceFile, other.sourceFile);
  }


}
