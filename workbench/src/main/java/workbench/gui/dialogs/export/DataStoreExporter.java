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
package workbench.gui.dialogs.export;

import java.nio.charset.Charset;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.exporter.DataExporter;
import workbench.db.exporter.ExportType;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.DbUnitCopier;
import workbench.gui.components.WbTable;

import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * @author  Thomas Kellerer
 */
public class DataStoreExporter
{
  private WbTable source;
  private ExportFileDialog dialog;
  private WbFile output;
  private String configKey;

  public DataStoreExporter(WbTable source, String config)
  {
    this.source = source;
    this.configKey = StringUtil.trimToNull(config);
  }

  public void saveAs()
  {
    this.dialog = new ExportFileDialog(source, source.getDataStore());
    this.dialog.setSelectDirectoryOnly(false);
    if (this.configKey != null)
    {
      this.dialog.setConfigKey(configKey);
    }
    this.output = null;
    boolean selected = dialog.selectOutput();
    if (selected && getSelectedFilename() != null)
    {
      this.output = new WbFile(getSelectedFilename());
      writeFile();
      if (dialog.doOpenFile())
      {
        dialog.openOutputFile();
      }
    }
  }

  private String getSelectedFilename()
  {
    if (dialog == null) return null;
    String fname = dialog.getSelectedFilename();
    WbFile f = new WbFile(fname);
    if (StringUtil.isBlank(f.getExtension()))
    {
      ExportType exportType = dialog.getExportType();
      fname += exportType.getDefaultFileExtension();
    }
    return fname;
  }

  public WbTable getSource()
  {
    return source;
  }

  public void setSource(WbTable source)
  {
    this.source = source;
  }

  private void writeFile()
  {
    if (this.source == null) return;
    if (this.source.getDataStore() == null) return;
    if (this.output == null)
    {
      throw new NullPointerException("No outputfile defined");
    }

    ExportType type = dialog.getExportType();
    if (type == ExportType.DBUNIT_XML)
    {
      writeDbUnitXML();
      return;
    }

    DataExporter exporter = new DataExporter(this.source.getDataStore().getOriginalConnection());
    dialog.setExporterOptions(exporter);

    try
    {
      exporter.startExport(output, this.source.getDataStore(), this.dialog.getColumnsToExport());
      if (!exporter.isSuccess())
      {
        CharSequence msg = exporter.getErrors();
        if (msg != null)
        {
          WbSwingUtilities.showErrorMessage(source, msg.toString());
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error writing export file", e);
      WbSwingUtilities.showErrorMessage(source, ExceptionUtil.getDisplay(e));
    }
  }

  private void writeDbUnitXML()
  {
    try
    {
      // The actual usage of the DbUnit classes must be in a different class than this class
      // Otherwise not having the DbUnit jar in the classpath will prevent this class from being instantiated
      // (and thus all other copy methods won't work either)
      DbUnitCopier copier = new DbUnitCopier();
      int[] selected = null;
      boolean selectedOnly = dialog.getBasicExportOptions().selectedRowsOnly();
      if (selectedOnly && source != null)
      {
        selected = source.getSelectedRows();
      }
      Charset encoding = Charset.forName(dialog.getBasicExportOptions().getEncoding());
      copier.writeToFile(output, source.getDataStore(), selected, encoding);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not create DBUnit xml", ex);
      WbSwingUtilities.showErrorMessage(source, ExceptionUtil.getDisplay(ex));
    }
  }
}
