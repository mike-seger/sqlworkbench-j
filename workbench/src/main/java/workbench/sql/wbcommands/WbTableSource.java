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
import java.util.List;

import workbench.resource.ResourceMgr;

import workbench.db.ReaderFactory;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.TableSourceBuilderFactory;
import workbench.db.ViewReader;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

/**
 * Display the source code of a table.
 *
 * @author Thomas Kellerer
 */
public class WbTableSource
  extends SqlCommand
{
  public static final String VERB = "WbTableSource";

  public WbTableSource()
  {
    super();
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();
    String args = getCommandLine(sql);

    String[] types = currentConnection.getMetadata().getTablesAndViewTypes();
    SourceTableArgument tableArg = new SourceTableArgument(args, null, null, types, currentConnection);

    List<TableIdentifier> tableList = tableArg.getTables();
    List<String> missingTables = tableArg.getMissingTables();

    if (missingTables.size() > 0)
    {
      for (String tablename : missingTables)
      {
        result.addWarning(ResourceMgr.getFormattedString("ErrTableNotFound", tablename));
      }
      result.addMessageNewLine();

      if (tableList.isEmpty())
      {
        result.setFailure();
        return result;
      }
    }

    for (TableIdentifier tbl : tableList)
    {
      String source = null;
      if (currentConnection.getMetadata().isViewType(tbl.getType()))
      {
        // This can happen for materialized views in Oracle and Postgres
        ViewReader viewReader = ReaderFactory.createViewReader(currentConnection);
        source = viewReader.getExtendedViewSource(tbl).toString();
      }
      else
      {
        TableDefinition tableDef = currentConnection.getMetadata().getTableDefinition(tbl);
        TableSourceBuilder reader = TableSourceBuilderFactory.getBuilder(currentConnection);
        source = reader.getTableSource(tableDef.getTable(), tableDef.getColumns());
        if (!currentConnection.getDbSettings().createInlineFKConstraints())
        {
          StringBuilder fk = reader.getFkSource(tbl);
          if (fk != null && fk.length() > 0)
          {
            source += "\n";
            source += fk;
          }
        }
      }

      if (source != null)
      {
        result.addMessage(source);
      }
    }

    result.setSuccess();
    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }
}
