/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.db;

import java.util.List;

import workbench.db.oracle.OraclePartitionLister;
import workbench.db.oracle.OracleUtils;
import workbench.db.postgres.PostgresPartitionLister;

/**
 *
 * @author Thomas Kellerer
 */
public interface PartitionLister
{
  public static final String PARTITION_TYPE_NAME = "PARTITION";

  List<? extends TablePartition> getPartitions(TableIdentifier table);
  List<? extends TablePartition> getSubPartitions(TableIdentifier baseTable, TablePartition mainPartition);
  boolean supportsSubPartitions();

  public static class Factory
  {
    public static PartitionLister createReader(WbConnection conn)
    {
      if (conn == null) return null;
      
      DBID dbid = DBID.fromConnection(conn);
      switch (dbid)
      {
        case Postgres:
          if (JdbcUtils.hasMinimumServerVersion(conn, "10"))
          {
            return new PostgresPartitionLister(conn);
          }
        case Oracle:
          if (OracleUtils.supportsPartitioning(conn))
          {
            return new OraclePartitionLister(conn);
          }
      }
      return null;
    }
  }
}
