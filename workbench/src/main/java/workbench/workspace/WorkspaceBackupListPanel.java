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
package workbench.workspace;

import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import workbench.interfaces.ValidatingComponent;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ValidatingDialog;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbTable;
import workbench.gui.sql.EditorHistory;
import workbench.gui.sql.EditorHistoryEntry;
import workbench.gui.sql.EditorPanel;
import workbench.gui.sql.PanelType;

import workbench.util.FileDialogUtil;

/**
 * A class to show the backups for a specific workspace including a summary of the content.
 *
 * @author Thomas Kellerer
 */
public class WorkspaceBackupListPanel
  extends JPanel
  implements ListSelectionListener, ActionListener, ValidatingComponent
{
  private File workspaceFile;
  private FileListTableModel backups;
  private EditorPanel editor;
  private WbTable filesTable;
  private JList<TabEntry> tabList;
  private JButton selectWorkspaceButton;

  public WorkspaceBackupListPanel()
  {
    this(null);
  }

  public WorkspaceBackupListPanel(File workspace)
  {
    initComponents();
    if (workspace == null)
    {
      showSelectButton();
    }
    setWorkspacefile(workspace);

    filesTable = new WbTable(false, false, false);
    ((WbSplitPane)splitPane).setDividerBorder(WbSwingUtilities.EMPTY_BORDER);
    ((WbSplitPane)listSplitPane).setDividerBorder(WbSwingUtilities.EMPTY_BORDER);
    filesTable.setBorder(WbSwingUtilities.EMPTY_BORDER);
    filesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    filesScrollPane.setViewportView(filesTable);
    tabList = new JList();
    tabList.setBorder(WbSwingUtilities.EMPTY_BORDER);
    tabsScrollPane.setViewportView(tabList);

    int height = filesTable.getRowHeight();
    splitPane.setDividerLocation(height * 15);

    editor = EditorPanel.createSqlEditor();
    editor.setBorder(WbSwingUtilities.createLineBorder(this));
    editor.setEditable(false);
    splitPane.setRightComponent(editor);
    filesTable.getSelectionModel().addListSelectionListener(this);
    listSplitPane.setDividerLocation(0.5);
    if (workspace != null)
    {
      EventQueue.invokeLater(() -> {loadBackups();});
    }
  }

  private void setWorkspacefile(File workspace)
  {
    this.workspaceFile = workspace;
    if (this.workspaceFile == null)
    {
      workspaceFileName.setText(ResourceMgr.getString("LblSelectWksp"));
    }
    else
    {
      String fname = workspaceFile.getName();
      String dir = Settings.getInstance().getBackupDirName();
      workspaceFileName.setText(ResourceMgr.getFormattedString("LblWkspBackups", fname, dir));
    }
  }

  private void loadBackups()
  {
    try
    {
      WbSwingUtilities.showWaitCursor(this);
      firePropertyChange(ValidatingDialog.PROPERTY_VALID_STATE, false, false);
      resetTabList();
      WorkspaceBackupList list = new WorkspaceBackupList(workspaceFile);
      List<File> files = list.getBackups();
      backups = new FileListTableModel(files);
      filesTable.setModel(backups);
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  public void showSelectButton()
  {
    if (selectWorkspaceButton != null) return;
    selectWorkspaceButton = new JButton("...");
    selectWorkspaceButton.addActionListener(this);
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 1;
    gc.gridy = 0;
    gc.gridwidth = 1;
    gc.gridheight = 1;
    gc.weightx = 0.0;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.LINE_END;
    headerPanel.add(selectWorkspaceButton, gc);
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == selectWorkspaceButton)
    {
      FileDialogUtil util = new FileDialogUtil();
      String filename = util.getWorkspaceFilename(SwingUtilities.getWindowAncestor(this), false, true);
      if (filename != null)
      {
        setWorkspacefile(new File(filename));
        EventQueue.invokeLater(() -> {loadBackups();});
      }
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    if (e.getValueIsAdjusting()) return;
    if (e.getSource() == filesTable.getSelectionModel())
    {
      int index = filesTable.getSelectedRow();
      if (index > -1 && index < backups.getRowCount())
      {
        firePropertyChange(ValidatingDialog.PROPERTY_VALID_STATE, false, true);
        resetTabList();
        loadWorkspace();
      }
    }
    else if (e.getSource() == tabList.getSelectionModel())
    {
      loadEditorContent(tabList.getSelectedIndex());
    }
  }

  public File getSelectedWorkspaceFile()
  {
    int index = filesTable.getSelectedRow();
    if (index < 0 || index > backups.getRowCount()) return null;
    return backups.getFile(index);
  }

  @Override
  public boolean validateInput()
  {
    return getSelectedWorkspaceFile() != null;
  }

  @Override
  public void componentDisplayed()
  {
  }

  @Override
  public void componentWillBeClosed()
  {
  }

  private WbWorkspace getSelectedWorkspace()
  {
    int index = filesTable.getSelectedRow();
    if (index < -1 || index > backups.getRowCount()) return null;
    File f = backups.getFile(index);
    return new WbWorkspace(f.getAbsolutePath());
  }

  private void loadWorkspace()
  {
    WbWorkspace wksp = getSelectedWorkspace();
    if (wksp == null) return;
    try
    {
      wksp.openForReading();
      int tabCount = wksp.getEntryCount();
      DefaultListModel<TabEntry> tabNames = new DefaultListModel<>();
      for (int i=0; i < tabCount; i++)
      {
        PanelType type = wksp.getPanelType(i);
        if (type == PanelType.sqlPanel)
        {
          TabEntry entry = new TabEntry(i, wksp.getTabTitle(i));
          tabNames.addElement(entry);
        }
      }
      tabList.setModel(tabNames);
      editor.setText("");
    }
    catch (Exception io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load workspace", io);
    }
    finally
    {
      wksp.close();
    }
  }

  private void resetTabList()
  {
    tabList.getSelectionModel().removeListSelectionListener(this);
    tabList.setModel(new DefaultListModel<>());
    editor.setText("");
    tabList.getSelectionModel().addListSelectionListener(this);
  }

  private void loadEditorContent(int index)
  {
    TabEntry entry = tabList.getModel().getElementAt(index);
    WbWorkspace wksp = getSelectedWorkspace();
    if (wksp == null) return;
    try
    {
      wksp.openForReading();
      EditorHistory history = new EditorHistory(editor, 10);
      wksp.readEditorHistory(entry.getWorkspaceIndex(), history);
      EditorHistoryEntry content = history.getTopEntry();
      editor.setText(content == null ? "" : content.getText());
      editor.setCaretPosition(0);
      editor.invalidate();
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load editor content", ex);
    }
    finally
    {
      wksp.close();
    }
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
    java.awt.GridBagConstraints gridBagConstraints;

    splitPane = new WbSplitPane();
    listSplitPane = new WbSplitPane();
    jPanel3 = new javax.swing.JPanel();
    jLabel2 = new javax.swing.JLabel();
    filesScrollPane = new WbScrollPane();
    jPanel1 = new javax.swing.JPanel();
    tabsScrollPane = new WbScrollPane();
    jLabel1 = new javax.swing.JLabel();
    jPanel2 = new javax.swing.JPanel();
    headerPanel = new javax.swing.JPanel();
    workspaceFileName = new javax.swing.JLabel();

    setLayout(new java.awt.GridBagLayout());

    splitPane.setDividerLocation(200);
    splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

    listSplitPane.setDividerLocation(200);

    jPanel3.setLayout(new java.awt.GridBagLayout());

    jLabel2.setText(ResourceMgr.getString("TxtFilename")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
    jPanel3.add(jLabel2, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    jPanel3.add(filesScrollPane, gridBagConstraints);

    listSplitPane.setLeftComponent(jPanel3);

    jPanel1.setLayout(new java.awt.GridBagLayout());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    jPanel1.add(tabsScrollPane, gridBagConstraints);

    jLabel1.setText(ResourceMgr.getString("LblWkspContent")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
    jPanel1.add(jLabel1, gridBagConstraints);

    listSplitPane.setRightComponent(jPanel1);

    splitPane.setLeftComponent(listSplitPane);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(splitPane, gridBagConstraints);

    jPanel2.setLayout(new java.awt.GridBagLayout());

    headerPanel.setMinimumSize(new java.awt.Dimension(488, 50));
    headerPanel.setLayout(new java.awt.GridBagLayout());

    workspaceFileName.setText("jLabel1");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    headerPanel.add(workspaceFileName, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    jPanel2.add(headerPanel, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    add(jPanel2, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JScrollPane filesScrollPane;
  private javax.swing.JPanel headerPanel;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JSplitPane listSplitPane;
  private javax.swing.JSplitPane splitPane;
  private javax.swing.JScrollPane tabsScrollPane;
  private javax.swing.JLabel workspaceFileName;
  // End of variables declaration//GEN-END:variables
}
