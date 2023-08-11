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
package workbench.db.postgres;

import java.util.List;
import java.util.Map;

import workbench.db.GrantItem;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class PgACLParserTest
{

  @Test
  public void testParserSimple()
  {
    PgACLParser parser = new PgACLParser("arthur=rw/zaphod,arthur=rwa/arthur");
    Map<String, List<GrantItem>> privs = parser.getPrivileges();
//    System.out.println(privs);
    assertEquals(1, privs.size());
    assertEquals(2, privs.get("arthur").size());

    assertEquals("SELECT", privs.get("arthur").get(0).getPrivilege());
    assertEquals(false, privs.get("arthur").get(0).isGrantable());
    assertEquals("arthur", privs.get("arthur").get(0).getGrantee());
    assertEquals("UPDATE", privs.get("arthur").get(1).getPrivilege());
    String sql = parser.getSQL("PERSON", "TABLE");
//    System.out.println(sql);
    assertEquals("GRANT SELECT, UPDATE ON TABLE PERSON TO arthur;", sql);
  }

  @Test
  public void testParserGrantable()
  {
    PgACLParser parser = new PgACLParser("arthur=r*w*/zaphod,arthur=rwa/arthur");
    Map<String, List<GrantItem>> privs = parser.getPrivileges();
//    System.out.println(privs);
    assertEquals(1, privs.size());
    assertEquals(2, privs.get("arthur").size());

    assertEquals("SELECT", privs.get("arthur").get(0).getPrivilege());
    assertEquals(true, privs.get("arthur").get(0).isGrantable());
    assertEquals("arthur", privs.get("arthur").get(0).getGrantee());
    assertEquals("UPDATE", privs.get("arthur").get(1).getPrivilege());
    assertEquals(true, privs.get("arthur").get(1).isGrantable());
    String sql = parser.getSQL("PERSON", "TABLE");
//    System.out.println(sql);
    assertEquals("GRANT SELECT, UPDATE ON TABLE PERSON TO arthur WITH GRANT OPTION;", sql);
  }

  @Test
  public void testParserMixed()
  {
    PgACLParser parser = new PgACLParser("arthur=rw*/zaphod,arthur=rwa/arthur");
    Map<String, List<GrantItem>> privs = parser.getPrivileges();
//    System.out.println(privs);
    assertEquals(1, privs.size());
    assertEquals(2, privs.get("arthur").size());

    assertEquals("SELECT", privs.get("arthur").get(0).getPrivilege());
    assertEquals("arthur", privs.get("arthur").get(0).getGrantee());
    assertFalse(privs.get("arthur").get(0).isGrantable());
    assertEquals("UPDATE", privs.get("arthur").get(1).getPrivilege());
    assertTrue(privs.get("arthur").get(1).isGrantable());
  }

}
