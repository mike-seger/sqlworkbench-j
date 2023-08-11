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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import workbench.gui.WbSwingUtilities;

import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *
 * @author Matthias Melzner
 * @author Thomas Kellerer
 */
public class FileTreeLoader
{
  private final FileNode dummyRoot = new FileNode(false);
  private final List<File> directories = new ArrayList<File>();
  private final DefaultTreeModel model = new DefaultTreeModel(dummyRoot);
  private final Set<String> excludedFiles = new TreeSet<>(String::compareToIgnoreCase);
  private final Set<String> excludedExtensions = new TreeSet<>(String::compareToIgnoreCase);

  public FileTreeLoader()
  {
    this(Collections.emptyList());
  }

  public FileTreeLoader(List<File> dirs)
  {
    setDirectories(dirs);
    excludedFiles.addAll(FileTreeSettings.getFilesToExclude());
    excludedExtensions.addAll(FileTreeSettings.getExtensionsToExclude());
  }

  public boolean removeDirectory(File directory)
  {
    if (directory == null) return false;
    boolean changed = false;
    directory = FileUtil.getCanonicalFile(directory);
    for (int i=0; i < dummyRoot.getChildCount(); i++)
    {
      FileNode node = (FileNode)dummyRoot.getChildAt(i);
      if (directory.equals(node.getFile()))
      {
        dummyRoot.remove(i);
        changed = true;
        break;
      }
    }

    if (changed)
    {
      this.directories.remove(directory);
      WbSwingUtilities.invoke(() ->{model.nodeStructureChanged(dummyRoot);});
    }
    return changed;
  }

  public List<TreePath> getExpandedRootDirs(JTree tree)
  {
    List<TreePath> result = new ArrayList<>();
    int count = dummyRoot.getChildCount();
    for (int i=0; i < count; i++)
    {
      Object[] nodes = new Object[] {dummyRoot, dummyRoot.getChildAt(i)};
      TreePath path = new TreePath(nodes);
      if (tree.isExpanded(path))
      {
        result.add(path);
      }
    }
    return result;
  }

  public void load()
  {
    dummyRoot.removeAllChildren();
    for (File dir : directories)
    {
      FileNode node = new FileNode(dir, true);
      dummyRoot.add(node);
      createChildren(node, dir);
    }
    WbSwingUtilities.invoke(() ->{model.nodeStructureChanged(dummyRoot);});
  }

  public void createChildren(FileNode parent, File folder)
  {
    List<File> files = Arrays.asList(folder.listFiles());
    files.sort(getComparator());

    for (File fileEntry : files)
    {
      if (excludeFile(fileEntry)) continue;
      FileNode node = new FileNode(fileEntry);
      parent.add(node);
      node.setAllowsChildren(fileEntry.isDirectory());
      if (fileEntry.isDirectory())
      {
        createChildren(node, fileEntry);
      }
    }
  }

  public void loadFiltered(String text)
  {
    dummyRoot.removeAllChildren();
    for (File dir : directories)
    {
      FileNode fileTemp = new FileNode(dir, true);
      dummyRoot.add(fileTemp);
      this.createChildrenFiltered(dummyRoot, dir, text);
    }
    model.nodeStructureChanged(dummyRoot);
  }

  private void createChildrenFiltered(FileNode parent, File folder, String text)
  {
    List<File> files = Arrays.asList(folder.listFiles());
    files.sort(getComparator());

    for (File fileEntry : files)
    {
      if (excludeFile(fileEntry)) continue;
      FileNode node = new FileNode(fileEntry);

      if (fileEntry.isDirectory())
      {
        node.setAllowsChildren(true);
        if (fileEntry.getName().toLowerCase().contains(text.toLowerCase()))
        {
          parent.add(node);
          createChildren(node, fileEntry);
        }

        createChildrenFiltered(node, fileEntry, text);
        if (node.getChildCount() > 0)
        {
          parent.add(node);
        }
      }
      else
      {
        node.setAllowsChildren(false);
        if (fileEntry.getName().toLowerCase().contains(text.toLowerCase()))
        {
          parent.add(node);
        }
      }
    }
  }

  private boolean excludeFile(File f)
  {
    if (f == null) return true;
    if (excludedFiles.contains(f.getName())) return true;
    WbFile wb = new WbFile(f);
    if (f.isDirectory()) return false;

    String fileExt = wb.getExtension();
    if (StringUtil.isBlank(fileExt)) return false;

    if (excludedExtensions.contains(fileExt)) return true;
    return false;
  }

  public void setDirectories(List<File> directories)
  {
    this.directories.clear();
    if (directories == null) return;

    for (File dir : directories)
    {
      if (dir == null || !dir.exists()) continue;
      this.directories.add(FileUtil.getCanonicalFile(dir));
    }
  }

  public TreePath addDirectory(File dir)
  {
    if (dir == null || !dir.exists()) return null;
    dir = FileUtil.getCanonicalFile(dir);
    this.directories.add(0, dir);
    FileNode node = new FileNode(dir, true);
    this.dummyRoot.insert(node, 0);
    createChildren(node, dir);
    WbSwingUtilities.invoke(() -> {model.nodeStructureChanged(dummyRoot);});
    Object[] nodes = new Object[]{dummyRoot, node};
    return new TreePath(nodes);
  }

  private Comparator<File> getComparator()
  {
    return (File f1, File f2) ->
    {
      if (f1.isDirectory() && !f2.isDirectory()) return -1;
      if (!f1.isDirectory() && f2.isDirectory()) return 1;
      return f1.getName().toLowerCase().compareTo(f2.getName().toLowerCase());
    };
  }

  public File getRootDirectoryForPath(TreePath path)
  {
    if (path == null) return null;
    if (path.getPathCount() < 2) return null;
    Object[] nodes = path.getPath();

    if (nodes[1] instanceof FileNode)
    {
      return ((FileNode)nodes[1]).getFile();
    }
    return null;
  }

  public List<File> getDirectories()
  {
    return Collections.unmodifiableList(this.directories);
  }

  public TreeModel getModel()
  {
    return this.model;
  }

  public void clear()
  {
    dummyRoot.removeAllChildren();
    WbSwingUtilities.invokeLater(() -> {model.nodeStructureChanged(dummyRoot);});
  }
}
