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
package workbench.db.exporter;

import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DbObjectFinder;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;
import workbench.storage.RowData;
import workbench.storage.SqlLiteralFormatter;
import workbench.storage.reader.RowDataReader;
import workbench.storage.reader.RowDataReaderFactory;

import workbench.sql.parser.ScriptParser;

import workbench.util.CollectionUtil;
import workbench.util.DdlObjectInfo;
import workbench.util.SqlUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class SqlRowDataConverterTest
  extends WbTestCase
{

  public SqlRowDataConverterTest()
  {
    super("SqlRowDataConverterTest");
  }

  @Test
  public void testTimestampTZ()
    throws Exception
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier created = new ColumnIdentifier("created", Types.TIMESTAMP_WITH_TIMEZONE);
    ColumnIdentifier dt = new ColumnIdentifier("dt", Types.DATE);
    ResultInfo info = new ResultInfo(new ColumnIdentifier[] { id, created, dt });
    TableIdentifier tbl = new TableIdentifier("data");
    info.setUpdateTable(tbl);
    DataStore ds = new DataStore(info);
    ds.forceUpdateTable(tbl);
    int row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(42));
    ds.setValue(row, 1, ZonedDateTime.of(2022, 4, 2, 19, 20, 21, 0, ZoneId.of("UTC")));
    ds.setValue(row, 2, LocalDate.of(2022, 4, 2));

    SqlRowDataConverter converter = new SqlRowDataConverter(null);
    converter.setResultInfo(info);
    converter.setCreateInsert();
    converter.setDateLiteralType("ansi");
    converter.setApplySQLFormatting(false);
    StringBuilder sql = converter.convertRowData(ds.getRow(row), row);

    String ansi = "INSERT INTO data (id,created,dt) VALUES (42,TIMESTAMP '2022-04-02 19:20:21.000000 +0000',DATE '2022-04-02');";
    assertEquals(sql.toString().trim(), ansi);
    converter.setDateLiteralType("postgresql");
    sql = converter.convertRowData(ds.getRow(row), row);
    String pg = "INSERT INTO data (id,created,dt) VALUES (42,'2022-04-02 19:20:21.000000 +0000'::timestamptz,DATE '2022-04-02');";
    assertEquals(sql.toString().trim(), pg);
  }

  @Test
  public void testMerge()
    throws Exception
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier fname = new ColumnIdentifier("fname", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("lname", Types.VARCHAR);
    ColumnIdentifier data = new ColumnIdentifier("data", Types.VARCHAR);
    ResultInfo info = new ResultInfo(new ColumnIdentifier[] { id, fname, lname, data });

    TableIdentifier tbl = new TableIdentifier("person");
    info.setUpdateTable(tbl);
    DataStore ds = new DataStore(info);
    ds.forceUpdateTable(tbl);
    int row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(42));
    ds.setValue(row, 1, "Arthur");
    ds.setValue(row, 2, "Dent");
    ds.setValue(row, 3, "one");

    row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(24));
    ds.setValue(row, 1, "Ford");
    ds.setValue(row, 2, "Prefect");
    ds.setValue(row, 3, "two");
    SqlRowDataConverter converter = new SqlRowDataConverter(null);
    converter.setCreateMerge();

    converter.setResultInfo(info);
    List<ColumnIdentifier> cols = CollectionUtil.arrayList(id, lname, fname);
    converter.setColumnsToExport(cols);
    StringBuilder result = converter.getStart();
    result.append(converter.convertRowData(ds.getRow(0), 0));
    result.append(converter.convertRowData(ds.getRow(1), 1));
    result.append(converter.getEnd(2));
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
      "  VALUES (md.id, md.fname, md.lname);\n" +
      "\n" +
      "\n" +
      "COMMIT;";
    assertEquals(expected, result.toString().trim());
  }

  @Test
  public void testDuplicateColumns()
    throws Exception
  {
    WbConnection con = null;
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      TestUtil util = new TestUtil("testDateLiterals");
      util.prepareEnvironment();

      con = util.getConnection("sqlConverterTest");
      String script =
        "CREATE TABLE person (id integer primary key, name varchar(20));\n" +
        "insert into person (id, name) values (42, 'Arthur Dent');\n" +
        "commit;";
      TestUtil.executeScript(con, script);

      stmt = con.createStatement();
      rs = stmt.executeQuery("SELECT id, name, name||'*' as name FROM person");
      ResultInfo info = new ResultInfo(rs.getMetaData(), con);
      TableIdentifier tbl = new DbObjectFinder(con).findObject(new TableIdentifier("person"));
      info.setUpdateTable(tbl);
      RowDataReader reader = RowDataReaderFactory.createReader(info, con);
      rs.next();
      RowData row = reader.read(rs, false);

      SqlRowDataConverter converter = new SqlRowDataConverter(con);
      converter.setOriginalConnection(con);
      converter.setResultInfo(info);
      List<ColumnIdentifier> cols = CollectionUtil.arrayList(info.getColumn(0), info.getColumn(2));
      converter.setColumnsToExport(cols);
      StringBuilder result = converter.convertRowData(row, 1);
      assertNotNull(result);
      String sql = result.toString().trim();
      String expected =
        "INSERT INTO PERSON\n" +
        "(\n" +
        "  ID,\n" +
        "  NAME\n" +
        ")\n" +
        "VALUES\n" +
        "(\n" +
        "  42,\n" +
        "  'Arthur Dent*'\n" +
        ");";
//      System.out.println("*****\n" + expected + "\n-------" + sql + "\n**********");
      assertEquals(expected, sql);
    }
    finally
    {
      con.disconnect();
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  @Test
  public void testCreateTable()
    throws Exception
  {
    WbConnection con = null;
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      TestUtil util = new TestUtil("testDateLiterals");
      util.prepareEnvironment();

      con = util.getConnection("sqlConverterTest");
      String script =
        "CREATE TABLE person (id integer primary key, name varchar(20));\n" +
        "CREATE VIEW v_Person AS SELECT * from person;";
      TestUtil.executeScript(con, script);

      stmt = con.createStatement();
      rs = stmt.executeQuery("SELECT * FROM v_person");
      ResultInfo info = new ResultInfo(rs.getMetaData(), con);

      SqlRowDataConverter converter = new SqlRowDataConverter(con);
      converter.setResultInfo(info);
      converter.setCreateTable(true);
      converter.setAlternateUpdateTable(new TableIdentifier("MYTABLE"));
      StringBuilder start = converter.getStart();
      assertNotNull(start);
      String sql = start.toString();
      DdlObjectInfo ddl = SqlUtil.getDDLObjectInfo(sql);
      assertEquals(ddl.getObjectName(), "MYTABLE");
      assertEquals(ddl.getObjectType(), "TABLE");
    }
    finally
    {
      con.disconnect();
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  @Test
  public void testMultiRowInsert()
    throws Exception
  {
    WbConnection con = null;
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      TestUtil util = new TestUtil("testMultiRowInsert");
      util.prepareEnvironment();
      con = util.getConnection("sqlConverterTest");
      String script =
        "CREATE TABLE person (id integer primary key, name varchar(20));\n" +
        "insert into person values (1, 'Arthur'), (2, 'Zaphod'), (3, 'Tricia');\n" +
        "commit;\n";
      TestUtil.executeScript(con, script);

      TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("PERSON"));

      stmt = con.createStatement();
      rs = stmt.executeQuery("SELECT * FROM person");
      DataStore ds = new DataStore(rs, true, null, 0, con);
      ds.setUpdateTableToBeUsed(tbl);
      ds.checkUpdateTable(con);
      SqlRowDataConverter converter = new SqlRowDataConverter(con);
      converter.setResultInfo(ds.getResultInfo());
      converter.setCreateTable(false);
      converter.setUseMultiRowInserts(true);

      converter.setType(ExportType.SQL_INSERT);
      String sql = converter.convertRowData(ds.getRow(0), 1).toString();
      sql += converter.convertRowData(ds.getRow(1), 2);
      sql += converter.convertRowData(ds.getRow(2), 3);
      sql += converter.getEnd(3);
      String expected =
        "INSERT INTO PERSON (ID,NAME) \n" +
        "VALUES\n" +
        "  (1,'Arthur'),\n" +
        "  (2,'Zaphod'),\n" +
        "  (3,'Tricia');\n" +
        "\n" +
        "COMMIT;";
      assertEquals(expected, sql.trim());
    }
    finally
    {
      con.disconnect();
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  @Test
  public void testSqlGeneration()
    throws Exception
  {
    WbConnection con = null;
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      TestUtil util = new TestUtil("testDateLiterals");
      util.prepareEnvironment();

      con = util.getConnection("sqlConverterTest");
      String script =
        "CREATE TABLE person (id integer primary key, name varchar(20));\n" +
        "insert into person values (1, 'Arthur');\n" +
        "commit;\n";
      TestUtil.executeScript(con, script);

      TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("PERSON"));

      stmt = con.createStatement();
      rs = stmt.executeQuery("SELECT * FROM person");
      DataStore ds = new DataStore(rs, true, null, 0, con);
      ds.setUpdateTableToBeUsed(tbl);
      ds.checkUpdateTable(con);
      SqlRowDataConverter converter = new SqlRowDataConverter(con);
      converter.setResultInfo(ds.getResultInfo());
      converter.setCreateTable(false);

      converter.setType(ExportType.SQL_INSERT);
      RowData row = ds.getRow(0);
      String sql = converter.convertRowData(row, 1).toString();
      ScriptParser p = new ScriptParser(sql);
      assertEquals(1, p.getSize());
      assertTrue(sql.startsWith("INSERT INTO PERSON"));

      converter.setType(ExportType.SQL_UPDATE);
      sql = converter.convertRowData(row, 1).toString();
      p = new ScriptParser(sql);
      assertEquals(1, p.getSize());
      assertTrue(sql.startsWith("UPDATE PERSON"));

      converter.setType(ExportType.SQL_DELETE);
      sql = SqlUtil.makeCleanSql(converter.convertRowData(row, 1).toString(), false);
      p = new ScriptParser(sql);
      assertEquals(1, p.getSize());
      assertTrue(sql.startsWith("DELETE FROM PERSON"));

      converter.setType(ExportType.SQL_DELETE_INSERT);
      sql = converter.convertRowData(row, 1).toString();
      p = new ScriptParser(sql);
      assertEquals(2, p.getSize());
      String delete = SqlUtil.makeCleanSql(p.getCommand(0), false);
      assertTrue(delete.startsWith("DELETE FROM PERSON"));
      String insert = SqlUtil.makeCleanSql(p.getCommand(1), false);
      assertTrue(insert.startsWith("INSERT INTO PERSON"));
    }
    finally
    {
      con.disconnect();
      JdbcUtils.closeAll(rs, stmt);
    }
  }


  @Test
  public void testConvert()
  {
    try
    {
      TestUtil util = new TestUtil("testDateLiterals");
      util.prepareEnvironment();

      String[] cols = new String[]
      {
        "char_col", "int_col", "date_col", "ts_col"
      };
      int[] types = new int[]
      {
        Types.VARCHAR, Types.INTEGER, Types.DATE, Types.TIMESTAMP
      };
      int[] sizes = new int[]
      {
        10, 10, 10, 10
      };

      ResultInfo info = new ResultInfo(cols, types, sizes);
      TableIdentifier tbl = new TableIdentifier("MYTABLE");
      info.setUpdateTable(tbl);

      SqlRowDataConverter converter = new SqlRowDataConverter(null);
      converter.setResultInfo(info);

      info.getColumn(0).setIsPkColumn(true);

      RowData data = new RowData(info);
      data.setValue(0, "data1");
      data.setValue(1, Integer.valueOf(42));
      Calendar c = Calendar.getInstance();
      c.set(2006, 9, 26, 17, 0);
      c.set(Calendar.SECOND, 0);
      c.set(Calendar.MILLISECOND, 0);
      java.util.Date d = c.getTime();
      data.setValue(2, c.getTime());
      java.sql.Timestamp ts = new java.sql.Timestamp(d.getTime());
      data.setValue(3, ts);
      data.resetStatus();

      converter.setDateLiteralType(SqlLiteralFormatter.JDBC_DATE_LITERAL_TYPE);
      converter.setCreateInsert();
      String line = converter.convertRowData(data, 0).toString().trim();
      String verb = SqlUtil.getSqlVerb(line);
      assertEquals("No insert generated", "INSERT", verb);

//      System.out.println(line);
      assertEquals("JDBC date literal not found", true, line.contains("{d '2006-10-26'}"));
      assertEquals("JDBC timestamp literal not found", true, line.contains("{ts '2006-10-26 "));

      converter.setDateLiteralType(SqlLiteralFormatter.ANSI_DATE_LITERAL_TYPE);
      line = converter.convertRowData(data, 0).toString().trim();
      assertEquals("ANSI date literal not found", true, line.contains("DATE '2006-10-26'"));
      assertEquals("ANSI timestamp literal not found", true, line.contains("TIMESTAMP '2006-10-26"));

      converter.setCreateUpdate();
      line = converter.convertRowData(data, 0).toString().trim();

      verb = SqlUtil.getSqlVerb(line);
      assertEquals("No UPDATE generated", "UPDATE", verb);
      assertEquals("Wrong WHERE statement", true, line.endsWith("WHERE char_col = 'data1';"));

      List<ColumnIdentifier> columns = new ArrayList<>();
      columns.add(info.getColumn(0));
      columns.add(info.getColumn(1));
      converter.setColumnsToExport(columns);
      line = converter.convertRowData(data, 0).toString().trim();
      assertEquals("date_col included", -1, line.indexOf("date_col ="));
      assertEquals("ts_col included", -1, line.indexOf("ts_col ="));
      assertEquals("int_col not updated", true, line.contains("SET int_col = 42"));

      converter.setCreateInsertDelete();
      line = converter.convertRowData(data, 0).toString().trim();
      ScriptParser p = new ScriptParser(line);
      int count = p.getSize();
      assertEquals("Not enough statements generated", 2, count);
      String sql = p.getCommand(0);
      verb = SqlUtil.getSqlVerb(sql);
      assertEquals("DELETE not first statement", "DELETE", verb);

      sql = p.getCommand(1);
      verb = SqlUtil.getSqlVerb(sql);
      assertEquals("INSERT not second statement", "INSERT", verb);
    }
    catch (Exception e)
    {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void testIncludeReadOnlyColumns()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection conn = util.getHSQLConnection("readonlycols");

    TestUtil.executeScript(conn,
      "create table foo (id integer generated always as identity, c1 integer, c2 integer);\n" +
      "insert into foo values (default,1,1), (default,2,2);\n" +
      "commit;\n");

    boolean check = Settings.getInstance().getCheckEditableColumns();
    boolean identity = Settings.getInstance().getGenerateInsertIgnoreIdentity();

    try
    {
      Settings.getInstance().setCheckEditableColumns(false);
      Settings.getInstance().setGenerateInsertIgnoreIdentity(true);
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery("select * from foo order by id");
      DataStore ds = new DataStore(rs, true);
      ds.setOriginalConnection(conn);
      ds.setUpdateTable(new TableIdentifier("FOO"));
      ds.getResultInfo().getColumn(1).setReadonly(true);
      ds.getResultInfo().getColumn(2).setReadonly(true);

      SqlRowDataConverter converter = new SqlRowDataConverter(conn);
      converter.setResultInfo(ds.getResultInfo());
      converter.setApplySQLFormatting(false);

      String insert = converter.convertRowData(ds.getRow(0), 0).toString();
      assertEquals("INSERT INTO FOO (C1,C2) VALUES (1,1);", insert.trim());

      insert = converter.convertRowData(ds.getRow(1), 0).toString();
      assertEquals("INSERT INTO FOO (C1,C2) VALUES (2,2);", insert.trim());

      Settings.getInstance().setGenerateInsertIgnoreIdentity(false);
      Settings.getInstance().setCheckEditableColumns(true);

      converter = new SqlRowDataConverter(conn);
      converter.setApplySQLFormatting(false);
      converter.setResultInfo(ds.getResultInfo());

      insert = converter.convertRowData(ds.getRow(0), 0).toString();
      assertEquals("INSERT INTO FOO (ID) VALUES (0);", insert.trim());

      insert = converter.convertRowData(ds.getRow(1), 0).toString();
      assertEquals("INSERT INTO FOO (ID) VALUES (1);", insert.trim());

      converter.setIncludeIdentityColumns(true);
      converter.setIncludeReadOnlyColumns(true);
      insert = converter.convertRowData(ds.getRow(0), 0).toString();
      assertEquals("INSERT INTO FOO (ID,C1,C2) VALUES (0,1,1);", insert.trim());

      converter.setIncludeIdentityColumns(false);
      converter.setIncludeReadOnlyColumns(true);
      insert = converter.convertRowData(ds.getRow(0), 0).toString();
      assertEquals("INSERT INTO FOO (C1,C2) VALUES (1,1);", insert.trim());

      converter.setIncludeIdentityColumns(false);
      converter.setIncludeReadOnlyColumns(false);
      insert = converter.convertRowData(ds.getRow(0), 0).toString();
      assertEquals("INSERT INTO FOO () VALUES ();", insert.trim());
    }
    finally
    {
      Settings.getInstance().setCheckEditableColumns(check);
      Settings.getInstance().setGenerateInsertIgnoreIdentity(identity);
      TestUtil.executeScript(conn, "drop table foo;");
    }
  }
}
