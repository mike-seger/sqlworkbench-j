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

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.*;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.StringUtil;
import workbench.util.WbStringTokenizer;

/**
 * A class to put plain text and HTML content into the clipboard.
 *
 * @author Francesco Trentini
 */
public class StringSelectionAdapter
  implements Transferable, ClipboardOwner
{
  private final DataFlavor[] flavors;

  private final String data; //plaintext
  private final String dataAsHTML; //html filled if requested

  public StringSelectionAdapter(String text, boolean includeHtml)
  {
    this(text, includeHtml, "\t", "\"");
  }

  public StringSelectionAdapter(String text, String htmlText)
  {
    data = text;
    flavors = new DataFlavor[] { DataFlavor.fragmentHtmlFlavor, DataFlavor.stringFlavor };
    dataAsHTML = htmlText;
  }

  public StringSelectionAdapter(String text, boolean includeHtml, String delimiter, String quoteChar)
  {
    this.data = text;
    if (includeHtml)
    {
      flavors = new DataFlavor[] { DataFlavor.fragmentHtmlFlavor, DataFlavor.stringFlavor };
      dataAsHTML = createHtmlFragment(text, delimiter, quoteChar);
    }
    else
    {
      flavors = new DataFlavor[] { DataFlavor.stringFlavor };
      dataAsHTML = null;
    }
  }

  private String createHtmlFragment(String text, String delimiter, String quoteChar)
  {
    try
    {
      String defaultCss =
        "table, th, td { border: 1px solid black; border-collapse: collapse; } " +
        "th, td { padding: 5px; text-align: left; } " +
        "table tr:nth-child(even) { background-color: #eee; } table tr:nth-child(odd) { background-color:#fff; } " +
        "table th { background-color: black; color: white; }";

      String css = Settings.getInstance().getCssForClipboardHtml(defaultCss);
      String preHtml =
        "<html><head><style>" + css + "</style></head><body>" + StringUtil.LINE_TERMINATOR +
        "<table>" + StringUtil.LINE_TERMINATOR;
      String postHtml = "</table>" + StringUtil.LINE_TERMINATOR + "</body></html>";
      String trOpen = "<tr>";
      String trClose = "</tr>" + StringUtil.LINE_TERMINATOR;
      String tdOpen = "<td>";
      String tdClose = "</td>";
      StringReader srctext = new StringReader(text);
      BufferedReader src = new BufferedReader(srctext);
      StringBuilder dst = new StringBuilder(text.length() + 100);

      dst.append(preHtml);

      WbStringTokenizer tok = new WbStringTokenizer(delimiter, true, quoteChar, false);
      for (String line = src.readLine(); line != null; line = src.readLine())
      {
        tok.setSourceString(line);
        List<String> fields = tok.getAllTokens();
        dst.append(trOpen);
        for (String field : fields)
        {
          dst.append(tdOpen);
          dst.append(StringUtil.coalesce(field, ""));
          dst.append(tdClose);
        }
        dst.append(trClose);
      }
      dst.append(postHtml);

      return dst.toString();
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not create HTML fragment", ex);
      return null;
    }
  }

  @Override
  public DataFlavor[] getTransferDataFlavors()
  {
    return (DataFlavor[])flavors.clone();
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor)
  {
    for (DataFlavor flv : flavors)
    {
      if (flv.equals(flavor)) return true;
    }
    return false;
  }

  @Override
  public Object getTransferData(DataFlavor flavor)
    throws UnsupportedFlavorException, IOException
  {
    if (!isDataFlavorSupported(flavor))
    {
      throw new UnsupportedFlavorException(flavor);
    }

    if (flavor.equals(DataFlavor.fragmentHtmlFlavor) && dataAsHTML != null)
    {
      return dataAsHTML;
    }

    return data;
  }

  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents)
  {
  }

  /**
   * For testing purposes.
   */
  public String getHTMLContent()
  {
    return dataAsHTML;
  }
}
