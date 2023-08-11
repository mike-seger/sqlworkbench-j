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

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import workbench.interfaces.Validator;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.ssh.PortForwarder;
import workbench.ssh.SshHostConfig;
import workbench.ssh.SshManager;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.WbFilePicker;

import workbench.util.PlatformHelper;
import workbench.util.StringUtil;
import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class SshHostConfigPanel
  extends JPanel
  implements DocumentListener, ActionListener
{
  private boolean canUseAgent;
  private boolean showConfigName;
  private ChangeListener listener;
  private boolean ignoreValueChange;
  private Validator nameValidator;
  private GridBagConstraints defaultErrorConstraints;
  private JLabel errorLabel;
  private char echoChar;

  public SshHostConfigPanel()
  {
    this(false);
  }

  public SshHostConfigPanel(boolean showGlobalName)
  {
    initComponents();

    WbSwingUtilities.adjustButtonWidth(showPasswordButton,22,22);

    showPasswordButton.setText(null);
    showPasswordButton.setIcon(IconMgr.getInstance().getLabelIcon("eye"));
    showPasswordButton.setMargin(this.password.getMargin());
    showPasswordButton.addActionListener(this);
    echoChar = password.getEchoChar();

    defaultErrorConstraints = new GridBagConstraints();
    defaultErrorConstraints.gridx = 0;
    defaultErrorConstraints.gridy = 0;
    defaultErrorConstraints.gridwidth = GridBagConstraints.REMAINDER;
    defaultErrorConstraints.fill = GridBagConstraints.HORIZONTAL;
    defaultErrorConstraints.ipadx = 0;
    defaultErrorConstraints.ipady = 0;
    defaultErrorConstraints.anchor = java.awt.GridBagConstraints.WEST;
    defaultErrorConstraints.insets = new Insets(15, 5, 0, 11);

    errorLabel = new JLabel(ResourceMgr.getString("ErrSshConfigNotUnique"));
    Border b = new CompoundBorder(new LineBorder(Color.RED.brighter(), 1), new EmptyBorder(3, 5, 3, 5));
    errorLabel.setBorder(b);
    errorLabel.setFont(errorLabel.getFont().deriveFont(Font.BOLD));
    errorLabel.setBackground(new Color(255, 255, 220));
    errorLabel.setOpaque(true);

    this.showConfigName = showGlobalName;

    if (showConfigName)
    {
      configName.getDocument().addDocumentListener(this);
    }
    else
    {
      this.remove(labelConfigName);
      this.remove(configName);
    }
    keyPassFile.setAllowMultiple(false);
    keyPassFile.setLastDirProperty("workbench.ssh.keypass.lastdir");
    keyPassFile.setToolTipText(labelKeyPass.getToolTipText());
    if (!PlatformHelper.isWindows())
    {
      // On Linux and MacOS keypass files are often stored in a hidden directory .ssh
      keyPassFile.setShowHiddenFiles(true);
    }
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == this.showPasswordButton)
    {
      if (password.getEchoChar() == (char)0)
      {
        password.setEchoChar(echoChar);
        password.putClientProperty("JPasswordField.cutCopyAllowed", false);
      }
      else
      {
        password.setEchoChar((char)0);
        password.putClientProperty("JPasswordField.cutCopyAllowed", true);
      }
    }
  }

  public void setValidator(Validator validator)
  {
    this.nameValidator = validator;
  }

  public void setAgentCheckBoxEnabled(boolean flag)
  {
    this.canUseAgent = flag;
  }

  public void checkAgentUsage()
  {
    WbThread th = new WbThread("Check JNA libraries")
    {
      @Override
      public void run()
      {
        canUseAgent = SshManager.canUseAgent();
        WbSwingUtilities.invoke(SshHostConfigPanel.this::setTryAgentState);
      }
    };
    th.start();
  }

  private void setTryAgentState()
  {
    if (this.isEnabled())
    {
      useAgent.setEnabled(canUseAgent);
      if (canUseAgent)
      {
        useAgent.setToolTipText(null);
      }
      else
      {
        useAgent.setToolTipText(ResourceMgr.getString("d_LblSshAgentNotAvailable"));
      }
    }
  }

  public String getConfigName()
  {
    if (showConfigName)
    {
      return configName.getText();
    }
    return null;
  }

  public void setConfig(SshHostConfig config)
  {
    try
    {
      ignoreValueChange = true;
      clear();

      setEnabled(config != null);

      if (config != null)
      {
        if (showConfigName)
        {
          configName.setText(StringUtil.coalesce(config.getConfigName(), ""));
        }
        else
        {
          configName.setText("");
        }
        hostname.setText(StringUtil.coalesce(config.getHostname(), ""));
        username.setText(StringUtil.coalesce(config.getUsername(), ""));
        password.setText(StringUtil.coalesce(config.getDecryptedPassword(), ""));
        keyPassFile.setFilename(config.getPrivateKeyFile());
        useAgent.setSelected(config.getTryAgent());

        int port = config.getSshPort();
        if (port > 0 && port != PortForwarder.DEFAULT_SSH_PORT)
        {
          sshPort.setText(Integer.toString(port));
        }
      }
    }
    finally
    {
      ignoreValueChange = false;
    }
  }


  @Override
  public void setEnabled(boolean flag)
  {
    super.setEnabled(flag);
    keyPassFile.setEnabled(flag);
    if (showConfigName)
    {
      configName.setEnabled(flag);
    }
    hostname.setEnabled(flag);
    username.setEnabled(flag);
    password.setEnabled(flag);
    sshPort.setEnabled(flag);
    if (flag)
    {
      useAgent.setEnabled(canUseAgent);
    }
    else
    {
      useAgent.setEnabled(false);
    }
  }

  public void clear()
  {
    try
    {
      ignoreValueChange = true;
      configName.setText("");
      keyPassFile.setFilename("");
      hostname.setText("");
      username.setText("");
      password.setText("");
      sshPort.setText("");
      useAgent.setSelected(false);
    }
    finally
    {
      ignoreValueChange = false;
    }
  }

  private void syncConfig(SshHostConfig config)
  {
    config.setUsername(StringUtil.trimToNull(username.getText()));
    config.setHostname(StringUtil.trimToNull(hostname.getText()));
    config.setPassword(password.getText());
    config.setSshPort(StringUtil.getIntValue(sshPort.getText(), 0));
    config.setPrivateKeyFile(StringUtil.trimToNull(keyPassFile.getFilename()));
    config.setTryAgent(useAgent.isSelected());
    if (showConfigName)
    {
      config.setConfigName(StringUtil.trimToNull(configName.getText()));
    }
  }

  public SshHostConfig getConfig()
  {
    String user = StringUtil.trimToNull(username.getText());
    String host = StringUtil.trimToNull(hostname.getText());
    if (user == null || host == null)
    {
      return null;
    }
    SshHostConfig config = new SshHostConfig();
    syncConfig(config);

    return config;
  }

  public void addNameChangeListener(ChangeListener l)
  {
    this.listener = l;
  }

  private void nameChanged()
  {
    if (ignoreValueChange) return;

    checkName();
    if (listener != null)
    {
      listener.stateChanged(new ChangeEvent(configName.getText()));
    }
  }
  private void checkName()
  {
    if (nameValidator == null) return;
    boolean nameValid = nameValidator.isValid(configName.getText());
    if (nameValid)
    {
      this.remove(errorLabel);
    }
    else
    {
      this.add(errorLabel, defaultErrorConstraints);
    }
  }

  @Override
  public void insertUpdate(DocumentEvent e)
  {
    nameChanged();
  }

  @Override
  public void removeUpdate(DocumentEvent e)
  {
    nameChanged();
  }

  @Override
  public void changedUpdate(DocumentEvent e)
  {
    nameChanged();
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    GridBagConstraints gridBagConstraints;

    labelHost = new JLabel();
    hostname = new JTextField();
    labelUsername = new JLabel();
    username = new JTextField();
    labelPassword = new JLabel();
    labelSshPort = new JLabel();
    sshPort = new JTextField();
    keyPassFile = new WbFilePicker();
    labelKeyPass = new JLabel();
    useAgent = new JCheckBox();
    labelConfigName = new JLabel();
    configName = new JTextField();
    jPanel1 = new JPanel();
    password = new JPasswordField();
    showPasswordButton = new JButton();

    setLayout(new GridBagLayout());

    labelHost.setLabelFor(hostname);
    labelHost.setText(ResourceMgr.getString("LblSshHost")); // NOI18N
    labelHost.setToolTipText(ResourceMgr.getString("d_LblSshHost")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 0);
    add(labelHost, gridBagConstraints);

    hostname.setToolTipText(ResourceMgr.getString("d_LblSshHost")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 11);
    add(hostname, gridBagConstraints);

    labelUsername.setLabelFor(username);
    labelUsername.setText(ResourceMgr.getString("LblSshUser")); // NOI18N
    labelUsername.setToolTipText(ResourceMgr.getString("d_LblSshUser")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 0);
    add(labelUsername, gridBagConstraints);

    username.setToolTipText(ResourceMgr.getString("d_LblSshUser")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 11);
    add(username, gridBagConstraints);

    labelPassword.setLabelFor(password);
    labelPassword.setText(ResourceMgr.getString("LblSshPwd")); // NOI18N
    labelPassword.setToolTipText(ResourceMgr.getString("d_LblSshPwd")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 0);
    add(labelPassword, gridBagConstraints);

    labelSshPort.setLabelFor(sshPort);
    labelSshPort.setText(ResourceMgr.getString("LblSshPort")); // NOI18N
    labelSshPort.setToolTipText(ResourceMgr.getString("d_LblSshPort")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 0);
    add(labelSshPort, gridBagConstraints);

    sshPort.setToolTipText(ResourceMgr.getString("d_LblSshPort")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 11);
    add(sshPort, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 11);
    add(keyPassFile, gridBagConstraints);

    labelKeyPass.setText(ResourceMgr.getString("LblSshKeyFile")); // NOI18N
    labelKeyPass.setToolTipText(ResourceMgr.getString("d_LblSshKeyFile")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 0);
    add(labelKeyPass, gridBagConstraints);

    useAgent.setText(ResourceMgr.getString("LblSshUseAgent")); // NOI18N
    useAgent.setToolTipText(ResourceMgr.getString("d_LblSshUseAgent")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(4, 1, 0, 0);
    add(useAgent, gridBagConstraints);

    labelConfigName.setText(ResourceMgr.getString("LblSshCfgName")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 0);
    add(labelConfigName, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 11);
    add(configName, gridBagConstraints);

    jPanel1.setLayout(new GridBagLayout());

    password.setToolTipText(ResourceMgr.getString("d_LblSshPwd")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    jPanel1.add(password, gridBagConstraints);

    showPasswordButton.setText("...");
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.BOTH;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 2, 0, 0);
    jPanel1.add(showPasswordButton, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 11);
    add(jPanel1, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JTextField configName;
  private JTextField hostname;
  private JPanel jPanel1;
  private WbFilePicker keyPassFile;
  private JLabel labelConfigName;
  private JLabel labelHost;
  private JLabel labelKeyPass;
  private JLabel labelPassword;
  private JLabel labelSshPort;
  private JLabel labelUsername;
  private JPasswordField password;
  private JButton showPasswordButton;
  private JTextField sshPort;
  private JCheckBox useAgent;
  private JTextField username;
  // End of variables declaration//GEN-END:variables
}
