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
package workbench.gui.actions.clipboard;

import java.awt.event.ActionEvent;

import workbench.resource.ResourceMgr;

import workbench.db.exporter.ExportType;

import workbench.gui.actions.WbAction;
import workbench.gui.components.ColumnSelection;
import workbench.gui.components.TextClipboardCopier;
import workbench.gui.components.WbTable;

/**
 * Action to copy the selected content of a WbTable as tab-separated text to the clipboard
 *
 * @see workbench.gui.components.ClipBoardCopier
 * @author Thomas Kellerer
 */
public class CopySelectedAsTextAction
  extends WbAction
{
  private WbTable client;

  public CopySelectedAsTextAction(WbTable aClient)
  {
    this(aClient, "MnuTxtCopySelectedAsText");
  }

  public CopySelectedAsTextAction(WbTable aClient, String labelKey)
  {
    super();
    this.client = aClient;
    this.setMenuItemName(ResourceMgr.MNU_TXT_COPY_SELECTED);
    this.initMenuDefinition(labelKey, null);
    this.setEnabled(false);
  }

  @Override
  public boolean hasCtrlModifier()
  {
    return true;
  }

  @Override
  public boolean hasShiftModifier()
  {
    return true;
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    TextClipboardCopier copier = new TextClipboardCopier(ExportType.TEXT, this.client);
    boolean copyHeaders = true;
    boolean selectColumns = false;
    if (invokedByMouse(e))
    {
      copyHeaders = !isShiftPressed(e);
      selectColumns = isCtrlPressed(e);
    }

    if (selectColumns)
    {
      ColumnSelection select = new ColumnSelection(client);
      select.selectColumns(copyHeaders, false, true, true, false);
      copier.setIncludeHeader(select.getIncludeHeaders());
      copier.setColumnsToCopy(select.getColumns());
    }
    else
    {
      copier.setIncludeHeader(copyHeaders);
    }
    copier.doCopy();
  }
}
