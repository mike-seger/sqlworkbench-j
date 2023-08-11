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
package workbench.db.postgres;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.JdbcProcedureReader;
import workbench.db.JdbcUtils;
import workbench.db.NoConfigException;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.QuoteHandler;
import workbench.db.RoutineType;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read procedure and function definitions from a Postgres database.
 *
 * @author  Thomas Kellerer
 */
public class PostgresProcedureReader
  extends JdbcProcedureReader
{
  // Maps PG type names to Java types.
  private final Map<String, Integer> pgType2Java = createJavaTypeMap();
  private final String argTypesExp;
  private final Set<String> trimSourceLanguages = CollectionUtil.caseInsensitiveSet("sql", "plpgsql");

  public PostgresProcedureReader(WbConnection conn)
  {
    super(conn);
    this.useSavepoint = true;

    if (JdbcUtils.hasMinimumServerVersion(connection, "8.4"))
    {
      argTypesExp = "(select array_to_string(array_agg(t::regtype), ';') from unnest(coalesce(p.proallargtypes, p.proargtypes)) as x(t)) as argtypes";
    }
    else
    {
      argTypesExp = "array_to_string(p.proargtypes, ';') as argtypes, \n";
    }
  }

  private Integer getJavaType(String pgType)
  {
    Integer i = pgType2Java.get(pgType);
    if (i == null) return Integer.valueOf(Types.OTHER);
    return i;
  }

  @Override
  public DataStore getProcedures(String catalog, String schemaPattern, String procName)
    throws SQLException
  {
    if ("*".equals(schemaPattern) || "%".equals(schemaPattern))
    {
      schemaPattern = null;
    }

    String namePattern = null;
    if ("*".equals(procName) || "%".equals(procName))
    {
      namePattern = null;
    }
    else if (StringUtil.isNotBlank(procName))
    {
      PGProcName pg = new PGProcName(procName);
      namePattern = pg.getName();
    }

    Statement stmt = null;
    Savepoint sp = null;
    ResultSet rs = null;

    boolean showParametersInName = connection.getDbSettings().showProcedureParameters();

    String sql =
        "-- SQL Workbench/J \n" +
        "SELECT n.nspname AS proc_schema, \n" +
        "       p.proname AS proc_name, \n" +
        "       d.description AS remarks, \n" +
        "       " + argTypesExp + ", \n" +
        "       array_to_string(p.proargnames, ';') as argnames, \n" +
        "       array_to_string(p.proargmodes, ';') as argmodes, \n"+
        "       p.proretset, \n" +
        getProctypeColumnExpression() +
        "       p.oid::text as procid \n" +
        " FROM pg_catalog.pg_proc p \n " +
        "   JOIN pg_catalog.pg_namespace n on p.pronamespace = n.oid \n" +
        "   LEFT JOIN pg_catalog.pg_description d ON p.oid = d.objoid \n" +
        "   LEFT JOIN pg_catalog.pg_class c ON d.classoid=c.oid AND c.relname='pg_proc' \n" +
        "   LEFT JOIN pg_catalog.pg_namespace pn ON c.relnamespace=pn.oid AND pn.nspname='pg_catalog'";

    boolean whereNeeded = true;
    if (StringUtil.isNotBlank(schemaPattern))
    {
      sql += "\n WHERE n.nspname LIKE '" + schemaPattern + "' ";
      whereNeeded = false;
    }

    if (StringUtil.isNotBlank(namePattern))
    {
      sql += whereNeeded ? "\n WHERE " : "\n  AND ";
      sql += "p.proname LIKE '" + namePattern + "' ";
      whereNeeded = false;
    }

    if (connection.getDbSettings().returnAccessibleProceduresOnly())
    {
      sql += whereNeeded ? "\n WHERE " : "\n  AND ";
      sql += "pg_catalog.has_function_privilege(p.oid,'execute')";
      whereNeeded = false;
    }

    sql += "\nORDER BY proc_schema, proc_name ";

    LogMgr.logMetadataSql(new CallerInfo(){}, "Retrieving procedures using:", sql);

    try
    {
      if (useSavepoint)
      {
        sp = this.connection.setSavepoint();
      }

      stmt = connection.createStatementForQuery();

      rs = stmt.executeQuery(sql);
      DataStore ds = buildProcedureListDataStore(this.connection.getMetadata(), false);

      while (rs.next())
      {
        String schema = rs.getString("proc_schema");
        String name = rs.getString("proc_name");
        String remark = rs.getString("remarks");
        String argTypes = rs.getString("argtypes");
        String argNames = rs.getString("argnames");
        String modes = rs.getString("argmodes");
        String type = rs.getString("proc_type");
        boolean isTableFunc = rs.getBoolean("proretset");
        String procId = rs.getString("procid");
        int row = ds.addRow();

        int resultType = java.sql.DatabaseMetaData.procedureReturnsResult;
        RoutineType rType = RoutineType.function;
        if (isTableFunc)
        {
          rType = RoutineType.tableFunction;
        }
        else if ("procedure".equals(type))
        {
          resultType = java.sql.DatabaseMetaData.procedureNoResult;
          rType = RoutineType.procedure;
        }
        ProcedureDefinition def = createDefinition(schema, name, argNames, argTypes, modes, procId, rType, resultType);
        def.setDbmsProcType(type);
        def.setComment(remark);
        def.setInternalIdentifier(procId);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_CATALOG, null);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_SCHEMA, schema);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_NAME, showParametersInName ? def.getDisplayName() : name);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_TYPE, resultType);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_REMARKS, remark);
        ds.getRow(row).setUserObject(def);
      }

      this.connection.releaseSavepoint(sp);
      ds.resetStatus();
      return ds;
    }
    catch (SQLException ex)
    {
      this.connection.rollback(sp);
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve procedures using:\n" + sql, ex);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  private String getProctypeColumnExpression()
  {
    if (JdbcUtils.hasMinimumServerVersion(connection, "11"))
    {
      return "   case p.prokind when 'p' then 'procedure' when 'a' then 'aggregate' else 'function' end as proc_type, \n";
    }
    return "       case when p.proisagg then 'aggregate' else 'function' end as proc_type, \n";
  }


  public ProcedureDefinition createProcedure(String schema, String name, String args, String types, String modes, String procId)
  {
    return createDefinition(schema, name, args, types, modes, procId, RoutineType.procedure, DatabaseMetaData.procedureReturnsResult);

  }
  public ProcedureDefinition createFunction(String schema, String name, String args, String types, String modes, String procId)
  {
    return createDefinition(schema, name, args, types, modes, procId, RoutineType.function, DatabaseMetaData.procedureReturnsResult);
  }

  public ProcedureDefinition createTableFunction(String schema, String name, String args, String types, String modes, String procId)
  {
    return createDefinition(schema, name, args, types, modes, procId, RoutineType.tableFunction, DatabaseMetaData.procedureReturnsResult);
  }

  public ProcedureDefinition createDefinition(String schema, String name, String args, String types, String modes, String procId, RoutineType routineType, int resultType)
  {
    ArgInfo info = new ArgInfo(args, types, modes);
    PGProcName pname = new PGProcName(name, info);

    ProcedureDefinition def = new ProcedureDefinition(null, schema, name, routineType, resultType);
    List<ColumnIdentifier> cols = convertToColumns(info);
    def.setParameters(cols);
    def.setDisplayName(pname.getFormattedName());
    def.setInternalIdentifier(procId);
    return def;
  }

  @Override
  public DataStore getProcedureColumns(ProcedureDefinition def)
    throws SQLException
  {
    if (Settings.getInstance().getBoolProperty("workbench.db.postgresql.fixproctypes", true)
        && JdbcUtils.hasMinimumServerVersion(connection, "8.4"))
    {
      PGProcName pgName = new PGProcName(def);
      return getColumns(def.getCatalog(), def.getSchema(), pgName);
    }
    else
    {
      return super.getProcedureColumns(def.getCatalog(), def.getSchema(), def.getProcedureName(), null);
    }
  }

  @Override
  public void readProcedureSource(ProcedureDefinition def, String catalogForSource, String schemaForSource)
    throws NoConfigException
  {
    boolean usePGFunction = Settings.getInstance().getBoolProperty("workbench.db.postgresql.procsource.useinternal", false);

    if (usePGFunction && JdbcUtils.hasMinimumServerVersion(connection, "8.4") && !"aggregate".equals(def.getDbmsProcType()))
    {
      readFunctionDef(def);
      return;
    }

    boolean is96 = JdbcUtils.hasMinimumServerVersion(connection, "9.6");
    boolean is92 = JdbcUtils.hasMinimumServerVersion(connection, "9.2");
    boolean is84 = JdbcUtils.hasMinimumServerVersion(connection, "8.4");
    boolean is14 = JdbcUtils.hasMinimumServerVersion(connection, "14");
    boolean showExtension = is92;

    String srcColumn = "p.prosrc";
    String isSQLFuncCol = "false as is_sql_function";
    if (is14)
    {
      srcColumn = "case when p.prosqlbody is null then p.prosrc else pg_get_functiondef(p.oid) end as prosrc";
      isSQLFuncCol = "p.prosqlbody is not null as is_sql_function";
    }

    PGProcName name = new PGProcName(def);

    String sql =
      "-- SQL Workbench/J \n" +
      "SELECT " + srcColumn + ", \n" +
      "       " + isSQLFuncCol + ", \n" +
      "       l.lanname as lang_name, \n" +
      "       n.nspname as schema_name, \n";

    if (JdbcUtils.hasMinimumServerVersion(connection, "8.4"))
    {
      sql += "       pg_get_function_result(p.oid) as formatted_return_type, \n" +
             "       pg_get_function_arguments(p.oid) as formatted_parameters, \n ";
    }
    else
    {
      sql += "       null::text as formatted_return_type, \n" +
             "       null::text as formatted_parameters, \n";
    }

    if (showExtension)
    {
      sql += "       ext.extname, \n";
    }
    else
    {
      sql += "       null::text as extname, \n";
    }

    sql +=  "       p.prorettype::regtype::text as return_type, \n" +
            "       " + argTypesExp + ", \n" +
            "       array_to_string(p.proargnames, ';') as argnames, \n" +
            "       array_to_string(p.proargmodes, ';') as argmodes, \n" +
            "       p.prosecdef, \n" +
            "       p.proretset, \n" +
            "       p.provolatile, \n" +
            "       p.proisstrict, \n" +
            "       " + (is92 ? "p.proleakproof" : "false as proleakproof") + ", \n" +
            "       " + (is96 ? "p.proparallel" : "null as proparallel") + ", \n" +
            "       " + (is84 ? "array_to_string(p.proconfig, ',') as proconfig" : "null::text as proconfig") + ", \n" +
            getProctypeColumnExpression() +
            "       obj_description(p.oid, 'pg_proc') as remarks ";

    boolean hasCost = JdbcUtils.hasMinimumServerVersion(connection, "8.3");
    if (hasCost)
    {
      sql += ",\n       p.procost ,\n       p.prorows ";
    }

    sql +=
      "\nFROM pg_proc p \n" +
      "   JOIN pg_language l ON p.prolang = l.oid \n" +
      "   JOIN pg_namespace n ON p.pronamespace = n.oid \n";

    if (showExtension)
    {
      sql +=
        "  LEFT JOIN pg_depend d ON d.objid = p.oid AND d.deptype = 'e' \n" +
        "  LEFT JOIN pg_extension ext on ext.oid = d.refobjid \n";
    }

    sql += "WHERE p.proname = '" + name.getName() + "' \n";
    if (StringUtil.isNotBlank(def.getSchema()))
    {
      sql += "  AND n.nspname = '" + def.getSchema() + "' \n";
    }

    String oids = name.getInputOIDsAsVector();
    if (StringUtil.isNotBlank(oids))
    {
      sql += " AND p.proargtypes = " + oids + " \n ";
    }
    else if (def.getDisplayName().contains("("))
    {
      // only restrict the arguments if the procedure name contained them as well.
      sql += " AND (p.proargtypes IS NULL OR array_length(p.proargtypes,1) = 0)";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "procedure source", sql);

    StringBuilder source = new StringBuilder(500);

    ResultSet rs = null;
    Savepoint sp = null;
    Statement stmt = null;

    try
    {
      if (useSavepoint)
      {
        sp = this.connection.setSavepoint();
      }
      stmt = connection.createStatementForQuery();
      rs = stmt.executeQuery(sql);

      boolean hasRow = rs.next();
      if (hasRow)
      {
        appendSource(rs, source, schemaForSource, name, hasCost);
      }
      connection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      source = new StringBuilder(ExceptionUtil.getDisplay(e));
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "procedure source", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    def.setSource(source);
  }

  private void appendSource(ResultSet rs, StringBuilder source, String schemaForSource, PGProcName name, boolean hasCost)
    throws SQLException
  {
    String procType = rs.getString("proc_type");
    String comment = rs.getString("remarks");
    String schema = rs.getString("schema_name");
    boolean isSQLBody = rs.getBoolean("is_sql_function");
    String extname = rs.getString("extname");

    String src = rs.getString("prosrc");
    if (rs.wasNull() || src == null) src = "";

    boolean isAggregate = "aggregate".equals(procType);
    boolean isFunction = "function".equals(procType);

    QuoteHandler quoter = connection.getMetadata();

    if (isSQLBody)
    {
      source.append(StringUtil.trim(src));
      source.append("\n;");
    }
    else if (isAggregate)
    {
      source.append(getAggregateSource(name, schema));
    }
    else
    {
      source.append("CREATE OR REPLACE " + procType.toUpperCase() + " ");
      source.append(quoter.quoteObjectname(StringUtil.coalesce(schemaForSource, schema)));
      source.append('.');
      source.append(quoter.quoteObjectname(name.getName()));

      String lang = rs.getString("lang_name");
      String returnType = rs.getString("return_type");
      String readableReturnType = rs.getString("formatted_return_type");

      String types = rs.getString("argtypes");
      String names = rs.getString("argnames");
      String modes = rs.getString("argmodes");
      String config = rs.getString("proconfig");
      String parallel = rs.getString("proparallel");
      boolean returnSet = rs.getBoolean("proretset");
      boolean leakproof = rs.getBoolean("proleakproof");

      boolean securityDefiner = rs.getBoolean("prosecdef");
      boolean strict = rs.getBoolean("proisstrict");
      String volat = rs.getString("provolatile");
      Double cost = null;
      Double rows = null;
      if (hasCost)
      {
        cost = rs.getDouble("procost");
        rows = rs.getDouble("prorows");
      }
      CharSequence parameters = rs.getString("formatted_parameters");
      if (parameters == null)
      {
        parameters = buildParameterList(names, types, modes);
      }

      source.append('(');
      source.append(parameters);

      source.append(")");
      if (procType.equalsIgnoreCase("function"))
      {
        source.append("\n  RETURNS ");
        if (readableReturnType == null)
        {
          if (returnSet)
          {
            source.append("SETOF ");
          }
          source.append(returnType);
        }
        else
        {
          source.append(readableReturnType);
        }
      }
      source.append("\n  LANGUAGE ");
      source.append(lang);
      source.append("\nAS\n$body$\n");
      if (trimSourceLanguages.contains(lang))
      {
        src = src.trim();
      }
      source.append(StringUtil.makePlainLinefeed(src));
      if (!src.endsWith(";")) source.append(';');
      source.append("\n$body$\n");

      if (StringUtil.isNotBlank(config))
      {
        source.append("  SET ");
        source.append(config);
        source.append('\n');
      }

      if (isFunction)
      {
        switch (volat)
        {
          case "i":
            source.append("  IMMUTABLE");
            break;
          case "s":
            source.append("  STABLE");
            break;
          default:
            source.append("  VOLATILE");
            break;
        }

        if (strict)
        {
          source.append("\n  STRICT");
        }

        if (leakproof)
        {
          source.append("\n  LEAKPROOF");
        }

        if (cost != null)
        {
          source.append("\n  COST ");
          source.append(cost.longValue());
        }

        if (rows != null && returnSet)
        {
          source.append("\n  ROWS ");
          source.append(rows.longValue());
        }
      }

      if (isFunction)
      {
        if (nonDefaultParallel(parallel))
        {
          source.append("\n  PARALLEL " + codeToParallelType(parallel));
        }
      }

      if (securityDefiner)
      {
        source.append("\n SECURITY DEFINER");
      }
      source.append(";\n");
    }

    if (StringUtil.isNotBlank(comment))
    {
      source.append("\nCOMMENT ON " + procType.toUpperCase() + " ");
      source.append(quoter.quoteObjectname(StringUtil.coalesce(schemaForSource, schema)));
      source.append('.');
      source.append(quoter.quoteObjectname(name.getName()));
      source.append(name.getSignature());
      source.append(" IS '");
      source.append(SqlUtil.escapeQuotes(comment));
      source.append("';\n\n");
    }

    if (StringUtil.isNotBlank(extname))
    {
      source.append("\n-- Created through extension: " + extname + "\n");
    }
  }

  private CharSequence buildParameterList(String names, String types, String modes)
  {
    ArgInfo info = new ArgInfo(names, types, modes);
    StringBuilder result = new StringBuilder(info.getNumArgs() * 10);

    result.append('(');
    for (int i=0; i < info.getNumArgs(); i++)
    {
      String mode = info.getArgMode(i);
      if ("t".equals(mode)) continue;

      if (i > 0) result.append(", ");

      result.append(info.getArgName(i));
      result.append(' ');
      result.append(info.getArgType(i));
    }
    return result;
  }

  /**
   * Read the definition of a function using pg_get_functiondef()
   *
   * @param def
   */
  protected void readFunctionDef(ProcedureDefinition def)
  {
    PGProcName name = new PGProcName(def);
    String funcname = def.getSchema() + "." + name.getName() + name.getSignature();
    String sql;

    if (JdbcUtils.hasMinimumServerVersion(connection, "9.1"))
    {
      sql =
        "SELECT pg_get_functiondef(p.oid) as source, ext.extname as extension_name \n" +
        "from pg_proc p \n" +
        "  left join pg_depend d ON d.objid = p.oid AND d.deptype = 'e' \n" +
        "  left join pg_extension ext on ext.oid = d.refobjid \n" +
        "where p.oid = '" + funcname + "'::regprocedure";
    }
    else
    {
      sql = "select pg_get_functiondef('" + funcname + "'::regprocedure) as source, null::text as extension_name";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "function definition", sql);

    StringBuilder source = null;
    ResultSet rs = null;
    Savepoint sp = null;
    Statement stmt = null;
    try
    {
      if (useSavepoint)
      {
        sp = this.connection.setSavepoint();
      }
      stmt = connection.createStatementForQuery();
      rs = stmt.executeQuery(sql);
      if (rs.next())
      {
        String src = rs.getString(1);
        String extension = rs.getString(2);
        if (StringUtil.isNotBlank(src))
        {
          source = new StringBuilder(src.length() + 50);
          source.append(src);
          if (!src.endsWith("\n"))  source.append('\n');

          source.append(";\n");

          if (StringUtil.isNotBlank(def.getComment()))
          {
            source.append("\nCOMMENT ON FUNCTION ");
            source.append(name.getFormattedName());
            source.append(" IS '");
            source.append(SqlUtil.escapeQuotes(def.getComment()));
            source.append("'\n;\n" );
          }
        }
        if (StringUtil.isNotBlank(extension))
        {
          source.append("\n-- Created through extension: " + extension + "\n");
        }
      }
    }
    catch (SQLException e)
    {
      source = new StringBuilder(ExceptionUtil.getDisplay(e));
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "function definition", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    def.setSource(source);
  }

  protected StringBuilder getAggregateSource(PGProcName name, String schema)
  {
    String baseSelect = "SELECT a.aggtransfn, a.aggfinalfn, format_type(a.aggtranstype, null) as stype, a.agginitval, op.oprname ";
    String from =
       " FROM pg_proc p \n" +
       "  JOIN pg_namespace n ON p.pronamespace = n.oid \n" +
       "  JOIN pg_aggregate a ON a.aggfnoid = p.oid \n" +
       "  LEFT JOIN pg_operator op ON op.oid = a.aggsortop ";

    boolean hasSort = JdbcUtils.hasMinimumServerVersion(connection, "8.1");
    if (hasSort)
    {
      baseSelect += ", a.aggsortop ";
    }

    boolean hasParallel = JdbcUtils.hasMinimumServerVersion(connection, "9.6");
    baseSelect += ", " + (hasParallel ? "p.proparallel" : "null as proparallel");

    String sql = baseSelect + "\n" + from;
    sql += " WHERE p.proname = '" + name.getName() + "' ";
    if (StringUtil.isNotBlank(schema))
    {
      sql += " and n.nspname = '" + schema + "' ";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "aggregate source", sql);
    StringBuilder source = new StringBuilder();
    ResultSet rs = null;
    Statement stmt = null;
    Savepoint sp = null;

    try
    {
      if (useSavepoint)
      {
        sp = this.connection.setSavepoint();
      }
      stmt = connection.createStatementForQuery();
      rs = stmt.executeQuery(sql);
      if (rs.next())
      {

        source.append("CREATE AGGREGATE ");
        source.append(name.getFormattedName());
        source.append("\n(\n");
        String sfunc = rs.getString("aggtransfn");
        source.append("  sfunc = ");
        source.append(sfunc);

        String stype = rs.getString("stype");
        source.append(",\n  stype = ");
        source.append(stype);

        String sortop = rs.getString("oprname");
        if (StringUtil.isNotBlank(sortop))
        {
          source.append(",\n  sortop = ");
          source.append(connection.getMetadata().quoteObjectname(sortop));
        }

        String finalfunc = rs.getString("aggfinalfn");
        if (StringUtil.isNotBlank(finalfunc) && !finalfunc.equals("-"))
        {
          source.append(",\n  finalfunc = ");
          source.append( finalfunc);
        }

        String initcond = rs.getString("agginitval");
        if (StringUtil.isNotBlank(initcond))
        {
          source.append(",\n  initcond = '");
          source.append(initcond);
          source.append('\'');
        }

        String parallel = rs.getString("proparallel");
        if (nonDefaultParallel(parallel))
        {
          source.append(",\n  parallel = ");
          source.append(codeToParallelType(parallel).toLowerCase());
        }
        source.append("\n);\n");
      }
      connection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      source = null;
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "aggregate source", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return source;

  }

  private boolean nonDefaultParallel(String parallel)
  {
    if (parallel == null) return false;
    return !parallel.equals("u");
  }

  private String codeToParallelType(String code)
  {
    switch (code)
    {
      case "s":
        return "SAFE";
      case "r":
        return "RESTRICTED";
      case "u":
        return "UNSAFE";
    }
    return code;
  }

  /**
   * A workaround for pre 8.3 drivers so that argument names are retrieved properly
   * from the database. This was mainly inspired by the source code of pgAdmin III
   * and the 8.3 driver sources
   *
   * @param catalog
   * @param schema
   * @param procname
   * @return a DataStore with the argumens of the procedure
   * @throws java.sql.SQLException
   */
  private DataStore getColumns(String catalog, String schema, PGProcName procname)
    throws SQLException
  {
    String sql =
        "SELECT format_type(p.prorettype, NULL) as formatted_type, \n" +
        "       t.typname as pg_type, \n" +
        "       " + argTypesExp + ", \n" +
        "       array_to_string(p.proargnames, ';') as argnames, \n" +
        "       array_to_string(p.proargmodes, ';') as argmodes, \n" +
        "       p.proretset, \n" +
        "       t.typtype \n" +
        "FROM pg_catalog.pg_proc p \n" +
        "   JOIN pg_catalog.pg_namespace n ON p.pronamespace = n.oid \n" +
        "   JOIN pg_catalog.pg_type t ON p.prorettype = t.oid \n" +
        "WHERE n.nspname = ? \n" +
        "  AND p.proname = ? \n";

    DataStore result = createProcColsDataStore();

    Savepoint sp = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;

    String oids = procname.getInputOIDsAsVector();

    if (StringUtil.isNotBlank(oids))
    {
      sql += " AND p.proargtypes = " + oids + " \n ";
    }
    else if (procname.getFormattedName().contains("("))
    {
      // only restrict the arguments if the procedure name contained them as well.
      sql += " AND (p.proargtypes IS NULL OR array_length(p.proargtypes,1) = 0)";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "procedure columns", sql, schema, procname.getName());

    try
    {
      sp = connection.setSavepoint();

      stmt = this.connection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, schema);
      stmt.setString(2, procname.getName());


      rs = stmt.executeQuery();
      if (rs.next())
      {
        String typeName = rs.getString("formatted_type");
        String pgType = rs.getString("pg_type");
        String types = rs.getString("argtypes");
        String names = rs.getString("argnames");
        String modes = rs.getString("argmodes");
        String returnTypeType = rs.getString("typtype");
        boolean isTableFunc = rs.getBoolean("proretset");

        boolean isFunction = (returnTypeType.equals("b") || returnTypeType.equals("d") || (returnTypeType.equals("p") && modes == null));

        if (isFunction & !isTableFunc)
        {
          int row = result.addRow();
          result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NAME, "returnValue");
          result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE, "RETURN");
          result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_JDBC_DATA_TYPE, getJavaType(pgType));
          result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_DATA_TYPE, StringUtil.trimQuotes(typeName));
        }

        ArgInfo info = new ArgInfo(names, types, modes);

        for (int i=0; i < info.getNumArgs(); i++)
        {
          // Don't add the output columns of a table function as parameters
          if (isTableFunc && "t".equals(info.getArgType(i))) continue;

          int row = result.addRow();
          result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE,info.getJDBCArgMode(i));
          result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_JDBC_DATA_TYPE, getJavaType(info.getArgType(i)));
          result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_DATA_TYPE, info.getArgType(i));
          result.setValue(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NAME, info.getArgName(i));
        }
      }
      else
      {
        LogMgr.logWarning(new CallerInfo(){}, "No columns returned for procedure: " + procname.getName(), null);
        return super.getProcedureColumns(catalog, schema, procname.getName(), null);
      }

      connection.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "procedure columns", sql, schema, procname.getName());
      return super.getProcedureColumns(catalog, schema, procname.getName(), null);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  private List<ColumnIdentifier> convertToColumns(ArgInfo info)
  {
    List<ColumnIdentifier> result = new ArrayList<>();
    for (int i=0; i < info.getNumArgs(); i++)
    {
      String type = info.getArgType(i);
      String name = info.getArgName(i);
      String mode = info.getJDBCArgMode(i);
      ColumnIdentifier col = new ColumnIdentifier(name);
      col.setDataType(getJavaType(type));
      col.setDbmsType(type);
      col.setArgumentMode(mode);
      result.add(col);
    }
    return result;
  }

  private Map<String, Integer> createJavaTypeMap()
  {
    // This mapping has been copied from the JDBC driver.
    // This map is a private attribute of the class org.postgresql.jdbc2.TypeInfoCache
    // so, even if I hardcoded references to the Postgres driver I wouldn't be able
    // to use the information.
    HashMap<String, Integer> typeMap = new HashMap<>();
    typeMap.put("int2", Integer.valueOf(Types.SMALLINT));
    typeMap.put("int4", Integer.valueOf(Types.INTEGER));
    typeMap.put("integer", Integer.valueOf(Types.INTEGER));
    typeMap.put("oid", Integer.valueOf(Types.BIGINT));
    typeMap.put("int8", Integer.valueOf(Types.BIGINT));
    typeMap.put("money", Integer.valueOf(Types.DOUBLE));
    typeMap.put("numeric", Integer.valueOf(Types.NUMERIC));
    typeMap.put("float4", Integer.valueOf(Types.REAL));
    typeMap.put("float8", Integer.valueOf(Types.DOUBLE));
    typeMap.put("char", Integer.valueOf(Types.CHAR));
    typeMap.put("bpchar", Integer.valueOf(Types.CHAR));
    typeMap.put("varchar", Integer.valueOf(Types.VARCHAR));
    typeMap.put("character varying", Integer.valueOf(Types.VARCHAR));
    typeMap.put("text", Integer.valueOf(Types.VARCHAR));
    typeMap.put("name", Integer.valueOf(Types.VARCHAR));
    typeMap.put("bytea", Integer.valueOf(Types.BINARY));
    typeMap.put("bool", Integer.valueOf(Types.BIT));
    typeMap.put("bit", Integer.valueOf(Types.BIT));
    typeMap.put("date", Integer.valueOf(Types.DATE));
    typeMap.put("time", Integer.valueOf(Types.TIME));
    typeMap.put("timetz", Integer.valueOf(Types.TIME));
    typeMap.put("timestamp", Integer.valueOf(Types.TIMESTAMP));
    typeMap.put("timestamptz", Integer.valueOf(Types.TIMESTAMP));
    return Collections.unmodifiableMap(typeMap);
  }

}
