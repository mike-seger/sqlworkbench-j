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
package workbench.gui.sql;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

import workbench.console.TextPrinter;
import workbench.interfaces.FontChangedListener;
import workbench.interfaces.ResultLogger;
import workbench.interfaces.TextContainer;
import workbench.resource.GuiSettings;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.ClearMessagesAction;
import workbench.gui.components.TextComponentMouseListener;
import workbench.gui.editor.SearchAndReplace;

/**
 * @author Thomas Kellerer
 */
public class LogArea
  extends JTextArea
  implements FontChangedListener, PropertyChangeListener, TextContainer, ResultLogger, TextPrinter
{
  private TextComponentMouseListener contextMenu;
  private int maxLines = Integer.MAX_VALUE;

  public LogArea(Container owner)
  {
    super();
    setDoubleBuffered(true);
    setBorder(WbSwingUtilities.EMPTY_BORDER);
    setFont(Settings.getInstance().getMsgLogFont());
    setEditable(false);
    setLineWrap(true);
    setWrapStyleWord(true);

    initColors();

    contextMenu = new TextComponentMouseListener(this);
    addMouseListener(contextMenu);

    contextMenu.addActionAtStart(new ClearMessagesAction(this), true);

    if (owner != null)
    {
      SearchAndReplace searcher = new SearchAndReplace(owner, this);
      contextMenu.addAction(searcher.getFindAction());
      contextMenu.addAction(searcher.getFindNextAction());
      searcher.getFindAction().addToInputMap(this);
      searcher.getFindNextAction().addToInputMap(this);
    }

    Settings.getInstance().addPropertyChangeListener(this, Settings.PROPERTY_EDITOR_FG_COLOR, Settings.PROPERTY_EDITOR_BG_COLOR);
    Settings.getInstance().addFontChangedListener(this);
  }

  @Override
  public void print(String msg)
  {
    append(msg);
  }

  @Override
  public void println(String msg)
  {
    addLine(msg);
  }

  @Override
  public void println()
  {
    append("\n");
  }

  @Override
  public void flush()
  {
    // not needed
  }

  @Override
  public void clearLog()
  {
    setText("");
  }

  @Override
  public void appendToLog(CharSequence msg)
  {
    if (msg != null) append(msg.toString());
  }

  @Override
  public void showLogMessage(CharSequence msg)
  {
    if (msg == null)
    {
      setText("");
    }
    else
    {
      setText(msg.toString());
    }
  }

  @Override
  public void setSelectedText(String text)
  {
    super.replaceSelection(text);
  }

  @Override
  public boolean isTextSelected()
  {
    return getSelectionEnd() > getSelectionStart();
  }

  @Override
  public String getWordAtCursor(String wordChars)
  {
    return null;
  }

  public void dispose()
  {
    setText("");
    Settings.getInstance().removePropertyChangeListener(this);
    Settings.getInstance().removeFontChangedListener(this);
    if (contextMenu != null)
    {
      removeMouseListener(contextMenu);
      contextMenu.dispose();
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    initColors();
  }

  @Override
  public void fontChanged(String aFontId, Font newFont)
  {
    if (aFontId.equals(Settings.PROPERTY_MSGLOG_FONT))
    {
      this.setFont(newFont);
    }
  }

  private void initColors()
  {
    Color bg = GuiSettings.getEditorBackground();
    if (bg != null) setBackground(bg);

    Color fg = GuiSettings.getEditorForeground();
    if (fg != null) setForeground(fg);
  }

  public void setMaxLineCount(int count)
  {
    this.maxLines = count;
  }

  public void deleteLine(int lineNumber)
  {
    try
    {
      int start = getLineStartOffset(lineNumber);
      int end = getLineEndOffset(lineNumber);
      getDocument().remove(start, (end - start));
    }
    catch (BadLocationException ble)
    {
      // ignore
    }
  }

  public void addLine(String line)
  {
    if (line == null) return;

    if (getLineCount() >= maxLines)
    {
      deleteLine(0);
    }
    append(line + "\n");
  }

  @Override
  public int getStartInLine(int offset)
  {
    try
    {
      int line = getLineOfOffset(offset);
      int start = getLineStartOffset(line);
      return offset - start;
    }
    catch (BadLocationException ex)
    {
      return -1;
    }
  }

  @Override
  public String getLineText(int line)
  {
    try
    {
      int start = getLineStartOffset(line);
      int end = getLineEndOffset(line);
      return getText(end, start - end);
    }
    catch (BadLocationException ble)
    {
      return null;
    }
  }

  @Override
  public int getLineOfOffset(int offset)
  {
    try
    {
      return super.getLineOfOffset(offset);
    }
    catch (BadLocationException ex)
    {
      return -1;
    }
  }

}
