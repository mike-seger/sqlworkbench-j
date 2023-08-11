/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer, Matthias Melzner
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
package workbench.gui.filetree;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import workbench.interfaces.Reloadable;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionProfile;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.ReloadAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.CloseIcon;
import workbench.gui.components.WbFileChooser;
import workbench.gui.components.WbLabelField;
import workbench.gui.components.WbPopupMenu;
import workbench.gui.components.WbToolbar;
import workbench.gui.components.WbToolbarButton;
import workbench.gui.sql.SqlPanel;

import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbProperties;
import workbench.util.WbThread;

/**
 * @author Matthias Melzner
 * @author Thomas Kellerer
 */
public class FileTreePanel
  extends JPanel
  implements Reloadable, ActionListener, MouseListener, TreeSelectionListener
{
  public static final String PROP_DIVIDER = "filetree.divider.location";
  public static final String PROP_ROOT_DIR = "filetree.rootdir";

  private final FileTree tree;
  private JPanel toolPanel;
  private ReloadAction reload;
  private WbToolbarButton closeButton;
  private final MainWindow window;
  private JTextField filterText;
  private JButton addDirectoryButton;
  private JButton removeDirectoryButton;
  private String workspaceDefaultDir;
  private final WbLabelField statusBar;

  public FileTreePanel(MainWindow window)
  {
    super(new BorderLayout());
    this.window = window;
    tree = new FileTree();
    this.setName("filetree");
    tree.getSelectionModel().addTreeSelectionListener(this);

    ConnectionProfile profile = window.getCurrentProfile();
    if (profile != null)
    {
      workspaceDefaultDir = StringUtil.trimToNull(profile.getDefaultDirectory());
    }
    tree.addMouseListener(this);
    JScrollPane scroll = new JScrollPane(tree);
    scroll.setBorder(WbSwingUtilities.EMPTY_BORDER);
    createToolbar();

    statusBar = new WbLabelField(new EmptyBorder(3, 5, 3, 3));
    add(toolPanel, BorderLayout.PAGE_START);
    add(scroll, BorderLayout.CENTER);
    add(statusBar, BorderLayout.PAGE_END);
  }

  private void createToolbar()
  {
    toolPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints();

    gc.gridx = 0;
    gc.gridy = 0;
    gc.weightx = 0.0;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.LINE_START;
    gc.insets = new Insets(0, 0, 0, IconMgr.getInstance().getSizeForLabel() / 3);
    JLabel label = new JLabel(ResourceMgr.getString("LblSearchShortcut"));
    toolPanel.add(label, gc);

    gc.gridx ++;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    filterText = new JTextField();
    filterText.addActionListener((ActionEvent e) -> {search();});
    toolPanel.add(filterText, gc);

    WbToolbar bar = new WbToolbar();

    addDirectoryButton = new WbToolbarButton(IconMgr.getInstance().getLabelIcon("folder_add"));
    addDirectoryButton.addActionListener(this);
    removeDirectoryButton = new WbToolbarButton(IconMgr.getInstance().getLabelIcon("folder_remove"));
    removeDirectoryButton.addActionListener(this);
    removeDirectoryButton.setEnabled(false);

    reload = new ReloadAction(this);
    reload.setUseLabelIconSize(true);
    reload.setTooltip(null);

    bar.add(addDirectoryButton);
    bar.add(removeDirectoryButton);
    bar.add(reload);
    bar.addSeparator();

    closeButton = new WbToolbarButton(new CloseIcon());
    closeButton.setActionCommand("close-panel");
    closeButton.addActionListener(this);
    closeButton.setRolloverEnabled(true);
    bar.add(closeButton);

    gc.gridx ++;
    gc.weightx = 0.0;
    gc.fill = GridBagConstraints.NONE;
    gc.insets = new Insets(0, 0, 0, 0);
    gc.anchor = GridBagConstraints.LINE_END;
    toolPanel.add(bar, gc);

  }

  public void loadInBackground()
  {
    WbThread t = new WbThread("FileTree loader")
    {
      @Override
      public void run()
      {
        tree.reload();
      }
    };
    t.start();
  }

  @Override
  public void reload()
  {
    filterText.setText("");
    loadInBackground();
  }

  public void search()
  {
    tree.loadFiltered(filterText.getText());
  }

  private void closePanel()
  {
    Window frame = SwingUtilities.getWindowAncestor(this);
    if (frame instanceof MainWindow)
    {
      final MainWindow mainWin = (MainWindow) frame;
      EventQueue.invokeLater(mainWin::closeFileTree);
    }
  }

  private File getDefaultDir()
  {
    File dir = getFileFromName(workspaceDefaultDir);
    if (dir == null)
    {
      dir = new File(".").getAbsoluteFile();
    }
    return dir;
  }

  private File getFileFromName(String name)
  {
    if (StringUtil.isBlank(name)) return null;
    File f = new File(name);
    if (f.exists() && f.isDirectory()) return f;
    return null;
  }

  private void addDirectory()
  {
    final JFileChooser jf = new WbFileChooser();
    jf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    jf.setMultiSelectionEnabled(false);
    File dir = tree.getSelectedRootDir();
    if (dir == null)
    {
      dir = getDefaultDir();
    }

    jf.setCurrentDirectory(dir);
    jf.setDialogTitle(ResourceMgr.getString("MnuTxtAddFolder"));
    int answer = jf.showOpenDialog(SwingUtilities.getWindowAncestor(this));
    if (answer == JFileChooser.APPROVE_OPTION)
    {
      final List<TreePath> expandedPaths = tree.getExpandedPaths();
      final File newDir = jf.getSelectedFile();
      WbThread th = new WbThread("Load Directory")
      {
        @Override
        public void run()
        {
          try
          {
            WbSwingUtilities.showWaitCursor(FileTreePanel.this);
            long start = System.currentTimeMillis();
            TreePath newPath = tree.getLoader().addDirectory(newDir);
            long duration = System.currentTimeMillis() - start;
            LogMgr.logDebug(new CallerInfo(){}, "Loading directory " + newDir + " took: " + duration + "ms");
            WbSwingUtilities.invoke(() ->
            {
              tree.restoreExpandedPaths(expandedPaths);
              tree.setSelectionPath(newPath);
              tree.expandPath(newPath);
              tree.scrollPathToVisible(newPath);
            });
          }
          finally
          {
            WbSwingUtilities.showDefaultCursor(FileTreePanel.this);
          }
        }
      };
      th.start();
    }
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    if (evt.getSource() == addDirectoryButton)
    {
      addDirectory();
    }
    if (evt.getSource() == removeDirectoryButton)
    {
      tree.removeSelectedRootDir();
    }
    if (evt.getSource() == closeButton)
    {
      closePanel();
    }
  }

  private void loadFile(File toSelect, boolean newTab)
  {
    if (toSelect == null) return;
    if (toSelect.isDirectory())
    {
      return;
    }

    String encodingToUse = FileUtil.detectFileEncoding(toSelect);
    if (encodingToUse == null)
    {
      encodingToUse = Settings.getInstance().getDefaultFileEncoding();
    }

    SqlPanel panel = null;
    if (newTab)
    {
      panel = (SqlPanel)window.addTab();
    }
    else
    {
      panel = window.getCurrentSqlPanel();
    }
    panel.readFile(toSelect, encodingToUse);
  }

  public void saveSettings(WbProperties props)
  {
    // clear existing directories first
    List<String> keys = props.getKeysWithPrefix(PROP_ROOT_DIR);
    for (String key : keys)
    {
      props.setProperty(key, null);
    }

    List<File> dirs = tree.getRootDirs();
    for (int i = 0; i < dirs.size(); i++)
    {
      props.setProperty(PROP_ROOT_DIR + "." + i, dirs.get(i).getAbsolutePath());
    }
  }

  public void restoreSettings(WbProperties props)
  {
    List<String> keys = props.getKeysWithPrefix(PROP_ROOT_DIR);
    List<File> dirs = new ArrayList<>(keys.size());

    for (String key : keys)
    {
      String path = props.getProperty(key);
      if (StringUtil.isBlank(path)) continue;

      File f = FileUtil.getCanonicalFile(new File(path));
      if (f.exists() && !dirs.contains(f))
      {
        dirs.add(f);
      }
    }

    if (dirs.isEmpty() && StringUtil.isNotBlank(workspaceDefaultDir))
    {
      File f = FileUtil.getCanonicalFile(new File(workspaceDefaultDir));
      if (f.exists())
      {
        dirs.add(f);
      }
    }

    if (dirs.isEmpty())
    {
      dirs.addAll(FileTreeSettings.getDefaultDirectories());
    }
    tree.setDirectories(dirs);
  }

  @Override
  public void mouseClicked(MouseEvent e)
  {
    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2)
    {
      TreePath p = tree.getClosestPathForLocation(e.getX(), e.getY());
      if (p == null) return;

      FileNode node = (FileNode)p.getLastPathComponent();
      File f = node.getFile();

      if (!f.isDirectory())
      {
        if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK || FileTreeSettings.getClickOption() == FileOpenMode.newTab)
        {
          this.loadFile(f, true);
        }
        else
        {
          if (!window.getCurrentSqlPanel().checkAndSaveFile())
          {
            return;
          }
          this.loadFile(f, false);
        }
      }
    }
    else if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1)
    {
      int x = e.getX();
      int y = e.getY();
      TreePath p = tree.getClosestPathForLocation(x, y);
      if (p == null) return;
      tree.setSelectionPath(p);

      JPopupMenu popup = createContextMenu();
      if (popup != null)
      {
        popup.show(tree, x, y);
      }
    }
  }

  private JPopupMenu createContextMenu()
  {
    JPopupMenu menu = new WbPopupMenu();
    menu.addPopupMenuListener(new PopupMenuListener()
    {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e)
      {
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
      {
        menu.removeAll();
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e)
      {
      }
    });

    WbAction openInSameTab = new WbAction()
    {
      @Override
      public void executeAction(ActionEvent e)
      {
        loadFile(tree.getSelectedFile(), false);
      }
    };
    openInSameTab.initMenuDefinition("MnuTxtOpenInSameTab");
    menu.add(openInSameTab);
    WbAction openInNewTab = new WbAction()
    {
      @Override
      public void executeAction(ActionEvent e)
      {
        loadFile(tree.getSelectedFile(), true);
      }
    };
    openInNewTab.initMenuDefinition("MnuTxtOpenInNewTab");
    menu.add(openInNewTab);

    File selected = tree.getSelectedFile();
    boolean isFile = selected != null && selected.isFile() && selected.canRead();
    openInSameTab.setEnabled(isFile);
    openInNewTab.setEnabled(isFile);

    WbAction closeDir = new WbAction()
    {
      @Override
      public void executeAction(ActionEvent e)
      {
        tree.removeSelectedRootDir();
      }
    };
    menu.addSeparator();

    closeDir.initMenuDefinition("MnuTxtRemoveFolder");
    closeDir.setIcon("folder_remove");
    closeDir.setEnabled(tree.isRootDirSelected());
    menu.add(closeDir);

    WbAction addDir = new WbAction()
    {
      @Override
      public void executeAction(ActionEvent e)
      {
        addDirectory();
      }
    };
    addDir.initMenuDefinition("MnuTxtAddFolder");
    addDir.setIcon("folder_add");
    addDir.setEnabled(true);
    menu.add(addDir);

    return menu;
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
    removeDirectoryButton.setEnabled(tree.isRootDirSelected());
    statusBar.setText(tree.getSelectionSummary());
  }

}
