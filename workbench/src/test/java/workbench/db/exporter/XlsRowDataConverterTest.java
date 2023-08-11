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
package workbench.db.exporter;

import java.sql.Types;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.importer.ExcelReader;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.WbFile;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class XlsRowDataConverterTest
  extends WbTestCase
{

  public XlsRowDataConverterTest()
  {
    super("XlsRowDataConverterTest");
  }

  @Test
  public void testFormulas()
    throws Exception
  {
    XlsRowDataConverter converter = new XlsRowDataConverter();
    converter.setUseXLSX();

    String[] cols = new String[] { "price", "pieces", "total_price"};
    int[] types = new int[] { Types.DECIMAL, Types.INTEGER, Types.VARCHAR };
    int[] sizes = new int[] { 10, 10, 20};

    DataStore ds = new DataStore(cols, types, sizes);
    ds.setGeneratingSql("select price, pieces, 'A1*B1' as total_price from products");
    int row = ds.addRow();
    ds.setValue(row, "price", 10.5);
    ds.setValue(row, "pieces", 5);
    ds.setValue(row, "total_price", "A2*B2");
    TestUtil util = getTestUtil();
    WbFile output = new WbFile(util.getBaseDir(), "formula_test.xlsx");
    output.delete();
    converter.setOutputFile(output);
    converter.setResultInfo(ds.getResultInfo());
    converter.setFormulaColumns(CollectionUtil.arrayList("total_price"));
    converter.setWriteHeader(true);
    converter.getStart();
    converter.convertRowData(ds.getRow(0), 0);
    converter.getEnd(1);

    ExcelReader reader = new ExcelReader(output, 1, null);
    reader.setActiveWorksheet(0);
    reader.load();
    List<Object> values = reader.getRowValues(1);
    Number total = (Number)values.get(2);
    assertEquals(52.5, total.doubleValue(), 0.01);
    reader.done();
  }

}
