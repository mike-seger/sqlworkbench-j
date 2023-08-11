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
package workbench.db.oracle;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.JdbcProcedureReader;
import workbench.db.JdbcUtils;
import workbench.db.NoConfigException;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.RoutineType;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.sql.DelimiterDefinition;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A ProcedureReader to read the source of an Oracle procedure.
 * Packages are handled properly. The Oracle JDBC driver
 * reports the package name in the catalog column
 * of the getProcedures() ResultSet.
 * The method {@link #readProcedureSource(ProcedureDefinition)} the
 * catalog definition of the ProcedureDefinition is checked. If it's not
 * null it is assumed that this the definition is actually a package.
 *
 * @see workbench.db.JdbcProcedureReader
 * @author Thomas Kellerer
 */
public class OracleProcedureReader
  extends JdbcProcedureReader
{
  public static final int COLUMN_IDX_PROC_LIST_ORA_STATUS = 5;

  private final OracleTypeReader typeReader = new OracleTypeReader();
  private final StringBuilder procHeader = new StringBuilder("CREATE OR REPLACE ");

  // ALL_PROCEDURES does not return invalid procedures
  // so an outer join against ALL_OBJECTS is necessary
  private final String standardProcSQL =
      "  select null as package_name,   \n" +
      "         ao.owner as procedure_owner,   \n" +
      "         ao.object_name as procedure_name,  \n" +
      "         null as overload_index,  \n" +
      "         null as remarks,  \n" +
      "         decode(ao.object_type, 'PROCEDURE', 1, 'FUNCTION', 2, 0) as PROCEDURE_TYPE,  \n" +
      "         ao.status, \n" +
      "         ap.pipelined \n " +
      "  from all_objects ao  \n" +
      "    left join all_procedures ap on ao.object_name = ap.object_name and ao.owner = ap.owner   \n" +
      "  where ao.object_type in ('PROCEDURE', 'FUNCTION') ";

  public OracleProcedureReader(WbConnection conn)
  {
    super(conn);
  }

  @Override
  public StringBuilder getProcedureHeader(ProcedureDefinition def)
  {
    return procHeader;
  }

  public boolean packageExists(String owner, String packageName)
  {
    boolean useUserSpecificCatalogs = OracleUtils.useUserSpecificCatalogs(connection, owner);
    String sql;

    if (useUserSpecificCatalogs)
    {
      sql =
        "-- SQL Workbench/J \n" +
        "SELECT count(*) \n" +
        "FROM user_objects \n" +
        "WHERE object_name = ? \n" +
        "  AND object_type = 'PACKAGE'";
    }
    else
    {
      sql =
        "-- SQL Workbench/J \n" +
        "SELECT count(*) \n" +
        "FROM all_objects \n" +
        "WHERE object_name = ? \n" +
        "  AND object_type = 'PACKAGE' \n" +
        "  AND owner = ?";
    }
    PreparedStatement stmt = null;
    ResultSet rs = null;

    LogMgr.logMetadataSql(new CallerInfo(){}, "package existence", sql, packageName, owner);

    int count = 0;
    try
    {
      synchronized (connection)
      {
        stmt = this.connection.getSqlConnection().prepareStatement(sql);
        stmt.setString(1, packageName);
        if (!useUserSpecificCatalogs) stmt.setString(2, owner);
        rs = stmt.executeQuery();
        if (rs.next())
        {
          count = rs.getInt(1);
        }
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "package existence", sql, packageName, owner);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return count > 0;
  }

  @Override
  public CharSequence getPackageSource(String catalog, String owner, String packageName)
  {
    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.procedure))
    {
      try
      {
        ProcedureDefinition def = ProcedureDefinition.createOracleDefinition(owner, null, packageName, DatabaseMetaData.procedureResultUnknown, null);
        return retrieveUsingDbmsMetadata(def);
      }
      catch (SQLException sql)
      {
        // already logged
      }
    }

    boolean useUserSpecificCatalogs = OracleUtils.useUserSpecificCatalogs(connection, owner);
    String sql;

    if (useUserSpecificCatalogs)
    {
      sql =
        "-- SQL Workbench/J \n" +
        "SELECT text \n" +
        "FROM user_source \n" +
        "WHERE name = ? \n" +
        "  AND type = ? \n" +
        "ORDER BY line";
    }
    else
    {
      sql =
        "-- SQL Workbench/J \n" +
        "SELECT text \n" +
        "FROM all_source \n" +
        "WHERE name = ? \n" +
        "  AND type = ? \n" +
        "  AND owner = ? \n" +
        "ORDER BY line";
    }

    StringBuilder result = new StringBuilder(1000);
    PreparedStatement stmt = null;
    ResultSet rs = null;

    DelimiterDefinition alternateDelimiter = this.connection.getAlternateDelimiter();

    if (alternateDelimiter == null)
    {
      alternateDelimiter = DelimiterDefinition.DEFAULT_ORA_DELIMITER;
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "package source", sql, packageName, owner);

    try
    {
      int lineCount = 0;

      synchronized (connection)
      {
        stmt = OracleUtils.prepareQuery(connection, sql);
        stmt.setString(1, packageName);
        stmt.setString(2, "PACKAGE");
        if (!useUserSpecificCatalogs) stmt.setString(3, owner);
        rs = stmt.executeQuery();
        while (rs.next())
        {
          String line = rs.getString(1);
          if (line != null)
          {
            lineCount ++;
            if (lineCount == 1)
            {
              result.append("CREATE OR REPLACE ");
            }
            result.append(StringUtil.makePlainLinefeed(line));
          }
        }
        if (lineCount > 0)
        {
          result.append('\n');
          result.append(alternateDelimiter.getDelimiter());
          result.append('\n');
          result.append('\n');
        }
        lineCount = 0;

        stmt.clearParameters();
        stmt.setString(1, packageName);
        stmt.setString(2, "PACKAGE BODY");
        if (!useUserSpecificCatalogs) stmt.setString(3, owner);
        rs = stmt.executeQuery();
        while (rs.next())
        {
          String line = rs.getString(1);
          if (line != null)
          {
            lineCount ++;
            if (lineCount == 1)
            {
              result.append("CREATE OR REPLACE ");
            }
            result.append(StringUtil.makePlainLinefeed(line));
          }
        }
      }
      result.append('\n');
      if (lineCount > 0)
      {
        result.append(alternateDelimiter.getDelimiter());
        result.append('\n');
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "package source", sql, packageName, owner);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public DataStore buildProcedureListDataStore(DbMetadata meta, boolean addSpecificName)
  {
    if (useCustomSql())
    {
      String[] cols = new String[] {"PROCEDURE_NAME", "TYPE", "PACKAGE", "SCHEMA", "REMARKS", "STATUS"};
      int[] types = new int[] {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR};
      int[] sizes = new int[] {30,12,10,10,20,20};
      DataStore ds = new DataStore(cols, types, sizes);
      return ds;
    }
    else
    {
      DataStore ds = super.buildProcedureListDataStore(meta, addSpecificName);
      ds.getResultInfo().getColumn(COLUMN_IDX_PROC_LIST_CATALOG).setColumnName("PACKAGE");
      return ds;
    }
  }

  public ProcedureDefinition resolveSynonym(String catalog, String schema, String procname)
    throws SQLException
  {
    TableIdentifier tbl = connection.getMetadata().getSynonymTable(new TableIdentifier(procname));
    if (tbl == null && catalog != null)
    {
      // maybe a public synonym on the package?
      tbl = connection.getMetadata().getSynonymTable(new TableIdentifier(catalog));
    }
    if (tbl != null)
    {
      schema = tbl.getSchema();
      if (catalog != null)
      {
        // This is a synonym for a package, in this case the "tablename" is the actual package name
        catalog = tbl.getTableName();
      }
      return ProcedureDefinition.createOracleDefinition(schema, procname, catalog, 0, null);
    }
    return null;
  }

  @Override
  public DataStore getProcedureColumns(ProcedureDefinition def)
    throws SQLException
  {
    String overload = def.getOracleOverloadIndex();
    DataStore result = createProcColsDataStore();
    ResultSet rs = null;

    try
    {
      String escape = connection.getSearchStringEscape();

      String catalog = def.getCatalog();
      String schema = def.getSchema();
      String name = def.getProcedureName();

      // we never want data for multiple procedures here
      // so escape any wildcard in the parameters
      // Note that the catalog parameter
      schema  = SqlUtil.escapeUnderscore(schema, escape);
      name = SqlUtil.escapeUnderscore(name, escape);

      rs = this.connection.getSqlConnection().getMetaData().getProcedureColumns(catalog, schema, name, "%");

      int overloadIndex = JdbcUtils.getColumnIndex(rs, "OVERLOAD");

      while (rs.next())
      {
        if (overload != null && overloadIndex > 0)
        {
          String toTest = rs.getString(overloadIndex);
          if (!StringUtil.equalString(toTest, overload)) continue;
        }
        String colCatalog = rs.getString("PROCEDURE_CAT");
        if (StringUtil.equalString(catalog, colCatalog))
        {
          processProcedureColumnResultRow(result, rs);
        }
      }
    }
    finally
    {
      JdbcUtils.closeResult(rs);
    }

    // Remove the implicit parameter for Object type functions that passes
    // the instance of that object to the function
    for (int row = result.getRowCount() - 1; row >= 0; row --)
    {
      String colname = result.getValueAsString(row, COLUMN_IDX_PROC_COLUMNS_COL_NAME);
      int type = result.getValueAsInt(row, COLUMN_IDX_PROC_COLUMNS_JDBC_DATA_TYPE, Types.OTHER);
      if ("SELF".equals(colname) && type == Types.OTHER)
      {
        result.deleteRow(row);
      }
    }
    return result;
  }

  private boolean useCustomSql()
  {
    if (connection == null) return false;
    return JdbcUtils.hasMinimumServerVersion(connection, "9.0") && Settings.getInstance().getBoolProperty("workbench.db.oracle.procedures.custom_sql", true);
  }

  private DataStore getProceduresFromJdbc(String catalog, String schema, String name)
    throws SQLException
  {
    DataStore result = super.getProcedures(catalog, schema, name);
    int count = result.getRowCount();

    // in order to display package source correctly, each row must have a proper ProcedureDefinition as the user object
    for (int row = 0; row < count; row ++)
    {
      String procName = result.getValueAsString(row, ProcedureReader.COLUMN_IDX_PROC_LIST_NAME);
      String procSchema = result.getValueAsString(row, ProcedureReader.COLUMN_IDX_PROC_LIST_SCHEMA);
      String packageName = result.getValueAsString(row, ProcedureReader.COLUMN_IDX_PROC_LIST_CATALOG);
      String remark = result.getValueAsString(row, ProcedureReader.COLUMN_IDX_PROC_LIST_REMARKS);
      int type = result.getValueAsInt(row, ProcedureReader.COLUMN_IDX_PROC_LIST_TYPE, DatabaseMetaData.procedureNoResult);
      ProcedureDefinition def = ProcedureDefinition.createOracleDefinition(procSchema, procName, packageName, type, remark);
      result.getRow(row).setUserObject(def);
    }
    return result;
  }

  private String getNameCondition(String name)
  {
    if (StringUtil.isEmpty(name)) return "";
    if (name.contains("_") || name.contains("%"))
    {
      return "LIKE '" + name + "' ";
    }
    return "= '" + name + "'";
  }

  private String getUserSpecificProcSQL()
  {
    String sql = standardProcSQL.replace("all_objects ao", "user_objects ao");
      sql = sql.replace("ao.owner as procedure_owner", "user as procedure_owner");
      sql = sql.replace(
        "join all_procedures ap on ao.object_name = ap.object_name and ao.owner = ap.owner",
        "join user_procedures ap on ao.object_name = ap.object_name");
    return sql;
  }


  @Override
  public DataStore getProcedures(String pkgName, String schema, String name)
    throws SQLException
  {
    if (!useCustomSql())
    {
      return getProceduresFromJdbc(pkgName, schema, name);
    }

    schema = DbMetadata.cleanupWildcards(schema);
    name = DbMetadata.cleanupWildcards(name);

    schema = connection.getMetadata().adjustObjectnameCase(schema);
    pkgName = connection.getMetadata().adjustObjectnameCase(pkgName);
    name = connection.getMetadata().adjustObjectnameCase(name);

    // ALL_PROCEDURES does not return invalid procedures
    // so an outer join against ALL_OBJECTS is necessary
    String standardProcs = standardProcSQL;

    boolean userSpecificCatalogs = OracleUtils.useUserSpecificCatalogs(connection, schema);
    if (userSpecificCatalogs)
    {
      standardProcs = getUserSpecificProcSQL();
    }
    else if (StringUtil.isNotBlank(schema))
    {
      standardProcs += "\n    and ao.owner = '" + schema + "' ";
    }

    if (StringUtil.isNotBlank(name))
    {
      standardProcs += "\n    and ao.object_name " + getNameCondition(name);
    }

    String pkgProcs =
      "  select package_name, procedure_owner, procedure_name, overload_index, remarks, procedure_type, status, pipelined \n" +
      "  from ( \n" +
      "    select ap.object_name as package_name, \n" +
      (userSpecificCatalogs ?
      "           user as procedure_owner, \n" :
      "           ap.owner as procedure_owner, \n") +
      "           ap.procedure_name, \n" +
      "           ap.overload as overload_index, \n" +
      "           decode(ao.object_type, 'TYPE', 'OBJECT TYPE', ao.object_type) as remarks, \n" +
      "           decode(aa.anz, 1, " + DatabaseMetaData.procedureReturnsResult + ", " + DatabaseMetaData.procedureNoResult + " ) as procedure_type, \n" +
      "           ao.status,  \n" +
      "           ap.pipelined, \n" +
      (userSpecificCatalogs ?
      "           row_number() over (partition by ap.object_name, ap.procedure_name, ap.overload order by ao.object_type desc) as rn \n" :
      "           row_number() over (partition by ap.owner, ap.object_name, ap.procedure_name, ap.overload order by ao.object_type desc) as rn \n") +
      (userSpecificCatalogs ?
      "    from user_procedures ap \n" :
      "    from all_procedures ap \n") +
      (userSpecificCatalogs ?
      "      join user_objects ao on ap.object_name = ao.object_name \n" :
      "      join all_objects ao on ap.object_name = ao.object_name and ap.owner = ao.owner \n") +
      "      left join (\n" +
      "        select owner, package_name, object_name, overload, count(*) as anz \n" +
      "        from all_arguments \n" +
      "        where in_out = 'OUT' \n" +
      "          and argument_name is null \n" +
      "        group by owner, package_name, object_name, overload \n" +
      "      ) aa on aa.package_name = ap.object_name \n" +
      "          and aa.object_name = ap.procedure_name \n" +
      "          and coalesce(aa.overload,'none') = coalesce(ap.overload, 'none')  \n" +
      (userSpecificCatalogs ? "" :
      "          and aa.owner = ap.owner \n") +
      "    where ao.object_type IN ('PACKAGE BODY', 'PACKAGE', 'TYPE', 'OBJECT TYPE') \n" +
      "      and ap.procedure_name is not null \n" +
      "      and ap.object_name    is not null \n" +
      "  )\n" +
      "  where rn = 1";

    if (StringUtil.isNotBlank(schema) && !userSpecificCatalogs)
    {
      pkgProcs += "\n    and procedure_owner = '" + schema + "' ";
    }

    if (StringUtil.isNotBlank(name))
    {
      pkgProcs += "\n    and procedure_name " + getNameCondition(name);
    }

    if (StringUtil.isNotBlank(pkgName))
    {
      pkgProcs += "\n    and package_name = '" + pkgName + "' ";
    }

    String sql =
      "-- SQL Workbench/J \n" +
      "select " + OracleUtils.getCacheHint() + "* \n" +
      "from (\n";

    if (StringUtil.isBlank(pkgName))
    {
      sql += standardProcs + "\n  UNION ALL \n" + pkgProcs;
    }
    else
    {
      sql += pkgProcs;
    }

    sql +=
      "\n)\n" +
      "ORDER BY 2,3,4";

    LogMgr.logMetadataSql(new CallerInfo(){}, "procedures", sql);

    long start = System.currentTimeMillis();
    Statement stmt = null;
    ResultSet rs = null;
    DataStore ds = buildProcedureListDataStore(this.connection.getMetadata(), false);
    try
    {
      stmt = OracleUtils.createStatement(connection);
      rs = stmt.executeQuery(sql);

      while (rs.next())
      {
        String packageName = rs.getString("PACKAGE_NAME");
        String owner = rs.getString("PROCEDURE_OWNER");
        String procedureName = rs.getString("PROCEDURE_NAME");
        String remark = rs.getString("REMARKS");
        String overloadIndicator = rs.getString("OVERLOAD_INDEX");
        String pipelined = rs.getString("PIPELINED");

        int type = rs.getInt("PROCEDURE_TYPE");
        Integer iType;
        if (rs.wasNull() || type == DatabaseMetaData.procedureResultUnknown)
        {
          // we can't really handle procedureResultUnknown, so it is treated as "no result"
          iType = Integer.valueOf(DatabaseMetaData.procedureNoResult);
        }
        else
        {
          iType = Integer.valueOf(type);
        }
        String status = rs.getString("STATUS");
        ProcedureDefinition def = ProcedureDefinition.createOracleDefinition(owner, procedureName, packageName, type, remark);
        def.setOracleOverloadIndex(overloadIndicator);
        if ("YES".equals(pipelined))
        {
          def.setRoutineType(RoutineType.tableFunction);
        }
        int row = ds.addRow();
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_CATALOG, packageName);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_SCHEMA, owner);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_NAME, procedureName);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_TYPE, iType);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_REMARKS, remark);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_REMARKS + 1, status);
        ds.getRow(row).setUserObject(def);
      }
      ds.resetStatus();
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "procedures", sql);
      // assume the SQL statement does not work with the Oracle version in use and disable the custom SQL for now
      System.setProperty("workbench.db.oracle.procedures.custom_sql", "false");
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Retrieving procedures took: " + duration + "ms");
    return ds;
  }

  /**
   * Returns table functions defined in the database.
   *
   * @param catalog   the catalog (not used)
   * @param schema    the schema (user)
   * @param name      the name pattern to look for
   * @return
   * @throws SQLException
   */
  @Override
  public List<ProcedureDefinition> getTableFunctions(String catalog, String schema, String name)
    throws SQLException
  {
    schema = DbMetadata.cleanupWildcards(schema);
    name = DbMetadata.cleanupWildcards(name);

    schema = connection.getMetadata().adjustObjectnameCase(schema);
    name = connection.getMetadata().adjustObjectnameCase(name);

    boolean useUserSpecificCatalogs = OracleUtils.useUserSpecificCatalogs(connection, schema);
    String query = useUserSpecificCatalogs ? getUserSpecificProcSQL() : standardProcSQL;

    query +=
      "\n    and (ap.pipelined = 'YES' \n" +
      "           OR ap.object_name IN (SELECT arg.object_name \n" +
      (useUserSpecificCatalogs ?
      "                                 FROM user_arguments arg\n" :
      "                                 FROM all_arguments arg\n") +
      "                                 WHERE arg.position = 0 \n" +
      "                                   AND arg.data_type = 'TABLE' \n" +
      "                                   $schema_condition$))";


    if (!useUserSpecificCatalogs && StringUtil.isNotBlank(schema))
    {
      query += "\n    and ao.owner = '" + schema + "' ";
      query = query.replace("$schema_condition$", "AND arg.owner = '" + schema + "' ");
    }
    else
    {
      query = query.replace("$schema_condition$", "");
    }

    if (StringUtil.isNotBlank(name))
    {
      query += "\n    and ao.object_name " + getNameCondition(name);
    }
    query += "\nORDER BY 2,3";

    List<ProcedureDefinition> result = new ArrayList<>();
    LogMgr.logMetadataSql(new CallerInfo(){}, "table functions", query);
    long start = System.currentTimeMillis();
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = OracleUtils.createStatement(connection);
      rs = stmt.executeQuery(query);
      while (rs.next())
      {
        String owner = rs.getString("PROCEDURE_OWNER");
        String procedureName = rs.getString("PROCEDURE_NAME");
        ProcedureDefinition def = new ProcedureDefinition(null, owner, procedureName, RoutineType.tableFunction, DatabaseMetaData.procedureReturnsResult);
        result.add(def);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "table functions", query);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Retrieving table functions took: " + duration + "ms");
    return result;
  }

  private CharSequence retrieveUsingDbmsMetadata(ProcedureDefinition def)
    throws SQLException
  {
    if (def == null) return null;

    if (def.isPackageProcedure())
    {
      return DbmsMetadata.getDDL(connection, "PACKAGE", def.getPackageName(), def.getSchema());
    }
    else
    {
      return DbmsMetadata.getDDL(connection, "PROCEDURE", def.getProcedureName(), def.getSchema());
    }
  }

  @Override
  protected CharSequence retrieveProcedureSource(ProcedureDefinition def)
    throws NoConfigException
  {
    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.procedure))
    {
      try
      {
        return retrieveUsingDbmsMetadata(def);
      }
      catch (SQLException ex)
      {
        // ignore, logging already done
      }
    }
    return super.retrieveProcedureSource(def);
  }

  @Override
  public void readProcedureSource(ProcedureDefinition def, String catalogForSource, String schemaForSource)
    throws NoConfigException
  {
    if (def.getPackageName() != null)
    {
      CharSequence source = getPackageSource(null, def.getSchema(), def.getPackageName());
      if (StringUtil.isBlank(source))
      {
        // Fallback if the ProcedureDefinition was not initialized correctly.
        // This will happen if our custom SQL was not used.
        OracleObjectType type = new OracleObjectType(def.getSchema(), def.getPackageName());
        source = typeReader.getObjectSource(connection, type);
      }
      def.setSource(source);
    }
    else if (def.isOracleObjectType())
    {
      OracleObjectType type = new OracleObjectType(def.getSchema(), def.getPackageName());
      CharSequence source = typeReader.getObjectSource(connection, type);
      def.setSource(source);
    }
    else
    {
      super.readProcedureSource(def, catalogForSource, schemaForSource);
    }
  }

  @Override
  public ProcedureDefinition findProcedureByName(DbObject toFind)
    throws SQLException
  {
    if (toFind == null) return null;

    String objSchema = toFind.getSchema();
    String objCat = toFind.getCatalog();

    if (objSchema != null && objCat != null)
    {
      // this is a fully qualified packaged procedure: scott.some_package.some_proc
      // we need to "swap" catalog and owner in order to properly find the procedure
      DataStore procs = getProcedures(objSchema, objCat, toFind.getObjectName());
      if (procs.getRowCount() == 0) return null;
      if (procs.getRowCount() > 0) return procs.getUserObject(0, ProcedureDefinition.class);
    }

    String user = connection.getMetadata().adjustObjectnameCase(connection.getCurrentUser());

    if (objSchema != null)
    {
      // this could be user.procedure or package.procedure
      // first we check for package.procedure for the current user:

      DataStore procs = getProcedures(objSchema, user, toFind.getObjectName());
      if (procs.getRowCount() > 0) return procs.getUserObject(0, ProcedureDefinition.class);

      // not a package procedure for the current user, check regular procedures assuming this is for a different user
      procs = getProcedures(null, objSchema, toFind.getObjectName());
      if (procs.getRowCount() > 0) return procs.getUserObject(0, ProcedureDefinition.class);
    }

    // no schema, no user specified, try the current user
    DataStore procs = getProcedures(null, user, toFind.getObjectName());
    if (procs.getRowCount() > 0) return procs.getUserObject(0, ProcedureDefinition.class);

    procs = getProcedures(null, null, toFind.getObjectName());
    if (procs.getRowCount() > 0) return procs.getUserObject(0, ProcedureDefinition.class);

    return null;
  }

  @Override
  public boolean supportsPackages()
  {
    return true;
  }

  public List<String> getParameterNames(ProcedureDefinition def)
  {
    try
    {
      DataStore procColumns = getProcedureColumns(def);
      if (procColumns == null) return Collections.emptyList();

      int rows = procColumns.getRowCount();
      List<String> names = new ArrayList<>(rows);
      for (int row = 0; row < rows; row ++)
      {
        String name = procColumns.getValueAsString(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NAME);
        if (name != null)
        {
          names.add(name);
        }
      }
      return names;
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read procedure parameter names", ex);
      return Collections.emptyList();
    }
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
        procSrc = OraclePackageParser.getProcedureSource(def.getSource(), def, getParameterNames(def));
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read procedure source", ex);
    }
    return procSrc;
  }
}
