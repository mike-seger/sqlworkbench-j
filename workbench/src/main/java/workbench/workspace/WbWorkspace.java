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
package workbench.workspace;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.gui.sql.EditorHistory;
import workbench.gui.sql.PanelType;

import workbench.sql.StatementHistory;

import workbench.util.CharacterRange;
import workbench.util.CollectionUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.WbProperties;

/**
 *
 * @author  Thomas Kellerer
 */
public class WbWorkspace
  implements Closeable
{
  public static final String TAB_PROP_PREFIX = "tab";

  private static final String VARIABLES_FILENAME = "variables.properties";
  private static final String TABINFO_FILENAME = "tabs.properties";
  private static final String TOOL_ENTRY_PREFIX = "toolprop_";

  private static final String CURSOR_POS_PROP = ".file.cursorpos";
  private static final String ENCODING_PROP = ".encoding";
  private static final String FILENAME_PROP = ".filename";

  private enum WorkspaceState
  {
    closed,
    reading,
    writing;
  }

  private WorkspaceState state = WorkspaceState.closed;
  private int tabCount = -1;

  private WbProperties tabInfo = new WbProperties(0);
  private final Map<String, WbProperties> toolProperties = new HashMap<>();
  private final WbProperties variables = new WbProperties(0);
  private final Map<Integer, EditorHistory> editorHistories = new HashMap<>();
  private final Map<Integer, StatementHistory> executionHistories = new HashMap<>();
  private String filename;
  private String loadError;
  private WorkspacePersistence persistence;
  private boolean allowDirectoryStorage;

  public WbWorkspace(String archiveName)
  {
    this(archiveName, Settings.getInstance().enableDirectoryBasedWorkspaceStorage());
  }

  public WbWorkspace(String archiveName, boolean allowDirectoryWorkspace)
  {
    if (archiveName == null) throw new NullPointerException("Filename cannot be null");
    this.allowDirectoryStorage = allowDirectoryWorkspace;
    setFilename(archiveName);
  }

  private boolean isDirectory(WbFile f)
  {
    if (f.exists())
    {
      return f.isDirectory();
    }
    return StringUtil.isBlank(f.getExtension());
  }

  /**
   * Opens the workspace for writing.
   *
   * Nothing will be saved.
   *
   * To actually save the workspace content {@link #save()} needs to be called.
   *
   * If the workspace was already open, it is closed.
   * This will reset all tab specific properties.
   *
   * @throws IOException
   * @see #save()
   */
  private void openForWriting()
    throws IOException
  {
    close();
    try
    {
      persistence.openForWriting();
      state = WorkspaceState.writing;
    }
    catch (IOException io)
    {
      state = WorkspaceState.closed;
      throw io;
    }
  }

  public String getLoadError()
  {
    return loadError;
  }

  /**
   * Opens the workspace for reading.
   *
   * This will automatically load all properties stored in the workspace, but not the panel statements.
   *
   * If the workspace was already open, it is closed and all internal properties are discarded.
   *
   * @throws IOException
   * @see #readEditorHistory(int, workbench.gui.sql.SqlHistory)
   */
  public boolean openForReading()
    throws IOException
  {
    close();
    clear();
    loadError = null;

    try
    {
      persistence.openForReading();
      readTabInfo();
      readToolProperties();
      readVariables();

      tabCount = calculateTabCount();

      state = WorkspaceState.reading;
      return true;
    }
    catch (Throwable th)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Could not open workspace " + filename, th);
      loadError = th.getMessage();
      state = WorkspaceState.closed;
    }
    return false;
  }

  public void setFilename(String archiveName)
  {
    if (archiveName == null) throw new NullPointerException("Filename cannot be null");
    if (state != WorkspaceState.closed)
    {
      LogMgr.logError(new CallerInfo(){}, "setFilename() called although workspace is not closed!", new Exception("Backtrace"));
    }
    filename = archiveName;
    WbFile f = new WbFile(filename);
    if (persistence != null)
    {
      persistence.close();
    }

    if (allowDirectoryStorage && isDirectory(f))
    {
      persistence = new DirectoryWorkspacePersistence(filename);
    }
    else
    {
      persistence = new ZipWorkspacePersistence(filename);
    }
  }

  public String getFilename()
  {
    return filename;
  }

  public Map<String, WbProperties> getToolProperties()
  {
    return toolProperties;
  }

  public void setEntryCount(int count)
  {
    tabInfo.setProperty("tab.total.count", count);
  }

  public void addEditorHistory(int index, EditorHistory history)
  {
    this.editorHistories.put(index, history);
  }

  public void addExecutionHistory(int index, StatementHistory history)
  {
    this.executionHistories.put(index, history);
  }

  public WbProperties getVariables()
  {
    WbProperties props = new WbProperties(0);
    props.putAll(variables);
    return props;
  }

  public void setVariables(Properties newVars)
  {
    variables.clear();
    if (newVars != null)
    {
      variables.putAll(newVars);
    }
  }

  /**
   * Returns the number of available tab entries.
   *
   * Calling this method is only valid if {@link #openForReading() } has been called before.
   *
   * @return the number of tabs stored in this workspace or -1 if the workspace was not opened properly
   * @see #openForReading()
   */
  public int getEntryCount()
  {
    if (state != WorkspaceState.reading)
    {
      return -1;
    }
    return tabCount;
  }

  public PanelType getPanelType(int index)
  {
    String type = tabInfo.getProperty(TAB_PROP_PREFIX + index + ".type", "sqlPanel");
    try
    {
      return PanelType.valueOf(type);
    }
    catch (Exception e)
    {
      return PanelType.sqlPanel;
    }
  }

  public void readSQLExecutionHistory(int anIndex, StatementHistory executionHistory)
    throws IOException
  {
    if (state != WorkspaceState.reading) throw new IllegalStateException("Workspace is not open for reading. Can not read execution history");
    persistence.readSQLExecutionHistory(anIndex, executionHistory);
  }
  /**
   *
   * @param anIndex
   * @param history
   * @throws IOException
   */
  public void readEditorHistory(int anIndex, EditorHistory history)
    throws IOException
  {
    if (state != WorkspaceState.reading) throw new IllegalStateException("Workspace is not open for reading. Entry count is not available");

    persistence.readEditorHistory(anIndex, history);
  }

  public boolean isOutputValid()
  {
    return persistence.isOutputValid();
  }

  /**
   * Saves everything to the zip file and closes this workspace.
   *
   * For each entry, the CRC of that entry is stored. All CRC values
   * from the most recent call to this method can be retrieved
   * using {@link #getLastCRCValues()}.
   *
   * @see #getLastCRCValues()
   */
  public void save()
    throws IOException
  {
    try
    {
      openForWriting();
      saveTabInfo();
      saveToolProperties();
      saveVariables();
      saveEditorHistory();
      saveExecutionHistory();
    }
    finally
    {
      close();
    }
    editorHistories.clear();
    executionHistories.clear();
  }

  @Override
  public void close()
  {
    persistence.close();
    state = WorkspaceState.closed;
  }

  public WbProperties getSettings()
  {
    return this.tabInfo;
  }

  public void prepareForSaving()
  {
    tabInfo.clear();
    editorHistories.clear();
  }

  private void clear()
  {
    toolProperties.clear();
    variables.clear();
    tabInfo.clear();
    editorHistories.clear();
  }

  private void readVariables()
  {
    variables.clear();

    try
    {
      WbProperties props = persistence.readProperties(VARIABLES_FILENAME);
      variables.putAll(props);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read variables file", ex);
    }
  }

  private void readToolProperties()
  {
    toolProperties.clear();
    List<String> entries = persistence.getEntries();
    for (String name : entries)
    {
      if (name.startsWith(TOOL_ENTRY_PREFIX))
      {
        WbFile f = new WbFile(name.substring(TOOL_ENTRY_PREFIX.length()));
        String toolkey = f.getFileName();
        WbProperties props = persistence.readProperties(name);
        toolProperties.put(toolkey, props);
      }
    }
  }

  private int calculateTabCount()
  {
    // new property that stores the total count of tabs
    int count = tabInfo.getIntProperty("tab.total.count", -1);
    if (count > 0) return count;

    // Old tabinfo.properties format
    boolean found = true;
    int index = 0;
    while (found)
    {
      if (tabInfo.containsKey(TAB_PROP_PREFIX + index + ".maxrows") ||
          tabInfo.containsKey(TAB_PROP_PREFIX + index + ".title") ||
          tabInfo.containsKey(TAB_PROP_PREFIX + index + ".append.results"))
      {
        tabInfo.setProperty(TAB_PROP_PREFIX + index + ".type", PanelType.sqlPanel.toString());
        index ++;
      }
      else if (tabInfo.containsKey(TAB_PROP_PREFIX + index + ".type"))
      {
        index ++;
      }
      else
      {
        found = false;
      }
    }

    int dbExplorer = this.tabInfo.getIntProperty("dbexplorer.visible", 0);

    // now add the missing .type entries for the DbExplorer panels
    for (int i=0; i < dbExplorer; i++)
    {
      tabInfo.setProperty(TAB_PROP_PREFIX + index + ".type", PanelType.dbExplorer.toString());
      index ++;
    }
    return index;
  }

  private void saveVariables()
    throws IOException
  {
    try
    {
      persistence.saveProperties(VARIABLES_FILENAME, variables);
    }
    catch (IOException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not write variables", ex);
      throw ex;
    }
  }

  private void saveToolProperties()
    throws IOException
  {
    if (CollectionUtil.isEmpty(this.toolProperties)) return;
    try
    {
      for (Map.Entry<String, WbProperties> propEntry : toolProperties.entrySet())
      {
        String entryName = TOOL_ENTRY_PREFIX + propEntry.getKey() + ".properties";
        persistence.saveProperties(entryName, propEntry.getValue());
      }
    }
    catch (IOException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not write variables", ex);
      throw ex;
    }
  }

  private void saveExecutionHistory()
    throws IOException
  {
    for (Map.Entry<Integer, StatementHistory> historyEntry : executionHistories.entrySet())
    {
      if (historyEntry.getValue() != null && historyEntry.getKey() != null)
      {
        try
        {
          int index = historyEntry.getKey();
          persistence.saveSQLExecutionHistory(index, historyEntry.getValue());
        }
        catch (IOException ex)
        {
          LogMgr.logError(new CallerInfo(){}, "Could not write SQL history for tab index: " + historyEntry.getKey(), ex);
        }
      }
    }
  }

  private void saveEditorHistory()
    throws IOException
  {
    persistence.saveEditorHistory(editorHistories);
  }

  private void saveTabInfo()
    throws IOException
  {
    persistence.saveProperties(TABINFO_FILENAME, tabInfo);
  }

  private void readTabInfo()
  {
    this.tabInfo = persistence.readProperties(TABINFO_FILENAME);
  }

  public void setSelectedTab(int anIndex)
  {
    this.tabInfo.setProperty("tab.selected", Integer.toString(anIndex));
  }

  public int getSelectedTab()
  {
    return StringUtil.getIntValue(this.tabInfo.getProperty("tab.selected", "0"));
  }

  public boolean isSelectedTabExplorer()
  {
    int index = getSelectedTab();
    return PanelType.dbExplorer == this.getPanelType(index);
  }

  public void setTabTitle(int index, String name)
  {
    String key = TAB_PROP_PREFIX + index + ".title";
    String encoded = StringUtil.escapeText(name, CharacterRange.RANGE_7BIT);
    this.tabInfo.setProperty(key, encoded);
  }

  public String getTabTitle(int index)
  {
    if (this.tabInfo == null) return null;
    String key = TAB_PROP_PREFIX + index + ".title";
    String value = (String)this.tabInfo.get(key);
    return StringUtil.decodeUnicode(value);
  }

  public int getExternalFileCursorPos(int tabIndex)
  {
    if (this.tabInfo == null) return -1;
    String key = TAB_PROP_PREFIX + tabIndex + CURSOR_POS_PROP;
    String value = (String)this.tabInfo.get(key);
    if (value == null) return -1;
    int result = -1;
    try
    {
      result = Integer.parseInt(value);
    }
    catch (Exception e)
    {
      result = -1;
    }

    return result;
  }

  public void setQueryTimeout(int index, int timeout)
  {
    String key = TAB_PROP_PREFIX + index + ".timeout";
    this.tabInfo.setProperty(key, Integer.toString(timeout));
  }

  public int getQueryTimeout(int index)
  {
    if (this.tabInfo == null) return 0;
    String key = TAB_PROP_PREFIX + index + ".timeout";
    String value = (String)this.tabInfo.get(key);
    if (value == null) return 0;
    int result = 0;
    try
    {
      result = Integer.parseInt(value);
    }
    catch (Exception e)
    {
      result = 0;
    }
    return result;
  }

  public void setMaxRows(int index, int numRows)
  {
    String key = TAB_PROP_PREFIX + index + ".maxrows";
    this.tabInfo.setProperty(key, Integer.toString(numRows));
  }

  public int getMaxRows(int tabIndex)
  {
    if (this.tabInfo == null) return 0;
    String key = TAB_PROP_PREFIX + tabIndex + ".maxrows";
    String value = (String)this.tabInfo.get(key);
    if (value == null) return 0;
    int result = 0;
    try
    {
      result = Integer.parseInt(value);
    }
    catch (Exception e)
    {
      result = 0;
    }
    return result;
  }

  public String getExternalFileName(int tabIndex)
  {
    if (this.tabInfo == null) return null;
    String key = TAB_PROP_PREFIX + tabIndex + FILENAME_PROP;
    String value = (String)this.tabInfo.get(key);
    return StringUtil.decodeUnicode(value);
  }

  public String getExternalFileEncoding(int tabIndex)
  {
    if (this.tabInfo == null) return null;
    String key = TAB_PROP_PREFIX + tabIndex + ENCODING_PROP;
    String value = (String)this.tabInfo.get(key);
    if (StringUtil.isEmpty(value)) return Settings.getInstance().getDefaultEncoding();
    return value;
  }

  public void setExternalFileCursorPos(int tabIndex, int cursor)
  {
    String key = TAB_PROP_PREFIX + tabIndex + CURSOR_POS_PROP;
    this.tabInfo.setProperty(key, Integer.toString(cursor));
  }

  public void setExternalFileName(int tabIndex, String filename)
  {
    String key = TAB_PROP_PREFIX + tabIndex + FILENAME_PROP;
    String encoded = StringUtil.escapeText(filename, CharacterRange.RANGE_7BIT);
    this.tabInfo.setProperty(key, encoded);
  }

  public void setExternalFileEncoding(int tabIndex, String encoding)
  {
    if (encoding == null) return;
    String key = TAB_PROP_PREFIX + tabIndex + ENCODING_PROP;
    this.tabInfo.setProperty(key, encoding);
  }

  @Override
  public String toString()
  {
    return filename;
  }

  public void restoreBackup(File backupFile)
    throws IOException
  {
    persistence.restoreBackup(backupFile);
  }

  public File createBackup()
  {
    return persistence.createBackup();
  }
}
