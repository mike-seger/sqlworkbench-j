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

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.WbConnection;
import workbench.db.exporter.DataExporter;
import workbench.db.exporter.ExportType;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.components.FeedbackWindow;
import workbench.gui.components.ValidatingDialog;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;

import workbench.util.ExceptionUtil;
import workbench.util.WbThread;

import static workbench.db.exporter.ExportType.*;

/**
 * @author Thomas Kellerer
 */
public class ExportFileDialog
  implements PropertyChangeListener
{
  private ExportType exportType = null;
  private String selectedFilename = null;
  private boolean isCancelled = false;
  private ExportOptionsPanel exportOptions;
  private boolean selectDirectory = false;

  private String lastDirConfigKey = "workbench.export.lastdir";
  private final static String SIZE_KEY = "workbench.saveas.dialog";

  private final DataStore source;
  private boolean sqlChecked = false;
  private final boolean forClipboard;

  private Component parentComponent;

  public ExportFileDialog(Component caller)
  {
    source = null;
    exportOptions = new ExportOptionsPanel(null);
    parentComponent = caller;
    forClipboard = false;
  }

  public ExportFileDialog(Component caller, DataStore ds)
  {
    this(caller, ds, false);
  }

  public ExportFileDialog(Component caller, DataStore ds, boolean forClipboard)
  {
    this.source = ds;
    this.forClipboard = forClipboard;
    this.exportOptions = new ExportOptionsPanel(source == null ? null : source.getResultInfo(), forClipboard);
    this.parentComponent = caller;
  }

  public ExportFileDialog(Component caller, ResultInfo info)
  {
    this(caller, info, false);
  }

  public ExportFileDialog(Component caller, ResultInfo info, boolean forClipboard)
  {
    this.source = null;
    this.forClipboard = forClipboard;
    this.exportOptions = new ExportOptionsPanel(info, forClipboard);
    this.parentComponent = caller;
  }

  public void setSelectedColumn(ResultInfo info, List<ColumnIdentifier> columns)
  {
    this.exportOptions.setSelectedColumns(info, columns);
  }

  public void setSelectedRowCount(int count)
  {
    exportOptions.setSelectedRowCount(count);
  }

  public void setQuerySql(String sql, WbConnection con)
  {
    this.exportOptions.setQuerySql(sql, con);
  }

  public List<ColumnIdentifier> getColumnsToExport()
  {
    return this.exportOptions.getColumnsToExport();
  }

  public void saveSettings()
  {
    exportOptions.saveSettings();
  }

  public void restoreSettings()
  {
    exportOptions.restoreSettings();
  }

  public SpreadSheetOptions getOdsOptions()
  {
    return exportOptions.getOdsOptions();
  }

  public SpreadSheetOptions getXlsOptions()
  {
    return exportOptions.getXlsOptions();
  }

  public SpreadSheetOptions getXlsXOptions()
  {
    return exportOptions.getXlsXOptions();
  }

  public SpreadSheetOptions getXlsMOptions()
  {
    return exportOptions.getXlsMOptions();
  }

  public SqlOptions getSqlOptions()
  {
    return exportOptions.getSqlOptions();
  }

  public HtmlOptions getHtmlOptions()
  {
    return exportOptions.getHtmlOptions();
  }

  public TextOptions getTextOptions()
  {
    return exportOptions.getTextOptions();
  }

  public XmlOptions getXmlOptions()
  {
    return exportOptions.getXmlOptions();
  }

  public ExportOptions getBasicExportOptions()
  {
    return exportOptions.getExportOptions();
  }

  public FormattedTextOptions getFormattedTextOptions()
  {
    return exportOptions.getFormattedTextOptions();
  }

  public String getSelectedFilename()
  {
    return this.selectedFilename;
  }

  public ExportType getExportType()
  {
    return this.exportType;
  }

  public boolean isCancelled()
  {
    return this.isCancelled;
  }

  /**
   *  Set the config key for the Settings object
   *  where the selected directory should be stored
   */
  public void setConfigKey(String key)
  {
    this.lastDirConfigKey = key;
  }

  public void setSelectDirectoryOnly(boolean flag)
  {
    this.selectDirectory = flag;
  }

  public boolean doOpenFile()
  {
    return this.exportOptions.doOpenFile();
  }

  public void openOutputFile()
  {
    File output = this.exportOptions.getSelectedFile();
    openFile(output);
  }

  public static void openFile(File output)
  {
    if (output == null) return;
    if (!output.exists()) return;

    WbThread t = new WbThread("OpenFile - " + output.getName())
    {
      @Override
      public void run()
      {
        try
        {
          Desktop.getDesktop().open(output);
        }
        catch (Exception ex)
        {
          LogMgr.logError(new CallerInfo(){}, "Error when opening file", ex);
          WbSwingUtilities.showErrorMessage(ExceptionUtil.getDisplay(ex));
        }
      }
    };
    t.start();
  }

  public void setAllowOpenFile(boolean flag)
  {
    this.exportOptions.setAllowOpenFile(flag);
  }

  public void setExportInfo(String info)
  {
    exportOptions.setExportInfo(info);
  }

  public boolean selectOutput()
  {
    return this.selectOutput(ResourceMgr.getString("TxtWindowTitleSaveData"));
  }

  /**
   * Show a dialog to select the output file and configure the export.
   *
   * @param title  the title for the dialog
   *
   * @return true if the user clicked OK, false if the dialog was cancelled
   */
  public boolean selectOutput(String title)
  {
    this.exportType = null;
    this.selectedFilename = null;
    boolean result = false;

    MainWindow mainWindow = WbSwingUtilities.getMainWindow(parentComponent);
    exportOptions.setLastDirProperty(lastDirConfigKey);
    exportOptions.setSelectDirectoriesOnly(selectDirectory);
    int gap = IconMgr.getInstance().getSizeForLabel() / 3;

    exportOptions.setBorder(new EmptyBorder(gap, gap, gap, gap));
    ValidatingDialog dialog = new ValidatingDialog(mainWindow, title, exportOptions);
    ResourceMgr.setWindowIcons(dialog, "workbench");
    if (!Settings.getInstance().restoreWindowSize(dialog, SIZE_KEY))
    {
      // to properly calculate the needed size, we have to activate
      // the largest option panel, then pack() can figure it out correctly
      exportOptions.setExportType(ExportType.SQL_INSERT);
      dialog.pack();
      dialog.setSize(640, (int)(dialog.getHeight() * 1.05));
    }

    dialog.setDefaultButton(0);
    WbSwingUtilities.center(dialog, mainWindow);
    this.restoreSettings();
    this.exportOptions.addPropertyChangeListener("exportType", this);

    if (exportOptions.getExportType() != null && exportOptions.getExportType().isSqlType())
    {
      EventQueue.invokeLater(this::checkSqlOptions);
    }
    dialog.setVisible(true);

    boolean ok = !dialog.isCancelled();
    Settings.getInstance().storeWindowSize(dialog, SIZE_KEY);

    if (ok)
    {
      this.isCancelled = false;

      this.exportType = this.exportOptions.getExportType();
      if (!forClipboard)
      {
        this.selectedFilename = exportOptions.getSelectedFile().getFullPath();
      }
      this.saveSettings();
      result = true;
    }
    else
    {
      this.isCancelled = true;
      result = false;
    }
    return result;
  }

  public void setExporterOptions(DataExporter exporter)
  {
    exporter.setOptions(this.getBasicExportOptions());

    switch (this.exportType)
    {
      case SQL_INSERT:
      case SQL_UPDATE:
      case SQL_DELETE_INSERT:
      case SQL_DELETE:
        exporter.setSqlOptions(this.getSqlOptions());
        break;
      case TEXT:
        exporter.setTextOptions(this.getTextOptions());
        break;
      case HTML:
        exporter.setHtmlOptions(this.getHtmlOptions());
        break;
      case XML:
        exporter.setXmlOptions(this.getXmlOptions());
        break;
      case ODS:
        exporter.setOdsOptions(getOdsOptions());
        break;
      case XLSX:
        exporter.setXlsXOptions(getXlsXOptions());
        break;
      case XLSM:
        exporter.setXlsMOptions(getXlsMOptions());
        break;
      case XLS:
        exporter.setXlsOptions(getXlsOptions());
        break;
      case JSON:
        exporter.setOutputType(ExportType.JSON);
        break;
      default:
        exporter.setTextOptions(this.getTextOptions());
        LogMgr.logWarning(new CallerInfo(){}, "Unknown file type selected", null);
        break;
    }
  }

  private FeedbackWindow checkWindow;

  protected void checkSqlOptions()
  {
    if (sqlChecked) return;
    if (source == null) return;
    if (source.hasPkColumns()) return;

    if (checkWindow != null) return;

    Dialog dialog = (Dialog)SwingUtilities.getWindowAncestor(exportOptions);
    checkWindow = new FeedbackWindow(dialog, ResourceMgr.getString("MsgRetrievingKeyColumns"));
    WbSwingUtilities.center(checkWindow, dialog);
    WbSwingUtilities.showWaitCursor(exportOptions);
    checkWindow.showAndStart(this::_checkSqlOptions);
  }

  protected void _checkSqlOptions()
  {
    if (source == null) return;

    try
    {
      sqlChecked = true;
      source.updatePkInformation();
      exportOptions.updateSqlOptions(source);
    }
    catch (Exception sql)
    {
      LogMgr.logError(new CallerInfo(){}, "Error checking SQL Options", sql);
      WbSwingUtilities.showErrorMessage(ExceptionUtil.getDisplay(sql));
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(exportOptions);
      if (checkWindow != null)
      {
        checkWindow.setVisible(false);
        checkWindow.dispose();
        checkWindow = null;
      }
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (this.exportOptions == null) return;

    if (evt.getSource() == this.exportOptions)
    {
      try
      {
        ExportType type = (ExportType)evt.getNewValue();
        switch (type)
        {
          case SQL_INSERT:
          case SQL_UPDATE:
          case SQL_DELETE_INSERT:
            checkSqlOptions();
            break;
        }
      }
      catch (Throwable th)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Could check SQL options");
      }
    }
  }

}
