/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.gui.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import workbench.resource.GeneratedIdentifierCase;
import workbench.resource.Settings;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbStringTokenizer;

/**
 * Turns multiple lines into a String suitable for a <code>VALUES</code> clause.
 *
 * If the input is e.g.
 * <pre>
 * 1,foo,bar
 * 2,bla,blub
 * </pre>
 * The result will be
 * <pre>
 * (1,'foo','bar'),
 * (2,'bla','blub')
 * </pre>
 * <p>Anything that is clearly a number will not be enclosed in single quotes.
 * If an item is already quoted (single or double quotes) they will be kept.
 * Everything else will be quoted (with single quotes).</p>
 *
 * <p>By default the delimiter is a comma. If the delimiter appears at the start or at the end
 * of the string, it will be removed.</p>
 *
 * <p>By specifying an alter alternate delimiter, input strings like the following can be converted too:</p>
 * <br>
 * <pre>
 * | 42 | Foo | Bar |
 * | 24 | Bar | Foo |
 * </pre>
 *
 * @author Thomas Kellerer
 */
public class ValuesListCreator
{
  private final String input;
  private final String delimiter;
  private String nullString;
  private final boolean useRegex;
  private boolean emptyStringIsNull = true;
  private boolean trimDelimiter = false;
  private boolean trimItems = true;
  private String lineEnding = "\n";
  private WbStringTokenizer tokenizer;
  private Pattern splitPattern;
  private boolean replaceDoubleQuotes = false;
  private final List<Boolean> quotesNeeded = new ArrayList<>();
  private boolean ignoreFirstLine = false;
  private boolean addSemicolon = false;
  private boolean addValuesClause = false;

  public ValuesListCreator(String input)
  {
    this(input, ",", false);
  }

  public ValuesListCreator(String input, String delimiter, boolean isRegex)
  {
    this.input = StringUtil.trim(input);
    this.useRegex = isRegex;
    if (isRegex)
    {
      this.delimiter = delimiter;
    }
    else
    {
      this.delimiter = StringUtil.unescape(delimiter);
    }
    initTokenizer();
  }

  public void setAddValuesClause(boolean addValuesClause)
  {
    this.addValuesClause = addValuesClause;
  }

  public void setIgnoreFirstLine(boolean ignoreFirstLine)
  {
    this.ignoreFirstLine = ignoreFirstLine;
  }

  public void setAddSemicolon(boolean addSemicolon)
  {
    this.addSemicolon = addSemicolon;
  }

  /**
   * If set to true, strings enclosed with double quotes will be replace with single quotes.
   */
  public void setReplaceDoubleQuotes(boolean replaceDoubleQuotes)
  {
    this.replaceDoubleQuotes = replaceDoubleQuotes;
  }

  /**
   * Define the string that is treated as a NULL value and will never be quoted.
   */
  public void setNullString(String string)
  {
    this.nullString = string;
  }

  public void setLineEnding(String ending)
  {
    this.lineEnding = ending;
  }

  public void setEmptyStringIsNull(boolean flag)
  {
    this.emptyStringIsNull = flag;
  }

  public void setTrimDelimiter(boolean flag)
  {
    this.trimDelimiter = flag;
  }

  public void setTrimItems(boolean flag)
  {
    this.trimItems = flag;
  }

  public String createValuesList()
  {
    if (StringUtil.isBlank(input)) return "";
    StringBuilder result = new StringBuilder(input.length() + 50);

    List<List<String>> lines = parseInput();

    if (addValuesClause)
    {
      result.append(getValuesClause());
      result.append(lineEnding);
    }

    int nr = 0;
    for (List<String> line : lines)
    {
      if (nr > 0)
      {
        result.append(',');
        result.append(lineEnding);
      }

      StringBuilder entry = convertToEntry(line);
      if (entry.length() > 0)
      {
        if (addValuesClause) result.append("  ");
        result.append(entry);
        nr ++;
      }
    }

    if (addSemicolon)
    {
      result.append(lineEnding);
      result.append(";");
      result.append(lineEnding);
    }

    return result.toString();
  }

  private String getValuesClause()
  {
    GeneratedIdentifierCase kwCase = Settings.getInstance().getFormatterKeywordsCase();
    return (kwCase == GeneratedIdentifierCase.upper ? "VALUES" : "values");
  }

