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

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import workbench.resource.ResourceMgr;

/**
 * A JTable table model for a list of files.
 *
 * @author Thomas Kellerer
 */
public class FileListTableModel
  extends AbstractTableModel
{
  private final List<File> files = new ArrayList<>();

  public FileListTableModel(List<File> fileToShow)
  {
    if (fileToShow != null)
    {
      this.files.addAll(fileToShow);
    }
  }

  public File getFile(int index)
  {
    return files.get(index);
  }

  @Override
  public int getRowCount()
  {
    return files.size();
  }

  @Override
  public int getColumnCount()
  {
    return 2;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex)
  {
    switch (columnIndex)
    {
      case 0:
        return files.get(rowIndex).getName();
      case 1:
        long modified = files.get(rowIndex).lastModified();
        return Instant.ofEpochMilli(modified).atZone(ZoneId.systemDefault()).toLocalDateTime();
      default:
        return null;
    }
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex)
  {
    return false;
  }

  @Override
  public Class<?> getColumnClass(int columnIndex)
  {
    switch (columnIndex)
    {
      case 0:
        return String.class;
      case 1:
        return LocalDateTime.class;
      default:
        return null;
    }
  }

  @Override
  public String getColumnName(int column)
  {
    switch (column)
    {
      case 0:
        return ResourceMgr.getString("TxtFilename");
      case 1:
        return ResourceMgr.getString("TxtCreatedAt");
      default:
        return null;
    }
  }


}
