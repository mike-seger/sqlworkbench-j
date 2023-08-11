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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import workbench.WbManager;
import workbench.resource.GuiSettings;

import workbench.db.ColumnIdentifier;

import workbench.gui.WbSwingUtilities;
import workbench.gui.renderer.SortHeaderRenderer;
import workbench.gui.renderer.WbRenderer;

import workbench.util.StringUtil;

/**
 * A class to adjust the column width of a WbTable to the displayed values.
 * <p>
 * In order for this to calculate string width's correctly, the table must have been added
 * to the Swing component tree.
 * <p>
 * All  methods should be called on AWT's EDT thread!
 *
 * @author Thomas Kellerer
 */
public class ColumnWidthOptimizer
{
  private final WbTable table;
  private final int maxLines;
  private final int remarksMinLength;
  private final int remarksMaxLength;

  public ColumnWidthOptimizer(WbTable client)
  {
    this.table = client;
    this.maxLines = GuiSettings.getAutRowHeightMaxLines();
    this.remarksMinLength = GuiSettings.remarksHeaderMinLength();
    this.remarksMaxLength = GuiSettings.remarksHeaderMaxLength();
  }

  public void optimizeAllColWidth()
  {
    this.optimizeAllColWidth(GuiSettings.getMinColumnWidth(), GuiSettings.getMaxColumnWidth(), GuiSettings.getIncludeHeaderInOptimalWidth());
  }

  public void optimizeAllColWidth(boolean respectColName)
  {
    this.optimizeAllColWidth(GuiSettings.getMinColumnWidth(), GuiSettings.getMaxColumnWidth(), respectColName);
  }

  public void optimizeAllColWidth(int minWidth, int maxWidth, boolean respectColName)
  {
    if (table == null) return;
    final int count = this.table.getColumnCount();
    if (count <= 0) return;

    final int[] widths = new int[count];

    TableCellRenderer realRenderer = table.getTableHeader().getDefaultRenderer();
    JComponent c = (JComponent)realRenderer.getTableCellRendererComponent(table, "", false, false, -1, 0);
    Insets insets = c.getInsets();
    FontMetrics fm = c.getFontMetrics(c.getFont());

    for (int col = 0; col < count; col++)
    {
      widths[col] =  calculateOptimalColumnWidth(col, minWidth, maxWidth, respectColName, fm, insets);
    }

    TableColumnModel colMod = this.table.getColumnModel();
    for (int col = 0; col < count; col++)
    {
      if (widths[col] > 0)
      {
        TableColumn column = colMod.getColumn(col);
        column.setPreferredWidth(widths[col]);
      }
    }
    table.validate();
  }

  public void optimizeColWidth(int aColumn, boolean respectColName)
  {
    this.optimizeColWidth(aColumn, GuiSettings.getMinColumnWidth(), GuiSettings.getMaxColumnWidth(), respectColName);
    WbSwingUtilities.repaintLater(table);
    WbSwingUtilities.repaintLater(table.getTableHeader());
  }

  private void optimizeColWidth(int col, int minWidth, int maxWidth, boolean respectColumnName)
  {
    int width = calculateOptimalColumnWidth(col, minWidth, maxWidth, respectColumnName, null);
    WbSwingUtilities.invoke(() -> {
      if (width > 0)
      {
        TableColumnModel colMod = this.table.getColumnModel();
        TableColumn column = colMod.getColumn(col);
        column.setPreferredWidth(width);
      }
    });
  }

  public int calculateOptimalColumnWidth(int col, int minWidth, int maxWidth, boolean respectColumnName, FontMetrics fontInfo)
  {
    return calculateOptimalColumnWidth(col, minWidth, maxWidth, respectColumnName, fontInfo, getHeaderInsets());
  }

