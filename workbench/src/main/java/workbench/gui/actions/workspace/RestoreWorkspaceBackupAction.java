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
package workbench.gui.actions.workspace;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;

import workbench.WbManager;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.workspace.WorkspaceBackupListPanel;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.WbAction;
import workbench.gui.components.ValidatingDialog;

import workbench.util.FileUtil;
import workbench.util.FileVersioner;

/**
 * @author Thomas Kellerer
 */
public class RestoreWorkspaceBackupAction
  extends WbAction
{
  private final String CONFIG_PROP = "workbench.gui.restore.wksp.backup.dialog";

  public RestoreWorkspaceBackupAction()
  {
    super();
    this.initMenuDefinition("MnuTxtRestoreWkspBck", null);
    this.setMenuItemName(ResourceMgr.MNU_TXT_TOOLS);
    this.setIcon(null);
    if (!Settings.getInstance().getWorkspaceBackupsAvailable())
    {
      setTooltip(ResourceMgr.getString("MsgNoWkspBck"));
    }
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    WorkspaceBackupListPanel panel = new WorkspaceBackupListPanel();

    String[] options = new String[2];
    options[0] = ResourceMgr.getString("LblRestoreWkspBackup");
    options[1] = ResourceMgr.getString("LblClose");

    JFrame window = WbManager.getInstance().getCurrentWindow();
    ValidatingDialog dialog = new ValidatingDialog(window, ResourceMgr.getString("MnuTxtRestoreWkspBck"), panel, options, true);
    dialog.setCancelOption(1);
    dialog.setButtonEnabled(0, false);

    Settings.getInstance().restoreWindowSize(dialog, CONFIG_PROP);
    WbSwingUtilities.center(dialog, window);
    dialog.setVisible(true);

    int selected = dialog.getSelectedOption();
    File f = panel.getSelectedWorkspaceFile();
    Settings.getInstance().storeWindowSize(dialog, CONFIG_PROP);
    if (selected == 0 && f != null && f.exists())
    {
      File configDir = Settings.getInstance().getWorkspaceDir();
      String name = FileVersioner.stripVersion(f);
      File target = new File(configDir, name);
      try
      {
        LogMgr.logInfo(new CallerInfo(){}, "Restoring workspace backup \"" + f.getAbsolutePath() + "\" to: " + target.getAbsolutePath());
        FileUtil.copy(f, target);
        String msg = ResourceMgr.getFormattedString("MsgWkspBckRestored", f.getAbsolutePath(), target.getAbsolutePath());
        WbSwingUtilities.showMessage(window, msg);
      }
      catch (IOException io)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not restore workspace backup \"" + f.getAbsolutePath() + "\" to directory: " + configDir.getAbsolutePath(), io);
        String errMsg = ResourceMgr.getFormattedString("MsgWkspRestoreErr", f.getAbsolutePath(), target.getAbsolutePath(), io.getLocalizedMessage());
        WbSwingUtilities.showErrorMessage(window, errMsg);
      }
    }

  }

}
