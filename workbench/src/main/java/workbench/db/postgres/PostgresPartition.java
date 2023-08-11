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

import workbench.db.TableIdentifier;
import workbench.db.TablePartition;
import workbench.db.WbConnection;


/**
 *
 * @author Thomas Kellerer
 */
public class PostgresPartition
  extends TablePartition
{
  private String subPartitionDefinition;

  // for sub-partitions
  private TableIdentifier parentPartition;

  // The table to which this partition belongs to
  private final TableIdentifier baseTable;

  public PostgresPartition(TableIdentifier baseTable, String partitionSchema, String partitionName)
  {
    this.baseTable = baseTable;
    setName(partitionName);
    setSchema(partitionSchema);
  }

  /**
   * Return the partition strategy and definition for a sub-partition.
   */
  public String getSubPartitionDefinition()
  {
    return subPartitionDefinition;
  }

  /**
   * Set the partition strategy and definition for a sub-partition.
   * @param subPartitionDefinition
   */
  public void setSubPartitionDefinition(String subPartitionDefinition)
  {
    this.subPartitionDefinition = subPartitionDefinition;
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    return PostgresPartitionReader.generatePartitionDDL(this, this.baseTable.getTableExpression(con), con);
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return getFullyQualifiedName(con);
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    TableIdentifier tbl = new TableIdentifier(null, getSchema(), getObjectName());
    return tbl.getDropStatement(con, cascade);
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

  @Override
  public boolean isSubPartition()
  {
    return parentPartition != null;
  }

  /**
   * If this is a sub-partition, this returns the table name of the parent partition.
   */
  public TableIdentifier getParentTable()
  {
    return parentPartition;
  }

  public void setParentTable(TableIdentifier parentTable)
  {
    this.parentPartition = parentTable;
  }
}
