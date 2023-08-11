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

import java.io.File;
import java.util.List;

import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;

import workbench.util.StringUtil;

/**
 *
 * @author Matthias Melzner
 * @author Thomas Kellerer
 */
public class FileTree
  extends JTree
{
  private final FileTreeLoader loader = new FileTreeLoader();
  private final FileTreeDragSource dragSource;

  public FileTree()
  {
    super();
    this.setRootVisible(false);
    setModel(loader.getModel());
    setBorder(WbSwingUtilities.EMPTY_BORDER);
    setShowsRootHandles(true);
    setAutoscrolls(true);
    setScrollsOnExpand(true);
    setEditable(false);
    setRowHeight(0);
    setCellRenderer(new FileNodeRenderer());
    setExpandsSelectedPaths(true);
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    ToolTipManager.sharedInstance().registerComponent(this);
    dragSource = new FileTreeDragSource(this);
  }

  public void setDirectories(List<File> dirs)
  {
    this.loader.setDirectories(dirs);
  }

  /**
   * Returns the number of files below the currently selected node.
   *
   * Returns -1 if the current selection is not a directory.
   */
  public String getSelectionSummary()
  {
    TreePath path = getSelectionPath();
    if (path == null || path.getPathCount() < 1) return "";

    TreeNode node = (TreeNode)path.getLastPathComponent();

    if (!(node instanceof FileNode)) return "";
    FileNode fn = (FileNode)node;

    String msg = "";
    if (fn.getAllowsChildren())
    {
      int fileCount = 0;
      int dirCount = 0;
      int children = fn.getChildCount();
      for (int i=0; i < children; i++)
      {
        FileNode childNode = (FileNode)fn.getChildAt(i);
        if (childNode.getFile() != null)
        {
          if (childNode.getFile().isFile())
          {
            fileCount ++;
          }
          if (childNode.getFile().isDirectory())
          {
            dirCount ++;
          }
        }
      }
      if (dirCount > 0)
      {
        msg = ResourceMgr.getFormattedString("TxtNumDirs", dirCount);
      }

      if (fileCount > 0)
      {
        if (msg.length() > 0) msg += ", ";
        msg += ResourceMgr.getFormattedString("TxtNumFiles", fileCount);
      }
    }
    else if (fn.getFile() != null)
    {
      msg = fn.getFile().getAbsolutePath();
    }
    return msg;
  }

  public File getSelectedRootDir()
  {
    TreePath path = getSelectionPath();
    return loader.getRootDirectoryForPath(path);
  }

  public List<File> getRootDirs()
  {
    return loader.getDirectories();
  }

  public FileTreeLoader getLoader()
  {
    return this.loader;
  }

  public void load()
  {
    try
    {
      WbSwingUtilities.showWaitCursorOnWindow(this);
      this.loader.load();
      WbSwingUtilities.invokeLater(() -> {expandRow(0);});
    }
    finally
    {
      WbSwingUtilities.showDefaultCursorOnWindow(this);
    }
    this.collapseRow(0);
    for (int i = this.getRowCount() - 1; i >= 0; i--)
    {
      this.expandRow(i);
    }
  }

  public void reload()
  {
    clear();
    load();
  }

  public void clear()
  {
    if (loader != null)
    {
      loader.clear();
      setModel(loader.getModel());
    }
  }

  public void expandNodes()
  {
    for (int i = 0; i < this.getRowCount(); i++)
    {
      this.expandRow(i);
    }
  }

  public List<TreePath> getExpandedPaths()
  {
    return loader.getExpandedRootDirs(this);
  }

  public void restoreExpandedPaths(List<TreePath> expandedPaths)
  {
    for (TreePath path : expandedPaths)
    {
      expandPath(path);
    }
  }

  public void removeSelectedRootDir()
  {
    if (!isRootDirSelected()) return;

    List<TreePath> expandedPaths = getExpandedPaths();
    File dir = getSelectedFile();
    if (dir != null && loader.removeDirectory(dir))
    {
      restoreExpandedPaths(expandedPaths);
    }
  }

  public boolean isRootDirSelected()
  {
    TreePath path = getSelectionPath();
    if (path == null) return false;
    return path.getPathCount() == 2;
  }

  public File getSelectedFile()
  {
    TreePath path = getSelectionPath();
    if (path == null || path.getPathCount() == 0) return null;

    FileNode node = (FileNode)path.getLastPathComponent();
    return node.getFile();
  }

  public void loadFiltered(String text)
  {
    if (StringUtil.isBlank(text))
    {
      reload();
      return;
    }

    try
    {
      WbSwingUtilities.showWaitCursorOnWindow(this);
      clear();
      loader.loadFiltered(text);
      WbSwingUtilities.invokeLater(this::expandNodes);
    }
    finally
    {
      WbSwingUtilities.showDefaultCursorOnWindow(this);
    }
  }
}
