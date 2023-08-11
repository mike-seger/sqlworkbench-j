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
package workbench.util;

import java.util.ArrayList;
import java.util.List;

import workbench.db.importer.TextFileParser;

/**
 * A class to efficiently parse a delimited line of data.
 *
 * @author  Thomas Kellerer
 */
public class CsvLineParser
  implements LineParser
{
  private String lineData = null;
  private int len = 0;
  private int current = 0;
  private String delimiter = TextFileParser.DEFAULT_DELIMITER;
  private int delimiterLength;
  private char quoteChar = 0;
  private boolean returnEmptyStrings = false;
  private boolean trimValues = false;
  private boolean oneMore = false;
  private QuoteEscapeType escapeType = QuoteEscapeType.none;
  private boolean unquotedEmptyIsNull = false;

  public CsvLineParser(char delimit)
  {
    delimiter = String.valueOf(delimit);
    delimiterLength = 1;
  }

  public CsvLineParser(char delimit, char quote)
  {
    delimiter = String.valueOf(delimit);
    delimiterLength = 1;
    quoteChar = quote;
  }

  public CsvLineParser(String delimit)
  {
    delimiter = delimit;
    delimiterLength = delimiter.length();
  }

  public CsvLineParser(String delimit, char quote)
  {
    if (delimit != null)
    {
      delimiter = delimit;
      delimiterLength = delimiter.length();
    }
    quoteChar = quote;
  }

  public QuoteEscapeType getEscapeType()
  {
    return escapeType;
  }

  public char getQuoteChar()
  {
    return quoteChar;
  }

  @Override
  public void setLine(String line)
  {
    this.lineData = line;
    this.len = this.lineData.length();
    this.current = 0;
  }


  /**
   * Controls if an unquoted empty string is treated as a null value
   * or an empty string.
   *
   * If this is set to true, returnEmptyStrings is set to false as well.
   *
   * @param flag
   * @see #setReturnEmptyStrings(boolean)
   */
  public void setUnquotedEmptyStringIsNull(boolean flag)
  {
    unquotedEmptyIsNull = flag;
    if (flag) returnEmptyStrings = false;
  }

  private boolean isDelimiter(char toTest, int currentIndex)
  {
    if (currentIndex < 0) return false;

    if (delimiterLength == 1)
    {
      return (toTest == delimiter.charAt(0));
    }

    if (toTest == delimiter.charAt(0))
    {
      for (int i=1; i < delimiterLength; i++)
      {
        if (i + currentIndex < this.lineData.length())
        {
          if (delimiter.charAt(i) != lineData.charAt(currentIndex + 1))
          {
            return false;
          }
        }
        else
        {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * Controls how empty strings are returned. If this is set to
   * true, than an empty element is returned as an empty string
   * otherwise an empty element is returned as null.
   */
  public void setReturnEmptyStrings(boolean flag)
  {
    this.returnEmptyStrings = flag;
  }

  public void setQuoteEscaping(QuoteEscapeType type)
  {
    this.escapeType = type;
  }

  @Override
  public boolean hasNext()
  {
    return oneMore || current < len;
  }

  @Override
  public String getNext()
  {
    boolean inQuotes = false;
    boolean hadQuotes = false;

    // Handle the case where the last character is the delimiter
    if (oneMore)
    {
      oneMore = false;
      if (returnEmptyStrings) return StringUtil.EMPTY_STRING;
      return null;
    }

    StringBuilder element = new StringBuilder(40);
    while (current < len)
    {
      char c = this.lineData.charAt(current);
      if (!inQuotes && (isDelimiter(c, current)))
      {
        break;
      }

      if (this.escapeType == QuoteEscapeType.escape && c == '\\')
      {
        c = getNextChar(current);
        if (c != 0)
        {
          element.append(c);
          current += 2;
          continue;
        }
      }
      else if (this.escapeType == QuoteEscapeType.duplicate && c == this.quoteChar)
      {
        char next = getNextChar(current);
        if (next == quoteChar)
        {
          char prevChar = getPreviousChar(current);

          // this is a leading quoted empty string
          if (prevChar == 0 && isDelimiter(getNextChar(current + 1), current + 1))
          {
            current += 2;
            break;
          }

          if (!isDelimiter(prevChar, current - 1) && !isDelimiter(getNextChar(current + 2), current + 2))
          {
            element.append(c);
            current += 2;
            continue;
          }
        }
      }

      if (c == this.quoteChar)
      {
        hadQuotes = true;
        inQuotes = !inQuotes;
        current ++;
        // don't append the quote to the result
        continue;
      }

      element.append(c);
      current ++;
    }

    current += this.delimiterLength;
    if (current == len && isDelimiter(lineData.charAt(current-delimiterLength), current - delimiterLength))
    {
      // if the line ends with the delimiter, we have one more (empty) element
      oneMore = true;
    }
    String next = element.toString();
    if (hadQuotes && unquotedEmptyIsNull && next.length() == 0) return StringUtil.EMPTY_STRING;
    if (!returnEmptyStrings && next.length() == 0) return null;

    if (trimValues) next = next.trim();
    return next;
  }

  private char getPreviousChar(int pos)
  {
    if (pos < 1) return 0;
    return lineData.charAt(pos - 1);
  }

  private char getNextChar(int pos)
  {
    if (pos < len - 1) return lineData.charAt(pos + 1);
    return 0;
  }

  @Override
  public void setTrimValues(boolean trimValues)
  {
    this.trimValues = trimValues;
  }

  public List<String> getAllElements()
  {
    List<String> result = new ArrayList<>();
    while (hasNext())
    {
      result.add(getNext());
    }
    return result;
  }
}
