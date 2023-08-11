/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2013, Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.workspace;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Timer;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.gui.MainWindow;

import workbench.util.DurationFormatter;

/**
 *
 * @author Thomas Kellerer
 */
public class WorkspaceBackupDaemon
  implements PropertyChangeListener, ActionListener
{
  private Timer timer;
  private final MainWindow client;

  public WorkspaceBackupDaemon(MainWindow client)
  {
    this.client = client;
    setInterval();
    Settings.getInstance().addPropertyChangeListener(this, Settings.PROP_WKSP_SAVE_INTERVAL);
  }

  private void setInterval()
  {
    int minutes = Settings.getInstance().getAutoSaveWorkspaceInterval();

    int duration = minutes * (int)DurationFormatter.ONE_MINUTE;
    if (duration > 0 && timer == null)
    {
      this.timer = new Timer(duration, this);
      this.timer.setDelay(duration);
    }
    else if (timer != null)
    {
      if (duration <= 0)
      {
        clearTimer();
      }
      else
      {
        this.timer.setDelay(duration);
      }
    }

    if (duration > 0)
    {
      LogMgr.logInfo(new CallerInfo(){}, "Automatic workspace saving initialized every " + minutes + "m");
    }
  }

  private void clearTimer()
  {
    if (this.timer != null)
    {
      this.timer.removeActionListener(this);
      this.timer.stop();
      this.timer = null;
    }
  }
  
  public void shutdown()
  {
    Settings.getInstance().removePropertyChangeListener(this);
    clearTimer();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    setInterval();
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (client.isSavingWorkspace())
    {
      LogMgr.logWarning(new CallerInfo(){}, "Workspace not automatically saved because a save was in progress");
    }
    else
    {
      LogMgr.logDebug(new CallerInfo(){}, "Automatically saving workspace");
      client.saveWorkspace(false);
    }
  }

}
