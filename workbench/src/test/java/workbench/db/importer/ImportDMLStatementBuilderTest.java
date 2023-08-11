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
package workbench.db.importer;

import java.sql.Types;
import java.util.List;

import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.util.CollectionUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ImportDMLStatementBuilderTest
  extends WbTestCase
{

  public ImportDMLStatementBuilderTest()
  {
    super("ImportDMLTest");
  }

  @Test
  public void testStandardMerge()
  {
    ColumnIdentifier pk1 = new ColumnIdentifier("pk1", Types.INTEGER);
    pk1.setIsNullable(false);
    pk1.setIsPkColumn(true);

    ColumnIdentifier pk2 = new ColumnIdentifier("pk2", Types.INTEGER);
    pk2.setIsNullable(false);
    pk2.setIsPkColumn(true);

    ColumnIdentifier c1 = new ColumnIdentifier("c1", Types.VARCHAR);
    ColumnIdentifier c2 = new ColumnIdentifier("c2", Types.VARCHAR);
    List<ColumnIdentifier> columns = CollectionUtil.arrayList(pk1, pk2, c1, c2);

    TableIdentifier tbl = new TableIdentifier("dbo", "some_table");
    ImportDMLStatementBuilder builder = new ImportDMLStatementBuilder(null, tbl, columns, null, false);
    builder.setKeyColumns(CollectionUtil.arrayList(pk1, pk2));
    String sql = builder.createStandardMerge(null, false, "USING ");
    String expected =
      "MERGE INTO dbo.some_table AS tg\n" +
      "USING (\n" +
      "  VALUES (?,?,?,?)\n" +
      ") AS vals (pk1,pk2,c1,c2)\n" +
      "  ON tg.pk1 = vals.pk1 AND tg.pk2 = vals.pk2\n" +
      "WHEN MATCHED THEN UPDATE\n" +
      "  SET tg.c1 = vals.c1,\n" +
      "      tg.c2 = vals.c2\n" +
      "WHEN NOT MATCHED THEN INSERT\n" +
      "  (pk1, pk2, c1, c2)\n" +
      "VALUES\n" +
      "  (vals.pk1, vals.pk2, vals.c1, vals.c2)";

    assertEquals(expected, sql);
  }

  @Test
  public void testOracleMerge()
  {
    ColumnIdentifier pk1 = new ColumnIdentifier("pk1", Types.INTEGER);
    pk1.setIsNullable(false);
    pk1.setIsPkColumn(true);

    ColumnIdentifier pk2 = new ColumnIdentifier("pk2", Types.INTEGER);
    pk2.setIsNullable(false);
    pk2.setIsPkColumn(true);

    ColumnIdentifier c1 = new ColumnIdentifier("c1", Types.VARCHAR);
    ColumnIdentifier c2 = new ColumnIdentifier("c2", Types.VARCHAR);
    List<ColumnIdentifier> columns = CollectionUtil.arrayList(pk1, pk2, c1, c2);

    TableIdentifier tbl = new TableIdentifier("arthur", "some_table");
    ImportDMLStatementBuilder builder = new ImportDMLStatementBuilder(null, tbl, columns, null, false);
    builder.setKeyColumns(CollectionUtil.arrayList(pk1, pk2));
    String sql = builder.createOracleMerge(null);
//    System.out.println(sql);
    String expected =
      "MERGE INTO arthur.some_table tg\n" +
      " USING (\n" +
      "  SELECT ? AS pk1,? AS pk2,? AS c1,? AS c2 FROM DUAL\n" +
      ") vals ON (tg.pk1 = vals.pk1 AND tg.pk2 = vals.pk2)\n" +
      "WHEN MATCHED THEN UPDATE\n" +
      "  SET tg.c1 = vals.c1,\n" +
      "      tg.c2 = vals.c2\n" +
      "WHEN NOT MATCHED THEN INSERT\n" +
      "  (pk1, pk2, c1, c2)\n" +
      "VALUES\n" +
      "  (vals.pk1, vals.pk2, vals.c1, vals.c2)";

    assertEquals(expected, sql);
  }

  @Test
  public void testPostgresUpsert()
  {
    ColumnIdentifier pk1 = new ColumnIdentifier("pk1", Types.INTEGER);
    pk1.setIsNullable(false);
    pk1.setIsPkColumn(true);

    ColumnIdentifier pk2 = new ColumnIdentifier("pk2", Types.INTEGER);
    pk2.setIsNullable(false);
    pk2.setIsPkColumn(true);

    ColumnIdentifier c1 = new ColumnIdentifier("c1", Types.VARCHAR);
    ColumnIdentifier c2 = new ColumnIdentifier("c2", Types.VARCHAR);
    List<ColumnIdentifier> columns = CollectionUtil.arrayList(pk1, pk2, c1, c2);

    TableIdentifier tbl = new TableIdentifier("public", "some_table");
    ImportDMLStatementBuilder builder = new ImportDMLStatementBuilder(null, tbl, columns, null, false);
    builder.setKeyColumns(CollectionUtil.arrayList(pk1, pk2));
    String sql = builder.createPostgresUpsert(null, null, false);
//    System.out.println(sql);
    String expected =
      "INSERT INTO public.some_table (pk1,pk2,c1,c2)\n" +
      "VALUES \n" +
      "(?,?,?,?)\n" +
      "ON CONFLICT (pk1,pk2)\n" +
      "DO UPDATE\n" +
      "  SET c1 = EXCLUDED.c1,\n" +
      "      c2 = EXCLUDED.c2";
    assertEquals(expected, sql);
  }

}
