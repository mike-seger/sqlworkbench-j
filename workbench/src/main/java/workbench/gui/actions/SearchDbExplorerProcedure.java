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
package workbench.gui.actions;

import java.awt.event.ActionEvent;

import workbench.interfaces.TextContainer;

import workbench.db.ProcedureDefinition;
import workbench.db.RoutineType;
import workbench.db.TableIdentifier;

import workbench.gui.dbobjects.objecttree.ObjectFinder;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to find a stored procedure based on the currently selected name in the editor.
 *
 * @author Mathias Melzer, Thomas Kellerer
 */
public class SearchDbExplorerProcedure
  extends WbAction
{
  private ObjectFinder finder;
  private TextContainer editor;

  public SearchDbExplorerProcedure(ObjectFinder procedureList, TextContainer editor)
  {
    super();
    this.finder = procedureList;
    this.editor = editor;

    this.initMenuDefinition("MnuTxtSearchProcedure");
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (finder == null) return;
    String text = SqlUtil.getIdentifierAtCursor(editor, finder.getConnection());
    if (StringUtil.isBlank(text)) return;
    TableIdentifier name = new TableIdentifier(text);

    ProcedureDefinition def = new ProcedureDefinition(name.getCatalog(),
      name.getSchema(), name.getObjectName(), RoutineType.unknown);
    finder.selectObject(def);
  }
}
