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
package workbench.gui.components;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

import workbench.WbManager;
import workbench.interfaces.Interruptable;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.MultiRowInserts;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.exporter.BlobMode;
import workbench.db.exporter.ExportType;
import workbench.db.exporter.SqlRowDataConverter;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.dbobjects.ProgressDialog;

import workbench.storage.DataStore;
import workbench.storage.RowActionMonitor;
import workbench.storage.RowData;

import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;
import workbench.util.WbThread;

/**
 * A class to copy the data of a {@link workbench.gui.components.WbTable} to
 * the clipboard.
 *
 * The following formats are supported:
 *
 * <ul>
 *  <li>SQL DELETE, see {@link #copyAsSqlDelete(boolean, boolean)}</li>
 *  <li>SQL DELETE/INSERT, see {@link #copyAsSqlDeleteInsert(boolean, boolean) (boolean, boolean)}</li>
 *  <li>SQL INSERT, see {@link #copyAsSqlInsert(boolean, boolean) (boolean, boolean)}</li>
 *  <li>SQL UPDATE, see {@link #copyAsSqlUpdate(boolean, boolean) (boolean, boolean)}</li>
 *  <li>DBUnit XML, see {@link #doCopyAsDBUnitXML(boolean, boolean)}
 * </ul>
 *
 * @author Thomas Kellerer
 */
