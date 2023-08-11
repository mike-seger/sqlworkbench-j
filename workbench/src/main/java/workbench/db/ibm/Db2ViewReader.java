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

import java.sql.SQLException;

import workbench.db.DefaultViewReader;
import workbench.db.DropType;
import workbench.db.NoConfigException;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class Db2ViewReader
  extends DefaultViewReader
{
  public Db2ViewReader(WbConnection con)
  {
    super(con);
  }

  @Override
  public CharSequence getViewSource(TableIdentifier viewId)
    throws NoConfigException
  {
    if (useSystemProc())
    {
      return retrieveViewSource(viewId, DropType.none);
    }
    return super.getViewSource(viewId);
  }

  @Override
  public CharSequence getExtendedViewSource(TableDefinition view, DropType dropType, boolean includeCommit)
    throws SQLException
  {
    if (useSystemProc())
    {
      return retrieveViewSource(view.getTable(), dropType);
    }
    return super.getExtendedViewSource(view, dropType, includeCommit);
  }

  @Override
  public CharSequence getFullViewSource(TableDefinition view)
    throws SQLException, NoConfigException
  {
    if (useSystemProc())
    {
      return retrieveViewSource(view.getTable(), DropType.none);
    }
    return super.getFullViewSource(view);
  }

  @Override
  public CharSequence getExtendedViewSource(TableIdentifier tbl, DropType dropType)
    throws SQLException
  {
    if (useSystemProc())
    {
      return retrieveViewSource(tbl, dropType);
    }
    return super.getExtendedViewSource(tbl, dropType);
  }

  @Override
  public CharSequence getExtendedViewSource(TableIdentifier tbl)
    throws SQLException
  {
    if (useSystemProc())
    {
      return retrieveViewSource(tbl, DropType.none);
    }
    return super.getExtendedViewSource(tbl);
  }

  public CharSequence retrieveViewSource(TableIdentifier view, DropType dropType)
  {
    if (view == null) return null;
    Db2GenerateSQL gen = new Db2GenerateSQL(connection);
    gen.setGenerateRecreate(dropType != DropType.none);
    return gen.getViewSource(view.getRawSchema(), view.getRawTableName());
  }

  private boolean useSystemProc()
  {
    return Db2GenerateSQL.useGenerateSQLProc(connection, Db2GenerateSQL.TYPE_VIEW);
  }
}
