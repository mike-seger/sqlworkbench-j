/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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
package workbench.db.postgres;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObject;
import workbench.db.DbObjectFinder;
import workbench.db.PostgresDbTest;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.TableIdentifier;
import workbench.db.TriggerDefinition;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class PostgresDependencyReaderTest
  extends WbTestCase
{

  public PostgresDependencyReaderTest()
  {
    super("PgDependencyTest");
  }

  @After
  public void tearDown()
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    PostgresTestUtil.dropAllObjects(conn);
  }

  @Test
  public void testTriggerDependencies()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    TestUtil.executeScript(conn,
          "create table t1 (id serial, some_column integer, other_column integer); \n" +
          "create function update_foo() returns trigger as $$ \n" +
          "begin  \n" +
          "  new.other_column := 42; \n" +
          "  return new; \n" +
          "end; \n" +
          "$$ language plpgsql; \n" +
          " \n" +
          "create trigger t1_update_trigger before update on t1 for each row execute procedure update_foo(); \n" +
          "commit;\n");

    TableIdentifier t1 = new DbObjectFinder(conn).findObject(new TableIdentifier("t1"));
    PostgresDependencyReader depReader = new PostgresDependencyReader(conn);

    PostgresTriggerReader trgReader = new PostgresTriggerReader(conn);
    List<TriggerDefinition> triggers = trgReader.getTriggerList(null, t1.getRawSchema(), t1.getRawTableName());
    assertEquals(1, triggers.size());
    List<DbObject> trgFunctions = depReader.getUsedObjects(conn, triggers.get(0));
    assertEquals(1, trgFunctions.size());
    assertEquals("update_foo", trgFunctions.get(0).getObjectName());

    ProcedureReader procReader = new PostgresProcedureReader(conn);
    List<ProcedureDefinition> procedures = procReader.getProcedureList(null, null, "update_foo");
    assertEquals(1, procedures.size());

    List<DbObject> objects = depReader.getUsedBy(conn, procedures.get(0));
    assertEquals(1, objects.size());
    assertEquals("t1_update_trigger", objects.get(0).getObjectName());
  }

  @Test
  public void testDependencies()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    TestUtil.executeScript(conn,
      "create table t1 (id serial); \n" +
      "create view v1 as select * from t1;\n" +
      "create view v2 as select t1.id as id1, v1.id as id2 from v1 cross join t1;\n" +
      "commit;");

    DbObjectFinder finder = new DbObjectFinder(conn);
    TableIdentifier t1 = finder.findObject(new TableIdentifier("t1"));
    TableIdentifier v1 = finder.findObject(new TableIdentifier("v1"));
    TableIdentifier v2 = finder.findObject(new TableIdentifier("v2"));

    PostgresDependencyReader reader = new PostgresDependencyReader(conn);
    List<DbObject> usedBy = reader.getUsedBy(conn, t1);
    assertNotNull(usedBy);
    assertEquals(2, usedBy.size());
    assertEquals("v1", usedBy.get(0).getObjectName());
    assertEquals("v2", usedBy.get(1).getObjectName());

    List<DbObject> usedObjects = reader.getUsedObjects(conn, v1);
    assertNotNull(usedObjects);
    assertEquals(1, usedObjects.size());
    assertEquals("t1", usedObjects.get(0).getObjectName());

    List<DbObject> v2Uses = reader.getUsedObjects(conn, v2);
    assertNotNull(v2Uses);
    assertEquals(2, v2Uses.size());
    assertEquals("t1", v2Uses.get(0).getObjectName());
    assertEquals("v1", v2Uses.get(1).getObjectName());

    List<DbObject> t1Uses = reader.getUsedObjects(conn, t1);
    assertNotNull(v2Uses);
    assertEquals(1, t1Uses.size());
    assertEquals("t1_id_seq", t1Uses.get(0).getObjectName());
  }

  @Test
  public void testEnumDependencies()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    TestUtil.executeScript(conn,
          "CREATE TYPE e_status AS ENUM ('open','waiting','pending','closed');\n" +
          "create table t1 (id int primary key, status e_status); \n" +
          "commit;\n");


    DbObjectFinder finder = new DbObjectFinder(conn);
    DbObject e1 = finder.findObject(new TableIdentifier("e_status"));
    PostgresDependencyReader depReader = new PostgresDependencyReader(conn);

    List<DbObject> objects = depReader.getUsedBy(conn, e1);
    assertEquals(1, objects.size());
    assertEquals("t1", objects.get(0).getObjectName());

    TableIdentifier t1 = finder.findObject(new TableIdentifier("t1"));
    List<DbObject> usedObjects = depReader.getUsedObjects(conn, t1);
    assertEquals(1, usedObjects.size());
    assertEquals("e_status", usedObjects.get(0).getObjectName());
    assertEquals("ENUM", usedObjects.get(0).getObjectType());
  }

  @Test
  public void testDomainDependencies()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    TestUtil.executeScript(conn,
          "CREATE DOMAIN positive_number AS numeric(12,2) NOT NULL CHECK (VALUE > 0);\n" +
          "create table t1 (id int primary key, salary positive_number); \n" +
          "commit;\n");

    DbObjectFinder finder = new DbObjectFinder(conn);
    DbObject domain = finder.findObject(new TableIdentifier("positive_number"));
    assertNotNull(domain);
    PostgresDependencyReader depReader = new PostgresDependencyReader(conn);

    List<DbObject> objects = depReader.getUsedBy(conn, domain);
    assertEquals(1, objects.size());
    assertEquals("t1", objects.get(0).getObjectName());

    TableIdentifier t1 = finder.findObject(new TableIdentifier("t1"));
    List<DbObject> usedObjects = depReader.getUsedObjects(conn, t1);
    assertEquals(1, usedObjects.size());
    assertEquals("positive_number", usedObjects.get(0).getObjectName());
    assertEquals("DOMAIN", usedObjects.get(0).getObjectType());
  }

  @Test
  public void testSupportsDependencies()
  {
    PostgresDependencyReader reader = new PostgresDependencyReader(null);
    List<String> types = CollectionUtil.arrayList("view", "table");
    for (String type : types)
    {
      assertTrue(reader.supportsUsedByDependency(type));
      assertTrue(reader.supportsIsUsingDependency(type));
    }
    assertTrue(reader.supportsUsedByDependency("sequence"));
    assertFalse(reader.supportsIsUsingDependency("sequence"));
  }

}
