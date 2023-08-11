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
package workbench.db.h2database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read information about defined CONSTANTs in H2.
 *
 * @author Thomas Kellerer
 */
public class H2ConstantReader
  implements ObjectListExtender
{
  private String getSql(WbConnection connection, String schema, String name)
  {
    boolean is20 = JdbcUtils.hasMinimumServerVersion(connection, "2.0");
    String baseSql =
      "SELECT constant_catalog,  \n" +
      "       constant_schema, \n" +
      "       constant_name, \n" +
      "       data_type, \n" +
      (is20 ?
      "       value_definition as constant_value, \n"
      :
      "       sql as constant_value, \n") +
      "       remarks \n" +
      "FROM information_schema.constants ";

    StringBuilder sql = new StringBuilder(baseSql.length() + 40);

    sql.append(baseSql);

    boolean whereAdded = false;
    if (StringUtil.isNotBlank(name))
    {
      sql.append("\nWHERE constant_name like '");
      sql.append(connection.getMetadata().quoteObjectname(name));
      sql.append("%' ");
      whereAdded = true;
    }

    if (StringUtil.isNotBlank(schema))
    {
      sql.append(whereAdded ? "\n  AND " : "\nWHERE ");

      sql.append(" constant_schema = '");
      sql.append(schema);
      sql.append("'");
    }
    sql.append(" ORDER BY 1, 2 ");

    LogMgr.logMetadataSql(new CallerInfo(){}, "constants", sql);

    return sql.toString();
  }

  public List<H2Constant> getConstantsList(WbConnection connection, String schemaPattern, String namePattern)
  {
    boolean is20 = JdbcUtils.hasMinimumServerVersion(connection, "2.0");
    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    List<H2Constant> result = new ArrayList<>();
    try
    {
      sp = connection.setSavepoint();
      stmt = connection.createStatementForQuery();
      String sql = getSql(connection, schemaPattern, namePattern);
      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        String cat = rs.getString("constant_catalog");
        String schema = rs.getString("constant_schema");
        String name = rs.getString("constant_name");
        H2Constant constant = new H2Constant(cat, schema, name);
        String dataType = null;
        if (is20)
        {
          dataType = rs.getString("data_type");
        }
        else
        {
          int type = rs.getInt("data_type");
          dataType = SqlUtil.getTypeName(type);
        }
        constant.setDataType(dataType);
        constant.setValue(rs.getString("constant_value"));
        constant.setComment(rs.getString("remarks"));
        result.add(constant);
      }
      connection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      connection.rollback(sp);
      LogMgr.logError(new CallerInfo(){}, "Could not read constants", e);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  @Override
  public H2Constant getObjectDefinition(WbConnection connection, DbObject object)
  {
    List<H2Constant> constants = getConstantsList(connection, object.getSchema(), object.getObjectName());
    if (CollectionUtil.isEmpty(constants)) return null;
    return constants.get(0);
  }

  public String getConstantSource(H2Constant constant)
  {
    if (constant == null) return null;

    StringBuilder result = new StringBuilder(50);
    result.append("CREATE CONSTANT ");
    result.append(constant.getObjectName());
    result.append(" VALUE ");
    result.append(constant.getValue());
    result.append(";\n");
    if (StringUtil.isNotBlank(constant.getComment()))
    {
      result.append("\nCOMMENT ON CONSTANT " + constant.getObjectName() + " IS '");
      result.append(SqlUtil.escapeQuotes(constant.getComment()));
      result.append("';\n");
    }
    return result.toString();
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result,
                                  String catalog, String schema, String objects, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded("CONSTANT", requestedTypes)) return false;

    List<H2Constant> constants = getConstantsList(con, schema, objects);
    if (constants.isEmpty()) return false;
    for (H2Constant constant : constants)
    {
      result.addDbObject(constant);
    }
    return true;
  }

  @Override
  public boolean handlesType(String type)
  {
    return StringUtil.equalStringIgnoreCase("CONSTANT", type);
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    if (object == null) return null;
    if (!handlesType(object.getObjectType())) return null;

    H2Constant constant = getObjectDefinition(con, object);
    if (constant == null) return null;

    String[] columns = new String[] { "CONSTANT", "DATA_TYPE", "VALUE", "REMARKS" };
    int[] types = new int[] { Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN, Types.VARCHAR };
    int[] sizes = new int[] { 20, 10, 5, 30, 30 };
    DataStore result = new DataStore(columns, types, sizes);
    result.addRow();
    result.setValue(0, 0, constant.getObjectName());
    result.setValue(0, 1, constant.getDataType());
    result.setValue(0, 2, constant.getValue());
    result.setValue(0, 3, constant.getComment());

    return result;
  }

  @Override
  public List<String> supportedTypes()
  {
    return Collections.singletonList("CONSTANT");
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    return getConstantSource(getObjectDefinition(con, object));
  }

  @Override
  public List<ColumnIdentifier> getColumns(WbConnection con, DbObject object)
  {
    return null;
  }

  @Override
  public boolean hasColumns()
  {
    return false;
  }

}
