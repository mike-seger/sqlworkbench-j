/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
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
package workbench.db.oracle;

import java.sql.SQLException;
import java.util.List;

import workbench.db.ColumnIdentifier;
import workbench.db.DbObject;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class OracleDomain
  implements DbObject
{
  public static final String TYPE_NAME = "DOMAIN";

  private String schema;
  private String name;
  private String remarks;
  private String dataDisplay;
  private List<ColumnIdentifier> columns;

  public OracleDomain()
  {
  }

  public OracleDomain(String schema, String name)
  {
    this.schema = schema;
    this.name = name;
  }

  public String getDataDisplay()
  {
    return dataDisplay;
  }

  public void setDataDisplay(String dataDisplay)
  {
    this.dataDisplay = dataDisplay;
  }

  public List<ColumnIdentifier> getColumns()
  {
    return columns;
  }

  public void setColumns(List<ColumnIdentifier> columns)
  {
    this.columns = columns;
  }

  @Override
  public String getCatalog()
  {
    return null;
  }

  @Override
  public void setSchema(String schema)
  {
    this.schema = schema;
  }

  @Override
  public String getSchema()
  {
    return schema;
  }

  @Override
  public String getObjectType()
  {
    return TYPE_NAME;
  }

  @Override
  public void setName(String name)
  {
    this.name = name;
  }

  @Override
  public String getObjectName()
  {
    return name;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return name;
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return name;
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return SqlUtil.fullyQualifiedName(conn, this);
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    return null;
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return SqlUtil.fullyQualifiedName(con, this);
  }

  @Override
  public String getComment()
  {
    return remarks;
  }

  @Override
  public void setComment(String comment)
  {
    this.remarks = comment;
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return null;
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

}
