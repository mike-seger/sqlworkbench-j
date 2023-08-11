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

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.sql.Types;
import java.util.List;

import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.IndexColumn;
import workbench.db.PkDefinition;
import workbench.db.TableIdentifier;
import workbench.db.exporter.ExportType;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class TextClipboardCopierTest
  extends WbTestCase
{
  private final Clipboard testClip = new TestClipboard("testClip");

  public TextClipboardCopierTest()
  {
    super("TextClipboardCopierTest");
  }

  @Test
  public void testFormattedText()
    throws Exception
  {
    WbTable tbl = createData();

    TextClipboardCopier copier = new TextClipboardCopier(ExportType.FORMATTED_TEXT, tbl)
    {
      @Override
      protected Clipboard getClipboard()
      {
        return testClip;
      }
    };
    copier.setIncludeHeader(true);
    copier.setNullString(null);
    copier.doCopy();
    Transferable contents = testClip.getContents(copier);
    Object data = contents.getTransferData(DataFlavor.stringFlavor);
    assertTrue(data instanceof String);
    List<String> lines = StringUtil.getLines((String)data);
    assertEquals("id | firstname | lastname", lines.get(0));
    assertEquals("---+-----------+---------", lines.get(1));
    assertEquals(" 1 | Arthur    | Dent    ", lines.get(2));
    assertEquals(" 2 | Ford      | Prefect ", lines.get(3));

    copier.setUseMarkdown(true);
    copier.doCopy();
    contents = testClip.getContents(copier);
    data = contents.getTransferData(DataFlavor.stringFlavor);
    lines = StringUtil.getLines((String)data);
    assertEquals("|id | firstname | lastname|", lines.get(0));
    assertEquals("|---|-----------|---------|", lines.get(1));
    assertEquals("| 1 | Arthur    | Dent    |", lines.get(2));
  }

  @Test
  public void testCopyDataToClipboard()
    throws Exception
  {
    WbTable tbl = createData();

    TextClipboardCopier copier = new TextClipboardCopier(ExportType.TEXT, tbl)
    {
      @Override
      protected Clipboard getClipboard()
      {
        return testClip;
      }
    };
    copier.setIncludeHeader(true);
    copier.setNullString(null);
    copier.setQuoteChar("\"");
    copier.doCopy();
    Transferable contents = testClip.getContents(copier);
    Object data = contents.getTransferData(DataFlavor.stringFlavor);
    assertTrue(data instanceof String);
    List<String> lines = StringUtil.getLines((String)data);
    assertEquals(3, lines.size());
    assertEquals("id\tfirstname\tlastname", lines.get(0));
    assertEquals("1\tArthur\tDent", lines.get(1));
    assertEquals("2\tFord\tPrefect", lines.get(2));
  }

  private WbTable createData()
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier fname = new ColumnIdentifier("firstname", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("lastname", Types.VARCHAR);
    ResultInfo info = new ResultInfo(new ColumnIdentifier[] {id, fname, lname});

    IndexColumn col = new IndexColumn("id", 1);
    PkDefinition pk = new PkDefinition("pk_person", CollectionUtil.arrayList(col));
    TableIdentifier tbl = new TableIdentifier("person");
    tbl.setPrimaryKey(pk);

    info.setUpdateTable(tbl);
    DataStore ds = new DataStore(info);
    int row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(1));
    ds.setValue(row, 1, "Arthur");
    ds.setValue(row, 2, "Dent");

    row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(2));
    ds.setValue(row, 1, "Ford");
    ds.setValue(row, 2, "Prefect");

    ds.setUpdateTableToBeUsed(tbl);
    WbTable wbt = new WbTable();
    wbt.setModel(new DataStoreTableModel(ds));
    return wbt;
  }

}
