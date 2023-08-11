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
package workbench.gui.editor.actions;

import java.awt.event.ActionEvent;

import workbench.resource.Settings;

import workbench.gui.editor.JEditTextArea;

/**
 *
 * @author Thomas Kellerer
 */
public class DuplicateCurrentLine
  extends EditorAction
{
  public DuplicateCurrentLine()
  {
    super("TxtEdDupLine", null);
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    JEditTextArea textArea = getTextArea(evt);
    if (textArea == null) return;


    int insertPoint = -1;
    int currentLine = textArea.getCaretLine();
    int currentColumn = textArea.getCaretPositionInLine(currentLine);

    boolean wasSelected = false;
    boolean doAppend = false;
    String dupeText = textArea.getSelectedText();

    if (dupeText != null)
    {
      insertPoint = textArea.getSelectionEnd();
      textArea.select(insertPoint, insertPoint);
      wasSelected = true;
    }
    else
    {
      String le = Settings.getInstance().getInternalEditorLineEnding();
      insertPoint = textArea.getLineEndOffset(currentLine);
      doAppend = currentLine == textArea.getLineCount() - 1;
      if (doAppend)
      {
        dupeText = le + textArea.getLineText(currentLine);
      }
      else
      {
        dupeText = textArea.getLineText(currentLine) + le;
      }
    }

    if (doAppend)
    {
      textArea.appendLine(dupeText);
    }
    else
    {
      textArea.insertText(insertPoint, dupeText);
    }

    if (wasSelected)
    {
      textArea.select(insertPoint, insertPoint + dupeText.length());
    }
    else
    {
      int newPos = textArea.getLineStartOffset(currentLine + 1) + currentColumn;
      textArea.setCaretPosition(newPos);
    }
  }
}
