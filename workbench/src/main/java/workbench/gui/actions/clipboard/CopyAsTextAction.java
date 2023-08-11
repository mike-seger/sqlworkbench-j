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
package workbench.gui.actions.clipboard;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.swing.KeyStroke;

import workbench.WbManager;
import workbench.console.DataStorePrinter;
import workbench.console.TextPrinter;
import workbench.interfaces.Interruptable;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.PlatformShortcuts;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.exporter.BlobMode;
import workbench.db.exporter.DataExporter;
import workbench.db.exporter.ExportType;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.WbAction;
import workbench.gui.components.StringSelectionAdapter;
import workbench.gui.components.WbTable;
import workbench.gui.dbobjects.ProgressDialog;
import workbench.gui.dialogs.export.ExportFileDialog;

import workbench.storage.DataStore;
import workbench.storage.RowActionMonitor;

import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;
import workbench.util.WbStringWriter;
import workbench.util.WbThread;

/**
 * Action to copy the contents of a WbTable to the clipboard in various formats.
 *
 * The output format and options are selected by showing the "Save Data As" dialog
 * but without the ability to choose an output file.
 *
 * @author Thomas Kellerer
 */
public class CopyAsTextAction
  extends WbAction
  implements Interruptable
{
  private final WbTable client;
  protected boolean copySelected;
  private WbThread worker;
  private ProgressDialog progress;
  private Interruptable job;
  private boolean cancelled = false;

  public CopyAsTextAction(WbTable aClient)
  {
    super();
    this.client = aClient;
    this.setMenuItemName(ResourceMgr.MNU_TXT_DATA);
    this.initMenuDefinition("MnuTxtDataToClipboard", KeyStroke.getKeyStroke(KeyEvent.VK_Y, PlatformShortcuts.getDefaultModifier()));
    copySelected = false;
    this.setEnabled(false);
  }

  @Override
  public boolean hasCtrlModifier()
  {
    return true;
  }

  @Override
  public boolean hasShiftModifier()
  {
    return true;
  }

  @Override
  public void executeAction(ActionEvent evt)
  {
    final ExportFileDialog dialog = new ExportFileDialog(this.client, client.getDataStore(), true);
    dialog.setSelectedRowCount(client.getSelectedRowCount());

    if (client.getColumnSelectionAllowed())
    {
      List<ColumnIdentifier> columns = client.getColumnsFromSelection();
      DataStore ds = client.getDataStore();
      dialog.setSelectedColumn(ds.getResultInfo(), columns);
    }

    boolean ok = dialog.selectOutput(ResourceMgr.getString("LblCopyToClp"));
    if (!ok) return;
    final ExportType type = dialog.getExportType();

    boolean selected = dialog.getBasicExportOptions().selectedRowsOnly();
    final int[] rows;
    if (selected)
    {
      rows = client.getSelectedRows();
    }
    else
    {
      rows = null;
    }

    if (client.getRowCount() >= GuiSettings.getCopyDataRowsThreshold())
    {
      MainWindow window = WbSwingUtilities.getMainWindow(client);
      progress = new ProgressDialog(ResourceMgr.getString("MsgCopying"), window, this, false);
      progress.getInfoPanel().setMonitorType(RowActionMonitor.MONITOR_PLAIN);
      progress.getInfoPanel().setInfoText(ResourceMgr.getString("MsgSpoolingRow"));
      progress.showProgressWindow();
    }

    cancelled = false;
    worker = new WbThread("CopyThread")
    {
      @Override
      public void run()
      {
        doCopy(type, dialog, rows);
      }
    };
    worker.start();
  }

  @Override
  public void cancelExecution()
  {
    cancelled = true;
    job.cancelExecution();
    try
    {
      if (worker != null)
      {
        worker.interrupt();
      }
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not interrupt worker thread");
    }
  }

  @Override
  public boolean confirmCancel()
  {
    return true;
  }

  private void doCopy(ExportType type, ExportFileDialog dialog, int[] rows)
  {
    WbStringWriter output = new WbStringWriter(client.getRowCount() * 100);
    try
    {
      WbSwingUtilities.showWaitCursorOnWindow(this.client);

      if (type == ExportType.FORMATTED_TEXT)
      {
        writeFormattedText(dialog, output, rows);
      }
      else
      {
        writeExport(dialog, output, rows);
      }

      if (cancelled)
      {
        LogMgr.logDebug(new CallerInfo(){}, "Copy as text cancelled");
        return;
      }

      final Transferable transferable;
      final ClipboardOwner owner ;
      String text = output.toString();
      if (Settings.getInstance().copyToClipboardAsHtml() && type == ExportType.TEXT)
      {
        String quote = dialog.getTextOptions().getTextQuoteChar();
        String delim = dialog.getTextOptions().getTextDelimiter();
        StringSelectionAdapter selection = new StringSelectionAdapter(text, true, delim, quote);
        transferable = selection;
        owner = selection;
      }
      else if (type == ExportType.HTML)
      {
        StringSelectionAdapter selection = new StringSelectionAdapter(text, text);
        transferable = selection;
        owner = selection;
      }
      else
      {
        StringSelection selection = new StringSelection(text);
        transferable = selection;
        owner = selection;
      }

      WbSwingUtilities.invoke(() ->
      {
        Clipboard clp = Toolkit.getDefaultToolkit().getSystemClipboard();
        clp.setContents(transferable, owner);
      });
    }
    catch (Throwable ex)
    {
      if (!cancelled)
      {
        LogMgr.logError(new CallerInfo(){}, "Error copying to clipboard", ex);
        if (ex instanceof OutOfMemoryError)
        {
          WbManager.getInstance().showOutOfMemoryError();
        }
        else
        {
          String msg = ResourceMgr.getString("ErrClipCopy");
          msg = StringUtil.replace(msg, "%errmsg%", ExceptionUtil.getDisplay(ex));
          WbSwingUtilities.showErrorMessage(client, msg);
        }
      }
    }
    finally
    {
      WbSwingUtilities.showDefaultCursorOnWindow(this.client);

      if (progress != null)
      {
        progress.setVisible(false);
        progress.dispose();
        progress = null;
      }
      cancelled = false;
      worker = null;
      job = null;
    }
  }

  private void writeExport(ExportFileDialog dialog, Writer out, int[] selectedRows)
  {
    DataExporter exporter = new DataExporter(client.getDataStore().getOriginalConnection());
    if (progress != null)
    {
      exporter.setReportInterval(1);
      exporter.setRowMonitor(progress.getMonitor());
    }
    this.job = exporter;
    dialog.setExporterOptions(exporter);

    BlobMode blobMode = dialog.getSqlOptions().getBlobMode();
    if (blobMode == BlobMode.SaveToFile)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Blob mode \"file\" not supported for clipboard actions. Using DBMS specific format");
      exporter.setBlobMode(BlobMode.DbmsLiteral);
    }

    exporter.writeTo(out, client.getDataStore(), dialog.getColumnsToExport(), selectedRows);
    if (!cancelled && !exporter.isSuccess())
    {
      CharSequence msg = exporter.getErrors();
      if (msg != null)
      {
        WbSwingUtilities.showErrorMessage(client, msg.toString());
      }
    }
  }

  private void writeFormattedText(ExportFileDialog dialog, Writer out, int[] selectedRows)
  {
      DataStorePrinter printer = new DataStorePrinter(client.getDataStore());
      if (progress != null)
      {
        printer.setRowMonitor(progress.getMonitor());
      }
      this.job = printer;
      printer.setNullString(dialog.getBasicExportOptions().getNullString());
      printer.setFormatColumns(true);
      printer.setPrintRowCount(false);
      printer.setUseMarkdownFormatting(dialog.getFormattedTextOptions().useGitHubMarkdown());
      printer.setShowResultName(GuiSettings.copyToClipboardFormattedTextWithResultName());
      List<ColumnIdentifier> columnsToCopy = dialog.getColumnsToExport();
      if (columnsToCopy != null)
      {
        List<String> colNames =new ArrayList<>(columnsToCopy.size());
        for (ColumnIdentifier id : columnsToCopy)
        {
          colNames.add(id.getColumnName());
        }
        printer.setColumnsToPrint(colNames);
      }
      if (cancelled) return;
      TextPrinter pw = TextPrinter.createPrinter(new PrintWriter(out));
      printer.printTo(pw, selectedRows);
  }

}
