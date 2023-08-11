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

import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import workbench.interfaces.MainPanel;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ConnectionProfile;
import workbench.db.DbSwitcher;
import workbench.db.WbConnection;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;

import workbench.util.CollectionUtil;
import workbench.util.WbThread;

/**
 * A drop down to present a list of available "databases".
 *
 * Changing the "current database" is done using a {@link DbSwitcher}.
 *
 * @author Thomas Kellerer
 * @see DbSwitcher
 */
public class SwitchDbComboBox
  extends JComboBox<String>
  implements ItemListener, PropertyChangeListener
{
  private boolean switchAll = false;
  private boolean ignoreItemChange = false;
  private WbConnection connection;
  private DbSwitcher switcher;
  private String variablePoolId;

  public SwitchDbComboBox()
  {
    this.addItemListener(this);
  }

  public SwitchDbComboBox(WbConnection conn)
  {
    setConnection(conn);
    this.addItemListener(this);
  }

  public void setVariablePoolId(String variablePoolId)
  {
    this.variablePoolId = variablePoolId;
  }

  public void setConnection(WbConnection conn)
  {
    if (this.connection != null)
    {
      this.connection.removeChangeListener(this);
    }

    this.connection = conn;
    this.switcher = DbSwitcher.Factory.createDatabaseSwitcher(conn);
    this.switcher.setVariablePoolId(variablePoolId);
    if (conn == null)
    {
      this.clear();
    }
    else
    {
      this.retrieve();
      if (conn.isShared())
      {
        setSwitchWindow(true);
      }
      this.connection.addChangeListener(this);
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getSource() != this.connection) return;

    if (WbConnection.PROP_CATALOG_LIST.equals(evt.getPropertyName()))
    {
      WbSwingUtilities.invokeLater(this::startRetrieve);
    }
    else if (WbConnection.PROP_CATALOG.equals(evt.getPropertyName()) && !ignoreItemChange)
    {
      selectCurrentDatabase(connection);
    }
  }

  private void startRetrieve()
  {
    WbThread th = new WbThread("Retrieve catalogs")
    {
      @Override
      public void run()
      {
        retrieve();
      }
    };
    th.start();
  }

  public void retrieve()
  {
    if (switcher == null) return;
    if (connection == null) return;

    clear();

    int width = WbSwingUtilities.calculateCharWidth(this, 20);
    Dimension d = getPreferredSize();
    d.setSize(width, d.height);
    this.setMaximumSize(d);

    List<String> dbs = connection.getObjectCache().getAvailableDatabases();
    if (dbs != null)
    {
      WbSwingUtilities.invoke(() -> {
        setModel(new DefaultComboBoxModel<>(dbs.toArray(String[]::new)));
      });
      selectCurrentDatabase(connection);
    }
  }

  public void selectCurrentDatabase(WbConnection conn)
  {
    try
    {
      ignoreItemChange = true;
      String current = switcher.getCurrentDatabase(conn);
      if (current != null)
      {
        this.setSelectedItem(current);
      }
    }
    finally
    {
      ignoreItemChange = false;
    }
  }

  public void setSwitchWindow(boolean flag)
  {
    this.switchAll = flag;
  }

  public void clear()
  {
    this.setModel(new DefaultComboBoxModel<>());
  }

  private boolean isConnectInProgress()
  {
    MainWindow window = WbSwingUtilities.getMainWindow(this);
    if (window == null) return false;

    return window.isConnectInProgress();
  }

  @Override
  public void itemStateChanged(ItemEvent e)
  {
    if (ignoreItemChange) return;

    if (e == null) return;

    if (e.getSource() == this && e.getStateChange() == ItemEvent.SELECTED)
    {
      if (isConnectInProgress())
      {
        // try later when the connect is finished
        WbSwingUtilities.invokeLater(this::changeDatabase);
      }
      else
      {
        changeDatabase();
      }
    }
  }

  public String getSelectedDatabase()
  {
    return (String)getSelectedItem();
  }

  private void changeDatabase()
  {
    String dbName = getSelectedDatabase();
    if (dbName == null) return;

    if (switcher == null) return;

    if (switchAll)
    {
      switchAll(switcher, dbName);
    }
    else if (connection != null)
    {
      try
      {
        switcher.switchDatabase(connection, dbName);
      }
      catch (SQLException sql)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not switch database", sql);
      }
    }
  }

  private void switchAll(DbSwitcher switcher, String dbName)
  {
    MainWindow window = WbSwingUtilities.getMainWindow(this);
    if (window == null) return;

    if (switcher.needsReconnect())
    {
      ConnectionProfile profile = window.getCurrentProfile();
      if (profile == null) return;

      String newUrl = switcher.getUrlForDatabase(profile.getUrl(), dbName);
      profile.switchToTemporaryUrl(newUrl);
      try
      {
        ignoreItemChange = true;
        window.connectTo(profile, false, false);
      }
      finally
      {
        ignoreItemChange = false;
      }
    }
    else
    {
      switchAllConnections(window, dbName);
    }
  }

  private void switchAllConnections(MainWindow window, String dbName)
  {
    final CallerInfo ci = new CallerInfo(){};
    Set<String> changedConnections = CollectionUtil.caseInsensitiveSet();
    int tabCount = window.getTabCount();
    for (int i=0; i < tabCount; i++)
    {
      Optional<MainPanel> panel = window.getPanel(i);
      if (!panel.isPresent()) continue;

      MainPanel p = panel.get();
      WbConnection conn = p.getConnection();
      if (conn == null) continue;

      if (conn.isBusy())
      {
        LogMgr.logDebug(ci, "Skipping database switch for panel " + p.getTabTitle() + " (" + p.getId() + ") because the connection is busy");
      }

      if (changedConnections.contains(conn.getId())) continue;
      if (!conn.isShared()) continue;

      LogMgr.logDebug(ci, "Switching database for panel " + p.getTabTitle() + " (" + p.getId() + ")");
      try
      {
        switcher.switchDatabase(conn, dbName);
        changedConnections.add(conn.getId());
      }
      catch (SQLException ex)
      {
        LogMgr.logError(ci, "Could not switch database for panel " + p.getTabTitle() + " (" + p.getId() + ")", ex);
      }
    }
  }

}
