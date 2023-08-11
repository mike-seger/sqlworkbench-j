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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.KeyStroke;

import workbench.interfaces.MacroChangeListener;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;
import workbench.resource.StoreableKeyStroke;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.WbFile;

/**
 * A singleton class to load and save SQL macros (aliases)
 *
 * @author Thomas Kellerer
 */
public class MacroManager
{
  public static final int DEFAULT_STORAGE = Integer.MIN_VALUE;

  private final Map<Integer, MacroStorage> macroClients = new HashMap<>();

  /**
   * Thread safe singleton instance.
   */
  private static class InstanceHolder
  {
    protected static MacroManager instance = new MacroManager();
  }

  private MacroManager()
  {
    WbFile file = getDefaultMacroFile();
    long start = System.currentTimeMillis();
    MacroStorage storage = new MacroStorage(file);
    long duration = System.currentTimeMillis() - start;
    macroClients.put(DEFAULT_STORAGE, storage);
    LogMgr.logDebug(new CallerInfo(){}, "Loading default macros took " + duration + "ms");
  }

  public static MacroManager getInstance()
  {
    return InstanceHolder.instance;
  }

  public static WbFile getDefaultMacroFile()
  {
    return new WbFile(Settings.getInstance().getMacroStorage());
  }

  public synchronized void save()
  {
    for (MacroStorage storage : macroClients.values())
    {
      storage.saveMacros();
    }
  }

  public void saveAs(int clientId, WbFile macroFile)
  {
    MacroStorage storage = macroClients.get(clientId);
    if (storage != null)
    {
      if (isShared(clientId, macroFile))
      {
        // if a different client is using the same macro file
        // we should create a copy under the new name
        MacroStorage copy = storage.createCopy();
        copy.saveMacros(macroFile);
        macroClients.put(clientId, copy);
      }
      else
      {
        storage.saveMacros(macroFile);
      }
    }
  }

  /**
   * Save the macro definitions currently used by the given client.
   *
   * @param clientId the client ID
   */
  public void save(int clientId)
  {
    MacroStorage storage = macroClients.get(clientId);
    if (storage != null && storage.isModified())
    {
      storage.saveMacros();
    }
  }

  public void loadDefaultMacros(int clientId)
  {
    loadMacros(clientId, getDefaultMacroFile());
  }

  public synchronized void loadMacros(int clientId, WbFile macroFile)
  {
    if (!macroFile.isAbsolute())
    {
      macroFile = new WbFile(Settings.getInstance().getMacroBaseDirectory());
    }

    MacroStorage storage = findLoadedMacros(macroFile);
    if (storage == null)
    {
      storage = new MacroStorage(macroFile);
      macroClients.put(clientId, storage);
    }
    LogMgr.logDebug(new CallerInfo(){}, "Loaded " + storage.getSize() + " macros from file " + macroFile.getFullpathForLogging() + " for clientId:  " + clientId);
  }

  private MacroStorage findLoadedMacros(File f)
  {
    if (f == null) return null;
    for (MacroStorage storage : macroClients.values())
    {
      if (storage.getCurrentFile().equals(f))
      {
        return storage;
      }
    }
    return null;
  }

  private boolean isShared(int clientId, File f)
  {
    if (f == null) return false;
    for (Map.Entry<Integer, MacroStorage> entry : macroClients.entrySet())
    {
      if (clientId != entry.getKey() && f.equals(entry.getValue().getCurrentFile()))
      {
        return true;
      }
    }
    return false;
  }

  private MacroStorage getStorageOrDefault(int macroClientId)
  {
    MacroStorage storage = macroClients.get(macroClientId);
    if (storage == null)
    {
      return macroClients.get(DEFAULT_STORAGE);
    }
    return storage;
  }

  public synchronized String getMacroText(int macroClientId, String key)
  {
    if (key == null) return null;

    MacroStorage storage = getStorageOrDefault(macroClientId);
    if (storage == null) return null;

    MacroDefinition macro = storage.getMacro(key);
    if (macro == null) return null;
    if (macro.isDbTreeMacro()) return null;
    return macro.getText();
  }

  public void addChangeListener(MacroChangeListener listener, int clientId)
  {
    MacroStorage storage = getMacros(clientId);
    if (storage != null)
    {
      storage.addChangeListener(listener);
    }
  }

  public void removeChangeListener(MacroChangeListener listener, int clientId)
  {
    MacroStorage storage = getMacros(clientId);
    if (storage != null)
    {
      storage.removeChangeListener(listener);
    }
  }

  public synchronized MacroStorage getMacros(int clientId)
  {
    return getStorageOrDefault(clientId);
  }

  /**
   * Checks if the given KeyStroke is assigned to any of the currently loaded macro files.
   * @param key the key to test
   *
   * @return true if the keystroke is currently used
   */
  public synchronized MacroDefinition getMacroForKeyStroke(KeyStroke key)
  {
    if (key == null) return null;
    for (MacroStorage storage : macroClients.values())
    {
      StoreableKeyStroke sk = new StoreableKeyStroke(key);
      List<MacroGroup> groups = storage.getGroups();
      for (MacroGroup group : groups)
      {
        for (MacroDefinition def : group.getMacros())
        {
          StoreableKeyStroke macroKey = def.getShortcut();
          if (macroKey != null && macroKey.equals(sk))
          {
            return def;
          }
        }
      }
    }
    return null;
  }

  public synchronized Map<String, MacroDefinition> getExpandableMacros(int clientId)
  {
    MacroStorage storage = getStorageOrDefault(clientId);
    Map<String, MacroDefinition> result = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
    List<MacroGroup> groups = storage.getGroups();
    for (MacroGroup group : groups)
    {
      for (MacroDefinition def : group.getMacros())
      {
        if (def.getExpandWhileTyping())
        {
          result.put(def.getName(), def);
        }
      }
    }
    return result;
  }
}
