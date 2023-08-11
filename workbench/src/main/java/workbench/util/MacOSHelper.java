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
package workbench.util;

import java.awt.Desktop;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;
import java.awt.desktop.PreferencesEvent;
import java.awt.desktop.PreferencesHandler;
import java.awt.desktop.QuitEvent;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitResponse;

import workbench.WbManager;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.gui.actions.OptionsDialogAction;
import workbench.gui.dialogs.WbAboutDialog;

/**
 * This class - if running on Mac OS - will install the needed application handlers.
 *
 * @see Desktop#setAboutHandler(java.awt.desktop.AboutHandler)
 * @see Desktop#setPreferencesHandler(java.awt.desktop.PreferencesHandler)
 * @see Desktop#setQuitHandler(java.awt.desktop.QuitHandler)
 *
 * @author Thomas Kellerer
 */
public class MacOSHelper
  implements AboutHandler, PreferencesHandler, QuitHandler
{
  private static boolean isMacOS = System.getProperty("os.name").startsWith("Mac OS");

  public static boolean isMacOS()
  {
    return isMacOS;
  }

  public void installApplicationHandler()
  {
    if (!isMacOS()) return;

    final CallerInfo ci = new CallerInfo(){};
    try
    {
      LogMgr.logInfo(ci, "Installing application handlers");
      Desktop desktop = Desktop.getDesktop();
      desktop.setAboutHandler(this);
      desktop.setPreferencesHandler(this);
      desktop.setQuitHandler(this);
    }
    catch (Throwable e)
    {
      LogMgr.logError(ci, "Could not install application handlers", e);
    }
  }

  @Override
  public void handleAbout(AboutEvent e)
  {
    LogMgr.logDebug(new CallerInfo(){}, "handlAbout() called()");
    WbAboutDialog.showDialog(null);
  }

  @Override
  public void handlePreferences(PreferencesEvent e)
  {
    LogMgr.logDebug(new CallerInfo(){}, "handlePreferences() called()");
    OptionsDialogAction.showOptionsDialog();
  }

  @Override
  public void handleQuitRequestWith(QuitEvent e, QuitResponse response)
  {
    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logDebug(ci, "handleQuitRequestWith() called");

    if (!WbManager.getInstance().canExit())
    {
      LogMgr.logDebug(ci, "Canelling quit request");
      response.cancelQuit();
      return;
    }
    WbManager.getInstance().doShutdown(0);
  }
}
