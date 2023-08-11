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

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import workbench.interfaces.FileActions;
import workbench.interfaces.MacroChangeListener;
import workbench.interfaces.MainPanel;
import workbench.interfaces.PropertyStorage;
import workbench.resource.GuiSettings;
import workbench.resource.IconMgr;
import workbench.resource.Settings;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.CollapseTreeAction;
import workbench.gui.actions.DeleteListEntryAction;
import workbench.gui.actions.EditMacroAction;
import workbench.gui.actions.ExpandTreeAction;
import workbench.gui.actions.RunMacroAction;
import workbench.gui.actions.SaveListFileAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.CloseIcon;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.WbLabelField;
import workbench.gui.components.WbToolbar;
import workbench.gui.components.WbToolbarButton;
import workbench.gui.editor.MacroExpander;
import workbench.gui.sql.SqlPanel;

import workbench.sql.macros.MacroDefinition;
import workbench.sql.macros.MacroManager;

import workbench.util.StringUtil;

/**
 * Display a floating window with the MacroTree.
 * When double clicking a macro in the tree, the macro is executed in the
 * passed MainWindow
 *
 * @author Thomas Kellerer
 */
public class MacroPanel
  extends JPanel
  implements MouseListener, TreeSelectionListener, MacroChangeListener, ActionListener
{
  public static final String LAYOUT_NAME = "macrolist";
  public static final String PROP_VISIBLE = "list.visible";
  private MacroTree tree;
  private final MainWindow mainWindow;
  private final RunMacroAction runAction;
  private final EditMacroAction editAction;
  private final WbAction copyTextAction;
  private JButton closeButton;
  private final String propkey = getClass().getName() + ".expandedgroups";
  public static final String TOOLKEY = "macropopup";
  private MacroTreeQuickFilter filterHandler;

  public MacroPanel(MainWindow parent, boolean showToolbar, boolean showCloseButton)
  {
    mainWindow = parent;
    setLayout(new BorderLayout(0, 0));
    setName(LAYOUT_NAME);

    tree = new MacroTree(parent.getMacroClientId(), true);
    JScrollPane p = new JScrollPane(tree);
    p.setBorder(WbSwingUtilities.EMPTY_BORDER);
    add(p, BorderLayout.CENTER);
    int clientId = parent.getMacroClientId();
    String infoText = MacroManager.getInstance().getMacros(clientId).getCurrentMacroFilename();
    WbLabelField lbl = new WbLabelField(infoText);
    add(lbl, BorderLayout.PAGE_END);

    restoreExpandedGroups();
    tree.addMouseListener(this);

    runAction = new RunMacroAction(mainWindow, null, -1);
    if (GuiSettings.getRunMacroWithEnter())
    {
      runAction.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
      runAction.addToInputMap(tree, JComponent.WHEN_FOCUSED);
    }
    editAction = new EditMacroAction();
    copyTextAction = new WbAction(this, "copy-query-text");
    copyTextAction.setMenuTextByKey("MnuTxtCopyMacroTxt");
    tree.addPopupAction(editAction, true);
    tree.addPopupAction(copyTextAction, false);
    tree.addPopupActionAtTop(runAction);
    tree.addTreeSelectionListener(this);

    FileActions actions = new FileActions()
    {
      @Override
      public void saveItem()
        throws Exception
      {
        saveMacros(false);
      }

      @Override
      public void deleteItem()
        throws Exception
      {
        tree.deleteSelection();
      }

      @Override
      public void newItem(boolean copyCurrent)
      {
      }
    };

    DeleteListEntryAction delete = new DeleteListEntryAction(actions);
    tree.addPopupAction(delete, false);
    SaveListFileAction save = new SaveListFileAction(actions, "LblSaveMacros");
    tree.addPopupAction(save, false);

    MacroManager.getInstance().addChangeListener(this, parent.getMacroClientId());
    ToolTipManager.sharedInstance().registerComponent(tree);

    if (showToolbar || showCloseButton)
    {
      JPanel toolPanel = new JPanel(new GridBagLayout());
      JToolBar toolbar = new WbToolbar();
      toolbar.add(new ExpandTreeAction(tree));
      toolbar.add(new CollapseTreeAction(tree));

      filterHandler = new MacroTreeQuickFilter(tree);
      JPanel filterPanel = filterHandler.createFilterPanel();
      filterPanel.setBorder(new DividerBorder(DividerBorder.LEFT));

      GridBagConstraints gc = new GridBagConstraints();
      gc.gridx = 0;
      gc.gridy = 0;
      gc.weightx = 0.0;
      gc.fill = GridBagConstraints.NONE;
      gc.anchor = GridBagConstraints.LINE_START;
      gc.insets = new Insets(0, 0, 0, IconMgr.getInstance().getSizeForLabel() / 3);
      toolPanel.add(toolbar, gc);
      gc.gridx ++;
      gc.weightx = 1.0;
      gc.fill = GridBagConstraints.HORIZONTAL;
      toolPanel.add(filterPanel, gc);

      if (showCloseButton)
      {
        JToolBar closeBar = new WbToolbar();
        closeButton = new WbToolbarButton(new CloseIcon());
        closeButton.setActionCommand("close-panel");
        closeButton.addActionListener(this);
        closeButton.setRolloverEnabled(true);
        closeBar.add(closeButton);
        gc.gridx ++;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        toolPanel.add(closeBar, gc);
      }
      add(toolPanel, BorderLayout.PAGE_START);
    }
  }

  public boolean isModified()
  {
    return tree.isModified();
  }

  private boolean useWorkspace()
  {
    return GuiSettings.getStoreMacroPopupInWorkspace() && mainWindow != null;
  }

  public void saveExpandedGroups()
  {
    List<String> groups = tree.getExpandedGroupNames();
    String grouplist = StringUtil.listToString(groups, ',', true);
    PropertyStorage config = getConfig();
    config.setProperty(propkey, grouplist);
  }

  public void saveWorkspaceSettings()
  {
    if (useWorkspace())
    {
      saveExpandedGroups();
    }
  }

  public void workspaceChanged()
  {
    macroListChanged();
    if (useWorkspace())
    {
      restoreExpandedGroups();
    }
  }

  public void restoreExpandedGroups()
  {
    tree.collapseAll();
    List<String> groups = getExpandedGroups();
    tree.expandGroups(groups);
  }

  public List<String> getExpandedGroups()
  {
    PropertyStorage config = getConfig();
    String groups = config.getProperty(propkey, null);
    return StringUtil.stringToList(groups, ",", true, true);
  }

  private PropertyStorage getConfig()
  {
    PropertyStorage config = null;

    if (useWorkspace())
    {
      config = mainWindow.getToolProperties(TOOLKEY);
    }

    if (config == null)
    {
      config = Settings.getInstance();
    }
    return config;
  }

  protected void saveMacros(boolean restoreListener)
  {
    MacroManager.getInstance().removeChangeListener(this, mainWindow.getMacroClientId());
    if (tree.isModified())
    {
      tree.saveChanges();
    }
    if (restoreListener)
    {
      MacroManager.getInstance().addChangeListener(this, mainWindow.getMacroClientId());
    }
  }

  public void dispose()
  {
    ToolTipManager.sharedInstance().unregisterComponent(tree);
    MacroManager.getInstance().removeChangeListener(this, mainWindow.getMacroClientId());
    tree.removeTreeSelectionListener(this);
    tree.clear();
  }

  @Override
  public void mouseClicked(MouseEvent e)
  {
    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2)
    {
      MacroDefinition macro = tree.getSelectedMacro();
      if (mainWindow != null && macro != null)
      {
        SqlPanel panel = mainWindow.getCurrentSqlPanel();
        if (panel != null)
        {
          if (macro.getExpandWhileTyping())
          {
            MacroExpander expander = panel.getEditor().getMacroExpander();
            if (expander != null)
            {
              expander.insertMacroText(macro.getText());
            }
          }
          else
          {
            MacroRunner runner = new MacroRunner();
            runner.runMacro(macro, panel, WbAction.isShiftPressed(e));
          }
          WbSwingUtilities.requestComponentFocus(mainWindow, panel);
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

  @Override
  public void valueChanged(TreeSelectionEvent e)
  {
    MacroDefinition macro = tree.getSelectedMacro();
    if (mainWindow != null && macro != null)
    {
      MainPanel panel = mainWindow.getCurrentPanel().get();
      if (panel instanceof MacroClient)
      {
        runAction.setEnabled(true);
        runAction.setMacro(macro);
      }
      editAction.setMacro(macro);
    }
    else
    {
      runAction.setEnabled(false);
      editAction.setMacro(null);
    }
  }

  @Override
  public void macroListChanged()
  {
    EventQueue.invokeLater(() ->
    {
      List<String> groups = tree.getExpandedGroupNames();
      tree.loadMacros(true);
      tree.expandGroups(groups);
    });
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == this.copyTextAction)
    {
      copyMacroText();
    }
    if (e.getSource() == this.closeButton)
    {
      closePanel();
    }
  }

  public void closePanel()
  {
    saveExpandedGroups();
    saveWorkspaceSettings();
    saveMacros(false);
    mainWindow.removeAdditionalComponent(this);
    dispose();
    firePropertyChange("display", "visible", "closed");

  }

  private void copyMacroText()
  {
    MacroDefinition macro = tree.getSelectedMacro();
    if (macro == null) return;
    String sql = macro.getText();
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(new StringSelection(sql),null);
  }

}