  public int calculateOptimalColumnWidth(int col, int minWidth, int maxWidth, boolean respectColumnName,
                                         FontMetrics fontInfo, Insets headerInsets)
  {
    if (table == null || col < 0 || col > table.getColumnCount() - 1)
    {
      return -1;
    }

    int optWidth = minWidth;

    if (respectColumnName)
    {
      optWidth = calculateOptimalHeaderWidth(col, fontInfo, headerInsets);
    }

    int rowCount = this.table.getRowCount();
    int addWidth = getAdditionalColumnSpace();

    for (int row = 0; row < rowCount; row++)
    {
      String displayValue = null;
      int stringWidth = 0;

      TableCellRenderer rend = this.table.getCellRenderer(row, col);
      Object value = table.getValueAt(row, col);
      JComponent c = (JComponent)rend.getTableCellRendererComponent(this.table, value, false, false, row, col);
      FontMetrics fm = fontInfo;

      // Don't use table.getFont() as the renderer might have changed the font for this cell
      Font f = c.getFont();
      if (fm == null)
      {
        // The component's font metrics aren't really accurate
        // because the component isn't part of the Swing component tree.
        // In that case the font information is not accurate enough
        // (especially on HiDPI screens)
        fm = getFontMetrics(f);
        if (fm == null)
        {
          fm = c.getFontMetrics(f);
        }
      }

      // The value that is displayed in the table through the renderer
      // is not necessarily identical to the String returned by table.getValueAsString()
      // so we'll first ask the Renderer or its component for the displayed value.
      if (c instanceof WbRenderer)
      {
        WbRenderer wb = (WbRenderer)c;
        stringWidth += wb.addToDisplayWidth();
        displayValue = wb.getDisplayValue();
      }
      else if (c instanceof JTextArea)
      {
        JTextArea text = (JTextArea)c;
        String t = text.getText();
        displayValue = StringUtil.getLongestLine(t, maxLines);
      }
      else if (c instanceof JLabel)
      {
        // DefaultCellRenderer is a JLabel
        displayValue = ((JLabel)c).getText();
      }
      else
      {
        displayValue = this.table.getValueAsString(row, col);
      }

      if (displayValue != null)
      {
        String visible = StringUtil.rtrim(displayValue);
        stringWidth += (int)Math.ceil(f.getStringBounds(visible, fm.getFontRenderContext()).getWidth());
        stringWidth += (int)(fm.getMaxAdvance() / 4);
        if (visible.length() < displayValue.length())
        {
          // accommodate for the "..." display if the string is truncated
          stringWidth += fm.getMaxAdvance() * 3;
        }
      }

      optWidth = Math.max(optWidth, stringWidth + addWidth);
    }

    if (maxWidth > 0)
    {
      optWidth = Math.min(optWidth, maxWidth);
    }
    return optWidth;
  }

  private FontMetrics getFontMetrics(Font f)
  {
    FontMetrics fm = table == null ? null : table.getFontMetrics(f);
    if (fm != null) return fm;

    JWindow win = (JWindow)SwingUtilities.getWindowAncestor(table);
    if (win != null)
    {
      fm = win.getFontMetrics(f);
    }
    if (fm != null) return fm;

    JFrame main = WbManager.getInstance().getCurrentWindow();
    if (main != null)
    {
      fm = main.getFontMetrics(f);
    }
    return fm;
  }

  /**
   * Adjust the column header width after sorting.
   */
  public void optimizeHeader()
  {
    if (table == null) return;
    TableColumnModel colMod = this.table.getColumnModel();
    if (colMod == null) return;
    Insets insets = getHeaderInsets();
    for (int col = 0; col < table.getColumnCount(); col ++)
    {
      TableColumn column = colMod.getColumn(col);

      // This method is only used to adjust the column header after the sort indicators have been displayed.
      // As the current width is most probably already adjusted (and reflects the size of the data in this column)
      // the new width should not be smaller than the old width (because the row data is not evaluated here!)
      int oldWidth = column.getWidth();
      int width = calculateOptimalHeaderWidth(col, null, insets);

      if (width > oldWidth)
      {
        column.setPreferredWidth(width);
      }
    }
  }

  private Insets getHeaderInsets()
  {
    TableCellRenderer realRenderer = table.getTableHeader().getDefaultRenderer();
    JComponent c = (JComponent)realRenderer.getTableCellRendererComponent(table, "", false, false, -1, 0);
    if (c != null)
    {
      return c.getInsets();
    }
    return new Insets(1,1,1,1);
  }

