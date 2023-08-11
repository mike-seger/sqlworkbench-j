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
package workbench.db.compare;

import java.sql.Types;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;
import workbench.storage.StatementFactory;

import workbench.util.SqlUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Thomas Kellerer
 */
public class RowDataComparerTest
  extends WbTestCase
{
  public RowDataComparerTest()
  {
    super("RowDataComparerTest");
  }

  @Test
  public void testNullPKValues()
  {
    ColumnIdentifier[] cols = new ColumnIdentifier[3];
    cols[0] = new ColumnIdentifier("ID_1");
    cols[0].setIsPkColumn(true);
    cols[0].setIsNullable(true);
    cols[0].setDataType(Types.INTEGER);

    cols[1] = new ColumnIdentifier("ID_2");
    cols[1].setIsPkColumn(true);
    cols[1].setIsNullable(true);
    cols[1].setDataType(Types.INTEGER);

    cols[2] = new ColumnIdentifier("DATA");
    cols[2].setIsPkColumn(false);
    cols[2].setIsNullable(false);
    cols[2].setDataType(Types.INTEGER);

    ResultInfo info = new ResultInfo(cols);
    info.setUpdateTable(new TableIdentifier("SOME_TABLE"));

    StatementFactory factory = new StatementFactory(info, null);
    factory.setEmptyStringIsNull(true);
    factory.setIncludeNullInInsert(true);

    RowData reference = new RowData(info);
    reference.setValue(0, 1);
    reference.setValue(1, null);
    reference.setValue(2, 42);
    reference.resetStatus();

    RowData target = new RowData(info);
    target.setValue(0, 1);
    target.setValue(1, null);
    target.setValue(2, 2);
    target.resetStatus();

    RowDataComparer instance = new RowDataComparer();
    instance.setTypeSql();
    instance.setApplySQLFormatting(false);
    instance.setRows(reference, target);
    instance.setConnection(null);
    instance.setResultInfo(info);
    String sql = instance.getMigration(0).trim();
    assertEquals("UPDATE SOME_TABLE SET DATA = 42 WHERE ID_1 = 1 AND ID_2 IS NULL;", sql);
  }

  @Test
  public void testSpecialCharacters()
  {
    ColumnIdentifier[] cols = new ColumnIdentifier[2];
    cols[0] = new ColumnIdentifier("ID");
    cols[0].setIsPkColumn(true);
    cols[0].setIsNullable(false);
    cols[0].setDataType(Types.INTEGER);

    cols[1] = new ColumnIdentifier("DATA");
    cols[1].setIsPkColumn(false);
    cols[1].setIsNullable(false);
    cols[1].setDataType(Types.VARCHAR);
    cols[1].setDbmsType("text");

    ResultInfo info = new ResultInfo(cols);
    info.setUpdateTable(new TableIdentifier("SOME_TABLE"));

    RowData reference = new RowData(info);
    reference.setValue(0, 42);
    reference.setValue(1, "Foo \u00bb d\u00e9faul : 50 m\u00b2/emploi");
    reference.resetStatus();

    RowData target = new RowData(info);
    target.setValue(0, 42);
    target.setValue(1, "Foo \u00bb d\u00e9faul : 50 m\u00b2/emploi");
    target.resetStatus();

    RowDataComparer instance = new RowDataComparer();
    instance.setTypeSql();
    instance.setApplySQLFormatting(false);
    instance.setConnection(null);
    instance.setResultInfo(info);

    instance.setRows(reference, target);
    String sql = instance.getMigration(1);
    assertNull(sql);
  }

  @Test
  public void testGetMigrationSql()
  {
    ColumnIdentifier[] cols = new ColumnIdentifier[3];
    cols[0] = new ColumnIdentifier("ID");
    cols[0].setIsPkColumn(true);
    cols[0].setIsNullable(false);

    cols[1] = new ColumnIdentifier("FIRSTNAME");
    cols[1].setIsPkColumn(false);
    cols[1].setIsNullable(false);

    cols[2] = new ColumnIdentifier("LASTNAME");
    cols[2].setIsPkColumn(false);
    cols[2].setIsNullable(false);

    ResultInfo info = new ResultInfo(cols);
    info.setUpdateTable(new TableIdentifier("PERSON"));

    RowData reference = new RowData(info);
    reference.setValue(0, 42);
    reference.setValue(1, "Zaphod");
    reference.setValue(2, "Beeblebrox");
    reference.resetStatus();

    RowData target = new RowData(info);
    target.setValue(0, 42);
    target.setValue(1, "Arthur");
    target.setValue(2, "Beeblebrox");
    target.resetStatus();

    RowDataComparer instance = new RowDataComparer();
    instance.setTypeSql();
    instance.setApplySQLFormatting(false);
    instance.setRows(reference, target);
    instance.setConnection(null);
    instance.setResultInfo(info);

    String sql = instance.getMigration(1);
    String verb = SqlUtil.getSqlVerb(sql);
    assertEquals("UPDATE", verb);
    assertTrue(sql.contains("SET FIRSTNAME = 'Zaphod'"));

    instance.setRows(reference, null);
    sql = instance.getMigration(1);
    verb = SqlUtil.getSqlVerb(sql);
//    System.out.println(sql);
    assertEquals("INSERT", verb);
    assertTrue(sql.contains("(42,'Zaphod','Beeblebrox')"));

    reference = new RowData(info);
    reference.setValue(0, 42);
    reference.setValue(1, "Zaphod");
    reference.setValue(2, null);
    reference.resetStatus();

    target = new RowData(info);
    target.setValue(0, 42);
    target.setValue(1, "Zaphod");
    target.setValue(2, null);
    target.resetStatus();

    instance.setRows(reference, target);
    sql = instance.getMigration(1);
    assertNull(sql);
  }

  @Test
  public void testGetMigrationXml()
  {
    ColumnIdentifier[] cols = new ColumnIdentifier[3];
    cols[0] = new ColumnIdentifier("ID");
    cols[0].setIsPkColumn(true);
    cols[0].setIsNullable(false);

    cols[1] = new ColumnIdentifier("FIRSTNAME");
    cols[1].setIsPkColumn(false);
    cols[1].setIsNullable(false);

    cols[2] = new ColumnIdentifier("LASTNAME");
    cols[2].setIsPkColumn(false);
    cols[2].setIsNullable(false);

    ResultInfo info = new ResultInfo(cols);
    info.setUpdateTable(new TableIdentifier("PERSON"));

    StatementFactory factory = new StatementFactory(info, null);
    factory.setEmptyStringIsNull(true);
    factory.setIncludeNullInInsert(true);

    RowData reference = new RowData(info);
    reference.setValue(0, 42);
    reference.setValue(1, "Zaphod");
    reference.setValue(2, "Beeblebrox");
    reference.resetStatus();

    RowData target = new RowData(info);
    target.setValue(0, 42);
    target.setValue(1, "Arthur");
    target.setValue(2, "Beeblebrox");
    target.resetStatus();

    RowDataComparer instance = new RowDataComparer();
    instance.setTypeXml(false);
    instance.setRows(reference, target);
    instance.setConnection(null);
    instance.setResultInfo(info);

    String xml = instance.getMigration(1);
//    System.out.println(xml);
    assertTrue(xml.startsWith("<update>"));

    instance.setRows(reference, null);
    xml = instance.getMigration(1);
//    System.out.println(xml);
    assertTrue(xml.startsWith("<insert>"));

    reference = new RowData(info);
    reference.setValue(0, 42);
    reference.setValue(1, "Zaphod");
    reference.setValue(2, null);
    reference.resetStatus();

    target = new RowData(info);
    target.setValue(0, 42);
    target.setValue(1, "Zaphod");
    target.setValue(2, null);
    target.resetStatus();

    instance.setRows(reference, target);
    xml = instance.getMigration(1);
    assertNull(xml);
  }

  @Test
  public void testGetMigrationXml2()
  {
    ColumnIdentifier[] cols = new ColumnIdentifier[3];
    cols[0] = new ColumnIdentifier("ID");
    cols[0].setIsPkColumn(true);
    cols[0].setIsNullable(false);
    cols[0].setDataType(Types.INTEGER);
    cols[0].setDbmsType("integer");

    cols[1] = new ColumnIdentifier("SOME_DATA");
    cols[1].setIsPkColumn(false);
    cols[1].setIsNullable(false);
    cols[1].setDataType(Types.VARCHAR);
    cols[1].setDbmsType("varchar(100)");

    cols[2] = new ColumnIdentifier("SOME_MORE");
    cols[2].setIsPkColumn(false);
    cols[2].setIsNullable(false);
    cols[2].setDataType(Types.VARCHAR);
    cols[2].setDbmsType("varchar(100)");

    ResultInfo info1 = new ResultInfo(cols);
    info1.setUpdateTable(new TableIdentifier("FOO1"));

    ResultInfo info2 = new ResultInfo(cols);
    info2.setUpdateTable(new TableIdentifier("FOO2"));

    RowData reference = new RowData(info1);
    reference.setValue(0, 1);
    reference.setValue(1, "one");
    reference.setValue(2, "more");
    reference.resetStatus();

    RowData target = new RowData(info2);
    target.setValue(0, 1);
    target.setValue(1, "one-");
    target.setValue(2, "more");
    target.resetStatus();

    RowDataComparer instance = new RowDataComparer();
    instance.setTypeXml(true);
    instance.setConnection(null);
    instance.setResultInfo(info2);
    instance.setRows(reference, target);
    String xml = instance.getMigration(1);
//    System.out.println(xml);
    String value = TestUtil.getXPathValue(xml, "/update/col[@name='SOME_DATA']/@modified");
    assertEquals("true", value);
    value = TestUtil.getXPathValue(xml, "/update/col[@name='SOME_MORE']/@modified");
//    System.out.println("value: " + value);
    assertEquals("", value);
  }

}
