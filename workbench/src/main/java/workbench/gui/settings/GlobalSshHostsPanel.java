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
package workbench.gui.settings;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import workbench.interfaces.FileActions;
import workbench.interfaces.Restoreable;
import workbench.interfaces.Validator;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.ssh.SshConfigMgr;
import workbench.ssh.SshHostConfig;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.DeleteListEntryAction;
import workbench.gui.actions.NewListEntryAction;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbToolbar;
import workbench.gui.profiles.SshHostConfigPanel;

import workbench.util.StringUtil;


/**
 *
 * @author Thomas Kellerer
 */
public class GlobalSshHostsPanel
  extends JPanel
  implements Restoreable, ListSelectionListener, FileActions,
             PropertyChangeListener, ChangeListener, Validator
{
  private JList hostList;
  private SshHostConfigPanel hostConfig;
  private WbToolbar toolbar;
  private DefaultListModel<SshHostConfig> configs;
  private boolean ignoreValueChanged = false;

  public GlobalSshHostsPanel()
  {
    super();
    setLayout(new BorderLayout());

    hostList = new JList();
    hostList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    hostList.setBorder(new EmptyBorder(2,2,2,2));

    int width = WbSwingUtilities.calculateCharWidth(hostList, 16);
    Dimension ps = hostList.getPreferredSize();

    JScrollPane scroll = new WbScrollPane(hostList);
    scroll.setPreferredSize(new Dimension(width, ps.height));

    this.toolbar = new WbToolbar();
    this.toolbar.add(new NewListEntryAction(this));
    this.toolbar.add(new DeleteListEntryAction(this));
    toolbar.setBorder(DividerBorder.BOTTOM_DIVIDER);

    hostConfig = new SshHostConfigPanel(true);
    hostConfig.setAgentCheckBoxEnabled(true);
    hostConfig.setEnabled(false);
    hostConfig.addNameChangeListener(this);
    hostConfig.setValidator(this);

    add(toolbar, BorderLayout.NORTH);
    add(scroll, BorderLayout.WEST);
    add(hostConfig, BorderLayout.CENTER);
  }


  @Override
  public boolean isValid(String name)
  {
    if (name == null) return false;

    name = name.trim();
    int selected = hostList.getSelectedIndex();
    return validateName(name, selected);
  }

  public boolean validateName(String name, int ignoreIndex)
  {
    if (name == null) return false;
    name = name.trim();
    int count = configs.getSize();

    for (int index = 0; index < count; index ++)
    {
      if (ignoreIndex == -1 || index != ignoreIndex)
      {
        if (name.equalsIgnoreCase(configs.get(index).getConfigName()))
        {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public void stateChanged(ChangeEvent e)
  {
    SshHostConfig config = (SshHostConfig)hostList.getSelectedValue();
    if (config != null)
    {
      config.setConfigName(hostConfig.getConfigName());
      hostList.repaint();
    }
  }

  @Override
  public void saveSettings()
  {
    applyConfig();
    List<SshHostConfig> l = new ArrayList<>();
    Enumeration<SshHostConfig> elements = this.configs.elements();
    while (elements.hasMoreElements())
    {
      l.add(elements.nextElement());
    }
    SshConfigMgr.getDefaultInstance().setConfigs(l);
    SshConfigMgr.getDefaultInstance().saveGlobalConfig();
  }

  @Override
  public void restoreSettings()
  {
    configs = new DefaultListModel();
    List<SshHostConfig> sshDefs = SshConfigMgr.getDefaultInstance().getGlobalConfigs();
    for (SshHostConfig config : sshDefs)
    {
      configs.addElement(config.createCopy());
    }
    hostList.setModel(configs);
    hostList.addListSelectionListener(this);
    hostList.setSelectedIndex(0);
  }

  private void applyConfig()
  {
    SshHostConfig config = hostConfig.getConfig();
    replaceConfig(config);
  }

  private void replaceConfig(SshHostConfig config)
  {
    if (config == null) return;

    for (int i=0; i < this.configs.size(); i++)
    {
      SshHostConfig cfg = configs.get(i);
      if (StringUtil.equalStringIgnoreCase(cfg.getConfigName(), config.getConfigName()))
      {
        configs.setElementAt(config, i);
      }
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent evt)
  {
    if (ignoreValueChanged) return;
    if (evt.getValueIsAdjusting()) return;

    applyConfig();
    int index = hostList.getSelectedIndex();

    if (index > -1 & index < configs.size())
    {
      SshHostConfig config = configs.getElementAt(index);
      if (config != null)
      {
        this.hostConfig.setConfig(config);
      }
    }
    else
    {
      this.hostConfig.setConfig(null);
    }
  }

  @Override
  public void saveItem() throws Exception
  {
  }

  @Override
  public void deleteItem() throws Exception
  {
    int index = hostList.getSelectedIndex();
    if (index > -1)
    {
      configs.remove(index);
    }

    if (hostList.getModel().getSize() == 0)
    {
      hostConfig.setConfig(null);
      hostConfig.setEnabled(false);
    }
    hostList.repaint();
  }

  @Override
  public void newItem(boolean copyCurrent)
  {
    try
    {
      applyConfig();
      String name = "SSH Host";
      int i = 2;
      while (!validateName(name, -1))
      {
        name = name + " " + i;
        i++;
      }
      SshHostConfig newConfig = new SshHostConfig(name);

      ignoreValueChanged = true;
      configs.addElement(newConfig);
      hostList.setSelectedIndex(configs.size() - 1);
      this.hostConfig.setConfig(newConfig);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not create new SSH configuration", e);
    }
    finally
    {
      ignoreValueChanged = false;
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getPropertyName().equals("name"))
    {
      hostList.repaint();
    }
  }

}
