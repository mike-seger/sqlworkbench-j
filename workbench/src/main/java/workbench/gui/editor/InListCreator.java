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
package workbench.gui.editor;

import java.util.List;

import workbench.resource.Settings;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class InListCreator
{
  private String text;

  public InListCreator(String selectedText)
  {
    this.text = selectedText;
  }

  /**
   * Change the supplied text so that it can be used for a SQL IN statement.
   * If all elements are numbers, no quotes will be used. If at least one element
   * is not a number, all elements will be quoted.
   */
  public String makeInList()
  {
    if (StringUtil.isBlank(text)) return text;
    List<String> lines = StringUtil.getLines(text);
    return makeInList(lines, needsQuotes(lines));
  }

  public String makeQuotedInList()
  {
    if (StringUtil.isBlank(text)) return text;
    List<String> lines = StringUtil.getLines(text);
    return makeInList(lines, true);
  }

  public String makeInListForNonChar()
  {
    if (StringUtil.isBlank(text)) return text;
    List<String> lines = StringUtil.getLines(text);
    return makeInList(lines, false);
  }

  private String makeInList(List<String> lines, boolean quoteElements)
  {
    final StringBuilder newText = new StringBuilder(this.text.length() + lines.size() * 5);
    String nl = Settings.getInstance().getInternalEditorLineEnding();

    int maxElementsPerLine = 5;
    if (quoteElements)
    {
      maxElementsPerLine = Settings.getInstance().getMaxCharInListElements();
    }
    else
    {
      maxElementsPerLine = Settings.getInstance().getMaxNumInListElements();
    }
    int elements = 0;

    boolean newLinePending = false;

    for (int i=0; i < lines.size(); i++)
    {
      String line = StringUtil.trimToNull(lines.get(i));
      if (line == null) continue;

      if (i == 0)
      {
        newText.append('(');
      }
      else
      {
        newText.append(", ");
      }
      if (newLinePending)
      {
        newText.append(nl);
        newText.append(' ');
        newLinePending = false;
      }
      boolean isQuoted = isQuoted(line);

      if (quoteElements && !isQuoted) newText.append('\'');
      newText.append(line);
      if (quoteElements && !isQuoted) newText.append('\'');
      elements ++;
      if (i < lines.size())
      {
        if ((elements & maxElementsPerLine) == maxElementsPerLine)
        {
          newLinePending = true;
          elements = 0;
        }
      }
    }
    newText.append(')');
    newText.append(nl);
    return newText.toString();
  }

  private boolean isQuoted(String line)
  {
    if (CollectionUtil.isEmpty(line)) return true;
    line = line.trim();
    return line.startsWith("'") && line.endsWith("'");
  }

  private boolean needsQuotes(List<String> lines)
  {
    if (CollectionUtil.isEmpty(lines)) return false;
    for (String line : lines)
    {
      if (ValuesListCreator.needsQuotes(line)) return true;
    }
    return false;
  }
}
