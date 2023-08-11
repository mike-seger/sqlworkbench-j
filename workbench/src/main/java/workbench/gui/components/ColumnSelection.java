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
package workbench.gui.components;

import java.util.List;

import javax.swing.SwingUtilities;

import workbench.resource.ResourceMgr;

import workbench.db.ColumnIdentifier;

import workbench.gui.WbSwingUtilities;

/**
 *
 * @author Thomas Kellerer
 */
public class ColumnSelection
{
  private final WbTable data;

  private boolean includeHeaders;
  private boolean selectedOnly;
  private boolean formatText;
  private List<ColumnIdentifier> columns;

  public ColumnSelection(WbTable data)
  {
    this.data = data;
  }

  public boolean getIncludeHeaders()
  {
    return includeHeaders;
  }

  public boolean getSelectedOnly()
  {
    return selectedOnly;
  }

  public boolean getUseFormattedText()
  {
    return formatText;
  }

  public List<ColumnIdentifier> getColumns()
  {
    return columns;
  }

  /**
   *  A general purpose method to select specific columns from the result set
   *  this is e.g. used for copying data to the clipboard
   *
   */
  public boolean selectColumns(boolean includeHeader, boolean selectedOnly, boolean showHeaderSelection, boolean showSelectedRowsSelection, boolean showTextFormat)
  {
    this.includeHeaders = includeHeader;
    this.selectedOnly = selectedOnly;

    ColumnIdentifier[] originalCols = data.getDataStore().getColumns();
    ColumnSelectorPanel panel = new ColumnSelectorPanel(originalCols, includeHeader, selectedOnly, showHeaderSelection, showSelectedRowsSelection, showTextFormat);
    panel.restoreSettings("clipboardcopy");
    panel.selectAll();
    boolean result = WbSwingUtilities.getOKCancel(ResourceMgr.getString("MsgSelectColumnsWindowTitle"), SwingUtilities.getWindowAncestor(data), panel);

    if (result)
    {
      columns = panel.getSelectedColumns();
      includeHeaders = panel.includeHeader();
      selectedOnly = panel.selectedOnly();
      formatText = panel.formatTextOutput();
    }
    panel.saveSettings("clipboardcopy");
    return result;
  }
}
