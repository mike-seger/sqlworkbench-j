/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.gui.profiles;

import java.awt.Window;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import workbench.resource.ResourceMgr;
import workbench.ssh.SshHostConfig;

import workbench.db.ConnectionProfile;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ValidatingDialog;

import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class ConnectionGuiHelper
{

  public static boolean doPrompt(Window parent, ConnectionProfile profile)
  {
    if (profile == null) return true;

    boolean needsDBPwd = profile.needsPasswordPrompt();
    boolean needsSshPwd = profile.needsSSHPasswordPrompt();

    if (profile.getPromptForUsername())
    {
      boolean ok = promptUsername(parent, profile);
      if (needsSshPwd && ok)
      {
        return promptForSSHPassword(parent, profile);
      }
      return ok;
    }

    // TODO: combine both passwords into a single prompt
    if (needsDBPwd)
    {
      return promptPassword(parent, profile);
    }

    if (needsSshPwd)
    {
      return promptForSSHPassword(parent, profile);
    }
    return true;
  }

  public static boolean promptUsername(Window parent, ConnectionProfile profile)
  {
    if (profile == null) return false;

    LoginPrompt prompt = new LoginPrompt(profile.getSettingsKey());
    boolean ok = ValidatingDialog.showConfirmDialog(parent, prompt, ResourceMgr.getFormattedString("TxtEnterLogin", profile.getName()));
    if (!ok) return false;

    profile.setPassword(prompt.getPassword());
    profile.setTemporaryUsername(prompt.getUserName());
    return true;
  }

  public static boolean promptPassword(Window parent, ConnectionProfile profile)
  {
    if (profile == null) return false;

    String title = profile.getName();
    String msg =  ResourceMgr.getFormattedString("MsgInputPwd", profile.getUsername());
    String pwd = WbSwingUtilities.passwordPrompt(parent, title, msg);
    if (StringUtil.isEmpty(pwd)) return false;

    profile.setPassword(pwd);
    return true;
  }

  public static boolean promptForSSHPassword(Window parent, ConnectionProfile profile)
  {
    if (profile == null) return false;
    SshHostConfig config = profile.getSshHostConfig();
    if (config == null) return true;

    String title;
    String msg;

    if (config.getPrivateKeyFile() == null)
    {
      msg = ResourceMgr.getFormattedString("MsgInputPwd", config.getUsername() + "@" + config.getHostname() + ":" + config.getSshPort());
      title = ResourceMgr.getFormattedString("MsgInputSshPwd");
    }
    else
    {
      title = ResourceMgr.getString("MsgInputSshPassPhrase");
      WbFile f = new WbFile(config.getPrivateKeyFile());
      msg = f.getFileName();
    }

    String pwd = WbSwingUtilities.passwordPrompt(parent, title, msg);
    if (StringUtil.isEmpty(pwd)) return false;
    config.setTemporaryPassword(pwd);
    return true;
  }

  public static void testConnection(final JComponent caller, final ConnectionProfile profile)
  {
    final Window window = SwingUtilities.getWindowAncestor(caller);

    if (!doPrompt(window, profile)) return;

    ConnectionTester tester = new ConnectionTester((JDialog)window, profile);
    tester.showAndStart();
  }

}
