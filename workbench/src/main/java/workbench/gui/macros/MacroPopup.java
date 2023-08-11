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
package workbench.gui.macros;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.EscAction;

/**
 * Display a floating window with the MacroTree.
 * When double clicking a macro in the tree, the macro is executed in the
 * passed MainWindow
 *
 * @author Thomas Kellerer
 */
public class MacroPopup
  extends JDialog
  implements WindowListener, ActionListener
{
  private final MacroPanel panel;
  private EscAction closeAction;
  private boolean isClosing;

  public MacroPopup(MainWindow parent)
  {
    super(parent, false);
    this.panel = new MacroPanel(parent, GuiSettings.getShowToolbarInMacroPopup(), false);
    this.getContentPane().add(panel);
    setTitle(ResourceMgr.getString("TxtMacroManagerWindowTitle"));
    if (!Settings.getInstance().restoreWindowPosition(this))
    {
      setLocation(parent.getX() + parent.getWidth() - getWidth()/2, parent.getY() + 25);
    }
    if (!Settings.getInstance().restoreWindowSize(this))
    {
      setSize(200,400);
    }

    if (GuiSettings.getCloseMacroPopupWithEsc())
    {
      closeAction = new EscAction(this, this);
    }
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(this);
  }

  private void closeWindow()
  {
    setVisible(false);
    dispose();
  }

  public boolean isClosing()
  {
    return isClosing;
  }

  @Override
  public void windowOpened(WindowEvent e)
  {
  }

  private void doClose()
  {
    isClosing = true;
    if (panel.isModified())
    {
      int result = WbSwingUtilities.getYesNoCancel(this, ResourceMgr.getString("MsgConfirmUnsavedMacros"));
      if (result == JOptionPane.CANCEL_OPTION)
      {
        isClosing = false;
        return;
      }
      if (result == JOptionPane.YES_OPTION)
      {
        panel.saveMacros(false);
      }
    }
    panel.saveExpandedGroups();
    Settings.getInstance().storeWindowPosition(this);
    Settings.getInstance().storeWindowSize(this);
    removeWindowListener(this);
    panel.dispose();
    EventQueue.invokeLater(this::closeWindow);
  }

  public void saveWorkspaceSettings()
  {
    panel.saveWorkspaceSettings();
  }

  public void workspaceChanged()
  {
    panel.workspaceChanged();
  }

  @Override
  public void windowClosing(WindowEvent e)
  {
    if (!isClosing)
    {
      doClose();
    }
  }

  @Override
  public void windowClosed(WindowEvent e)
  {
  }

  @Override
  public void windowIconified(WindowEvent e)
  {
  }

  @Override
  public void windowDeiconified(WindowEvent e)
  {
  }

  @Override
  public void windowActivated(WindowEvent e)
  {
  }

  @Override
  public void windowDeactivated(WindowEvent e)
  {
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == this.closeAction && !isClosing)
    {
      doClose();
    }
  }
}
