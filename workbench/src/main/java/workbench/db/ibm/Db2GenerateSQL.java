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
package workbench.db.ibm;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DBID;
import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 * A wrapper class to call DB2's QSYS2.GENERATE_SQL procedure.
 *
 * See https://www.ibm.com/docs/en/i/7.4?topic=services-generate-sql-procedure
 *
 * @author Thomas Kellerer
 */
public class Db2GenerateSQL
{
  public static final String TYPE_PROCEDURE = "PROCEDURE";
  public static final String TYPE_FUNCTION = "FUNCTION";
  public static final String TYPE_VIEW = "VIEW";
  public static final String TYPE_TABLE = "TABLE";
  public static final String TYPE_INDEX = "INDEX";
  public static final String TYPE_TRIGGER = "TRIGGER";
  public static final String TYPE_VARIABLE = "VARIABLE";

  public static final String RECREATE_PROP = "generate_sql.use.replace";

  private final String GEN_SQL =
    "call QSYS2.GENERATE_SQL(DATABASE_OBJECT_LIBRARY_NAME => ?, " +
    "DATABASE_OBJECT_NAME => ?, " +
    "DATABASE_OBJECT_TYPE => ?, " +
    "CREATE_OR_REPLACE_OPTION  => ?, " +
    "STATEMENT_FORMATTING_OPTION => ?, " +
    "HEADER_OPTION => '0')";

  private WbConnection conn;
  private Boolean generateRecreate;

  public Db2GenerateSQL(WbConnection db)
  {
    this.conn = db;
  }

  public void setGenerateRecreate(boolean flag)
  {
    this.generateRecreate = flag;
  }

  public CharSequence getTableSource(String schema, String tableName)
  {
    return getObjectSource(schema, tableName, TYPE_TABLE);
  }

  public CharSequence getIndexSource(String schema, String indexName)
  {
    return getObjectSource(schema, indexName, TYPE_INDEX);
  }

  public CharSequence getViewSource(String schema, String viewName)
  {
    return getObjectSource(schema, viewName, TYPE_VIEW);
  }

  public CharSequence getProcedureSource(String schema, String procName)
  {
    return getObjectSource(schema, procName, TYPE_PROCEDURE);
  }

  public CharSequence getFunctionSource(String schema, String procName)
  {
    return getObjectSource(schema, procName, TYPE_FUNCTION);
  }

  public CharSequence getTriggerSource(String schema, String triggerName)
  {
    return getObjectSource(schema, triggerName, TYPE_TRIGGER);
  }

  public CharSequence getVariableSource(String schema, String varName)
  {
    return getObjectSource(schema, varName, TYPE_VARIABLE);
  }

  public CharSequence getObjectSource(String schema, String objectName, String objectType)
  {
    StringBuilder result = null;

    boolean generateReplace = getGenerateRecreate(objectType);
    boolean enableFormatting = getEnableFormatting(objectType);

    PreparedStatement stmt = null;
    ResultSet rs = null;
    String nl = Settings.getInstance().getInternalEditorLineEnding();
    String replaceParm = generateReplace ? "1" : "0";
    String formatParam = enableFormatting ? "1" : "0";

    try
    {
      stmt = conn.getSqlConnection().prepareStatement(GEN_SQL);
      stmt.setString(1, schema);
      stmt.setString(2, objectName);
      stmt.setString(3, objectType);
      stmt.setString(4, replaceParm);
      stmt.setString(5, formatParam);

      LogMgr.logMetadataSql(new CallerInfo(){}, "object source", GEN_SQL, schema, objectName, objectType, replaceParm, formatParam);

      stmt.execute();
      rs = stmt.getResultSet();
      if (rs == null)
      {
        LogMgr.logWarning(new CallerInfo(){}, "GENERATE_SQL did not return a result for schema: " + schema +
          ", name: " + objectName + ", type: " + objectType);
      }
      else
      {
        result = new StringBuilder(100);
        while (rs.next())
        {
          String line = StringUtil.removeTrailing(rs.getString(3), ' ');
          if (line != null)
          {
            result.append(line);
          }
          if (!enableFormatting && !StringUtil.hasLineFeed(line))
          {
            result.append(nl);
          }
        }
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "object source", GEN_SQL, schema, objectName, objectType, replaceParm);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  private boolean getGenerateRecreate(String type)
  {
    if (type == null) return false;
    if (conn == null) return false;
    if (generateRecreate != null) return generateRecreate;
    type = type.trim().toLowerCase();
    boolean defaultValue = conn.getDbSettings().getBoolProperty(RECREATE_PROP, true);
    return conn.getDbSettings().getBoolProperty(RECREATE_PROP + "." + type, defaultValue);
  }

  private boolean getEnableFormatting(String type)
  {
    if (type == null) return false;
    if (conn == null) return false;
    return conn.getDbSettings().getBoolProperty("source." + type.toLowerCase() + ".systemproc.enable.formatting", true);
  }

  public static boolean useGenerateSQLProc(WbConnection conn, String type)
  {
    if (conn == null) return false;
    if (type == null) return false;
    if (DBID.fromConnection(conn) != DBID.DB2_ISERIES) return false;
    if (!JdbcUtils.hasMinimumServerVersion(conn, "7.2")) return false;

    return conn.getDbSettings().getBoolProperty("source." + type.toLowerCase() + ".use.systemproc", false);
  }
}
