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
package workbench.db.duckdb;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DropType;
import workbench.db.GenerationOptions;
import workbench.db.JdbcUtils;
import workbench.db.SequenceDefinition;
import workbench.db.SequenceReader;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.SqlUtil;

import static workbench.db.SequenceReader.*;
/**
 *
 * @author Thomas Kellerer
 */
public class DuckDBSequenceReader
  implements SequenceReader
{
  private final WbConnection dbConnection;
  private final String baseSql =
      "select database_name, schema_name, sequence_name, \n" +
      "       start_value, min_value, max_value, increment_by, cycle, temporary \n" +
      "from duckdb_sequences()";

  public DuckDBSequenceReader(WbConnection dbConnection)
  {
    this.dbConnection = dbConnection;
  }


  @Override
  public List<SequenceDefinition> getSequences(String catalogPattern, String schemaPattern, String namePattern)
    throws SQLException
  {
    List<SequenceDefinition> result = new ArrayList<>();

    if (namePattern == null) namePattern = "%";

    StringBuilder query = new StringBuilder(baseSql);
    query.append("\n WHERE ");
    SqlUtil.appendExpression(query, "sequence_name", namePattern, dbConnection);
    SqlUtil.appendAndCondition(query, "database_name", catalogPattern, dbConnection);
    SqlUtil.appendAndCondition(query, "schema_name", schemaPattern, dbConnection);

    query.append("\n ORDER BY database_name, schema_name, sequence_name");
    LogMgr.logMetadataSql(new CallerInfo(){}, "sequence list", query);

    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = dbConnection.createStatementForQuery();
      rs = stmt.executeQuery(query.toString());
      DataStore ds = new DataStore(rs, true);
      for (int row=0; row < ds.getRowCount(); row ++)
      {
        SequenceDefinition def = createSequenceDefinition(ds, row);
        result.add(def);
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "sequence list", query);
    }
    finally
    {
      JdbcUtils.closeResult(rs);
    }
    return result;
  }

  @Override
  public SequenceDefinition getSequenceDefinition(String catalog, String schema, String sequence)
  {
    DataStore ds = getRawSequenceDefinition(catalog, schema, sequence);
    if (ds == null) return null;
    try
    {
      return createSequenceDefinition(ds, 0);
    }
    catch (SQLException sql)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve sequence details", sql);
      return null;
    }
  }

  private SequenceDefinition createSequenceDefinition(DataStore ds, int row)
    throws SQLException
  {
    if (ds == null || ds.getRowCount() < 1) return null;
    String db = ds.getValueAsString(0, "database_name");
    String schema = ds.getValueAsString(row, "schema_name");
    String name = ds.getValueAsString(row, "sequence_name");
    long start = ds.getValueAsLong(row, "start_value", 1);
    long min = ds.getValueAsLong(row, "min_value", 1);
    long max = ds.getValueAsLong(row, "max_value", Long.MAX_VALUE);
    long incr = ds.getValueAsLong(row, "increment_by", 1);
    Object temp = ds.getValue(row, "temporary");
    Object cycle = ds.getValue(row, "cycle");
    SequenceDefinition def = new SequenceDefinition(db, schema, name);
    def.setSequenceProperty(PROP_CYCLE, cycle);
    def.setSequenceProperty(PROP_INCREMENT, incr);
    def.setSequenceProperty(PROP_START_VALUE, start);
    def.setSequenceProperty(PROP_MIN_VALUE, min);
    def.setSequenceProperty(PROP_MAX_VALUE, max);
    def.setSequenceProperty("temporary", temp);
    return def;
  }

  @Override
  public DataStore getRawSequenceDefinition(String catalog, String schema, String sequence)
  {
    String query = baseSql +
      "\nwhere database_name = ? \n" +
      "  and schema_name = ? \n" +
      "  and sequence_name = ?";

    ResultSet rs = null;
    PreparedStatement pstmt = null;
    DataStore result = null;
    LogMgr.logMetadataSql(new CallerInfo(){}, "sequence definition", query, catalog, schema, sequence);
    try
    {
      pstmt = this.dbConnection.getSqlConnection().prepareStatement(query);
      pstmt.setString(1, catalog);
      pstmt.setString(2, schema);
      pstmt.setString(3, sequence);
      rs = pstmt.executeQuery();
      result = new DataStore(rs, true);
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "sequence definition", query, catalog, schema, sequence);
    }
    finally
    {
      JdbcUtils.closeResult(rs);
    }
    return result;
  }

  @Override
  public String getSequenceTypeName()
  {
    return DEFAULT_TYPE_NAME;
  }

  @Override
  public CharSequence getSequenceSource(String catalog, String schema, String sequence, GenerationOptions options)
  {
    SequenceDefinition def = getSequenceDefinition(catalog, schema, sequence);
    return getSequenceSource(def, options);
  }

  @Override
  public CharSequence getSequenceSource(SequenceDefinition def, GenerationOptions options)
  {
    if (def == null) return null;

    StringBuilder buf = new StringBuilder(250);

    try
    {
      String name = def.getObjectExpression(dbConnection);
      Long max = (Long) def.getSequenceProperty(PROP_MAX_VALUE);
      Long min = (Long) def.getSequenceProperty(PROP_MIN_VALUE);
      Long inc = (Long) def.getSequenceProperty(PROP_INCREMENT);
      Boolean cycle = (Boolean) def.getSequenceProperty(PROP_CYCLE);
      if (cycle == null) cycle = Boolean.FALSE;
      Boolean temp = (Boolean) def.getSequenceProperty("temporary");
      if (temp == null) temp = Boolean.FALSE;

      if (options.getDropType() != DropType.none)
      {
        buf.append("DROP SEQUENCE IF EXISTS ");
        buf.append(name);
        if (options.getDropType() == DropType.cascaded)
        {
          buf.append(" CASCADE");
        }
        buf.append(";\n\n");
      }
      buf.append("CREATE ");
      if (temp)
      {
        buf.append("TEMPORARY");
      }
      buf.append(" SEQUENCE ");
      buf.append(name);
      buf.append("\n       INCREMENT BY ");
      buf.append(inc);
      buf.append("\n       MINVALUE ");
      buf.append(min);
      long maxMarker = 9223372036854775807L;
      if (max != maxMarker)
      {
        buf.append("\n       MAXVALUE ");
        buf.append(max.toString());
      }
      buf.append("\n       ");
      if (!cycle)
      {
        buf.append("NO ");
      }
      buf.append("CYCLE");
      buf.append(";\n");
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error creating sequence source", e);
    }
    return buf;
  }

}
