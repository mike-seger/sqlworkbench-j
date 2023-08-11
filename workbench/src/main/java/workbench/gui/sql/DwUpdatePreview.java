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
package workbench.gui.sql;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.sql.SQLException;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import workbench.WbManager;
import workbench.log.CallerInfo;

import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ValidatingDialog;

import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.storage.DataStore;
import workbench.storage.DmlStatement;
import workbench.storage.SqlLiteralFormatter;

import workbench.util.ExceptionUtil;
import workbench.util.MemoryWatcher;
import workbench.util.MessageBuffer;

/**
 * @author Thomas Kellerer
 */
public class DwUpdatePreview
{

  public boolean confirmUpdate(Component caller, DataStore ds, WbConnection dbConn)
  {
    boolean doSave = true;

    Window win = SwingUtilities.getWindowAncestor(caller);
    MessageBuffer buffer = null;
    try
    {
      List<DmlStatement> stmts = ds.getUpdateStatements(dbConn);
      if (stmts.isEmpty()) return true;

      Dimension max = new Dimension(800,600);
      Dimension pref = new Dimension(400, 300);
      final EditorPanel preview = EditorPanel.createSqlEditor();
      preview.setEditable(false);
      preview.showFindOnPopupMenu();
      preview.setBorder(WbSwingUtilities.EMPTY_BORDER);
      preview.setPreferredSize(pref);
      preview.setMaximumSize(max);
      JPanel display = new JPanel(new BorderLayout(0, 8));
      JScrollPane scroll = new JScrollPane(preview);
      scroll.setMaximumSize(max);
      int maxStatements = Settings.getInstance().getIntProperty("workbench.db.previewsql.maxstatements", 5000);
      if (maxStatements < stmts.size())
      {
        LogMgr.logWarning(new CallerInfo(){}, "Only " + maxStatements + " of " + stmts.size() + " statments displayed. To view all statements increase the value of the property 'workbench.db.previewsql.maxstatements'");
      }
      buffer = new MessageBuffer(maxStatements);

      SqlLiteralFormatter f = new SqlLiteralFormatter(dbConn);
      f.createDbmsBlobLiterals(dbConn);

      boolean lowMemory = false;
      for (DmlStatement dml : stmts)
      {
        buffer.append(dml.getExecutableStatement(f, dbConn));
        buffer.append(";");
        buffer.appendNewLine();
        if (MemoryWatcher.isMemoryLow(true))
        {
          lowMemory = true;
          buffer.clear();
        }
      }

      if (lowMemory)
      {
        WbManager.getInstance().showLowMemoryError();
        return false;
      }

      preview.setText(buffer.getBuffer().toString());
      preview.setCaretPosition(0);
      preview.repaint();
      display.add(scroll, BorderLayout.CENTER);

      JCheckBox copyCbx = new JCheckBox(ResourceMgr.getString("LblCopyToClp"));
      boolean doCopy = Settings.getInstance().getBoolProperty("workbench.db.previewsql.copyclipboard", false);
      copyCbx.setSelected(doCopy);
      display.add(copyCbx, BorderLayout.PAGE_END);

      ValidatingDialog dialog = ValidatingDialog.createDialog(win, display, ResourceMgr.getString("MsgConfirmUpdates"), null, 0, false);
      if (Settings.getInstance().restoreWindowSize(dialog, "workbench.gui.confirmupdate.dialog"))
      {
        WbSwingUtilities.center(dialog, win);
      }
      dialog.setVisible(true);
      Settings.getInstance().storeWindowSize(dialog, "workbench.gui.confirmupdate.dialog");
      doSave = !dialog.isCancelled();
      doCopy = copyCbx.isSelected();
      Settings.getInstance().setProperty("workbench.db.previewsql.copyclipboard", doCopy);

      if (doSave && doCopy)
      {
        copyToClipboard(preview);
      }
    }
    catch (SQLException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when previewing SQL", e);
      String msg = ExceptionUtil.getDisplay(e);
      WbSwingUtilities.showErrorMessage(win, msg);
      return false;
    }
    catch (OutOfMemoryError mem)
    {
      if (buffer != null)
      {
        buffer.clear();
        System.gc();
      }
      WbManager.getInstance().showOutOfMemoryError();
      return false;
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when previewing SQL", th);
      WbSwingUtilities.showErrorMessage(caller, ExceptionUtil.getDisplay(th));
      return false;
    }
    return doSave;
  }

  private void copyToClipboard(EditorPanel panel)
  {
    try
    {
      Clipboard clipboard = panel.getToolkit().getSystemClipboard();
      String selection = panel.getText();
      clipboard.setContents(new StringSelection(selection), null);
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not copy text to clipboard", th);
    }
  }
}
