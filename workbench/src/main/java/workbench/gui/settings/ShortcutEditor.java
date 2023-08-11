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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.sql.Types;
import java.util.Collection;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.*;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.ActionRegistration;
import workbench.gui.actions.EscAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.ColumnWidthOptimizer;
import workbench.gui.components.DataStoreTableModel;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.WbButton;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbTable;
import workbench.gui.settings.ShortcutDisplay.DisplayType;

import workbench.storage.DataStore;
import workbench.storage.filter.ContainsComparator;
import workbench.storage.filter.DataRowExpression;

import workbench.sql.macros.MacroDefinition;
import workbench.sql.macros.MacroManager;

import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 *
 */
public class ShortcutEditor
  extends JPanel
  implements ActionListener, ListSelectionListener, WindowListener, MouseListener
{
  private static final String KEY_WINDOW_SIZE = "workbench.shortcuteditor";
  private WbTable keysTable;
  private DataStore definitions;
  private DataStoreTableModel model;
  private JDialog window;
  private final Frame parent;

  private JButton okButton;
  private JButton cancelButton;
  private JButton assignButton;
  private JButton resetButton;
  private JButton resetAllButton;
  private JButton clearButton;
  private JTextField searchField;

  private String escActionCommand;

  public ShortcutEditor(Frame fparent)
  {
    super();
    this.parent = fparent;

    // make sure actions that are not created upon startup are
    // registered with us!
    ActionRegistration.registerActions();
  }

  public void showWindow()
  {
    WbSwingUtilities.showWaitCursor(parent);
    window = new JDialog(parent, ResourceMgr.getString("LblConfigureShortcutWindowTitle"), true);
    window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    window.addWindowListener(this);
    JPanel contentPanel = new JPanel(new BorderLayout());

    this.keysTable = new WbTable(false, false, false);
    this.keysTable.useMultilineTooltip(false);
    this.keysTable.setShowPopupMenu(true);
    this.keysTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    this.keysTable.getExportAction().setLastDirKey("workbench.shortcuteditor.lastDir");
    this.setLayout(new BorderLayout());
    JScrollPane scroll = new WbScrollPane(this.keysTable);
    contentPanel.add(scroll, BorderLayout.CENTER);

    EscAction esc = new EscAction(window, this);
    escActionCommand = esc.getActionName();

    this.createModel();
    this.keysTable.setRowSelectionAllowed(true);
    this.keysTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.keysTable.setAdjustToColumnLabel(true);
    ColumnWidthOptimizer optimizer = new ColumnWidthOptimizer(this.keysTable);
    optimizer.optimizeAllColWidth(80,-1,true);
    this.keysTable.addMouseListener(this);
    this.cancelButton = new WbButton(ResourceMgr.getString("LblCancel"));
    this.cancelButton.addActionListener(this);

    okButton = new WbButton(ResourceMgr.getString("LblOK"));
    okButton.addActionListener(this);

    WbSwingUtilities.makeEqualWidth(okButton, cancelButton);

    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
    buttonPanel.add(this.okButton);
    buttonPanel.add(this.cancelButton);
    this.add(buttonPanel, BorderLayout.SOUTH);

    JPanel editPanel = new JPanel();
    editPanel.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 1;
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(2,10,5,10);
    this.assignButton = new WbButton(ResourceMgr.getString("LblAssignShortcut"));
    this.assignButton.setToolTipText(ResourceMgr.getDescription("LblAssignShortcut"));
    this.assignButton.addActionListener(this);
    this.assignButton.setEnabled(false);
    editPanel.add(assignButton, c);

    c.gridy ++;
    this.clearButton = new WbButton(ResourceMgr.getString("LblClearShortcut"));
    this.clearButton.setToolTipText(ResourceMgr.getDescription("LblClearShortcut"));
    this.clearButton.addActionListener(this);
    this.clearButton.setEnabled(false);
    editPanel.add(clearButton, c);

    c.gridy ++;
    c.insets = new Insets(15,10,5,10);
    this.resetButton = new WbButton(ResourceMgr.getString("LblResetShortcut"));
    this.resetButton.setToolTipText(ResourceMgr.getDescription("LblResetShortcut"));
    this.resetButton.addActionListener(this);
    this.resetButton.setEnabled(false);
    editPanel.add(resetButton, c);

    c.gridy ++;
    c.insets = new Insets(2,10,5,10);
    c.anchor = GridBagConstraints.NORTHWEST;
    this.resetAllButton = new WbButton(ResourceMgr.getString("LblResetAllShortcuts"));
    this.resetAllButton.setToolTipText(ResourceMgr.getDescription("LblResetAllShortcuts"));
    this.resetAllButton.addActionListener(this);
    editPanel.add(resetAllButton, c);

    c.gridy ++;
    c.insets = new Insets(15,10,5,10);
    JLabel label = new JLabel();
    label.setText(ResourceMgr.getString("LblSearchShortcut"));
    editPanel.add(label, c);

    c.gridy ++;
    c.insets = new Insets(2,10,5,10);
    c.weighty = 1.0;
    this.searchField = new JTextField();
    this.searchField.getDocument().addDocumentListener(new DocumentListener()
    {

      @Override
      public void insertUpdate(DocumentEvent e)
      {
        search();
      }

      @Override
      public void removeUpdate(DocumentEvent e)
      {
        search();
      }

      @Override
      public void changedUpdate(DocumentEvent e)
      {
        search();
      }

    });
    this.searchField.setEnabled(true);
    editPanel.add(searchField, c);

    contentPanel.add(editPanel, BorderLayout.EAST);

    JPanel p = new JPanel();
    Dimension d = new Dimension(1,20);
    p.setMinimumSize(d);
    p.setPreferredSize(d);
    p.setBorder(new DividerBorder(DividerBorder.HORIZONTAL_MIDDLE));
    contentPanel.add(p, BorderLayout.SOUTH);

    p = new JPanel();
    d = new Dimension(5,1);
    p.setMinimumSize(d);
    p.setPreferredSize(d);
    contentPanel.add(p, BorderLayout.WEST);

    p = new JPanel();
    d = new Dimension(1,5);
    p.setMinimumSize(d);
    p.setPreferredSize(d);
    contentPanel.add(p, BorderLayout.NORTH);

    this.add(contentPanel, BorderLayout.CENTER);

    window.getContentPane().add(this);
    if (!Settings.getInstance().restoreWindowSize(this.window, KEY_WINDOW_SIZE))
    {
      window.setSize(600,400);
    }
    WbSwingUtilities.center(window, parent);
    WbSwingUtilities.showDefaultCursor(parent);
    window.setVisible(true);
    WbSwingUtilities.invokeLater(() ->  {keysTable.requestFocusInWindow();});
  }

  private void createModel()
  {
    ShortcutManager mgr = ShortcutManager.getInstance();
    Collection<ShortcutDefinition> keys = mgr.getDefinitions();

    String[] cols = new String[] { ResourceMgr.getString("LblKeyDefCommandCol"),
                                   ResourceMgr.getString("LblKeyDefKeyCol"),
                                   ResourceMgr.getString("LblKeyDefAlternate"),
                                   ResourceMgr.getString("LblKeyDefDefaultCol") };
    int[] types = new int[] { Types.VARCHAR, Types.OTHER, Types.OTHER, Types.OTHER };

    this.definitions = new DataStore(cols, types);

    for (ShortcutDefinition key : keys)
    {
      ShortcutDefinition def = key.createCopy();
      int row = this.definitions.addRow();
      String cls = def.getActionClass();
      String title = mgr.getActionNameForClass(cls);
      if (title == null || !classIsAvailable(cls))
      {
        // If action classes for which customized keystrokes exist are renamed, this can happen
        LogMgr.logWarning(new CallerInfo(){}, "Ignoring invalid action class: " + cls);
        continue;
      }
      String tooltip = mgr.getTooltip(cls);
      ActionDisplay disp = new ActionDisplay(title, tooltip);
      this.definitions.setValue(row, 0, disp);
      this.definitions.setValue(row, 1, new ShortcutDisplay(def, DisplayType.PRIMARY));
      this.definitions.setValue(row, 2, new ShortcutDisplay(def, DisplayType.ALTERNATE));
      this.definitions.setValue(row, 3, new ShortcutDisplay(def, DisplayType.DEFAULT));
      this.definitions.getRow(row).setUserObject(def);
    }
    this.definitions.sortByColumn(0, true);
    this.definitions.resetStatus();
    this.model = new DataStoreTableModel(this.definitions);
    this.model.setAllowEditing(false);
    this.keysTable.setModel(model, true);
    TableColumn col = this.keysTable.getColumnModel().getColumn(0);
    col.setCellRenderer(new ActionDisplayRenderer());
    this.keysTable.getSelectionModel().addListSelectionListener(this);
  }

  private boolean classIsAvailable(String clazz)
  {
    try
    {
      Class.forName(clazz);
      return true;
    }
    catch (Throwable th)
    {
      return false;
    }
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    Object source = e.getSource();

    if (source == this.cancelButton)
    {
      this.closeWindow();
    }
    else if (source == this.okButton)
    {
      this.saveShortcuts();
      this.closeWindow();
    }
    else if (source == this.assignButton)
    {
      this.assignKey();
    }
    else if (source == this.resetButton)
    {
      this.resetCurrentKey();
    }
    else if (source == this.resetAllButton)
    {
      this.resetAllKeys();
    }
    else if (source == this.clearButton)
    {
      this.clearPrimaryShortcut();
    }
    else if (e.getActionCommand().equals(escActionCommand))
    {
      this.closeWindow();
    }
  }

  private void saveSettings()
  {
    Settings.getInstance().storeWindowSize(this.window, KEY_WINDOW_SIZE);
  }

  private void saveShortcuts()
  {
    ShortcutManager mgr = ShortcutManager.getInstance();
    int count = this.definitions.getRowCount();
    for (int row = 0; row < count; row++)
    {
      ShortcutDefinition def = getDefinition(row);
      mgr.updateShortcut(def);
    }

    mgr.updateActions();
    mgr.fireShortcutsChanged();
  }

  private void applyFilter()
  {
    String search = StringUtil.trimToNull(searchField.getText());
    if (search == null)
    {
      this.model.resetFilter();
    }
    else
    {
      DataRowExpression filter = new DataRowExpression();
      filter.setIgnoreCase(true);
      filter.setFilterValue(search);
      ContainsComparator comp = new ContainsComparator();
      filter.setComparator(comp);
      this.model.applyFilter(filter);
    }
  }

  private void search()
  {
    this.applyFilter();
  }

  private void closeWindow()
  {
    this.saveSettings();
    this.window.setVisible(false);
    this.window.dispose();
  }

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    boolean enabled = (e.getFirstIndex() >= 0);
    this.resetButton.setEnabled(enabled);
    this.assignButton.setEnabled(enabled);
    this.clearButton.setEnabled(enabled);
  }

  private void assignKey()
  {
    int row = this.keysTable.getSelectedRow();
    if (row < 0) return;

    KeyboardMapper mapper = new KeyboardMapper();
    ShortcutDefinition def = getDefinition(row);
    mapper.setCurrentPrimaryKey(def.getActiveKeyStroke());
    mapper.setCurrentAlternateKey(def.getAlternateKeyStroke());
    boolean ok = mapper.show(this);

    if (ok)
    {
      WbAction currentAction = getAction(row);
      KeyStroke key = mapper.getKeyStroke();

      if (canAssign(key, currentAction, row))
      {
        if (key == null)
        {
          def.clearKeyStroke();
        }
        else
        {
          def.setCurrentKey(new StoreableKeyStroke(key));
        }
      }

      KeyStroke alternateKey = mapper.getAltenateKeyStroke();
      if (canAssign(alternateKey, currentAction, row))
      {
        if (alternateKey == null)
        {
          def.setAlternateKey(null);
        }
        else
        {
          def.setAlternateKey(new StoreableKeyStroke(alternateKey));
        }
      }
      this.model.fireTableRowsUpdated(row, row);
    }
  }

  private WbAction getAction(int row)
  {
    ShortcutDefinition def = getDefinition(row);
    String currentClass = def.getActionClass();
    return ShortcutManager.getInstance().getActionForClass(currentClass);
  }

  private ShortcutDefinition getDefinition(int row)
  {
    return this.definitions.getUserObject(row, ShortcutDefinition.class);
  }

  private boolean canAssign(KeyStroke key, WbAction currentAction, int currentRow)
  {
    if (key == null) return true;
    String display = StoreableKeyStroke.displayString(key);

    int oldrow = this.findKey(key, currentRow);
    if (!currentAction.allowDuplicate() && oldrow > -1)
    {
      WbAction action = getAction(oldrow);
      if (!action.allowDuplicate())
      {
        String name = this.definitions.getValueAsString(oldrow, 0);
        String msg = ResourceMgr.getFormattedString("MsgShortcutAlreadyAssigned", display, name);
        boolean choice = WbSwingUtilities.getYesNo(this, msg);
        if (!choice) return false;

        ShortcutDefinition oldDef = getDefinition(oldrow);
        if (oldDef.getCurrentKeyStroke() == key)
        {
          oldDef.clearKeyStroke();
        }

        if (oldDef.getAlternateKeyStroke() == key)
        {
          oldDef.setAlternateKey(null);
        }
        this.model.fireTableRowsUpdated(oldrow, oldrow);
      }
    }

    MacroDefinition def = MacroManager.getInstance().getMacroForKeyStroke(key);
    if (!currentAction.allowDuplicate() && def != null)
    {
      String msg = ResourceMgr.getFormattedString("MsgShortcutMacroAlreadyAssigned", display, def.getName());
      boolean choice = WbSwingUtilities.getYesNo(this, msg);
      if (!choice) return false;

      def.setShortcut(null);
    }

    if (key.equals(GuiSettings.getExpansionKey()))
    {
      WbSwingUtilities.showErrorMessageKey(this, "MsgShortcutExpansion");
      return false;
    }
    return true;
  }

  private void clearPrimaryShortcut()
  {
    int row = this.keysTable.getSelectedRow();
    if (row < 0) return;
    ShortcutDefinition shortcut = getDefinition(row);
    shortcut.clearKeyStroke();
    this.model.fireTableRowsUpdated(row, row);
  }

  private void resetCurrentKey()
  {
    int row = this.keysTable.getSelectedRow();
    if (row < 0) return;

    ShortcutDefinition shortcut = getDefinition(row);
    shortcut.resetToDefault();
    this.model.fireTableRowsUpdated(row, row);
  }

  private void resetAllKeys()
  {
    int selected = this.keysTable.getSelectedRow();
    int count = this.keysTable.getRowCount();
    for (int row=0; row < count; row++)
    {
      ShortcutDefinition shortcut = getDefinition(row);
      shortcut.resetToDefault();
    }
    this.model.fireTableDataChanged();
    if (selected > -1)
    {
      this.keysTable.getSelectionModel().setSelectionInterval(selected, selected);
    }
  }

  private int findKey(KeyStroke key, int ignoreRow)
  {
    int count = this.definitions.getRowCount();
    for (int row = 0; row < count; row++)
    {
      if (row == ignoreRow) continue;
      ShortcutDefinition shortcut = getDefinition(row);
      if (shortcut.getActiveKeyStroke() == key || shortcut.getAlternateKeyStroke() == key)
      {
        return row;
      }
    }
    return -1;
  }

  @Override
  public void windowActivated(WindowEvent e)
  {
  }

  @Override
  public void windowClosed(WindowEvent e)
  {
  }

  @Override
  public void windowClosing(WindowEvent e)
  {
    this.closeWindow();
  }

  @Override
  public void windowDeactivated(WindowEvent e)
  {
  }

  @Override
  public void windowDeiconified(WindowEvent e)
  {
  }

  @Override
  public void windowIconified(WindowEvent e)
  {
  }

  @Override
  public void windowOpened(WindowEvent e)
  {
  }

  @Override
  public void mouseClicked(MouseEvent e)
  {
    if (e.getSource() == this.keysTable &&
        e.getClickCount() == 2 &&
        e.getButton() == MouseEvent.BUTTON1)
    {
      this.assignKey();
    }
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
  public void mousePressed(MouseEvent e)
  {
  }

  @Override
  public void mouseReleased(MouseEvent e)
  {
  }
}
