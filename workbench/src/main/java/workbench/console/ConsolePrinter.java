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
package workbench.console;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingConstants;

import workbench.interfaces.Interruptable;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.exporter.TextRowDataConverter;

import workbench.storage.RowActionMonitor;
import workbench.storage.RowData;

import workbench.util.CharacterRange;
import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to print results to the console
 * Concrete classes either print a DataStore or a ResultSet
 *
 * @author Thomas Kellerer
 */
public abstract class ConsolePrinter
  implements Interruptable
{
  protected Map<Integer, Integer> columnWidths;
  protected TextRowDataConverter converter = new TextRowDataConverter();
  protected boolean doFormat = true;
  protected boolean showRowCount = true;
  protected boolean printRowAsLine = true;
  protected boolean printContinuationIndicator = true;
  protected Set<String> includedColumns;

  protected abstract String getResultName();
  protected abstract Map<Integer, Integer> getColumnSizes();
  protected abstract int getColumnCount();
  protected abstract String getColumnName(int col);
  protected abstract int getColumnType(int col);
  protected String nullString;
  protected boolean showResultName = true;
  protected boolean printHeader = true;
  protected boolean useMarkdownFormatting = false;
  protected boolean cancelled;
  protected RowActionMonitor rowMonitor;

  public ConsolePrinter()
  {
    converter.setNullString(ConsoleSettings.getNullString());
    converter.setDefaultNumberFormatter(Settings.getInstance().createDefaultDecimalFormatter());
    converter.setDefaultIntegerFormatter(Settings.getInstance().createDefaultIntegerFormatter());
    nullString = ConsoleSettings.getNullString();
    useMarkdownFormatting = Settings.getInstance().useMarkDownForConsolePrint();
  }

  public void setUseMarkdownFormatting(boolean flag)
  {
    this.useMarkdownFormatting = flag;
  }

  public void setShowResultName(boolean showResultName)
  {
    this.showResultName = showResultName;
  }

  public void setNullString(String displayValue)
  {
    nullString = displayValue;
  }

  public void setPrintContinuationIndicator(boolean flag)
  {
    this.printContinuationIndicator = flag;
  }

  public void setRowMonitor(RowActionMonitor monitor)
  {
    this.rowMonitor = monitor;
  }

  protected void updateProgress(long currentRow)
  {
    if (this.rowMonitor != null)
    {
      this.rowMonitor.setCurrentRow((int)currentRow, -1);
    }
  }

  /**
   * If set to true (the default) one row is printed per console line.
   * If set to false, one row is displayed as a "form", i.e. one row per column,
   * rows are divided by a divider line.
   */
  public void setPrintRowsAsLine(boolean flag)
  {
    printRowAsLine = flag;
  }

  /**
   * If formatting of columns is enabled, the width of each column
   * is adjusted to fit the data. How good the optimization is, depends
   * on the concrete implementation of this class.
   * <br/>
   * If formatting is disabled, values are printed in the width they need
   * with control characters escaped to ensure a single-output line per row
   *
   * @see DataStorePrinter
   * @see ResultSetPrinter
   * @see workbench.util.StringUtil#escapeText(java.lang.String, workbench.util.CharacterRange, java.lang.String)
   *
   * @param flag
   */
  public void setFormatColumns(boolean flag)
  {
    doFormat = flag;
  }

  public void setPrintRowCount(boolean flag)
  {
    this.showRowCount = flag;
  }

  public void setPrintHeader(boolean flag)
  {
    this.printHeader = flag;
  }

  @Override
  public void cancelExecution()
  {
    this.cancelled = true;
  }

  @Override
  public boolean confirmCancel()
  {
    return true;
  }

  protected void printHeader(TextPrinter pw)
  {
    if (!printRowAsLine) return;

    if (columnWidths == null && doFormat)
    {
      columnWidths = getColumnSizes();
    }

    String resultName = getResultName();

    if (showResultName && StringUtil.isNotBlank(resultName))
    {
      pw.println("---- " + resultName);
    }
    int headerWidth = 0;
    int currentCol = 0;
    if (doFormat && useMarkdownFormatting)
    {
      pw.print("|");
    }
    for (int col = 0; col < getColumnCount(); col ++)
    {
      String colName = getColumnName(col);
      if (!isColumnIncluded(colName)) continue;

      if (currentCol > 0) pw.print(" | ");

      if (doFormat)
      {
        int colWidth = columnWidths.get(Integer.valueOf(col));
        writePadded(pw, colName, colWidth, false);
        headerWidth += colWidth;
      }
      else
      {
        pw.print(colName);
      }
      currentCol ++;
    }
    if (doFormat && useMarkdownFormatting)
    {
      pw.print("|");
    }
    pw.println();

    if (headerWidth > 0 && doFormat)
    {
      currentCol = 0;
      if (doFormat && useMarkdownFormatting)
      {
        pw.print("|");
      }
      // Print divider line
      for (int i=0; i < getColumnCount(); i++)
      {
        if (!isColumnIncluded(i)) continue;

        if (currentCol > 0)
        {
          if (useMarkdownFormatting)
          {
            pw.print("-|-");
          }
          else
          {
            pw.print("-+-");
          }
        }
        pw.print(StringUtil.padRight("-", columnWidths.get(Integer.valueOf(i)), '-'));
        currentCol ++;
      }
      if (doFormat && useMarkdownFormatting)
      {
        pw.print("|");
      }
      pw.println();
    }
  }

  public void setColumnsToPrint(Collection<String> columns)
  {
    if (CollectionUtil.isEmpty(columns))
    {
      includedColumns = null;
    }
    includedColumns = CollectionUtil.caseInsensitiveSet();
    includedColumns.addAll(columns);
  }

  protected void printRow(TextPrinter pw, RowData row, int rowNumber)
  {
    updateProgress(rowNumber + 1);
    if (printRowAsLine)
    {
      printAsLine(pw, row);
    }
    else
    {
      printAsRecord(pw, row, rowNumber);
    }
  }

  protected boolean isColumnIncluded(String colName)
  {
    if (includedColumns == null) return true;
    return includedColumns.contains(colName);
  }

  protected boolean isColumnIncluded(int index)
  {
    return isColumnIncluded(getColumnName(index));
  }

  public void printAsRecord(TextPrinter pw, RowData row, int rowNum)
  {
    int colcount = row.getColumnCount();
    int colwidth = 0;

    pw.println("---- [" + ResourceMgr.getString("TxtRow") + " " + (rowNum + 1) + "] -------------------------------");

    // Calculate max. colname width
    for (int col=0; col < colcount; col++)
    {
      String colname = getColumnName(col);
      if (colname.length() > colwidth) colwidth = colname.length();
    }

    for (int col=0; col < colcount; col++)
    {
      String colname = getColumnName(col);
      if (!isColumnIncluded(colname)) continue;

      String value = getDisplayValue(row, col);
      writePadded(pw, colname, colwidth + 1, false);
      pw.print(": ");
      if (doFormat)
      {
        String[] lines = value.split(StringUtil.REGEX_CRLF);
        pw.println(lines[0]);
        if (lines.length > 1)
        {
          for (int i=1; i < lines.length; i++)
          {
            writePadded(pw, " ", colwidth + 3, false);
            pw.println(lines[i]);
          }
        }
      }
      else
      {
        pw.println(value);
      }
    }
  }

  protected String getDisplayValue(RowData row, int col)
  {
    int type = getColumnType(col);
    String value = null;

    if (SqlUtil.isBlobType(type))
    {
      Object data = row.getValue(col);

      // In case the BLOB data was converted to a string by a DataConverter
      if (data instanceof String)
      {
        value = (String)row.getValue(col);
      }
      else if (data != null)
      {
        value = "(BLOB)";
      }
    }
    else
    {
      value = converter.getValueAsFormattedString(row, col);
    }

    if (value == null)
    {
      value = nullString;
    }

    return value;
  }

  protected void printAsLine(TextPrinter pw, RowData row)
  {
    int colcount = row.getColumnCount();
    try
    {
      Map<Integer, String[]> continuationLines = new HashMap<>(colcount);

      int realColCount = 0;
      if (doFormat && useMarkdownFormatting)
      {
        pw.print("|");
      }
      for (int col = 0; col < colcount; col ++)
      {
        if (!isColumnIncluded(col)) continue;
        if (realColCount > 0) pw.print(" | ");

        String value = getDisplayValue(row, col);

        if (doFormat)
        {
          int colwidth = columnWidths.get(Integer.valueOf(col));
          String[] lines = value.split(StringUtil.REGEX_CRLF);
          if (lines.length == 0)
          {
            // the value only contained newlines --> treat as an empty string (thus a single line)
            writePadded(pw, value.trim(), colwidth, alignRight(col));
          }
          else
          {
            writePadded(pw, lines[0], colwidth, alignRight(col));
            if (lines.length > 1)
            {
              continuationLines.put(col, lines);
            }
          }
        }
        else
        {
          pw.print(StringUtil.escapeText(value, CharacterRange.RANGE_CONTROL));
        }
        realColCount ++;
      }
      if (doFormat && useMarkdownFormatting)
      {
        pw.print("|");
      }
      pw.println();
      printContinuationLines(pw, continuationLines);

      pw.flush();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when printing DataStore contents", e);
    }
  }

  private boolean alignRight(int col)
  {
    if (GuiSettings.getNumberDataAlignment() == SwingConstants.LEFT) return false;
    int type = getColumnType(col);
    return SqlUtil.isNumberType(type);
  }

  private int getColStartColumn(int col)
  {
    if (columnWidths == null) return 0;
    int colstart = 0;
    for (int i=0; i < col; i++)
    {
      colstart += columnWidths.get(i);
      if (i > 0) colstart += 3;
    }
    return colstart;
  }

  private void printContinuationLines(TextPrinter pw, Map<Integer, String[]> lineMap)
  {
    boolean printed = true;
    int colcount = getColumnCount();
    int currentLine = 1; // line 0 has already been printed
    int printedColNr = 0;
    while (printed)
    {
      printed = false;
      int currentpos = 0;
      for (int col = 0; col < colcount; col ++)
      {
        if (!isColumnIncluded(col)) continue;
        String[] lines = lineMap.get(col);
        printedColNr ++;

        if (lines == null) continue;
        if (lines.length <= currentLine) continue;

        int colstart = getColStartColumn(col) - currentpos;
        writePadded(pw, "", colstart, false);
        if (printContinuationIndicator && printedColNr > 1)
        {
          pw.print(" : ");
        }
        pw.print(lines[currentLine]);
        currentpos = colstart + lines[currentLine].length() + (col * 3);
        printed = true;
      }
      currentLine++;
      if (printed) pw.println();
    }
  }

  private int writePadded(TextPrinter out, String value, int width, boolean rightAligned)
  {
    StringBuilder result = new StringBuilder(width);
    if (value != null) result.append(value);

    if (width > 0)
    {
      if (result.length() < width)
      {
        int delta = width - result.length();
        StringBuilder pad = new StringBuilder(delta);
        for (int i=0; i < delta; i++)
        {
          pad.append(' ');
        }
        if (rightAligned)
        {
          result.insert(0, pad);
        }
        else
        {
          result.append(pad);
        }
      }
    }
    out.print(result.toString());
    return result.length();
  }

}
