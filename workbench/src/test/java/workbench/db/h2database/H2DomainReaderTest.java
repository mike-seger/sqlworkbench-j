/*
 * H2DomainReaderTest.java
 *
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
package workbench.db.h2database;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ConnectionMgr;
import workbench.db.DomainIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class H2DomainReaderTest
  extends WbTestCase
{

  public H2DomainReaderTest()
  {
    super("H2DomainReaderTest");
  }

  @AfterClass
  public static void tearDown()
  {
    ConnectionMgr.getInstance().disconnectAll();
  }

  @Test
  public void testGetDomainList()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection con = util.getConnection();

    String script = "CREATE DOMAIN positive_integer AS integer CHECK (value > 0);";
    TestUtil.executeScript(con, script);

    List<TableIdentifier> objects = con.getMetadata().getObjectList(null, new String[] { "DOMAIN" });
    assertNotNull(objects);
    assertEquals(1, objects.size());

    H2DomainReader reader = new H2DomainReader();
    List<DomainIdentifier> domains = reader.getDomainList(con, null, null);
    assertNotNull(domains);
    assertEquals(1, domains.size());

    DomainIdentifier domain = domains.get(0);
    assertEquals("(VALUE > 0)", domain.getCheckConstraint());
    assertEquals("INTEGER", domain.getDataType());
    assertFalse(domain.isNullable());
    String sql = domain.getSource(con).toString();
//    System.out.println(sql);
    assertEquals("CREATE DOMAIN POSITIVE_INTEGER AS INTEGER\n   CHECK (VALUE > 0);", sql.trim());
  }

}
