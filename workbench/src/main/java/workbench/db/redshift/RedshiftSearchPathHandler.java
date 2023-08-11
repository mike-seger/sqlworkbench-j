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
package workbench.db.redshift;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbSearchPath;
import workbench.db.WbConnection;

import workbench.db.JdbcUtils;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class RedshiftSearchPathHandler
  implements DbSearchPath
{
  @Override
  public boolean isRealSearchPath()
  {
    return true;
  }

  @Override
  public List<String> getSearchPath(WbConnection con, String defaultSchema)
  {
    if (con == null) return Collections.emptyList();

    if (defaultSchema != null)
    {
      return Collections.singletonList(con.getMetadata().adjustSchemaNameCase(defaultSchema));
    }

    List<String> result = new ArrayList<>();

    ResultSet rs = null;
    Statement stmt = null;
    Savepoint sp = null;

    String query = con.getDbSettings().getProperty("retrieve.search_path", "select current_schemas(true)::text");
    LogMgr.logMetadataSql(new CallerInfo(){}, "search path", query);

    try
    {
      if (con.getDbSettings().useSavePointForDML())
      {
        sp = con.setSavepoint();
      }
      stmt = con.createStatementForQuery();
      rs = stmt.executeQuery(query);
      if (rs.next())
      {
        String schemas = rs.getString(1);
        if (schemas.length() > 1)
        {
          // remove the curly braces at the start and beginning
          schemas = schemas.substring(1, schemas.length() - 1);
        }
        result.addAll(StringUtil.stringToList(schemas, ",", true, true, false, false));
      }
      con.releaseSavepoint(sp);
    }
    catch (SQLException ex)
    {
      con.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "search path", query);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (result.isEmpty())
    {
      LogMgr.logWarning(new CallerInfo(){}, "Using public as the default search path");
      // Fallback. At least look in the public schema
      result.add("public");
    }
    return result;
  }
}