  private List<List<String>> parseInput()
  {
    if (StringUtil.isBlank(input)) return Collections.emptyList();
    List<String> lines = StringUtil.getLines(input);
    if (ignoreFirstLine && lines.size() > 1)
    {
      lines.remove(0);
    }

    List<List<String>> result = new ArrayList<>(lines.size());

    int numCols = -1;
    for (String line : lines)
    {
      line = line.trim();
      if (line.isBlank()) continue;
      
      if (!useRegex && trimDelimiter)
      {
        if (line.startsWith(delimiter))
        {
          line = line.substring(1);
        }
        if (line.endsWith(delimiter))
        {
          line = line.substring(0, line.length() - 1);
        }
      }

      List<String> items = splitLine(line);
      if (items == null || items.isEmpty()) continue;
      if (items.size() > numCols)
      {
        numCols = items.size();
      }
      result.add(items);
    }

    this.quotesNeeded.clear();
    for (int i=0; i < numCols; i++)
    {
      boolean columnNeedsQuotes = columnNeedsQuotes(result, i);
      quotesNeeded.add(columnNeedsQuotes);
    }
    return result;
  }

  private boolean columnNeedsQuotes(List<List<String>> entries, int column)
  {
    for (List<String> line : entries)
    {
      if (column >= 0 && column < line.size())
      {
        if (needsQuotes(line.get(column))) return true;
      }
    }
    return false;
  }

  private void initTokenizer()
  {
    if (this.useRegex)
    {
      splitPattern = Pattern.compile(delimiter);
      tokenizer = null;
    }
    else
    {
      tokenizer = new WbStringTokenizer(delimiter, "", true);
      splitPattern = null;
    }
  }

  private StringBuilder convertToEntry(List<String> items)
  {
    StringBuilder result = new StringBuilder(items.size() * 10);
    result.append('(');
    for (int col = 0; col < items.size(); col++)
    {
      String item = items.get(col);
      if (col > 0) result.append(", ");

      if (isNull(item))
      {
        result.append("NULL");
      }
      else if (useQuotes(col) && !isQuoted(item))
      {
        result.append('\'');
        result.append(SqlUtil.escapeQuotes(item));
        result.append('\'');
      }
      else
      {
        result.append(item);
      }
    }
    result.append(')');
    return result;
  }

  private List<String> splitLine(String line)
  {
    List<String> elements = null;
    if (tokenizer != null)
    {
      if (!line.contains(delimiter)) return CollectionUtil.arrayList(line);
      tokenizer.setSourceString(line);
      elements = tokenizer.getAllTokens();
    }
    else
    {
      if (!splitPattern.matcher(line).find()) return CollectionUtil.arrayList(line);
      String[] items = splitPattern.split(line);
      elements = Arrays.asList(items);
    }
    for (int i=0; i < elements.size(); i++)
    {
      String item = elements.get(i);
      if (trimItems && item != null) item = item.trim();
      if (replaceDoubleQuotes)
      {
        item = replaceQuotes(item);
      }
      elements.set(i, item);
    }
    return elements;
  }

  private boolean useQuotes(int column)
  {
    if (column >= 0 && column < quotesNeeded.size())
    {
      return quotesNeeded.get(column);
    }
    return true;
  }

  private boolean isQuoted(String item)
  {
    if (item == null) return false;
    item = item.trim();
    return (item.startsWith("'") && item.endsWith("'"));
  }


  private String replaceQuotes(String item)
  {
    if (item == null) return item;
    if (item.startsWith("\"") && item.endsWith("\""))
    {
      return "'" + StringUtil.trimQuotes(item, '"') + "'";
    }
    return item;
  }

  private boolean isNull(String item)
  {
    if (item == null) return true;
    if (emptyStringIsNull && StringUtil.isEmpty(item)) return true;
    if (nullString != null && nullString.equals(item)) return true;
    return false;
  }

  public static boolean needsQuotes(String input)
  {
    if (input == null) return false;
    input = input.trim();

    // no need to quote it, if it's already quoted.
    if (input.startsWith("'") && input.endsWith("'")) return false;

    // if it doesn't start with a digit it can't be a number and has to be quoted.
    if (input.matches("^[^0-9-]+.*")) return true;

    // if it starts with one or more zeros, but isn't a decimal number
    // assume it's a string
    if (input.matches("^0+.+") && input.indexOf('.') == -1) return true;

    return !StringUtil.isNumber(input);
  }

}
