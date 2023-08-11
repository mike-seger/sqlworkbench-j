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
package workbench.sql.wbcommands;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbObject;
import workbench.db.ProcedureDefinition;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

/**
 *
 * @author Thomas Kellerer
 */
public class ObjectResultListDataStore
  extends DataStore
{
  public static final int COL_IDX_OBJECT_NAME = 0;
  public static final int COL_IDX_OBJECT_SCHEMA = 1;
  public static final int COL_IDX_OBJECT_TYPE = 2;
  public static final int COL_IDX_OBJECT_SOURCE = 3;

  private static final String[] colnames = new String[] { "NAME", "SCHEMA", "TYPE", "SOURCE"};
  private static final int[] colTypes = new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.CLOB };
  private static final int[] colSizes = new int[] { 30, 30, 30, 50 };

  public ObjectResultListDataStore(boolean showSource)
  {
    super(colnames, colTypes, colSizes);
    if (!showSource)
    {
      removeColumn(COL_IDX_OBJECT_SOURCE);
    }
  }

  public ObjectResultListDataStore(WbConnection con, List<DbObject> resultList, boolean showSource)
    throws SQLException
  {
    super(colnames, colTypes, colSizes);
    setResultList(con, resultList, showSource);
  }

  public final void setResultList(WbConnection con, List<DbObject> resultList, boolean showSource)
  {
    if (resultList == null) return;

    for (DbObject object : resultList)
    {
      int row = addRow();
      String name = object.getObjectName();
      String type = object.getObjectType();

      if (object instanceof ProcedureDefinition)
      {
        ProcedureDefinition def = (ProcedureDefinition)object;
        if (def.isPackageProcedure())
        {
          name = def.getPackageName();
          type = "PACKAGE";
        }
      }
      setValue(row, COL_IDX_OBJECT_NAME, name);
      setValue(row, COL_IDX_OBJECT_SCHEMA, object.getSchema());
      setValue(row, COL_IDX_OBJECT_TYPE, type);
      if (showSource)
      {
        setValue(row, COL_IDX_OBJECT_SOURCE, getSource(object, con));
      }
      getRow(row).setUserObject(object);
    }
    
    if (!showSource)
    {
      removeColumn(COL_IDX_OBJECT_SOURCE);
    }
    resetStatus();
  }

  public String getName(int row)
  {
    return getValueAsString(row, COL_IDX_OBJECT_NAME);
  }

  public String getType(int row)
  {
    return getValueAsString(row, COL_IDX_OBJECT_TYPE);
  }

  public CharSequence getSource(int row, WbConnection con)
  {
    DbObject dbo = getUserObject(row, DbObject.class);
    return getSource(dbo, con);
  }

  private CharSequence getSource(DbObject dbo, WbConnection con)
  {
    try
    {
      return dbo.getSource(con);
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve source for object: " +
        dbo.getFullyQualifiedName(null), ex);
    }
    return null;
  }

}
