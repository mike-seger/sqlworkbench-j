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
package workbench.db.mssql;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import workbench.db.DbObject;
import workbench.db.WbConnection;

/**
 * A class to represent the definitioni of a partition function in SQL Server.
 *
 * @author Thomas Kellerer
 */
public class PartitionFunction
  implements DbObject
{
  public static final String TYPE_NAME = "PARTITION FUNCTION";
  private String name;
  private String catalog;

  private List<String> parameterTypes = new ArrayList<>(1);
  private List<String> values = new ArrayList<>();
  private String typeDef;
  // SQL Server's internal primary key
  private int functionId;

  public PartitionFunction(String name)
  {
    this.name = name;
  }

  public void setCatalog(String catalog)
  {
    this.catalog = catalog;
  }

  @Override
  public String getCatalog()
  {
    return catalog;
  }

  @Override
  public String getSchema()
  {
    return null;
  }

  @Override
  public String getObjectType()
  {
    return TYPE_NAME;
  }

  @Override
  public String getObjectName()
  {
    return getName();
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return conn.getMetadata().quoteObjectname(name);
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return getName();
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return getObjectName(conn);
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    return getSource();
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return getName();
  }

  @Override
  public String getComment()
  {
    return null;
  }

  @Override
  public void setComment(String cmt)
  {
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

  public int getFunctionId()
  {
    return functionId;
  }

  public void setFunctionId(int id)
  {
    this.functionId = id;
  }

  public String getName()
  {
    return name;
  }

  @Override
  public void setName(String name)
  {
    this.name = name;
  }

  public String getTypeDef()
  {
    return typeDef;
  }

  public void setTypeDef(String typeDef)
  {
    this.typeDef = typeDef;
  }

  public List<String> getParameterTypes()
  {
    return parameterTypes;
  }

  public List<String> getValues()
  {
    return values;
  }

  public void addParameter(String type)
  {
    parameterTypes.add(type);
  }

  public void addValue(String value)
  {
    values.add(value);
  }

  public String getParameterString()
  {
    return parameterTypes.stream().collect(Collectors.joining(","));
  }

  public String getValueString()
  {
    return values.stream().collect(Collectors.joining(","));
  }

  public String getSource()
  {
    return
      "CREATE PARTITION FUNCTION " + name + " (" + getParameterString() + ")\n" +
      "  AS " + typeDef + "\n" +
      "  FOR VALUES (" + getValueString() + ");";
  }
}
