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
package workbench.sql.wbcommands.console;

import java.sql.SQLException;

import javax.swing.JFrame;

import workbench.WbManager;
import workbench.console.WbConsole;
import workbench.console.WbConsoleFactory;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.MasterPasswordDialog;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.GlobalPasswordManager;
import workbench.util.StringUtil;

/**
 * A SQL command for the console to set the master password to encrypt connection profile passwords.
 *
 * @author  Thomas Kellerer
 */
public class WbSetMasterPwd
  extends SqlCommand
{
  public static final String VERB = "WbSetMasterPassword";

  public WbSetMasterPwd()
  {
    super();
  }

  @Override
  public StatementRunnerResult execute(String aSql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();
    if (!GlobalPasswordManager.getInstance().showPasswordPrompt(true))
    {
      result.setFailure();
      return result;
    }

    String pwd = null;
    if (WbManager.getInstance().isGUIMode())
    {
      JFrame win = WbManager.getInstance().getCurrentWindow();
      MasterPasswordDialog pwdDialog = new MasterPasswordDialog(win, false);
      Settings.getInstance().restoreWindowSize(pwdDialog);
      WbSwingUtilities.center(pwdDialog, win);
      pwdDialog.setVisible(true);
      if (!pwdDialog.wasCancelled() && pwdDialog.getMasterPassword() != null)
      {
        pwd = pwdDialog.getMasterPassword();
      }
    }
    else
    {
      pwd = getPasswordFromConsole(result);
    }

    if (pwd != null && result.isSuccess())
    {
      GlobalPasswordManager.getInstance().applyNewPassword(pwd);
      Settings.getInstance().saveSettings(false);
      ConnectionMgr.getInstance().saveProfiles();
      result.addMessageByKey("MsgMasterPwdSet");
    }

    return result;
  }

  private String getPasswordFromConsole(StatementRunnerResult result)
  {
    String msg = ResourceMgr.getString("LblMasterPwdWarn");
    msg = msg.replace("<br>", "\n");
    msg = msg.replace("<html>", "");
    msg = msg.replace("</html>", "");
    System.out.println(msg);
    System.out.println("");

    WbConsole console = WbConsoleFactory.getConsole();

    String pwd1 = console.readPassword(ResourceMgr.getString("LblNewPwd") + ": ");
    if (StringUtil.isEmpty(pwd1))
    {
      result.addErrorMessageByKey("MsgNoPwd");
      return null;
    }
    String pwd2 = console.readPassword(ResourceMgr.getString("LblPwdRepeat") + ": ");
    if (!StringUtil.equalString(pwd1, pwd2))
    {
      result.addErrorMessageByKey("MsgPwdNoMatch");
      return null;
    }
    result.setSuccess();
    return pwd1;
  }

  @Override
  protected boolean isConnectionRequired()
  {
    return false;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
