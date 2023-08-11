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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * A list of macros combined into a group.
 * <br/>
 * A MacroGroup defines a sort order of each element which is maintained through the GUI.
 * <br/>
 * Groups can be hidden from the menu.
 *
 * @author Thomas Kellerer
 */
public class MacroGroup
  implements Sortable
{
  private String name;
  private final List<MacroDefinition> macros = new ArrayList<>();
  private final List<MacroDefinition> filtered = new ArrayList<>();
  private final List<MacroDefinition> deleted = new ArrayList<>();
  private int sortOrder;
  private String tooltip;
  private boolean modified = false;
  private boolean showInMenu = true;
  private boolean showInPopup = true;
  private File groupInfoFile;

  public MacroGroup()
  {
  }

  public MacroGroup(String groupName)
  {
    this.name = groupName;
  }

  public String getTooltip()
  {
    return tooltip;
  }

  public void setTooltip(String tip)
  {
    modified = modified || StringUtil.stringsAreNotEqual(tooltip, tip);
    tooltip = StringUtil.trimToNull(tip);
  }

  public boolean isVisibleInMenu()
  {
    return showInMenu;
  }

  public void setVisibleInMenu(boolean flag)
  {
    this.modified = modified || flag != showInMenu;
    this.showInMenu = flag;
  }

  public boolean isVisibleInPopup()
  {
    return showInPopup;
  }

  public void setVisibleInPopup(boolean flag)
  {
    this.modified = modified || flag != showInPopup;
    this.showInPopup = flag;
  }

  @Override
  public int getSortOrder()
  {
    return sortOrder;
  }

  @Override
  public void setSortOrder(int order)
  {
    this.modified = modified || sortOrder != order;
    this.sortOrder = order;
  }

  @Override
  public String toString()
  {
    return name;
  }

  public String getName()
  {
    return name;
  }

  public void setName(String macroName)
  {
    modified = modified || StringUtil.stringsAreNotEqual(name, macroName);
    name = macroName;
  }

  public synchronized void addMacro(MacroDefinition macro)
  {
    macros.add(macro);
    applySort();
    modified = true;
  }

  /**
   * Sorts the macros by name.
   *
   * After the macros are sorted, the {@link MacroDefinition#setSortOrder(int)} is
   * set to the new position of the macro.
   */
  public synchronized void sortByName()
  {
    Comparator<MacroDefinition> comp = (MacroDefinition o1, MacroDefinition o2) ->
    {
      if (o1 == null && o2 == null) return 0;
      if (o1 == null) return -1;
      if (o2 == null) return 1;
      return o1.getName().compareToIgnoreCase(o2.getName());
    };

    Collections.sort(macros, comp);
    for (int i=0; i < macros.size(); i++)
    {
      macros.get(i).setSortOrder(i);
    }
  }

  /**
   * Sort all macros according to their {@link MacroDefinition#getSortOrder()}.
   *
   * After the macros are sorted, their sort order is updated to be gapless.
   */
  public synchronized void applySort()
  {
    applySort(macros);
  }

  private void applySort(List<MacroDefinition> toSort)
  {
    Collections.sort(toSort, new Sorter());
    for (int i=0; i < toSort.size(); i++)
    {
      toSort.get(i).setSortOrder(i);
    }
  }

  /**
   * Returns those macros that are set to "display in menu" (i.e. where MacroDefinition.isVisibleInMenu()
   * returns true.
   * <br/>
   * This ignores the isVisibleInMenu() setting of this group.
   *
   * @see #getVisibleMacroSize()
   * @see MacroDefinition#isVisibleInMenu() ()
   */
  public synchronized List<MacroDefinition> getVisibleMacros()
  {
    List<MacroDefinition> result = new ArrayList<>(macros.size());
    for (MacroDefinition macro : macros)
    {
      if (macro.isVisibleInMenu() && !macro.isDbTreeMacro())
      {
        result.add(macro);
      }
    }
    return result;
  }

  /**
   * Returns those macros that should be shown in the "Macro Popup" window.
   * returns true.
   * <br/>
   * This ignores the isVisibleInMenu() setting of this group.
   *
   * @see #getVisibleMacroSize()
   * @see MacroDefinition#isVisibleInPopup()
   */
  public synchronized List<MacroDefinition> getMacrosForPopup()
  {
    List<MacroDefinition> result = new ArrayList<>(macros.size());
    for (MacroDefinition macro : macros)
    {
      if (macro.isVisibleInPopup() && !macro.isDbTreeMacro())
      {
        result.add(macro);
      }
    }
    return result;
  }

  /**
   * Sets the list of macros for this group.
   *
   * This method is only here to make the class serializable for the XMLEncoder and should
   * not be used directly.
   *
   * @param newMacros
   */
  public synchronized void setMacros(List<MacroDefinition> newMacros)
  {
    filtered.clear();
    macros.clear();
    macros.addAll(newMacros);
    applySort();
    modified = false;
  }

  /**
   * Returns currently visible macros in this group.
   *
   * Filtered macros are not returned.
   *
   * @see #applyFilter(String)
   */
  public synchronized List<MacroDefinition> getMacros()
  {
    return macros;
  }

  /**
   * Returns all macros in this group including filtered macros.
   */
  public synchronized List<MacroDefinition> getAllMacros()
  {
    List<MacroDefinition> allMacros = new ArrayList<>();
    allMacros.addAll(macros);
    allMacros.addAll(filtered);
    applySort(allMacros);
    return allMacros;
  }

  public synchronized List<MacroDefinition> getDeletedMacros()
  {
    return new ArrayList<>(deleted);
  }

  public synchronized void removeMacro(MacroDefinition macro)
  {
    if (macros.remove(macro))
    {
      deleted.add(macro);
      modified = true;
    }
  }

  public boolean isGroupForInfoFile(File f)
  {
    if (this.groupInfoFile == null || f == null) return false;
    return this.groupInfoFile.equals(f);
  }

  public void setGroupInfoFile(File groupInfoFile)
  {
    this.groupInfoFile = groupInfoFile;
  }

  /**
   * Creates a stateful deep copy of this group.
   *
   * For each macro definition that is part of this group, a copy
   * is created and added to the list of macros of the copy.
   *
   * The copy will have the same modified state as the source
   *
   * @return a deep copy of this group
   * @see MacroDefinition#createCopy()
   */
  public MacroGroup createCopy()
  {
    MacroGroup copy = new MacroGroup();
    copy.name = this.name;
    copy.sortOrder = this.sortOrder;
    copy.showInMenu = this.showInMenu;
    copy.showInPopup = this.showInPopup;
    copy.tooltip = this.tooltip;
    copy.modified = this.modified;
    for (MacroDefinition def : macros)
    {
      copy.macros.add(def.createCopy());
    }
    for (MacroDefinition def : filtered)
    {
      copy.filtered.add(def.createCopy());
    }
    for (MacroDefinition def : deleted)
    {
      copy.deleted.add(def.createCopy());
    }
    applySort(copy.macros);
    return copy;
  }

  /**
   * Checks if this group has been modified.
   *
   * Returns true if any attribute of this group has been changed
   * or if any MacroDefinition in this group has been changed.
   *
   * @return true, if this group or any Macro has been modified
   * @see MacroDefinition#isModified()
   */
  public boolean isModified()
  {
    if (modified) return true;
    for (MacroDefinition macro : macros)
    {
      if (macro.isModified()) return true;
    }
    return false;
  }

  /**
   * Resets the internal modified flag and on all macros that are in this group.
   *
   * After a call to this method, isModified() will return false;
   */
  public void resetModified()
  {
    modified = false;
    for (MacroDefinition macro : macros)
    {
      macro.resetModified();
    }
    deleted.clear();
  }

  /**
   * Returns the number of macros in this groups that should be displayed in the menu.
   *
   * This might return a non-zero count even if isVisibleInMenu() returns false!
   *
   * @see #getVisibleMacros()
   */
  public int getVisibleMacroSize()
  {
    int size = 0;
    for (MacroDefinition def : macros)
    {
      if (def.isVisibleInMenu()) size ++;
    }
    return size;
  }

  public int getTotalSize()
  {
    return macros.size() + filtered.size();
  }

  public int getSize()
  {
    return macros.size();
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj instanceof MacroGroup)
    {
      return StringUtil.equalStringIgnoreCase(this.name, ((MacroGroup)obj).name);
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    int hash = 7;
    hash = 19 * hash + (this.name != null ? this.name.toLowerCase().hashCode() : 0);
    return hash;
  }

  public void resetFilter()
  {
    macros.addAll(filtered);
    filtered.clear();
    applySort();
  }

  public boolean isFiltered()
  {
    return CollectionUtil.isNonEmpty(filtered);
  }

  public void applyFilter(String filter)
  {
    resetFilter();

    if (StringUtil.isBlank(filter)) return;

    filter = filter.toLowerCase();
    Iterator<MacroDefinition> itr = macros.iterator();
    while (itr.hasNext())
    {
      MacroDefinition macro = itr.next();
      if (!macro.getName().toLowerCase().contains(filter))
      {
        filtered.add(macro);
        itr.remove();
      }
    }
  }
}
