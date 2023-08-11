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
package workbench.gui.toolbar;

import workbench.db.WbConnection;

import workbench.gui.components.ConnectionInfo;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.WbToolbar;
import workbench.gui.lnf.LnFHelper;

/**
 * The toolbar of the main window.
 *
 * @author Thomas Kellerer
 */
public class MainToolbar
  extends WbToolbar
{
  private ConnectionInfo connectionInfo;
  private String variablePoolId;

  public MainToolbar()
  {
    super();
    addDefaultBorder();
  }

  public void setVariablePoolId(String id)
  {
    variablePoolId = id;
    if (connectionInfo != null)
    {
      connectionInfo.setVariablePoolId(id);
    }
  }

  public void addDefaultBorder()
  {
    if (LnFHelper.isWindowsLookAndFeel())
    {
      this.setBorder(new DividerBorder(DividerBorder.TOP));
      this.setBorderPainted(true);
      this.setRollover(true);
    }
  }

  public void addConnectionInfo()
  {
    if (connectionInfo == null)
    {
      connectionInfo = new ConnectionInfo(getBackground());
      connectionInfo.setVariablePoolId(variablePoolId);
    }
    add(connectionInfo);
  }

  public ConnectionInfo getConnectionInfo()
  {
    return connectionInfo;
  }

  public void setConnection(WbConnection conn)
  {
    if (connectionInfo == null) return;
    connectionInfo.setConnection(conn);
  }

  public void dispose()
  {
    if (connectionInfo != null)
    {
      connectionInfo.dispose();
    }
  }
}
