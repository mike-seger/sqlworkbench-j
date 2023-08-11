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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.DbSettings;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.QuoteHandler;
import workbench.db.TableColumnsDatastore;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read DOMAIN information for Oracle 23c and later.
 *
 * @author Thomas Kellerer
 */
public class OracleDomainReader
  implements ObjectListExtender
{
  @Override
  public List<String> supportedTypes()
  {
    return List.of(OracleDomain.TYPE_NAME);
  }

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  @Override
  public boolean handlesType(String type)
  {
    return OracleDomain.TYPE_NAME.equalsIgnoreCase(type);
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    if (object == null) return null;
    if (!handlesType(object.getObjectType())) return null;

    OracleDomain domain = getObjectDefinition(con, object);
    if (domain == null) return null;

    List<ColumnIdentifier> columns = domain.getColumns();
    TableColumnsDatastore ds = new  TableColumnsDatastore(columns);
    return ds;
  }

  @Override
  public OracleDomain getObjectDefinition(WbConnection con, DbObject name)
  {
    List<OracleDomain> domains = retrieveDomains(con, name.getSchema(), name.getObjectName());
    if (domains.size() > 0)
    {
      OracleDomain result = domains.get(0);
      List<ColumnIdentifier> columns = retrieveColumns(con, name);
      result.setColumns(columns);
      return result;
    }
    return null;
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    try
    {
      return DbmsMetadata.getDDL(con, "SQL_DOMAIN", object.getObjectName(), object.getSchema());
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve DOMAIN source for " + object.getObjectExpression(con), ex);
      return null;
    }
  }

  @Override
  public List<ColumnIdentifier> getColumns(WbConnection con, DbObject object)
  {
    return retrieveColumns(con, object);
  }

  @Override
  public boolean hasColumns()
  {
    return true;
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result, String catalog, String schema, String objectNamePattern, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded(OracleDomain.TYPE_NAME, requestedTypes)) return false;
    List<OracleDomain> domains = retrieveDomains(con, schema, objectNamePattern);
    for (OracleDomain d : domains)
    {
      result.addDbObject(d);
    }
    return domains.size() > 0;
  }

  private List<ColumnIdentifier> retrieveColumns(WbConnection conn, DbObject domain)
  {
    String sql =
      "-- SQL Workbench/J \n" +
      "SELECT " + OracleUtils.getCacheHint() + " c.column_name AS column_name,  \n" +
      OracleTableDefinitionReader.getSelectForColumnInfo(conn, "c") + ", \n" +
      "       data_default \n" +
      "FROM all_domain_cols c \n" +
      "WHERE owner = ? \n" +
      "  AND domain_name = ? ";
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    String owner = SqlUtil.removeObjectQuotes(domain.getSchema());
    String name = SqlUtil.removeObjectQuotes(domain.getObjectName());

    LogMgr.logMetadataSql(new CallerInfo(){}, "domain columns", sql, owner, name);
    List<ColumnIdentifier> result = new ArrayList<>();
    QuoteHandler quoter = conn.getMetadata();
    DbSettings dbSettings = conn.getDbSettings();
    OracleDataTypeResolver resolver = new OracleDataTypeResolver(conn);
    try
    {
      pstmt = OracleUtils.prepareQuery(conn, sql);
      pstmt.setString(1, owner);
      pstmt.setString(2, name);
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String colName = rs.getString("COLUMN_NAME");
        int sqlType = rs.getInt("DATA_TYPE");
        String typeName = rs.getString("TYPE_NAME");
        ColumnIdentifier col = new ColumnIdentifier(quoter.quoteObjectname(colName), resolver.fixColumnType(sqlType, typeName));

        int size = rs.getInt("COLUMN_SIZE");
        if (rs.wasNull())
        {
          size = Integer.MAX_VALUE;
        }
        int digits = rs.getInt("DECIMAL_DIGITS");
        if (rs.wasNull()) digits = -1;

        String defaultValue = rs.getString("DATA_DEFAULT");
        if (defaultValue != null && dbSettings.trimDefaults())
        {
          defaultValue = defaultValue.trim();
        }
        String nullable = rs.getString("IS_NULLABLE");
        String display = resolver.getSqlTypeDisplay(typeName, sqlType, size, digits, OracleDataTypeResolver.CharSemantics.Char);

        col.setDbmsType(display);
        col.setIsNullable("YES".equalsIgnoreCase(nullable));
        result.add(col);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "domain columns", sql, owner, name);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return result;
  }

  private List<OracleDomain> retrieveDomains(WbConnection conn, String schema, String name)
  {
    StringBuilder select = new StringBuilder(50);
    select.append(
      "-- SQL Workbench/J \n" +
      "SELECT owner, name " +
      "FROM all_domains");

    int schemaIndex = -1;
    int nameIndex = -1;

    if (StringUtil.isNotBlank(schema))
    {
      if (schema.indexOf("%") > 0)
      {
        select.append("\nWHERE owner LIKE ? ");
        SqlUtil.appendEscapeClause(select, conn, schema);
        schema = SqlUtil.escapeUnderscore(name, conn);
      }
      else
      {
        select.append("\nWHERE owner = ? ");
      }
      schemaIndex = 1;
    }

    if (StringUtil.isNotBlank(name))
    {
      if (schemaIndex != -1)
      {
        select.append("\n  AND ");
        nameIndex = 2;
      }
      else
      {
        select.append("\nWHERE ");
        nameIndex = 1;
      }
      if (name.indexOf('%') > 0)
      {
        select.append(" name LIKE ? ");
        SqlUtil.appendEscapeClause(select, conn, name);
        name = SqlUtil.escapeUnderscore(name, conn);
      }
      else
      {
        select.append(" name = ? ");
      }
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "domains", select);

    PreparedStatement stmt = null;
    ResultSet rs = null;
    List<OracleDomain> result = new ArrayList<>();
    try
    {
      stmt = OracleUtils.prepareQuery(conn, select.toString());
      if (schemaIndex > -1)
      {
        stmt.setString(schemaIndex, schema);
      }
      if (nameIndex > -1)
      {
        stmt.setString(nameIndex, name);
      }
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String domainSchema = rs.getString(1);
        String domainName = rs.getString(2);
        OracleDomain domain = new OracleDomain(domainSchema, domainName);
        result.add(domain);
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "domains", select, schema, name);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }


}
