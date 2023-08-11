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

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.workspace.WbWorkspace;
import workbench.workspace.WorkspaceBackupListPanel;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.WbAction;
import workbench.gui.components.ValidatingDialog;

import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class LoadWorkspaceFromBackup
  extends WbAction
{
  private final String CONFIG_PROP = "workbench.gui.restore.wksp.backup.dialog";
  private MainWindow client;

  public LoadWorkspaceFromBackup(MainWindow aClient)
  {
    super();
    this.client = aClient;
    this.initMenuDefinition("MnuTxtLoadWkspFromBck", null);
    this.setMenuItemName(ResourceMgr.MNU_TXT_WORKSPACE);
    this.setIcon(null);
    super.setEnabled(false);
    if (!Settings.getInstance().getWorkspaceBackupsAvailable())
    {
      setTooltip(ResourceMgr.getString("MsgNoWkspBck"));
    }
  }

  @Override
  public void setEnabled(boolean flag)
  {
    boolean wasEnabled = this.isEnabled();
    super.setEnabled(flag);
    if (!wasEnabled && flag)
    {
      setTooltip(ResourceMgr.getDescription("MnuTxtLoadWkspFromBck"));
    }
  }


  @Override
  public void executeAction(ActionEvent e)
  {
    if (this.client == null) return;
    String file = client.getCurrentWorkspaceFile();
    if (StringUtil.isBlank(file)) return;

    File workspace = new File(file);
    WorkspaceBackupListPanel panel = new WorkspaceBackupListPanel(workspace);

    String[] options = new String[2];
    options[0] = ResourceMgr.getString("TxtLoadWksp");
    options[1] = ResourceMgr.getString("LblClose");

    ValidatingDialog dialog = new ValidatingDialog(client, workspace.getName(), panel, options, true);

    Settings.getInstance().restoreWindowSize(dialog, CONFIG_PROP);
    WbSwingUtilities.center(dialog, client);
    dialog.setVisible(true);

    int selected = dialog.getSelectedOption();
    Settings.getInstance().storeWindowSize(dialog, CONFIG_PROP);
    if (selected == 0)
    {
      String originalFile = client.getCurrentWorkspaceFile();
      File f = panel.getSelectedWorkspaceFile();
      if (f != null)
      {
        WbWorkspace toLoad = new WbWorkspace(f.getAbsolutePath());
        try
        {
          toLoad.openForReading();
          client.loadWorkspace(toLoad, false);
        }
        catch (IOException io)
        {
          LogMgr.logError(new CallerInfo(){}, "Could not load backup workspace", io);
        }
        finally
        {
          toLoad.setFilename(originalFile);
        }
      }
      else
      {
        LogMgr.logError(new CallerInfo(){}, "No backup file selected!", null);
      }
    }

  }

}
