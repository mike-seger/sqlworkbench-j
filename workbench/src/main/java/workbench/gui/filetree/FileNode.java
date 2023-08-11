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
package workbench.gui.filetree;

import java.io.File;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 *
 * @author Thomas Kellerer
 */
public class FileNode
  extends DefaultMutableTreeNode
{
  private boolean showFullPath = false;

  public FileNode()
  {
  }

  public FileNode(boolean showFullPath)
  {
    this.showFullPath = showFullPath;
  }

  public FileNode(Object userObject)
  {
    super(userObject);
  }

  public FileNode(Object userObject, boolean showFullPath)
  {
    super(userObject);
    this.showFullPath = showFullPath;
  }

  public File getFile()
  {
    return (File)getUserObject();
  }

  public boolean getShowFullPath()
  {
    return showFullPath;
  }

  public void setShowFullPath(boolean showFullPath)
  {
    this.showFullPath = showFullPath;
  }

  @Override
  public String toString()
  {
    File f = getFile();
    if (f == null) return "";
    try
    {
      if (showFullPath)
      {
        return f.getCanonicalPath();
      }
      return f.getCanonicalFile().getName();
    }
    catch (Exception ex)
    {
      return showFullPath ? f.getAbsolutePath() : f.getAbsoluteFile().getName();
    }
  }

}
