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

import javax.swing.plaf.TabbedPaneUI;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.gui.lnf.LnFHelper;

/**
 *
 * @author  Thomas Kellerer
 */
public class TabbedPaneUIFactory
{

  public static TabbedPaneUI getBorderLessUI()
  {
    if (LnFHelper.isWindowsLookAndFeel() &&
        Settings.getInstance().getBoolProperty("workbench.gui.replacetabbedpane", true))
    {
      return getClassInstance("workbench.gui.components.BorderLessWindowsTabbedPaneUI");
    }
    return null;
  }

  private static TabbedPaneUI getClassInstance(String className)
  {
    TabbedPaneUI ui = null;
    try
    {
      Class cls = Class.forName(className);
      ui = (TabbedPaneUI)cls.getDeclaredConstructor().newInstance();
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Cannot create custom TabbedPaneUI: " + className, th);
    }
    return ui;
  }
}
