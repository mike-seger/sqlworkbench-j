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
package workbench.db.firebird;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DbMetadata;
import workbench.db.JdbcProcedureReader;
import workbench.db.NoConfigException;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.WbConnection;
import workbench.db.oracle.OraclePackageParser;

import workbench.storage.DataStore;

import workbench.sql.DelimiterDefinition;

import workbench.db.JdbcUtils;
import workbench.db.RoutineType;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

import static workbench.db.ProcedureReader.*;

/**
 * An implementation of the ProcedureReader interface for the
 * <a href="https://www.firebirdsql.org">Firebird</a> database server.
 *
 * The new packages in Firebird 3.0 are not handled properly yes.
 *
 * @author  Thomas Kellerer
 */
public class FirebirdProcedureReader
  extends JdbcProcedureReader
{
  private boolean is30;

  public FirebirdProcedureReader(WbConnection conn)
  {
    super(conn);
    is30 = JdbcUtils.hasMinimumServerVersion(conn, "3.0");
  }

  @Override
  public void readProcedureSource(ProcedureDefinition def, String catalogForSource, String schemaForSource)
    throws NoConfigException
  {
    if (is30 && def.isPackageProcedure())
    {
      CharSequence src = getPackageSource(null, null, def.getPackageName());
      def.setSource(src);
    }
    else
    {
      super.readProcedureSource(def, catalogForSource, schemaForSource);
    }
  }

  @Override
  public DataStore buildProcedureListDataStore(DbMetadata meta, boolean addSpecificName)
  {
    DataStore ds = super.buildProcedureListDataStore(meta, addSpecificName);
    if (supportsPackages())
    {
      ds.getResultInfo().getColumn(COLUMN_IDX_PROC_LIST_CATALOG).setColumnName("PACKAGE");
    }
    return ds;
  }

  @Override
  public boolean supportsPackages()
  {
    return is30;
  }

  @Override
  public List<ProcedureDefinition> getTableFunctions(String catalogPattern, String schemaPattern, String namePattern)
    throws SQLException
  {
    List<ProcedureDefinition> result = new ArrayList<>();
    if (!JdbcUtils.hasMinimumServerVersion(connection, "2.5"))
    {
      return result;
    }
    StringBuilder sql = new StringBuilder(150);
    sql.append(
      "  select trim(rdb$package_name) as procedure_package, \n" +
      "         trim(rdb$procedure_name) as procedure_name, \n" +
      "         trim(rdb$description) as remarks,  \n" +
      "         case rdb$procedure_type\n" +
      "           when 1 then " + DatabaseMetaData.procedureReturnsResult + " \n" +
      "           when 2 then " + DatabaseMetaData.procedureNoResult + " \n" +
      "           else " + DatabaseMetaData.procedureResultUnknown + " \n" +
      "         end as procedure_type\n " +
      "  from rdb$procedures  \n" +
      "  where (rdb$private_flag = 0 or rdb$package_name is null) \n" +
      "    and rdb$procedure_type = 1"
      );

    namePattern = DbMetadata.cleanupWildcards(namePattern);

    if (StringUtil.isNotEmpty(namePattern))
    {
      SqlUtil.appendAndCondition(sql, "procedure_name", namePattern, connection);
    }
    sql.append(" \nORDER BY rdb$procedure_name");

    Statement stmt = null;
    ResultSet rs = null;
    LogMgr.logMetadataSql(new CallerInfo(){}, "table functions", sql);
    try
    {
      stmt = connection.createStatementForQuery();
      rs = stmt.executeQuery(sql.toString());
      while (rs.next())
      {
        String pkg = rs.getString("procedure_package");
        String procName = rs.getString("procedure_name");
        String remarks = rs.getString("remarks");
        int type = rs.getInt("procedure_type");
        ProcedureDefinition def = new ProcedureDefinition(procName, RoutineType.tableFunction, type);
        def.setComment(remarks);
        def.setPackageName(pkg);
        result.add(def);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "table functions", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }


  @Override
  public DataStore getProcedures(String catalog, String schema, String name)
    throws SQLException
  {
    if (!supportsPackages())
    {
      return super.getProcedures(catalog, schema, name);
    }
    return getProceduresAndPackages(name);
  }

  private DataStore getProceduresAndPackages(String name)
    throws SQLException
  {
    StringBuilder sql = new StringBuilder(150);
    sql.append(
      "select * \n" +
      "from (  \n" +
      "  select trim(rdb$package_name) as procedure_cat,   \n" +
      "         null as procedure_schem,  \n" +
      "         trim(rdb$procedure_name) as procedure_name,  \n" +
      "         rdb$description as remarks,  \n" +
      "         case rdb$procedure_type\n" +
      "           when 1 then " + DatabaseMetaData.procedureReturnsResult + " \n" +
      "           when 2 then " + DatabaseMetaData.procedureNoResult + " \n" +
      "           else " + DatabaseMetaData.procedureResultUnknown + " \n" +
      "         end as procedure_type\n " +
      "  from rdb$procedures  \n" +
      "  where rdb$private_flag = 0 \n" +
      "     or rdb$package_name is null \n" +
      "  union all  \n" +
      "  select trim(rdb$package_name),   \n" +
      "         null as procedure_schem,  \n" +
      "         trim(rdb$function_name),  \n" +
      "         rdb$description as remarks,  \n" +
      "         " + DatabaseMetaData.procedureReturnsResult + " as procedure_type  \n" +
      "  from rdb$functions \n" +
      "  where rdb$private_flag = 0 \n" +
      "     or rdb$package_name is null \n" +
      ") t \n");

    name = DbMetadata.cleanupWildcards(name);

    if (StringUtil.isNotEmpty(name))
    {
      sql.append("WHERE ");
      SqlUtil.appendExpression(sql, "procedure_name", name, connection);
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "procedures", sql);

    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = connection.createStatementForQuery();
      rs = stmt.executeQuery(sql.toString());

      DataStore ds = fillProcedureListDataStore(rs);

      for (int row=0; row < ds.getRowCount(); row++)
      {
        String pkg = ds.getValueAsString(row, ProcedureReader.COLUMN_IDX_PROC_LIST_CATALOG);
        String procName = ds.getValueAsString(row, ProcedureReader.COLUMN_IDX_PROC_LIST_NAME);
        String remarks = ds.getValueAsString(row, ProcedureReader.COLUMN_IDX_PROC_LIST_REMARKS);
        int resultType = ds.getValueAsInt(row, ProcedureReader.COLUMN_IDX_PROC_LIST_TYPE, DatabaseMetaData.procedureResultUnknown);
        RoutineType rType = RoutineType.fromProcedureResult(resultType);
        ProcedureDefinition def = new ProcedureDefinition(procName, rType, resultType);
        def.setComment(remarks);
        def.setPackageName(pkg);
        ds.getRow(row).setUserObject(def);
      }

      // sort the complete combined result according to the JDBC API
      ds.sort(getProcedureListSort());

      ds.resetStatus();
      return ds;
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "procedures", sql);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeStatement(stmt);
    }
  }

  @Override
  public StringBuilder getProcedureHeader(ProcedureDefinition def)
  {
    StringBuilder source = new StringBuilder(100);
    try
    {
      DataStore ds = this.getProcedureColumns(def);
      source.append("CREATE OR ALTER ");

      boolean isFunction = false;
      if (is30 && def.isFunction() && !def.isTableFunction())
      {
        source.append("FUNCTION "); // Firebird 3.0
        isFunction = true;
      }
      else
      {
        source.append("PROCEDURE ");
      }

      source.append(def.getProcedureName());
      String retType = null;
      int count = ds.getRowCount();
      int added = 0;
      for (int i=0; i < count; i++)
      {
        String vartype = ds.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_DATA_TYPE);
        String name = ds.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NAME);
        String ret = ds.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
        if ("OUT".equals(ret))
        {
          if (retType == null)
          {
            retType = "(" + name + " " + vartype;
          }
          else
          {
            retType += ", " + name + " " + vartype;
          }
        }
        else if ("RETURN".equals(ret))
        {
          retType = vartype;
        }
        else
        {
          if (added > 0)
          {
            source.append(", ");
          }
          else
          {
            source.append(" (");
          }
          source.append(name);
          source.append(' ');
          source.append(vartype);
          added ++;
        }
      }
      if (added > 0) source.append(')');
      if (retType != null)
      {
        source.append("\n  RETURNS ");
        source.append(retType);
        if (!isFunction) source.append(")");
      }
      source.append("\nAS\n");
    }
    catch (Exception e)
    {
      source = StringUtil.emptyBuilder();
    }
    return source;
  }

  @Override
  public CharSequence getPackageSource(String catalog, String schema, String packageName)
  {
    String sql =
      "select rdb$package_header_source, rdb$package_body_source \n" +
      "from rdb$packages \n" +
      "where rdb$package_name = ? ";

    PreparedStatement stmt = null;
    ResultSet rs = null;
    StringBuilder result = new StringBuilder(500);
    DelimiterDefinition delim = Settings.getInstance().getAlternateDelimiter(connection, DelimiterDefinition.STANDARD_DELIMITER);

    try
    {
      stmt = connection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, packageName);
      rs = stmt.executeQuery();
      if (rs.next())
      {
        String header = rs.getString(1);
        result.append("CREATE OR ALTER PACKAGE ");
        result.append(connection.getMetadata().quoteObjectname(packageName));
        result.append("\nAS\n");
        result.append(header);
        result.append(delim.getScriptText());
        result.append('\n');
        String body = rs.getString(2);
        result.append("RECREATE PACKAGE BODY ");
        result.append(connection.getMetadata().quoteObjectname(packageName));
        result.append("\nAS\n");
        result.append(body);
        result.append(delim.getScriptText());
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve package source using: \n" + SqlUtil.replaceParameters(sql, packageName), ex);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public DataStore getProcedureColumns(ProcedureDefinition def)
    throws SQLException
  {
    if (is30)
    {
      return retrieveProcedureColumns(def);
    }
    return super.getProcedureColumns(def);
  }

  public DataStore retrieveProcedureColumns(ProcedureDefinition def)
    throws SQLException
  {
    // for Firebird 30 we need to use our own statement
    // as the JDBC driver does not return any information about packages
    String sql =
      "select * \n" +
      "from ( \n" +
      "  select trim(pp.rdb$parameter_name) as column_name, \n" +
      "         f.rdb$field_type as field_type, \n" +
      "         f.rdb$field_sub_type as field_sub_type, \n" +
      "         f.rdb$field_precision as field_precision, \n" +
      "         f.rdb$field_scale as field_scale, \n" +
      "         f.rdb$field_length as field_length, \n" +
      "         case pp.rdb$parameter_type when 0 then " + DatabaseMetaData.procedureColumnIn + " else " + DatabaseMetaData.procedureColumnOut + " end as parameter_mode, \n" +
      "         trim(pp.rdb$description) as remarks, \n" +
      "         f.rdb$character_length as char_len, \n" +
      "         pp.rdb$parameter_number + 1 as parameter_number,  \n" +
      (is30 ?
        "         trim(pp.rdb$package_name) as package_name,  \n" :
        "         null as package_name, \n"
      ) +
      "         trim(pp.rdb$procedure_name) as procedure_name,  \n" +
      "         'procedure' as proc_type \n" +
      "  from rdb$procedure_parameters pp \n" +
      "    join rdb$fields f on pp.rdb$field_source = f.rdb$field_name \n" +
      "  union all \n" +
      "  select trim(fp.rdb$argument_name), \n" +
      "         f.rdb$field_type, \n" +
      "         f.rdb$field_sub_type, \n" +
      "         f.rdb$field_precision, \n" +
      "         f.rdb$field_scale, \n" +
      "         f.rdb$field_length, \n" +
      "         case when rdb$argument_name is null then " + DatabaseMetaData.procedureColumnReturn + " else " + DatabaseMetaData.procedureColumnIn + " end, \n" +
      "         trim(fp.rdb$description), \n" +
      "         f.rdb$character_length, \n" +
      "         fp.rdb$argument_position + 1,  \n" +
      (is30 ?
        "         trim(fp.rdb$package_name), \n" :
        "         null, \n"
      ) +
      "         trim(fp.rdb$function_name), \n" +
      "         'function' as proc_type \n" +
      "  from rdb$function_arguments fp \n" +
      "    join rdb$fields f on fp.rdb$field_source = f.rdb$field_name \n" +
      ") t \n" +
      "where procedure_name = ? \n " +
      "  and proc_type = ? \n";

    if (def.isPackageProcedure())
    {
      sql += "  and package_name = ? \n";
    }
    sql += "order by parameter_mode desc, parameter_number";


    String type = null;
    if (def.isFunction() && !def.isTableFunction())
    {
      type =  "function";
    }
    else
    {
      type = "procedure";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "procedure parameter", sql, def.getProcedureName(), type, def.getPackageName());

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    DataStore result = createProcColsDataStore();

    try
    {
      pstmt = connection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, def.getProcedureName());
      pstmt.setString(2, type);

      if (def.isPackageProcedure())
      {
        pstmt.setString(3, def.getPackageName());
      }
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String colName = rs.getString("column_name");
        short fbType = rs.getShort("field_type");
        short fbSubType = rs.getShort("field_sub_type");
        int precision = rs.getInt("field_precision");
        short scale = rs.getShort("field_scale");
        int length = rs.getInt("field_length");
        String remarks = rs.getString("remarks");
        int colPos = rs.getInt("parameter_number");
        int mode = rs.getInt("parameter_mode");
        int row = result.addRow();
        result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NAME, colName);
        result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE, convertArgModeToString(mode));
        result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NR, colPos);

        int jdbcDataType = getDataType(fbType, fbSubType, scale);
        result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_JDBC_DATA_TYPE, jdbcDataType);

        String typeName = getDataTypeName(fbType, fbSubType, scale);

        int size = 0;
        int digits = 0;

        if (SqlUtil.isNumberType(jdbcDataType))
        {
          size = precision;
          digits = (scale == -1 ? 0 : scale);
        }
        else
        {
          size = length;
          digits = 0;
        }
        String display = connection.getMetadata().getDataTypeResolver().getSqlTypeDisplay(typeName, jdbcDataType, size, digits);
        result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_DATA_TYPE, display);
        result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_REMARKS, remarks);
      }

    }
    catch (Exception ex)
    {
    LogMgr.logMetadataError(new CallerInfo(){}, ex, "procedure parameter", sql, def.getProcedureName(), type, def.getPackageName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }

    return result;
  }


  @Override
  public CharSequence getPackageProcedureSource(ProcedureDefinition def)
  {
    if (!supportsPackages()) return null;
    if (def == null) return null;
    if (!def.isPackageProcedure()) return null;

    CharSequence procSrc = null;

    try
    {
      if (def.getSource() == null)
      {
        readProcedureSource(def, null, null);
      }
      if (def.getSource() != null)
      {
        // the syntax between Firebird and Oracle is similar enough
        // so that the same "parser" can be used
        // we don't need to supply parameters here, because Firebird doesn't support function overloading
        procSrc = OraclePackageParser.getProcedureSource(def.getSource(), def, null);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read procedure source", ex);
    }
    return procSrc;
  }


  // -----------------------------------------------------------------------------
  // --- the following code is copied from the Jaybird Source
  // --- as that is all defined as private, it can't be accessed from the outside
  // -----------------------------------------------------------------------------
  private static final short SMALLINT_TYPE = 7;
  private static final short INTEGER_TYPE = 8;
  private static final short QUAD_TYPE = 9;
  private static final short FLOAT_TYPE = 10;
  private static final short D_FLOAT_TYPE = 11;
  private static final short DATE_TYPE = 12;
  private static final short TIME_TYPE = 13;
  private static final short CHAR_TYPE = 14;
  private static final short INT64_TYPE = 16;
  private static final short DOUBLE_TYPE = 27;
  private static final short TIMESTAMP_TYPE = 35;
  private static final short VARCHAR_TYPE = 37;
  private static final short BLOB_TYPE = 261;
  private static final short BOOLEAN_TYPE = 23;

  private static int getDataType(short fieldType, short fieldSubType, short fieldScale)
  {
    switch (fieldType)
    {
      case SMALLINT_TYPE:
        if (fieldSubType == 1 || (fieldSubType == 0 && fieldScale < 0))
          return Types.NUMERIC;
        else if (fieldSubType == 2)
          return Types.DECIMAL;
        else
          return Types.SMALLINT;
      case INTEGER_TYPE:
        if (fieldSubType == 1 || (fieldSubType == 0 && fieldScale < 0))
          return Types.NUMERIC;
        else if (fieldSubType == 2)
          return Types.DECIMAL;
        else
          return Types.INTEGER;
      case DOUBLE_TYPE:
      case D_FLOAT_TYPE:
        return Types.DOUBLE;
      case FLOAT_TYPE:
        return Types.FLOAT;
      case CHAR_TYPE:
        return Types.CHAR;
      case VARCHAR_TYPE:
        return Types.VARCHAR;
      case TIMESTAMP_TYPE:
        return Types.TIMESTAMP;
      case TIME_TYPE:
        return Types.TIME;
      case DATE_TYPE:
        return Types.DATE;
      case INT64_TYPE:
        if (fieldSubType == 1 || (fieldSubType == 0 && fieldScale < 0))
          return Types.NUMERIC;
        else if (fieldSubType == 2)
          return Types.DECIMAL;
        else
          return Types.BIGINT;
      case BLOB_TYPE:
        if (fieldSubType < 0)
          return Types.BLOB;
        else if (fieldSubType == 0)
          return Types.LONGVARBINARY;
        else if (fieldSubType == 1)
          return Types.LONGVARCHAR;
        else
          return Types.OTHER;
      case QUAD_TYPE:
        return Types.OTHER;
      case BOOLEAN_TYPE:
        return Types.BOOLEAN;
      default:
        return Types.NULL;
    }
  }

  private static String getDataTypeName(short sqltype, short sqlsubtype, short sqlscale)
  {
    switch (sqltype)
    {
      case SMALLINT_TYPE:
        if (sqlsubtype == 1 || (sqlsubtype == 0 && sqlscale < 0))
          return "NUMERIC";
        else if (sqlsubtype == 2)
          return "DECIMAL";
        else
          return "SMALLINT";
      case INTEGER_TYPE:
        if (sqlsubtype == 1 || (sqlsubtype == 0 && sqlscale < 0))
          return "NUMERIC";
        else if (sqlsubtype == 2)
          return "DECIMAL";
        else
          return "INTEGER";
      case DOUBLE_TYPE:
      case D_FLOAT_TYPE:
        return "DOUBLE PRECISION";
      case FLOAT_TYPE:
        return "FLOAT";
      case CHAR_TYPE:
        return "CHAR";
      case VARCHAR_TYPE:
        return "VARCHAR";
      case TIMESTAMP_TYPE:
        return "TIMESTAMP";
      case TIME_TYPE:
        return "TIME";
      case DATE_TYPE:
        return "DATE";
      case INT64_TYPE:
        if (sqlsubtype == 1 || (sqlsubtype == 0 && sqlscale < 0))
          return "NUMERIC";
        else if (sqlsubtype == 2)
          return "DECIMAL";
        else
          return "BIGINT";
      case BLOB_TYPE:
        if (sqlsubtype < 0)
          return "BLOB SUB_TYPE <0";
        else if (sqlsubtype == 0)
          return "BLOB SUB_TYPE 0";
        else if (sqlsubtype == 1)
          return "BLOB SUB_TYPE 1";
        else
          return "BLOB SUB_TYPE " + sqlsubtype;
      case QUAD_TYPE:
        return "ARRAY";
      case BOOLEAN_TYPE:
        return "BOOLEAN";
      default:
        return "NULL";
    }
  }
  // ----------- End of Jaybird code ---------
  // -----------------------------------------

}
