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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import workbench.db.DbObject;
import workbench.db.WbConnection;

/**
 * A class to represent the definition of a partition schema for SQL Server.
 *
 * @author Thomas Kellerer
 */
public class PartitionScheme
  implements DbObject
{
  public static final String TYPE_NAME = "PARTITION SCHEME";
  private String catalog;
  private String name;
  private String function;
  private SortedSet<String> fileGroups = new TreeSet<>();

  public PartitionScheme()
  {
  }

  public PartitionScheme(String name, String function)
  {
    this.name = name;
    this.function = function;
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
    if (conn == null) return getName();
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

  public String getName()
  {
    return name;
  }

  @Override
  public void setName(String name)
  {
    this.name = name;
  }

  public String getFunctionName()
  {
    return function;
  }

  public void setFunctionName(String function)
  {
    this.function = function;
  }

  public void addFileGroup(String group)
  {
    if (group.equalsIgnoreCase("primary"))
    {
      group = "[PRIMARY]";
    }
    fileGroups.add(group);
  }

  public SortedSet<String> getFileGroups()
  {
    return fileGroups;
  }


  public String getGroupString()
  {
    return fileGroups.stream().collect(Collectors.joining(","));
  }

  public String getSource()
  {
    String sql =
      "CREATE PARTITION SCHEME " + name + "\n"+
      "  AS PARTITION " + function + "\n";
    if (fileGroups.size() == 1)
    {
      sql += "  ALL";
    }
    sql += " TO (" + getGroupString() + ");";
    return sql;
  }
}
