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
package workbench.db.postgres;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.db.PartitionLister;
import workbench.db.SubPartitionState;
import workbench.db.TableIdentifier;
import workbench.db.TablePartition;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresPartitionLister
  implements PartitionLister
{
  private final WbConnection conn;

  public PostgresPartitionLister(WbConnection conn)
  {
    this.conn = conn;
  }

  @Override
  public List<PostgresPartition> getPartitions(TableIdentifier table)
  {
    PostgresPartitionReader reader = new PostgresPartitionReader(table, conn);
    reader.readPartitionInformation();
    List<PostgresPartition> partitions = reader.getTablePartitions();

    List<PostgresPartition> mainPartitions = new ArrayList<>();
    for (PostgresPartition partition : partitions)
    {
      partition.setSubPartitionState(SubPartitionState.none);
      if (!partition.isSubPartition())
      {
        mainPartitions.add(partition);
        List<PostgresPartition> subs = extractSubPartitions(partitions, partition);
        if (subs.size() > 0)
        {
          partition.setSubPartitions(subs);
          partition.setSubPartitionState(SubPartitionState.yes);
        }
      }
    }
    return mainPartitions;
  }

  private List<PostgresPartition> extractSubPartitions(List<PostgresPartition> all, PostgresPartition mainPartition)
  {
    List<PostgresPartition> subs = new ArrayList<>();

    String main = mainPartition.getObjectName();
    for (PostgresPartition p : all)
    {
      if (p.getParentTable() != null && p.getParentTable().getRawTableName().equals(main))
      {
        p.setSubPartitionState(SubPartitionState.none);
        p.setIsSubPartition(true);
        p.setPartitionStrategy(mainPartition.getSubPartitionStrategy());
        p.setSubPartitionStrategy(null);
        subs.add(p);
      }
    }
    return subs;
  }

  @Override
  public List<? extends TablePartition> getSubPartitions(TableIdentifier baseTable, TablePartition partition)
  {
    if (partition instanceof PostgresPartition)
    {
      PostgresPartition pgPart = (PostgresPartition)partition;
      return pgPart.getSubPartitions();
    }
    return Collections.emptyList();
  }

  @Override
  public boolean supportsSubPartitions()
  {
    return true;
  }

}
