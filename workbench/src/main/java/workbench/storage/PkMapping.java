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
package workbench.storage;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.util.CollectionUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;

/**
 * A class to hold user-defined primary key mappings for tables (or views).
 *
 * @author Thomas Kellerer
 */
public class PkMapping
{
  private final Map<String, String> columnMapping = new HashMap<>();

  private static PkMapping instance;

  public static synchronized boolean isInitialized()
  {
    return instance != null;
  }

  public static synchronized PkMapping getInstance()
  {
    if (instance == null)
    {
      instance = new PkMapping();
    }
    return instance;
  }

  PkMapping(File source)
  {
    loadMapping(source);
  }

  private PkMapping()
  {
    loadMapping(Settings.getInstance().getPKMappingFile());
  }

  public synchronized void clear()
  {
    if (columnMapping != null)
    {
      columnMapping.clear();
    }
  }

  public synchronized String getMappingAsText()
  {
    if (CollectionUtil.isEmpty(this.columnMapping)) return null;

    StringBuilder result = new StringBuilder(this.columnMapping.size() * 50);
    Iterator<Entry<String, String>> itr = this.columnMapping.entrySet().iterator();
    while (itr.hasNext())
    {
      Map.Entry entry = itr.next();
      result.append(entry.getKey());
      result.append('=');
      result.append(entry.getValue());
      result.append('\n');
    }
    return result.toString();
  }

  public final synchronized void loadMapping(File mappingFile)
  {
    if (mappingFile == null) return;
    Properties props = new Properties();

    final CallerInfo ci = new CallerInfo(){};
    if (!mappingFile.exists())
    {
      LogMgr.logWarning(ci, "Mapping file '" + mappingFile.getAbsolutePath() + "' not found! Please check workbench.settings", null);
      return;
    }
    InputStream in = null;
    try
    {
      in = new BufferedInputStream(new FileInputStream(mappingFile));
      props.load(in);
    }
    catch (Exception e)
    {
      LogMgr.logError(ci, "Error reading mapping file", e);
      this.columnMapping.clear();
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }

    LogMgr.logInfo(new CallerInfo(){}, "Using PK mappings from " + mappingFile.getAbsolutePath());

    for (Entry<Object, Object> entry : props.entrySet())
    {
      String table = (String)entry.getKey();
      String columns = (String)entry.getValue();
      if (StringUtil.isEmpty(columns))
      {
        LogMgr.logWarning(ci, "Mapping for table \"" + table + "\" ignored because column list is empty");
      }
      else
      {
        this.columnMapping.put(table, columns);
      }
    }
  }

  public synchronized void removeMapping(String table)
  {
    if (this.columnMapping == null || table == null) return;
    this.columnMapping.remove(table);
  }

  public synchronized void addMapping(TableIdentifier table, String columns)
  {
    addMapping(table.getTableExpression(), columns);
  }

  /**
   * Defines a PK mapping for the specified table name
   */
  public synchronized void addMapping(String table, String columns)
  {
    if (!StringUtil.isEmpty(table) && !StringUtil.isEmpty(columns))
    {
      this.columnMapping.put(table.toLowerCase(), columns);
    }
  }

  public synchronized List<String> getPKColumns(TableIdentifier tbl)
  {
    if (this.columnMapping == null) return null;
    String tname = tbl.getTableName().toLowerCase();

    String columns = this.columnMapping.get(tname);
    if (columns == null)
    {
      String fullname = tbl.getTableExpression().toLowerCase();
      columns = this.columnMapping.get(fullname);
    }
    List<String> cols = null;
    if (columns != null)
    {
      cols = StringUtil.stringToList(columns, ",", true, true);
      LogMgr.logInfo(new CallerInfo(){}, "Using PK Columns [" + columns + "]" + " for table [" + tbl.getTableExpression() + "]");
    }
    return cols;
  }

  public synchronized Map<String, String> getMapping()
  {
    if (this.columnMapping == null) return Collections.emptyMap();
    return Collections.unmodifiableMap(this.columnMapping);
  }

  public synchronized void saveMapping(File mappingFile)
  {
    if (this.columnMapping == null) return;
    if (mappingFile == null)
    {
      LogMgr.logError(new CallerInfo(){}, "No mapping file provided!", new Exception("Stacktrace"));
      return;
    }

    BufferedWriter out = null;
    try
    {
      Iterator<Entry<String, String>> itr = this.columnMapping.entrySet().iterator();
      out = new BufferedWriter(new FileWriter(mappingFile));
      out.write("# Primary key mapping for " + ResourceMgr.TXT_PRODUCT_NAME);
      out.newLine();
      while (itr.hasNext())
      {
        Map.Entry<String, String> entry = itr.next();
        String table = entry.getKey();
        String cols = entry.getValue();
        out.write(table);
        out.write('=');
        out.write(cols);
        out.newLine();
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error saving mapping to properties file", e);
    }
    finally
    {
      FileUtil.closeQuietely(out);
    }
  }

  public synchronized void addMapping(TableIdentifier table, ColumnIdentifier[] cols)
  {
    StringBuilder colNames = new StringBuilder(50);
    for (ColumnIdentifier col : cols)
    {
      if (col.isPkColumn())
      {
        if (colNames.length() > 0) colNames.append(',');
        colNames.append(col.getColumnName());
      }
    }
    if (colNames.length() > 0)
    {
      this.addMapping(table, colNames.toString());
    }
  }
}