public class ClipBoardCopier
  implements Interruptable
{
  private final DataStore data;
  private final WbTable client;
  private boolean cancelled = false;
  private ProgressDialog progress;

  /**
   * Create a new ClipBoardCopier to copy the contents of the given table.
   *
   * @param table  the table for which the data should be copied.
   */
  public ClipBoardCopier(WbTable table)
  {
    this.client = table;
    this.data = client.getDataStore();
  }

  /**
   * For testing purposes only.
   *
   * @param ds the datastore containing the data to copy
   */
  ClipBoardCopier(DataStore ds)
  {
    this.client = null;
    this.data = ds;
  }

  /**
   * For testing purposes.
   */
  protected Clipboard getClipboard()
  {
    return Toolkit.getDefaultToolkit().getSystemClipboard();
  }

  public void copyAsSqlInsert(boolean selectedOnly, boolean showSelectColumns)
  {
    this.copyAsSql(ExportType.SQL_INSERT, selectedOnly, showSelectColumns);
  }

  public void copyAsDbUnit(final boolean selectedOnly, final boolean showSelectColumns)
  {
    if (this.data == null)
    {
      // Should not happen.
      WbSwingUtilities.showErrorMessage(client, "No DataStore available!");
      LogMgr.logError(new CallerInfo(){}, "Cannot copy without a DataStore!", null);
      return;
    }

    createFeedbackWindow();
    cancelled = false;
    WbThread t = new WbThread("CopyThread")
    {
      @Override
      public void run()
      {
        try
        {
          doCopyAsDBUnitXML(selectedOnly, showSelectColumns);
        }
        finally
        {
          closeFeedback();
        }
      }
    };
    t.start();
    showFeedback();
  }

  public void doCopyAsDBUnitXML(boolean selectedOnly, final boolean showSelectColumns)
  {
    if (data == null) return;

    checkUpdateTable();

    try
    {
      WbSwingUtilities.showWaitCursorOnWindow(this.client);

      // The actual usage of the DbUnit classes must be in a different class than this class
      // Otherwise not having the DbUnit jar in the classpath will prevent this class from being instantiated
      // (and thus all other copy methods won't work either)
      DbUnitCopier copier = new DbUnitCopier();
      int[] selected = null;
      if (selectedOnly && client != null)
      {
        selected = client.getSelectedRows();
      }

      String xml = copier.createDBUnitXMLDataString(data, selected);

      if (xml != null && !cancelled)
      {
        Clipboard clp = getClipboard();
        StringSelection sel = new StringSelection(xml);
        clp.setContents(sel, sel);
      }
    }
    catch (Throwable e)
    {
      if (!cancelled)
      {
        if (e instanceof OutOfMemoryError)
        {
          WbManager.getInstance().showOutOfMemoryError();
        }
        else
        {
          String msg = ResourceMgr.getString("ErrClipCopy");
          msg = StringUtil.replace(msg, "%errmsg%", ExceptionUtil.getDisplay(e));
          if (!WbManager.isTest())
          {
            WbSwingUtilities.showErrorMessage(client, msg);
          }
        }
        LogMgr.logError(new CallerInfo(){}, "Error when copying as SQL", e);
      }
    }
    finally
    {
      closeFeedback();
      WbSwingUtilities.showDefaultCursorOnWindow(this.client);
    }
  }

  public void copyAsSqlDeleteInsert(boolean selectedOnly, boolean showSelectColumns)
  {
    this.copyAsSql(ExportType.SQL_DELETE_INSERT, selectedOnly, showSelectColumns);
  }

  public void copyAsSqlDelete(boolean selectedOnly, boolean showSelectColumns)
  {
    this.copyAsSql(ExportType.SQL_DELETE, selectedOnly, showSelectColumns);
  }

  /**
   * Copy the data of the client table as SQL UPDATE statements to the clipboard.
   * Before copying, the primary key columns of the underlying {@link workbench.storage.DataStore}
   * are checked. If none are present, the user is prompted to select the key columns
   *
   * @see workbench.storage.DataStore#hasPkColumns()
   * @see workbench.gui.components.WbTable#detectDefinedPkColumns()
   * @see #copyAsSql(workbench.db.exporter.ExportType, boolean, boolean)
   */
  public void copyAsSqlUpdate(boolean selectedOnly, boolean showSelectColumns)
  {
    copyAsSql(ExportType.SQL_UPDATE, selectedOnly, showSelectColumns);
  }


  /**
   *  Copy the data of the client table into the clipboard using SQL statements
   */
  public void copyAsSql(final ExportType type, final boolean selectedOnly, final boolean showSelectColumns)
  {
    if (this.data == null)
    {
      // Should not happen.
      WbSwingUtilities.showErrorMessage(client, "No DataStore available!");
      LogMgr.logError(new CallerInfo(){}, "Cannot copy without a DataStore!", null);
      return;
    }

    cancelled = false;
    createFeedbackWindow();
    WbThread t = new WbThread("CopyThread")
    {
      @Override
      public void run()
      {
        doCopyAsSql(type, selectedOnly, showSelectColumns);
      }
    };
    t.start();
    showFeedback();
  }

  private boolean needsPK(ExportType type)
  {
    return type == ExportType.SQL_UPDATE || type == ExportType.SQL_MERGE || type == ExportType.SQL_DELETE_INSERT;
  }

  public void doCopyAsSql(final ExportType type, boolean selectedOnly, final boolean showSelectColumns)
  {
    try
    {
      String sql = createSqlString(type, selectedOnly, showSelectColumns);
      if (sql != null && !cancelled)
      {
        Clipboard clp = getClipboard();
        StringSelection sel = new StringSelection(sql);
        clp.setContents(sel, sel);
      }
    }
    finally
    {
      closeFeedback();
      cancelled = false;
    }
  }

  private boolean supportsMultiRowInserts()
  {
    if (data == null) return false;
    if (data.getOriginalConnection() == null) return false;
    return data.getOriginalConnection().getDbSettings().supportsMultiRowInsert();
  }

  public String createSqlString(final ExportType type, boolean selectedOnly, boolean showSelectColumns)
  {
    if (this.data.getRowCount() <= 0) return null;

    if (needsPK(type))
    {
      boolean pkOK = this.data.hasPkColumns();
      if (!pkOK && this.client != null)
      {
        this.client.checkPkColumns(true);
      }

      // re-check in case the user simply clicked OK during the PK prompt
      pkOK = this.data.hasPkColumns();

      // Can't do anything if we don't have PK
      if (!pkOK)
      {
        LogMgr.logError(new CallerInfo(){}, "Cannot create UPDATE or DELETE statements without a primary key!", null);
        if (!WbManager.isTest()) WbSwingUtilities.showErrorMessageKey(client, "ErrCopyNotAvailable");
        return null;
      }
    }

    checkUpdateTable();

    if (cancelled)
    {
      return null;
    }

    List<ColumnIdentifier> columnsToInclude = null;
    if (selectedOnly && !showSelectColumns && this.client.getColumnSelectionAllowed())
    {
      columnsToInclude = client.getColumnsFromSelection();
    }

    if (showSelectColumns && !WbManager.isTest())
    {
      ColumnSelection select = new ColumnSelection(this.client);
      boolean ok = select.selectColumns(false, selectedOnly, false, client.getSelectedRowCount() > 0, false);
      if (!ok)
      {
        cancelled = true;
        return null;
      }

      columnsToInclude = select.getColumns();
      selectedOnly = select.getSelectedOnly();
    }

    // Now check if the selected columns are different to the key columns.
    // If only key columns are selected then creating an UPDATE statement does not make sense.
    if (type == ExportType.SQL_UPDATE)
    {
      List<ColumnIdentifier> keyColumns = new ArrayList<>();
      for (ColumnIdentifier col : data.getResultInfo().getColumns())
      {
        if (col.isPkColumn())
        {
          keyColumns.add(col);
        }
      }

      if (columnsToInclude != null && columnsToInclude.size() == keyColumns.size() && columnsToInclude.containsAll(keyColumns))
      {
        LogMgr.logError(new CallerInfo(){}, "Cannot create UPDATE statement with only key columns!", null);
        if (!WbManager.isTest()) WbSwingUtilities.showErrorMessageKey(client, "ErrCopyNoNonKeyCols");
        cancelled = true;
        return null;
      }
    }

    try
    {
      if (!WbManager.isTest()) WbSwingUtilities.showWaitCursorOnWindow(this.client);

      int[] rows = null;
      if (selectedOnly) rows = this.client.getSelectedRows();

      SqlRowDataConverter converter = new SqlRowDataConverter(data.getOriginalConnection());
      converter.setIncludeTableOwner(Settings.getInstance().getIncludeOwnerInSqlExport());
      converter.setDateLiteralType(Settings.getInstance().getDefaultCopyDateLiteralType());
      converter.setType(type);

      MultiRowInserts multiRowInserts = Settings.getInstance().getUseMultirowInsertForClipboard();
      switch (multiRowInserts)
      {
        case never:
          converter.setUseMultiRowInserts(false);
          break;
        case always:
          converter.setUseMultiRowInserts(true);
          break;
        default:
          converter.setUseMultiRowInserts(supportsMultiRowInserts());
      }
      converter.setTransactionControl(false);
      converter.setIgnoreColumnStatus(true);

      switch (type)
      {
        case SQL_INSERT:
        case SQL_DELETE_INSERT:
        case SQL_INSERT_IGNORE:
          converter.setApplySQLFormatting(Settings.getInstance().getDoFormatInserts());
        case SQL_UPDATE:
          converter.setApplySQLFormatting(Settings.getInstance().getDoFormatUpdates());
        case SQL_DELETE:
          converter.setApplySQLFormatting(Settings.getInstance().getDoFormatDeletes());
        default:
          converter.setApplySQLFormatting(false);
      }

      if (columnsToInclude != null)
      {
        // if columns were manually selected always include all columns regardless of their "type".
        converter.setIncludeIdentityColumns(true);
        converter.setIncludeReadOnlyColumns(true);
      }

      converter.setResultInfo(data.getResultInfo());

      if (type == ExportType.SQL_INSERT || type == ExportType.SQL_DELETE_INSERT)
      {
        if (data.getResultInfo().getUpdateTable() == null)
        {
          String tbl = data.getInsertTable();
          TableIdentifier table = new TableIdentifier(tbl, data.getOriginalConnection());
          converter.setAlternateUpdateTable(table);
        }
      }

      converter.setColumnsToExport(columnsToInclude);
      converter.setBlobMode(BlobMode.DbmsLiteral);

      int count;
      if (rows != null)
      {
        count = rows.length;
      }
      else
      {
        count = data.getRowCount();
      }

      StringBuilder result = new StringBuilder(count * 100);
      RowData rowdata = null;

      StringBuilder start = converter.getStart();
      if (start != null)
      {
        result.append(start);
      }

      if (cancelled) return null;

      for (int row = 0; row < count; row ++)
      {
        if (rows == null)
        {
          rowdata = this.data.getRow(row);
        }
        else
        {
          rowdata = data.getRow(rows[row]);
        }
        if (this.progress != null)
        {
          progress.getMonitor().setCurrentRow(row, -1);
        }
        StringBuilder sql = converter.convertRowData(rowdata, row);
        result.append(sql);
        boolean needsNewLine = false;
        if (type == ExportType.SQL_INSERT)
        {
          needsNewLine = !converter.getUseMultiRowInserts();
        }
        else
        {
          needsNewLine = type != ExportType.SQL_MERGE && !StringUtil.endsWith(sql, '\n');
        }
        if (needsNewLine)
        {
          result.append('\n');
        }
        if (cancelled) return null;
      }

      StringBuilder end = converter.getEnd(count);
      if (end != null)
      {
        result.append(end);
      }
      return result.toString();
    }
    catch (Throwable e)
    {
      if (!cancelled)
      {
        if (e instanceof OutOfMemoryError)
        {
          WbManager.getInstance().showOutOfMemoryError();
        }
        else
        {
          String msg = ResourceMgr.getString("ErrClipCopy");
          msg = StringUtil.replace(msg, "%errmsg%", ExceptionUtil.getDisplay(e));
          if (!WbManager.isTest()) WbSwingUtilities.showErrorMessage(client, msg);
        }
        LogMgr.logError(new CallerInfo(){}, "Error when copying as SQL", e);
      }
    }
    finally
    {
      if (!WbManager.isTest()) WbSwingUtilities.showDefaultCursorOnWindow(this.client);
    }
    return null;
  }

  private void checkUpdateTable()
  {
    if (data == null) return;
    TableIdentifier updateTable = data.getUpdateTable();
    if (updateTable == null && client != null)
    {
      UpdateTableSelector selector = new UpdateTableSelector(client);
      updateTable = selector.selectUpdateTable();
      if (updateTable != null)
      {
        client.getDataStore().setUpdateTable(updateTable);
      }
    }
  }

  @Override
  public void cancelExecution()
  {
    this.cancelled = true;
  }

  @Override
  public boolean confirmCancel()
  {
    return true;
  }

  private void showFeedback()
  {
    if (progress != null)
    {
      progress.showProgressWindow();
    }
  }

  private void createFeedbackWindow()
  {
    if (client != null && client.getRowCount() >= GuiSettings.getCopyDataRowsThreshold())
    {
      MainWindow window = WbSwingUtilities.getMainWindow(client);
      progress = new ProgressDialog(ResourceMgr.getString("MsgCopying"), window, this, false);
      progress.getInfoPanel().setMonitorType(RowActionMonitor.MONITOR_PLAIN);
      progress.getInfoPanel().setInfoText(ResourceMgr.getString("MsgSpoolingRow"));
    }
    else
    {
      progress = null;
    }
  }

  private void closeFeedback()
  {
    if (progress != null)
    {
      progress.setVisible(false);
      progress.dispose();
      progress = null;
    }
  }
}

