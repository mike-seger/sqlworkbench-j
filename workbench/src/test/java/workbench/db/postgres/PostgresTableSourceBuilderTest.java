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
package workbench.db.postgres;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObjectFinder;
import workbench.db.DropType;
import workbench.db.JdbcUtils;
import workbench.db.PostgresDbTest;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.TableSourceBuilderFactory;
import workbench.db.WbConnection;

import workbench.sql.parser.ParserType;
import workbench.sql.parser.ScriptParser;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class PostgresTableSourceBuilderTest
  extends WbTestCase
{
  private static final String TEST_SCHEMA = "sourcebuilder";

  public PostgresTableSourceBuilderTest()
  {
    super("PostgresTableSourceBuilder");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_SCHEMA);
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;
    TestUtil.executeScript(con,
      "create table base_table (id integer, some_data varchar(100));\n" +
      "alter table base_table add constraint uc_some_data unique (some_data); \n" +
      "create table child_table (other_data varchar(100)) inherits (base_table);\n" +
      "create type order_status_type as enum ('new', 'open', 'closed');\n" +
      "create domain product_price as integer check (value > 0);\n" +
      "create table more_data (id integer, order_status order_status_type, price product_price);\n" +
      "create sequence data_seq owned by more_data.id;\n" +
      "commit;\n");
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testComputedColumns()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (!JdbcUtils.hasMinimumServerVersion(con, "12")) return;
    assertNotNull(con);
    TestUtil.executeScript(con,
      "create table x (c1 integer, c2 integer, c3 integer generated always as (c1 * c2) stored);");

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("x"));
    String ddl = tbl.getSource(con).toString();
    assertTrue(ddl.contains(("c3  integer   GENERATED ALWAYS AS (c1 * c2) STORED")));
  }

  @Test
  public void testTableOptions()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TableIdentifier tbl = new TableIdentifier(TEST_SCHEMA, "child_table");
    String sql = tbl.getSource(con).toString();
    assertTrue(sql.contains("INHERITS (base_table)"));

    TestUtil.executeScript(con, "create table child_fill (foo_data text) inherits (base_table) with (fillfactor=40);");
    tbl = new DbObjectFinder(con).findTable(new TableIdentifier("child_fill"));
    String source = tbl.getSource(con).toString();
    assertTrue(source.contains("INHERITS (base_table)\nWITH (fillfactor=40)"));
  }

  @Test
  public void testIdentity()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (!JdbcUtils.hasMinimumServerVersion(con, "11")) return;
    assertNotNull(con);
    TestUtil.executeScript(con,
      "create table " + TEST_SCHEMA + ".identity_test (" +
      "  id integer generated always as identity, " +
      "  some_data varchar(100)" +
      ");\n" +
      "commit;");
    TableIdentifier tbl = new TableIdentifier(TEST_SCHEMA, "identity_test");
    TableIdentifier idTest = new DbObjectFinder(con).findTable(tbl);

    String sql = idTest.getSource(con).toString();
    assertTrue(sql.contains("GENERATED ALWAYS AS IDENTITY"));
  }

  @Test
  public void testSerial()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);
    TestUtil.executeScript(con,
      "create table " + TEST_SCHEMA + ".serial_test (" +
      "  id serial, " +
      "  some_data varchar(100)" +
      ");\n" +
      "commit;");
    TableIdentifier tbl = new TableIdentifier(TEST_SCHEMA, "serial_test");
    TableIdentifier idTest = new DbObjectFinder(con).findTable(tbl);

    String sql = idTest.getSource(con).toString();
    assertTrue(sql.contains("id         serial,"));
  }

  @Test
  public void testDefault()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);
    if (!JdbcUtils.hasMinimumServerVersion(con, "9.4")) return;
    TestUtil.executeScript(con,
      "create sequence " + TEST_SCHEMA + ".default_sequence;\n" +
      "create table " + TEST_SCHEMA + ".default_test (" +
      "  c1 integer default 42, " +
      "  c2 varchar(100) default '42'," +
      "  c3 text default concat('cd', to_char(nextval('" + TEST_SCHEMA + ".default_sequence'), '99999999'))\n " +
      ");\n" +
      "commit;");
    TableIdentifier tbl = new TableIdentifier(TEST_SCHEMA, "default_test");
    TableIdentifier idTest = new DbObjectFinder(con).findTable(tbl);

    String sql = idTest.getSource(con).toString().trim();
    String expected =
      "CREATE TABLE IF NOT EXISTS default_test\n" +
      "(\n" +
      "   c1  integer        DEFAULT 42,\n" +
      "   c2  varchar(100)   DEFAULT '42'::character varying,\n" +
      "   c3  text           DEFAULT concat('cd', to_char(nextval('default_sequence'::regclass), '99999999'::text))\n" +
      ");";
