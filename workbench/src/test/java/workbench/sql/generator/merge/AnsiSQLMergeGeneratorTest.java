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
package workbench.sql.generator.merge;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;
import workbench.storage.RowDataContainer;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class AnsiSQLMergeGeneratorTest
  extends WbTestCase
{
  public AnsiSQLMergeGeneratorTest()
  {
    super("AnsiSQLMergeGeneratorTest");
  }

  @Test
  public void testGenerateMerge()
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier fname = new ColumnIdentifier("fname", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("lname", Types.VARCHAR);
    ColumnIdentifier dob = new ColumnIdentifier("dob", Types.DATE);
    ResultInfo info = new ResultInfo(new ColumnIdentifier[] { id, fname, lname, dob });

    TableIdentifier tbl = new TableIdentifier("person");
    info.setUpdateTable(tbl);
    DataStore ds = new DataStore(info);
    ds.forceUpdateTable(tbl);
    int row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(42));
    ds.setValue(row, 1, "Arthur");
    ds.setValue(row, 2, "Dent");
    ds.setValue(row, 3, LocalDate.now());

    row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(24));
    ds.setValue(row, 1, "Ford");
    ds.setValue(row, 2, "Prefect");
    ds.setValue(row, 3, LocalDate.now());

    AnsiSQLMergeGenerator generator = new AnsiSQLMergeGenerator();
    List<ColumnIdentifier> cols = CollectionUtil.arrayList(id, lname, fname);
    generator.setColumns(cols);
    String sql = generator.generateMerge(ds);
    assertNotNull(sql);
    String expected =
      "MERGE INTO person ut\n" +
      "USING (\n" +
      "  VALUES\n" +
      "    (42, 'Arthur', 'Dent'),\n" +
      "    (24, 'Ford', 'Prefect')\n" +
      ") AS md (id, fname, lname) ON (ut.id = md.id)\n" +
      "WHEN MATCHED THEN UPDATE\n" +
      "     SET fname = md.fname,\n" +
      "         lname = md.lname\n" +
      "WHEN NOT MATCHED THEN\n" +
      "  INSERT (id, fname, lname)\n" +
      "  VALUES (md.id, md.fname, md.lname);";
//    System.out.println("----- expected: \n" + expected + "\n****** result: \n" + sql + "\n-------");
    assertEquals(expected, sql.trim());

    RowDataContainer selected = RowDataContainer.Factory.createContainer(ds, new int[] {0});
    sql = generator.generateMerge(selected);
    assertNotNull(sql);

    expected =
      "MERGE INTO person ut\n" +
      "USING (\n" +
      "  VALUES\n" +
      "    (42, 'Arthur', 'Dent')\n" +
      ") AS md (id, fname, lname) ON (ut.id = md.id)\n" +
      "WHEN MATCHED THEN UPDATE\n" +
      "     SET fname = md.fname,\n" +
      "         lname = md.lname\n" +
      "WHEN NOT MATCHED THEN\n" +
      "  INSERT (id, fname, lname)\n" +
      "  VALUES (md.id, md.fname, md.lname);";
//    System.out.println("----- expected: \n" + expected + "\n****** result: \n" + sql + "\n-------");
    assertEquals(expected, sql.trim());
  }

  @Test
  public void testIncremental()
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier fname = new ColumnIdentifier("fname", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("lname", Types.VARCHAR);
    ResultInfo info = new ResultInfo(new ColumnIdentifier[] { id, fname, lname });

    TableIdentifier tbl = new TableIdentifier("person");
    info.setUpdateTable(tbl);
    DataStore ds = new DataStore(info);
    ds.forceUpdateTable(tbl);
    int row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(42));
    ds.setValue(row, 1, "Arthur");
    ds.setValue(row, 2, "Dent");

    row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(24));
    ds.setValue(row, 1, "Ford");
    ds.setValue(row, 2, "Prefect");

    AnsiSQLMergeGenerator generator = new AnsiSQLMergeGenerator();
    StringBuilder result = new StringBuilder(100);
    String part = generator.generateMergeStart(ds);
    result.append(part);
    part = generator.addRow(info, ds.getRow(0), 0);
    result.append(part);

    part = generator.addRow(info, ds.getRow(1), 1);
    result.append(part);

    result.append(generator.generateMergeEnd(ds));

    String expected =
      "MERGE INTO person ut\n" +
      "USING (\n" +
      "  VALUES\n" +
      "    (42, 'Arthur', 'Dent'),\n" +
      "    (24, 'Ford', 'Prefect')\n" +
      ") AS md (id, fname, lname) ON (ut.id = md.id)\n" +
      "WHEN MATCHED THEN UPDATE\n" +
      "     SET fname = md.fname,\n" +
      "         lname = md.lname\n" +
      "WHEN NOT MATCHED THEN\n" +
      "  INSERT (id, fname, lname)\n" +
      "  VALUES (md.id, md.fname, md.lname);";
//    System.out.println("----- expected: \n" + expected + "\n****** result: \n" + result.toString() + "\n-------");
    assertEquals(expected, result.toString().trim());
  }
}
