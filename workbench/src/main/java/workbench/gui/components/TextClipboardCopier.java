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
package workbench.gui.components;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.table.TableColumnModel;

import workbench.WbManager;
import workbench.console.DataStorePrinter;
import workbench.console.TextPrinter;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.exporter.ExportType;

import workbench.gui.WbSwingUtilities;

import workbench.storage.DataPrinter;

import workbench.util.CharacterRange;
import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class TextClipboardCopier
{
  private final ExportType type;

  private boolean includeHeader;
  private boolean useMarkdown;
  private String delimiter = "\t";
  private boolean selectedOnly;
  private String quoteChar = Settings.getInstance().getProperty("workbench.copy.text.quotechar", "\"");
  private String nullString = GuiSettings.getDisplayNullString();

  private List<ColumnIdentifier> columnsToCopy;
  private WbTable data;

  public TextClipboardCopier(ExportType exportType, WbTable source)
  {
    this.data = source;
    this.type = exportType;
  }

  public void setColumnsToCopy(List<ColumnIdentifier> columns)
  {
    this.columnsToCopy = columns;
  }

  public void setDelimiter(String delimiter)
  {
    if (StringUtil.isBlank(delimiter))
    {
      this.delimiter = delimiter;
    }
  }

  public void setIncludeHeader(boolean flag)
  {
    this.includeHeader = flag;
  }

  public void setSelectedOnly(boolean flag)
  {
    this.selectedOnly = flag;
  }

  public void setNullString(String nullString)
  {
    this.nullString = nullString;
  }

  public void setUseMarkdown(boolean flag)
  {
    this.useMarkdown = flag;
  }

  public void setQuoteChar(String quote)
  {
    if (StringUtil.isNotBlank(quote))
    {
      this.quoteChar = quote;
    }
  }

  public void doCopy()
  {
    if (this.data == null)
    {
      WbSwingUtilities.showErrorMessage(data, "No DataStore available!");
      LogMgr.logError(new CallerInfo(){}, "Cannot copy without a DataStore!", null);
      return;
    }

    if (this.data.getRowCount() <= 0) return;

    try
    {
      int count = this.data.getRowCount();
      int[] rows = null;
      if (selectedOnly)
      {
        rows = data == null ? null : data.getSelectedRows();
        count = rows == null ? 0 : rows.length;
      }

      boolean includeHtml = Settings.getInstance().copyToClipboardAsHtml();
      StringWriter out = new StringWriter(count * 250);

      if (type == ExportType.FORMATTED_TEXT)
      {
        // never support HTML for "formatted text"
        includeHtml = false;
        DataStorePrinter printer = new DataStorePrinter(this.data.getDataStore());
        printer.setNullString(GuiSettings.getDisplayNullString());
        printer.setFormatColumns(true);
        printer.setPrintRowCount(false);
        printer.setUseMarkdownFormatting(useMarkdown);
        printer.setShowResultName(GuiSettings.copyToClipboardFormattedTextWithResultName());
        if (columnsToCopy != null)
        {
          List<String> colNames =new ArrayList<>(columnsToCopy.size());
          for (ColumnIdentifier id : columnsToCopy)
          {
            colNames.add(id.getColumnName());
          }
          printer.setColumnsToPrint(colNames);
        }
        TextPrinter pw = TextPrinter.createPrinter(new PrintWriter(out));
        printer.printTo(pw, rows);
      }
      else
      {
        // Do not use StringUtil.LINE_TERMINATOR for the line terminator
        // because for some reason this creates additional empty lines under Windows
        DataPrinter printer = new DataPrinter(this.data.getDataStore(), delimiter, "\n", columnsToCopy, includeHeader);

        String name = Settings.getInstance().getProperty("workbench.copy.text.escaperange", CharacterRange.RANGE_NONE.getName());
        CharacterRange range = CharacterRange.getRangeByName(name);
        printer.setEscapeRange(range);
        printer.setAbortOnMissingQuoteChar(false);
        printer.setQuoteChar(quoteChar);
        printer.setNullString(nullString);
        printer.setColumnMapping(getColumnOrder());

        printer.writeDataString(out, rows);
      }

      WbSwingUtilities.showWaitCursorOnWindow(this.data);
      Clipboard clp = getClipboard();
      StringSelectionAdapter sel = new StringSelectionAdapter(out.toString(), includeHtml, delimiter, quoteChar);
      clp.setContents(sel, sel);
    }
    catch (Throwable ex)
    {
      if (ex instanceof OutOfMemoryError)
      {
        WbManager.getInstance().showOutOfMemoryError();
      }
      else
      {
        String msg = ResourceMgr.getString("ErrClipCopy");
        msg = StringUtil.replace(msg, "%errmsg%", ExceptionUtil.getDisplay(ex));
        WbSwingUtilities.showErrorMessage(data, msg);
      }
      LogMgr.logError(new CallerInfo(){}, "Could not copy text data to clipboard", ex);
    }
    WbSwingUtilities.showDefaultCursorOnWindow(this.data);
  }

  private int[] getColumnOrder()
  {
    if (data == null) return null;
    if (!data.isColumnOrderChanged()) return null;

    TableColumnModel model = data.getColumnModel();
    int colCount = model.getColumnCount();
    int[] result = new int[colCount];

    for (int i=0; i < colCount; i++)
    {
      int modelIndex = model.getColumn(i).getModelIndex();
      result[i] = modelIndex;
    }
    return result;
  }

  /**
   * Protected so that Unit Tests can use the non-system clipboard.
   */
  protected Clipboard getClipboard()
  {
    return Toolkit.getDefaultToolkit().getSystemClipboard();
  }

}
