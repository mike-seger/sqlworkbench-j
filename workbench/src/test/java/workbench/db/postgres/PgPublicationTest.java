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

import java.sql.SQLException;
import java.util.List;

import workbench.db.TableIdentifier;

import org.junit.Test;

import static org.junit.Assert.*;
/**
 *
 * @author Thomas Kellerer
 */
public class PgPublicationTest
{

  public PgPublicationTest()
  {
  }

  @Test
  public void testGenerateSource() throws SQLException
  {
    PgPublication pub = new PgPublication("mypub");
    pub.setPublishViaPartitionRoot(true);
    pub.setReplicatesDeletes(true);
    pub.setReplicatesTruncate(true);
    pub.setReplicatesInserts(true);
    pub.setReplicatesUpdates(true);
    TableIdentifier t1 = new TableIdentifier("table1");
    t1.getSourceOptions().addConfigSetting(PgPublication.OPTION_KEY_FILTER, "(id = 42)");
    TableIdentifier t2 = new TableIdentifier("table2");
    pub.setTables(List.of(t1, t2));
    String sql = pub.getSource(null).toString();
    String expected =
      "CREATE PUBLICATION mypub\n" +
      "FOR TABLE\n" +
      "  table1 WHERE (id = 42),\n" +
      "  table2\n" +
      "WITH (publish_via_partition_root = true);";
//    System.out.println(sql);
    assertEquals(expected, sql);
    pub.setReplicatesDeletes(false);
    pub.setReplicatesTruncate(false);
    pub.setReplicatesInserts(true);
    pub.setReplicatesUpdates(true);
    sql = pub.getSource(null).toString();
    expected =
      "CREATE PUBLICATION mypub\n" +
      "FOR TABLE\n" +
      "  table1 WHERE (id = 42),\n" +
      "  table2\n" +
      "WITH (publish = 'insert, update', publish_via_partition_root = true);";
//    System.out.println(sql);
    assertEquals(expected, sql);

  }

}
