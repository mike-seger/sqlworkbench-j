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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 *
 * @author Thomas Kellerer
 */
public class FileTreeDragSource
  implements DragSourceListener, DragGestureListener, Serializable
{
  private final FileTree fileTree;
  private final DragSource dragSource;
  public FileTreeDragSource(FileTree tree)
  {
    this.fileTree = tree;
    dragSource = new DragSource();
    dragSource.createDefaultDragGestureRecognizer(fileTree, DnDConstants.ACTION_COPY, this);
  }

  @Override
  public void dragEnter(DragSourceDragEvent dsde)
  {
  }

  @Override
  public void dragOver(DragSourceDragEvent dsde)
  {
  }

  @Override
  public void dropActionChanged(DragSourceDragEvent dsde)
  {
  }

  @Override
  public void dragExit(DragSourceEvent dse)
  {
  }

  @Override
  public void dragDropEnd(DragSourceDropEvent dsde)
  {
  }

  @Override
  public void dragGestureRecognized(DragGestureEvent dge)
  {
    final File f = fileTree.getSelectedFile();
    if (f == null || f.isDirectory()) return;

    Transferable transferable = new Transferable()
    {
      @Override
      public DataFlavor[] getTransferDataFlavors()
      {
        return new DataFlavor[]{DataFlavor.javaFileListFlavor};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor)
      {
        return DataFlavor.javaFileListFlavor == flavor;
      }

      @Override
      public Object getTransferData(DataFlavor df)
        throws UnsupportedFlavorException, IOException
      {
        return List.of(f);
      }
    };
    dragSource.startDrag(dge, DragSource.DefaultCopyDrop, transferable, this);
  }

}
