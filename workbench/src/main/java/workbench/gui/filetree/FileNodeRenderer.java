/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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
package workbench.gui.filetree;

import java.awt.Component;
import java.io.File;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultTreeCellRenderer;

import workbench.resource.IconMgr;

public class FileNodeRenderer
  extends DefaultTreeCellRenderer
{
  private final FileSystemView fsv = FileSystemView.getFileSystemView();
  private final boolean showSystemIcons = false;

  public FileNodeRenderer()
  {
    super();
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean isLeaf, int row, boolean hasFocus)
  {
    Component result = super.getTreeCellRendererComponent(tree, value, isSelected, expanded, isLeaf, row, hasFocus);

    File f = null;
    if (value instanceof FileNode)
    {
      f = ((FileNode)value).getFile();
    }
    else if (value instanceof File)
    {
      f = (File)value;
    }
    setIcon(getIcon(f, expanded));

    return result;
  }

  private Icon getIcon(File f, boolean expanded)
  {
    if (f == null) return null;
    if (showSystemIcons)
    {
      return fsv.getSystemIcon(f);
    }
    if (f.isDirectory())
    {
      return expanded ? IconMgr.getInstance().getLabelIcon("folder-open") : IconMgr.getInstance().getLabelIcon("folder");
    }
    return IconMgr.getInstance().getLabelIcon("new");
  }
}
