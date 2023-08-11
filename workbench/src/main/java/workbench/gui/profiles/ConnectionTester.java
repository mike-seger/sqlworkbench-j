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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;

import javax.swing.JDialog;

import workbench.resource.ResourceMgr;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.FeedbackWindow;

import workbench.util.ExceptionUtil;
import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class ConnectionTester
  implements Runnable, ActionListener
{
  private static int id = 1;
  private final FeedbackWindow connectingInfo ;
  private final JDialog parentWindow;
  private final ConnectionProfile profile;
  private final WbThread worker;
  private boolean cancelled = false;

  public ConnectionTester(JDialog window, ConnectionProfile profile)
  {
    this.parentWindow = window;
    this.profile = profile;
    this.connectingInfo = new FeedbackWindow(window, ResourceMgr.getString("MsgConnecting"), this, "LblCancelPlain");
    this.worker = new WbThread(this, "ConnectionTester");
  }


  public void showAndStart()
  {
    worker.start();
    WbSwingUtilities.center(connectingInfo, parentWindow);
    connectingInfo.setVisible(true);
  }

  @Override
  public void run()
  {
    WbConnection conn = null;
    try
    {
      conn = ConnectionMgr.getInstance().getConnection(profile, "$Connection-Test$-" + (id++));
      WbSwingUtilities.setVisible(connectingInfo, false);
      WbSwingUtilities.showMessage(parentWindow, ResourceMgr.getFormattedString("MsgBatchConnectOk", profile.getUrl()));
    }
    catch (ThreadDeath td)
    {
      // ignore, this happens when the test was cancelled
    }
    catch (Throwable ex)
    {
      WbSwingUtilities.setVisible(connectingInfo, false);
      if (!cancelled)
      {
        String error = ExceptionUtil.getDisplay(ex, false);
        WbSwingUtilities.showFriendlyErrorMessage(parentWindow, ResourceMgr.getString("ErrConnectFailed"), error);
      }
    }
    finally
    {
      if (conn != null)
      {
        ConnectionMgr.getInstance().abortAll(Collections.singletonList(conn));
      }
      WbSwingUtilities.invokeLater(connectingInfo::dispose);
    }
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    try
    {
      cancelled = true;
      WbSwingUtilities.showWaitCursor(connectingInfo);
      worker.interrupt();

      if (worker.isAlive())
      {
        try
        {
          worker.interrupt();
          worker.stop();
        }
        catch(Throwable th)
        {
          // ignore
        }
      }
      connectingInfo.setVisible(false);
    }
    catch (Throwable th)
    {
      // ignore
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(connectingInfo);
    }
  }

}
