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
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;

import workbench.util.StringUtil;
import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class FeedbackWindow
  extends JDialog
  implements ActionListener
{
  private JLabel connectLabel;
  private ActionListener cancelAction;
  private JButton cancelButton;
  private JProgressBar progressBar;

  public FeedbackWindow(Frame owner, String msg)
  {
    super(owner, false);
    initComponents(msg, null, null, false);
  }

  public FeedbackWindow(Frame owner, String message, ActionListener action, String buttonTextKey)
  {
    this(owner, message, action, buttonTextKey, false);
  }

  public FeedbackWindow(Frame owner, String message, ActionListener action, String buttonTextKey, boolean modal)
  {
    super(owner, modal);
    initComponents(message, action, buttonTextKey, false);
  }

  public FeedbackWindow(Dialog owner, String message)
  {
    super(owner, true);
    initComponents(message, null, null, false);
  }

  public FeedbackWindow(Dialog owner, String message, ActionListener action, String buttonTextKey)
  {
    this(owner, message, action, buttonTextKey, false);
  }

  public FeedbackWindow(Dialog owner, String message, ActionListener action, String buttonTextKey, boolean showProgress)
  {
    super(owner, true);
    initComponents(message, action, buttonTextKey, showProgress);
  }

  private void initComponents(String msg, ActionListener action, String buttonTextKey, boolean showProgress)
  {
    cancelAction = action;
    JPanel p = new JPanel(new GridBagLayout());
    Border line = WbSwingUtilities.createLineBorder(p);
    int hgap = (int)(IconMgr.getInstance().getToolbarIconSize() * 1.25);
    int vgap = (int)(IconMgr.getInstance().getToolbarIconSize());
    p.setBorder(new CompoundBorder(line, new EmptyBorder(vgap, hgap, vgap, hgap)));

    boolean showCancel = cancelAction != null && buttonTextKey != null;

    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1.0;
    gc.anchor = GridBagConstraints.PAGE_START;
    gc.insets = new Insets(vgap/4,0,vgap/2,0);
    if (!showCancel && !showProgress)
    {
      gc.weighty = 1.0;
    }
    connectLabel = new JLabel(msg);
    FontMetrics fm = connectLabel.getFontMetrics(connectLabel.getFont());

    int width = fm.stringWidth(msg);
    int height = (int)(fm.getHeight());
    Dimension labelSize = new Dimension((int)(width * 1.5), (int)(height * 1.2));
    connectLabel.setMinimumSize(labelSize);
    connectLabel.setHorizontalAlignment(SwingConstants.CENTER);
    p.add(connectLabel, gc);

    if (showProgress)
    {
      this.progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
      this.progressBar.setMinimumSize(labelSize);
      gc.gridy ++;
      gc.anchor = GridBagConstraints.PAGE_START;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weighty = 1.0;
      p.add(progressBar, gc);
    }

    if (showCancel)
    {
      cancelButton = new JButton(ResourceMgr.getString(buttonTextKey));
      cancelButton.addActionListener(this);
      gc.gridy ++;
      gc.weightx = 0;
      gc.weighty = 1.0;
      gc.fill = GridBagConstraints.NONE;
      gc.anchor = GridBagConstraints.PAGE_START;
      gc.insets = new Insets(vgap,0,vgap/2,0);
      p.add(cancelButton, gc);
    }

    setUndecorated(true);
    getRootPane().setWindowDecorationStyle(JRootPane.NONE);
    setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(p, BorderLayout.CENTER);
    pack();
  }

  public JProgressBar getProgressBar()
  {
    return this.progressBar;
  }
  public void showAndStart(final Runnable task)
  {
    EventQueue.invokeLater(() ->
    {
      WbThread t = new WbThread(task, "FeedbackWindow");
      t.start();
      setVisible(true);
    });
  }

  public String getMessage()
  {
    return connectLabel.getText();
  }

  public void setMessage(String msg)
  {
    if (StringUtil.isBlank(msg))
    {
      connectLabel.setText("");
    }
    else
    {
      connectLabel.setText(msg);
    }
    pack();
  }

  public void forceRepaint()
  {
    WbSwingUtilities.invoke(() ->
    {
      doLayout();
      invalidate();
      validate();
      repaint();
    });
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    if (cancelAction != null)
    {
      evt.setSource(this);
      cancelAction.actionPerformed(evt);
    }
  }

  @Override
  public void dispose()
  {
    if (this.cancelButton != null && this.cancelAction != null)
    {
      cancelButton.removeActionListener(cancelAction);
    }
    super.dispose();
  }

}