  public int calculateOptimalHeaderWidth(int col, FontMetrics fm, Insets headerInsets)
  {
    if (table == null || col < 0 || col > table.getColumnCount() - 1)
    {
      return -1;
    }

    SortHeaderRenderer renderer = table.getHeaderRenderer();
    if (renderer == null) return -1;

    String colName = table.getColumnName(col);

    int iconWidth = 0;
    if (table.isViewColumnSorted(col))
    {
      iconWidth = (int)(SortHeaderRenderer.getArrowSize(fm, table.isPrimarySortColumn(col)) * 1.05);
    }

    boolean dataTypeVisible = false;
    boolean remarksVisible = false;
    boolean tableNameVisible = false;

    DataStoreTableModel model = table.getDataStoreTableModel();

    if (renderer != null)
    {
      dataTypeVisible = renderer.getShowDataType();
      remarksVisible = renderer.getShowRemarks();
      tableNameVisible = renderer.getShowColumnTable();
      if (renderer.getShowTableAsColumnPrefix())
      {
        tableNameVisible = false;
        ColumnIdentifier colId = model.getColumn(col);
        if (colId != null)
        {
          colName = StringUtil.concatWithSeparator(".", colId.getSourceTableName(), colName);
        }
      }
    }

    FontMetrics hfm = fm;
    if (hfm == null)
    {
      JComponent c = (JComponent)renderer.getTableCellRendererComponent(table, colName, false, false, -1, col);
      hfm = c.getFontMetrics(c.getFont());
    }
    if (hfm == null) return -1;

    int addHeaderSpace = getAdditionalColumnSpace() + headerInsets.left + headerInsets.right;
    int headerWidth = hfm.stringWidth(colName) + addHeaderSpace;

    if (renderer == null || model == null) return headerWidth + iconWidth;

    if (dataTypeVisible)
    {
      String typeName = model.getDbmsType(col);
      if (typeName != null)
      {
        int typeWidth = hfm.stringWidth(typeName) + addHeaderSpace;
        if (typeWidth > headerWidth)
        {
          headerWidth = typeWidth;
        }
      }
    }

    if (remarksVisible)
    {
      String remarks = model.getColumnRemarks(col);
      if (StringUtil.isNotBlank(remarks))
      {
        int pos = StringUtil.findFirstWhiteSpace(remarks, (char)0, remarksMinLength);
        String word;
        if (pos > -1)
        {
          word = remarks.substring(0, pos);
        }
        else
        {
          word = StringUtil.getMaxSubstring(remarks, remarksMaxLength, null);
        }
        int commentWidth = hfm.stringWidth(word) + addHeaderSpace;
        if (commentWidth > headerWidth)
        {
          headerWidth = commentWidth;
        }
      }
    }

    if (tableNameVisible)
    {
      String tname = model.getColumnTable(col);
      if (StringUtil.isNotEmpty(tname))
      {
        int tableWidth = hfm.stringWidth(tname) + addHeaderSpace;
        if (tableWidth > headerWidth)
        {
          headerWidth = tableWidth;
        }
      }
    }

    return headerWidth + iconWidth;
  }

  private int getAdditionalColumnSpace()
  {
    int addWidth = table.getIntercellSpacing().width;
    if (table.getShowVerticalLines())
    {
      addWidth += 4;
    }
    addWidth += table.getColumnModel().getColumnMargin();

    if (addWidth == 0)
    {
      addWidth = 4;
    }
    return addWidth;
  }

  /**
   * Adjusts the columns to the width defined from the
   * underlying tables.
   *
   * This will use getColumnWidth() for each column, it does not take the columns content into account
   *
   * @see #optimizeAllColWidth()
   */
  public void adjustColumns(boolean adjustToColumnLabel)
  {
    if (this.table.getModel() == null) return;

    DataStoreTableModel dwModel = this.table.getDataStoreTableModel();
    if (dwModel == null) return;

    Font f = this.table.getFont();
    FontMetrics fm = this.table.getFontMetrics(f);
    int charWidth = Math.max(fm.getMaxAdvance(), fm.charWidth('M'));
    TableColumnModel colMod = this.table.getColumnModel();
    if (colMod == null) return;

    int minWidth = GuiSettings.getMinColumnWidth();
    int maxWidth = GuiSettings.getMaxColumnWidth();

    int addWidth = this.getAdditionalColumnSpace();

    for (int i = 0; i < colMod.getColumnCount(); i++)
    {
      TableColumn col = colMod.getColumn(i);
      int lblWidth = 0;
      if (adjustToColumnLabel)
      {
        String s = dwModel.getColumnName(i);
        lblWidth = fm.stringWidth(s) + addWidth;
      }

      int width = (dwModel.getColumnWidth(i) * charWidth) + addWidth;
      int w = Math.max(width, lblWidth);

      if (maxWidth > 0)
      {
        w = Math.min(w, maxWidth);
      }
      if (minWidth > 0)
      {
        w = Math.max(w, minWidth);
      }
      col.setPreferredWidth(w);
    }
  }
}
