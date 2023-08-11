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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import workbench.interfaces.QuickFilter;
import workbench.resource.GuiSettings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.QuickFilterAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.WbToolbar;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class MacroTreeQuickFilter
  implements KeyListener, QuickFilter
{
  private JTextField filterValue;
  private QuickFilterAction filterAction;
  private WbAction resetFilter;
  private boolean keySelectionInProgress;
  private final MacroTree tree;
  private List<String> openedGroups;

  public MacroTreeQuickFilter(MacroTree client)
  {
    this.tree = client;
  }

  public JPanel createFilterPanel()
  {
    JPanel filterPanel = new JPanel(new GridBagLayout());
    filterPanel.setBorder(new EmptyBorder(0,0,0,4));
    filterValue = new JTextField();

    filterAction = new QuickFilterAction(this);
    resetFilter = new WbAction()
    {
      @Override
      public void executeAction(ActionEvent e)
      {
        resetFilter();
      }
    };
    resetFilter.setIcon("resetfilter");
    resetFilter.setEnabled(false);

    WbToolbar filterBar = new WbToolbar();
    filterBar.add(filterAction);
    filterBar.add(resetFilter);
    filterBar.setMargin(WbSwingUtilities.getEmptyInsets());
    filterBar.setBorder(new EmptyBorder(0,0,0,0));
    filterBar.setBorderPainted(true);

    GridBagConstraints gc = new GridBagConstraints();
    gc.anchor = GridBagConstraints.LINE_START;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.fill = GridBagConstraints.NONE;
    gc.weightx = 0.0;
    gc.weighty = 0.0;
    gc.insets = new Insets(0,0,0,0);
    filterPanel.add(filterBar, gc);

    gc.gridx ++;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1.0;
    filterPanel.add(filterValue, gc);

    filterValue.addKeyListener(this);
    return filterPanel;
  }

  private boolean isEditKey(KeyEvent event)
  {
    int key = event.getKeyChar();
    switch (key)
    {
      case KeyEvent.VK_BACK_SPACE:
      case KeyEvent.VK_DELETE:
      case KeyEvent.VK_CUT:
      case KeyEvent.VK_INSERT:
      case KeyEvent.VK_CLEAR:
        return true;
      default:
        return false;
    }
  }

  @Override
  public void keyTyped(final KeyEvent e)
  {
    if (e.isConsumed()) return;
    if (Character.isISOControl(e.getKeyChar()) && isEditKey(e) == false) return;

    if (GuiSettings.filterMacroWhileTyping())
    {
      EventQueue.invokeLater(this::applyQuickFilter);
    }
  }

  @Override
  public void keyPressed(KeyEvent e)
  {
    if (e.getSource() != this.filterValue || e.getModifiersEx() != 0) return;

    switch (e.getKeyCode())
    {
      case KeyEvent.VK_LEFT:
      case KeyEvent.VK_RIGHT:
        if (keySelectionInProgress)
        {
          tree.dispatchEvent(e);
        }
        else
        {
          e.consume();
        }
        break;

      case KeyEvent.VK_UP:
      case KeyEvent.VK_DOWN:
      case KeyEvent.VK_PAGE_DOWN:
      case KeyEvent.VK_PAGE_UP:
        keySelectionInProgress = true;
        tree.dispatchEvent(e);
        break;

      case KeyEvent.VK_ENTER:
        if (keySelectionInProgress)
        {
          tree.dispatchEvent(e);
        }
        else
        {
          e.consume();
          keySelectionInProgress = false;
          applyQuickFilter();
        }
        break;

      case KeyEvent.VK_ESCAPE:
        if (isFiltered())
        {
          e.consume();
          keySelectionInProgress = false;
          resetFilter();
        }
        break;

      default:
        keySelectionInProgress = false;
    }
  }

  @Override
  public void keyReleased(KeyEvent e)
  {
  }

  private boolean isFiltered()
  {
    return tree.getModel().isFiltered();
  }

  @Override
  public void resetFilter()
  {
    tree.getModel().resetFilter();
    resetFilter.setEnabled(false);
    if (CollectionUtil.isNonEmpty(openedGroups))
    {
      tree.expandGroups(openedGroups);
      openedGroups = null;
    }
    tree.selectFirstMacro();
  }

  @Override
  public synchronized void applyQuickFilter()
  {
    if (!tree.getModel().isFiltered())
    {
      this.openedGroups = tree.getExpandedGroupNames();
    }
    String text = filterValue.getText();
    tree.getModel().applyFilter(text);
    tree.expandAll();
    tree.selectFirstMacro();
    resetFilter.setEnabled(true);
  }

}