//    System.out.println("====\n" + sql + "\n**** expected ***\n" + expected);
    assertEquals(expected, sql);
  }

  @Test
  public void testColumnOptions()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TableIdentifier tbl = new TableIdentifier(TEST_SCHEMA, "more_data");
    String sql = tbl.getSource(con).toString();
    assertTrue(sql.contains(" enum 'order_status_type':"));
    assertTrue(sql.contains(" domain 'product_price': integer CONSTRAINT product_price_check CHECK (VALUE > 0)"));
    assertTrue(sql.contains("sequence sourcebuilder.data_seq"));
  }

  @Test
  public void testUniqueConstraint()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TableIdentifier tbl = new TableIdentifier(TEST_SCHEMA, "base_table");
    String sql = tbl.getSource(con).toString();
    assertTrue(sql.contains("ADD CONSTRAINT uc_some_data UNIQUE (some_data);"));
  }

  @Test
  public void testUnloggedTables()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);
    if (!JdbcUtils.hasMinimumServerVersion(con, "9.1")) return;
    TestUtil.executeScript(con, "create unlogged table no_crash_safe (id integer, some_data varchar(100));");
    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("no_crash_safe"));
    String source = tbl.getSource(con).toString();
    assertTrue(source.contains("CREATE UNLOGGED TABLE"));
  }

  @Test
  public void testConfigSettings()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);
    TestUtil.executeScript(con, "create table foo_fill (id integer, some_data varchar(100)) with (fillfactor=40);");
    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("foo_fill"));
    String source = tbl.getSource(con).toString();
    assertTrue(source.contains("fillfactor=40"));
  }

  @Test
  public void testStorage()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);
    DbObjectFinder finder = new DbObjectFinder(con);
    TableIdentifier tbl = finder.findTable(new TableIdentifier("base_table"));
    String source = tbl.getSource(con).toString();
//    System.out.println(source);
    assertFalse(source.contains("SET STORAGE"));

    TestUtil.executeScript(con,
      "create table foo_storage (id integer, some_data varchar(100));\n" +
      "alter table foo_storage alter some_data set storage plain;\n" +
      "commit;");

    tbl = finder.findTable(new TableIdentifier("foo_storage"));
    source = tbl.getSource(con).toString();
//    System.out.println(source);
    ScriptParser p = new ScriptParser(source, ParserType.Postgres);
    int size = p.getSize();
    assertEquals(2, size);
    assertEquals("ALTER TABLE foo_storage ALTER some_data SET STORAGE PLAIN", p.getCommand(1));
  }

  @Test
  public void testConstraints()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);
    TestUtil.executeScript(con,
      "create table one \n" +
      "(\n" +
      "   id integer primary key, \n" +
      "   some_column integer \n" +
      "); \n" +
      "create table other \n" +
      "(\n" +
      "   id integer primary key, \n" +
      "   some_column integer \n" +
      "); \n" +
      "create table two \n" +
      "(\n" +
      "   id integer primary key, \n" +
      "   one_id integer, \n" +
      "   other_id integer not null, \n" +
      "   constraint fk_two2one foreign key (one_id) references one (id) match full, \n " +
      "   constraint fk_two2other foreign key (other_id) references other (id) deferrable \n " +
      ");\n" +
      "comment on constraint fk_two2one on two is 'The foreign key';\n" +
      "commit;");

    DbObjectFinder finder = new DbObjectFinder(con);
    TableIdentifier tbl = finder.findTable(new TableIdentifier("two"));
    TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);

    String source = builder.getTableSource(tbl, DropType.none, true);
//    System.out.println(source);
    assertTrue(source.contains("COMMENT ON CONSTRAINT fk_two2one ON two IS 'The foreign key';"));
    assertTrue(source.contains("FOREIGN KEY (one_id) REFERENCES one(id) MATCH FULL"));
    assertTrue(source.contains("FOREIGN KEY (other_id) REFERENCES other(id) DEFERRABLE"));

    builder.setCreateInlineFKConstrants(true);
    source = builder.getTableSource(tbl, DropType.none, true);
//    System.out.println(source);
    assertTrue(source.contains("COMMENT ON CONSTRAINT fk_two2one ON two IS 'The foreign key';"));
  }

  @Test
  public void testQuotedIdentifiers()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);
    TestUtil.executeScript(con,
      "create table \"Base\"\n" +
      "(\n" +
      "   \"Id\" integer, \n" +
      "   some_column integer, \n" +
      "   constraint \"Base_PK\" primary key (\"Id\") \n " +
      "); \n" +
      "create table \"Foo_Bar\"\n" +
      "(\n" +
      "   \"PK_id\" integer, \n" +
      "   \"Other_Value\" varchar(100), \n" +
      "   some_column integer, \n" +
      "   constraint \"Some_PK\" primary key (\"PK_id\", \"Other_Value\"), \n " +
      "   constraint \"Some_FK\" foreign key (\"PK_id\") references \"Base\" \n " +
      ");\n" +
      "comment on table \"Foo_Bar\" is 'Some witty comment';\n" +
      "commit;");

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("\"Foo_Bar\""));
    TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);

    String source = builder.getTableSource(tbl, DropType.none, true);
//    System.out.println(source);
    assertTrue(source.startsWith("CREATE TABLE IF NOT EXISTS \"Foo_Bar\""));
    assertTrue(source.contains("ALTER TABLE \"Foo_Bar\""));
    assertTrue(source.contains("ADD CONSTRAINT \"Some_PK\""));
    assertTrue(source.contains("PRIMARY KEY (\"PK_id\", \"Other_Value\")"));
    assertTrue(source.contains("COMMENT ON TABLE \"Foo_Bar\""));
    assertTrue(source.contains("ADD CONSTRAINT \"Some_FK\""));
    assertTrue(source.contains("FOREIGN KEY (\"PK_id\")"));
    assertTrue(source.contains("REFERENCES \"Base\"(\"Id\")"));
  }

}
