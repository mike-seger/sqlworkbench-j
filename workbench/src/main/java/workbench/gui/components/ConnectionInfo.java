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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.ssh.SshConfig;

import workbench.db.ConnectionProfile;
import workbench.db.ConnectionPropertiesReader;
import workbench.db.DbSwitcher;
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.WbAction;
import workbench.gui.renderer.ColorUtils;
import workbench.gui.tools.ConnectionInfoPanel;

import workbench.util.CollectionUtil;

/**
 * A panel to show information about a database connection.
 *
 * For read-only connections an icon will be displayed, that
 * also handles a double click to temporarily disable the
 * read-only mode.
 *
 * @author  Thomas Kellerer
 * @see SwitchDbComboBox
 */
public class ConnectionInfo
  extends JPanel
  implements PropertyChangeListener, ActionListener, MouseListener
{
  private WbConnection sourceConnection;
  private final Color defaultBackground;
  private final WbAction showInfoAction;
  private final WbLabelField infoText;
  private JLabel iconLabel;
  private boolean useCachedSchema;
  private SwitchDbComboBox dbSwitcher;
  private final JPanel contentPanel;
  private String variablePoolId;

  public ConnectionInfo(Color aBackground)
  {
    super(new BorderLayout(2,0));
    this.contentPanel = new JPanel(new GridBagLayout());
    this.add(contentPanel, BorderLayout.CENTER);
    infoText = new WbLabelField();
    infoText.setOpaque(false);

    contentPanel.setOpaque(true);

    if (aBackground != null)
    {
      contentPanel.setBackground(aBackground);
      defaultBackground = aBackground;
    }
    else
    {
      defaultBackground = infoText.getBackground();
    }
    showInfoAction = new WbAction(this, "show-info");
    showInfoAction.setMenuTextByKey("MnuTxtConnInfo");
    showInfoAction.setEnabled(false);
    infoText.addPopupAction(showInfoAction);
    infoText.setText(ResourceMgr.getString("TxtNotConnected"));
    GridBagConstraints gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1.0;
    gc.weighty = 1.0;
    gc.gridx = 1; // The "locked" icon will be displayed at gridx = 0
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.LINE_START;
    contentPanel.add(infoText, gc);
  }

  public void setVariablePoolId(String id)
  {
    this.variablePoolId = id;
    if (this.dbSwitcher != null)
    {
      this.dbSwitcher.setVariablePoolId(variablePoolId);
    }
  }

  private void removeDbSwitcher()
  {
    if (dbSwitcher != null)
    {
      remove(dbSwitcher);
      dbSwitcher.clear();
      dbSwitcher = null;
    }
  }

  private void addDbSwitcher(WbConnection conn)
  {
    if (conn == null) return;

    if (dbSwitcher == null)
    {
      LogMgr.logTrace(new CallerInfo(){}, "Adding DB switcher in thread: " + Thread.currentThread() + " for: " + conn);
      dbSwitcher = new SwitchDbComboBox(conn);
      add(dbSwitcher, BorderLayout.LINE_START);
    }
    else
    {
      LogMgr.logTrace(new CallerInfo(){}, "Updating DB switcher in thread: " + Thread.currentThread() + " for: " + conn);
      dbSwitcher.setConnection(conn);
    }
    dbSwitcher.setEnabled(!conn.isBusy());
  }

  private boolean useDbSwitcher(WbConnection conn)
  {
    if (conn == null) return false;
    if (conn.isClosed()) return false;
    if (!conn.getDbSettings().enableDatabaseSwitcher()) return false;

    DbSwitcher switcher = DbSwitcher.Factory.createDatabaseSwitcher(conn);
    return switcher != null && switcher.supportsSwitching(conn);
  }

  private void updateDBSwitcher(WbConnection conn)
  {
    if (useDbSwitcher(conn))
    {
      addDbSwitcher(conn);
    }
    else
    {
      if (conn != null) LogMgr.logDebug(new CallerInfo(){}, "Removing DB switcher for: " + conn);
      removeDbSwitcher();
    }
  }

  private boolean connectionsAreEqual(WbConnection one, WbConnection other)
  {
    if (one == null && other == null) return true;
    if (one == null || other == null) return false;
    if (one == other) return true;
    if (one.getId().equals(other.getId()))
    {
      return one.getUrl().equals(other.getUrl());
    }
    return false;
  }

  public void setDbSwitcherEnabled(boolean flag)
  {
    if (this.dbSwitcher != null)
    {
      this.dbSwitcher.setEnabled(flag);
    }
  }

  public void setConnection(WbConnection aConnection)
  {
    if (connectionsAreEqual(sourceConnection, aConnection)) return;

    if (this.sourceConnection != null)
    {
      this.sourceConnection.removeChangeListener(this);
    }

    this.sourceConnection = aConnection;

    Color bkg = null;
    Color fg = null;

    if (this.sourceConnection != null)
    {
      this.sourceConnection.addChangeListener(this);
      ConnectionProfile p = aConnection.getProfile();
      if (p != null)
      {
        bkg = p.getInfoDisplayColor();
        if (GuiSettings.useContrastColor() && bkg != null)
        {
          fg = ColorUtils.getContrastColor(bkg);
        }
      }
    }

    updateDBSwitcher(sourceConnection);

    useCachedSchema = true;
    try
    {
      updateDisplay();
    }
    finally
    {
      useCachedSchema = false;
    }

    final Color background = bkg;
    final Color foreground = fg;

    EventQueue.invokeLater(() ->
    {
      showInfoAction.setEnabled(sourceConnection != null);

      if (background == null)
      {
        contentPanel.setBackground(defaultBackground);
      }
      else
      {
        contentPanel.setBackground(background);
      }

      if (foreground == null)
      {
        infoText.setForeground(UIManager.getDefaults().getColor("Label.foreground"));
        Font f = infoText.getFont();
        if (GuiSettings.useBoldFontForConnectionInfo())
        {
          infoText.setFont(f.deriveFont(f.getStyle() - Font.BOLD));
        }
      }
      else
      {
        infoText.setForeground(foreground);
        if (GuiSettings.useBoldFontForConnectionInfo())
        {
          infoText.setFont(infoText.getFont().deriveFont(Font.BOLD));
        }
      }
    });
  }

  private void updateDisplay()
  {
    WbSwingUtilities.invoke(this::_updateDisplay);
  }

  private void _updateDisplay()
  {
    WbConnection conn = this.sourceConnection;
    if (conn != null && !conn.isClosed())
    {
      String display = conn.getDisplayString(useCachedSchema);
      infoText.setText(display);
      StringBuilder tip = new StringBuilder(50);
      tip.append("<html><div style=\"white-space:nowrap;\"><p>");
      tip.append(conn.getDatabaseProductName());
      tip.append(" ");
      tip.append(conn.getDatabaseVersion().toString());
      tip.append("&nbsp;&nbsp;</p><p>");
      tip.append(ResourceMgr.getFormattedString("TxtDrvVersion", conn.getDriverVersion()));
      tip.append("&nbsp;&nbsp;</p>");
      ConnectionPropertiesReader reader = ConnectionPropertiesReader.Fatory.getReader(conn);
      if (reader != null)
      {
        Map<String, String> info = reader.getConnectionProperties(conn);
        if (CollectionUtil.isNonEmpty(info))
        {
          for (Map.Entry<String, String> entry : info.entrySet())
          {
            tip.append("<p>");
            tip.append(entry.getKey());
            tip.append(": ");
            tip.append(entry.getValue());
            tip.append("&nbsp;&nbsp;</p>");
          }
        }
      }

      tip.append("<p>Workbench connection: ");
      tip.append(conn.getId());
      tip.append("&nbsp;&nbsp;</p>");

      SshConfig sshConfig = conn.getProfile().getSshConfig();
      if (sshConfig != null)
      {
        tip.append("<p>SSH: ");
        tip.append(sshConfig.getInfoString());
        tip.append("</p>");
      }
      tip.append("</div></html>");
      infoText.setToolTipText(tip.toString());
    }
    else
    {
      infoText.setText(ResourceMgr.getString("TxtNotConnected"));
      infoText.setToolTipText(null);
    }

    infoText.setBackground(this.getBackground());
    infoText.setCaretPosition(0);
    showMode();

    invalidate();
    validate();

    if (getParent() != null)
    {
      getParent().invalidate();
      // this seems to be the only way to resize the component
      // approriately after setting a new text when using the dreaded GTK+ look and feel
      getParent().validate();
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getSource() == this.sourceConnection)
    {
      switch (evt.getPropertyName())
      {
        case WbConnection.PROP_CATALOG:
        case WbConnection.PROP_SCHEMA:
        case WbConnection.PROP_READONLY:
          updateDisplay();
          break;
        case WbConnection.PROP_BUSY:
          if (this.dbSwitcher != null)
          {
            boolean connectionIsBusy = Boolean.parseBoolean((String)evt.getNewValue());
            this.dbSwitcher.setEnabled(!connectionIsBusy);
          }
      }
    }
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (this.sourceConnection == null) return;
    // if (!WbSwingUtilities.isConnectionIdle(this, sourceConnection)) return;

    ConnectionInfoPanel.showConnectionInfo(sourceConnection);
  }

  private void showMode()
  {
    String tooltip = null;
    if (sourceConnection == null)
    {
      hideIcon();
    }
    else
    {
      ConnectionProfile profile = sourceConnection.getProfile();
      boolean profileReadOnly = profile.isReadOnly();
      boolean sessionReadonly = sourceConnection.isSessionReadOnly();
      if (profileReadOnly && !sessionReadonly)
      {
        // the profile is set to read only, but it was changed temporarily
        showIcon("unlocked");
        tooltip = ResourceMgr.getString("TxtConnReadOnlyOff");
      }
      else if (profileReadOnly || sessionReadonly)
      {
        showIcon("lock");
        tooltip = ResourceMgr.getString("TxtConnReadOnly");
      }
      else
      {
        hideIcon();
      }
    }
    if (this.iconLabel != null)
    {
      this.iconLabel.setToolTipText(tooltip);
    }
    invalidate();
  }

  private void hideIcon()
  {
    if (iconLabel != null)
    {
      iconLabel.removeMouseListener(this);
      contentPanel.remove(iconLabel);
      iconLabel = null;
    }
  }

  private void showIcon(String name)
  {
    if (iconLabel == null)
    {
      iconLabel = new JLabel();
      iconLabel.setOpaque(false);
      iconLabel.setBackground(getBackground());
    }
    iconLabel.addMouseListener(this);
    ImageIcon png = IconMgr.getInstance().getPngIcon(name, IconMgr.getInstance().getToolbarIconSize());
    iconLabel.setIcon(png);
    GridBagConstraints gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 0.0;
    gc.weighty = 0.0;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.LINE_START;
    contentPanel.add(iconLabel, gc);
  }

  @Override
  public void mouseClicked(MouseEvent e)
  {
    if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1)
    {
      if (sourceConnection == null)
      {
        // should not happen
        LogMgr.logWarning(new CallerInfo(){}, "Readonly change requested but no connection available!");
        return;
      }
      ConnectionProfile profile = sourceConnection.getProfile();
      boolean profileReadOnly = profile.isReadOnly();
      boolean sessionReadOnly = sourceConnection.isSessionReadOnly();
      if (!sessionReadOnly && profileReadOnly)
      {
        sourceConnection.resetSessionReadOnly();
      }
      if (profileReadOnly && sessionReadOnly)
      {
        Window parent = SwingUtilities.getWindowAncestor(this);
        boolean makeRead = WbSwingUtilities.getYesNo(parent, ResourceMgr.getString("MsgDisableReadOnly"));
        if (makeRead)
        {
          sourceConnection.setSessionReadOnly(false);
        }
      }
    }
  }

  @Override
  public void mousePressed(MouseEvent e)
  {
  }

  @Override
  public void mouseReleased(MouseEvent e)
  {
  }

  @Override
  public void mouseEntered(MouseEvent e)
  {
  }

  @Override
  public void mouseExited(MouseEvent e)
  {
  }

  public void dispose()
  {
    if (showInfoAction != null)
    {
      showInfoAction.dispose();
    }
    infoText.dispose();
    if (this.sourceConnection != null)
    {
      this.sourceConnection.removeChangeListener(this);
    }
    if (this.dbSwitcher != null)
    {
      this.dbSwitcher.clear();
    }
  }
}
