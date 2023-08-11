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

import workbench.WbManager;
import workbench.console.WbConsole;
import workbench.console.WbConsoleFactory;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionMgr;

import workbench.gui.WbSwingUtilities;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.GlobalPasswordManager;
import workbench.util.StringUtil;

/**
 * A SQL command for the console to remove the master password used to encrypt connection profile passwords.
 *
 * @author  Thomas Kellerer
 */
public class WbRemoveMasterPwd
  extends SqlCommand
{
  public static final String VERB = "WbRemoveMasterPassword";

  public WbRemoveMasterPwd()
  {
    super();
  }

  @Override
  public StatementRunnerResult execute(String aSql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();
    if (!Settings.getInstance().getUseMasterPassword())
    {
      result.addErrorMessageByKey("ErrNoMasterPwdSet");
      return result;
    }

    if (!GlobalPasswordManager.getInstance().showPasswordPrompt(true))
    {
      result.setFailure();
      return result;
    }

    boolean confirm = false;

    String prompt = ResourceMgr.getString("MsgConfirmRemoveMasterPwd");
    if (WbManager.getInstance().isGUIMode())
    {
      confirm = WbSwingUtilities.getYesNo(WbManager.getInstance().getCurrentWindow(), prompt);
    }
    else
    {
      WbConsole console = WbConsoleFactory.getConsole();
      String yes = ResourceMgr.getString("MsgConfirmYes").toLowerCase();
      String no = ResourceMgr.getString("MsgConfirmNo").toLowerCase();
      prompt += " (" + yes + "/" + no + "): ";
      String choice = console.readLineWithoutHistory(prompt);
      confirm = yes.equalsIgnoreCase(StringUtil.trim(choice));
    }
    if (confirm)
    {
      GlobalPasswordManager.getInstance().applyNewPassword(null);
      Settings.getInstance().saveSettings(false);
      ConnectionMgr.getInstance().saveProfiles();
      result.addMessageByKey("MsgMasterPwdRemoved");
    }
    result.setSuccess();
    return result;
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
