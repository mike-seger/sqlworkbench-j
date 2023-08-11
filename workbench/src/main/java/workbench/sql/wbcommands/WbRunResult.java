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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import workbench.AppArguments;
import workbench.WbManager;
import workbench.console.ConsoleSettings;
import workbench.interfaces.ResultSetConsumer;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.storage.DataStore;

import workbench.sql.BatchRunner;
import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.StringUtil;

import static workbench.sql.wbcommands.WbInclude.*;

/**
 * A SQL command that runs the result of another SQL statement as a script.
 *
 * This is similar to psql's <code>\gexec</code> command (with the main difference
 * that WbRunResult needs to be put before the generating statement, not after).
 *
 * @author  Thomas Kellerer
 */
public class WbRunResult
  extends SqlCommand
  implements ResultSetConsumer
{
  public static final String ARG_DRY_RUN = "dryRun";
  public static final String ARG_QUERY = "query";
  public static final String VERB = "WbRunResult";
  private BatchRunner batchRunner;
  private boolean dryRun = false;

  public WbRunResult()
  {
    super();
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(CommonArgs.ARG_CONTINUE, ArgumentType.BoolArgument);
    cmdLine.addArgument(AppArguments.ARG_SHOWPROGRESS, ArgumentType.BoolArgument);
    cmdLine.addArgument(AppArguments.ARG_IGNORE_DROP, ArgumentType.BoolArgument);
    cmdLine.addArgument(CommonArgs.ARG_VERBOSE, ArgumentType.BoolSwitch);
    cmdLine.addArgument(WbInclude.ARG_PRINT_STATEMENTS, ArgumentType.BoolSwitch);
    cmdLine.addArgument(AppArguments.ARG_SHOW_TIMING, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_DRY_RUN, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_QUERY);
    ConditionCheck.addParameters(cmdLine);
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
    StatementRunnerResult result = createResult(sql);

    String args = getCommandLine(sql);
    cmdLine.parse(args);

    if (displayHelp(result))
    {
      return result;
    }

    if (!checkConditions(result))
    {
      return result;
    }

    boolean checkEscape = Settings.getInstance().useNonStandardQuoteEscaping(currentConnection);
    boolean defaultContinue = Settings.getInstance().getWbIncludeDefaultContinue();
    boolean continueOnError = cmdLine.getBoolean(CommonArgs.ARG_CONTINUE, defaultContinue);
    boolean verbose = cmdLine.getBoolean(CommonArgs.ARG_VERBOSE, runner.getVerboseLogging());
    boolean ignoreDrop = cmdLine.getBoolean(AppArguments.ARG_IGNORE_DROP, runner.getIgnoreDropErrors());
    boolean showStmts = cmdLine.getBoolean(ARG_PRINT_STATEMENTS, this.runner.getTraceStatements());
    boolean showTiming = cmdLine.getBoolean(AppArguments.ARG_SHOW_TIMING, false);
    boolean showProgress = cmdLine.getBoolean(AppArguments.ARG_SHOWPROGRESS, true);
    dryRun = cmdLine.getBoolean(ARG_DRY_RUN);
    batchRunner = new BatchRunner();
    batchRunner.setVariablePoolID(variablePoolID);
    batchRunner.setBaseDir(getBaseDir());
    batchRunner.setConnection(this.currentConnection);
    batchRunner.setMessageLogger(this.messageLogger);
    batchRunner.setVerboseLogging(verbose);
    batchRunner.setIgnoreDropErrors(ignoreDrop);
    batchRunner.setAbortOnError(!continueOnError);
    batchRunner.setCheckEscapedQuotes(checkEscape);
    batchRunner.setShowTiming(showTiming);
    batchRunner.setParameterPrompter(this.prompter);
    batchRunner.setExecutionController(runner.getExecutionController());
    batchRunner.showResultSets(true);
    batchRunner.setShowProgress(showProgress);
    batchRunner.setRowMonitor(this.rowMonitor);
    batchRunner.setShowStatementWithResult(showStmts);
    batchRunner.setShowStatementSummary(false);
    batchRunner.setShowRowCounts(true);

    if (WbManager.getInstance().isConsoleMode())
    {
      batchRunner.setMaxColumnDisplayLength(ConsoleSettings.getMaxColumnDataWidth());
    }
    else if (WbManager.getInstance().isGUIMode())
    {
      batchRunner.setConsole(null);
    }
    LogMgr.logInfo(new CallerInfo(){}, "WbRunResult initialized. The result of the next statement will be run as a script");
    result.setSuccess();

    String query = cmdLine.getValue(ARG_QUERY);
    if (StringUtil.isBlank(query))
    {
      runner.setConsumer(this);
      return result;
    }

    try
    {
      currentStatement = currentConnection.createStatementForQuery();
      ResultSet rs = currentStatement.executeQuery(query);
      processResults(result, true, rs);
      if (isCancelled)
      {
        result.addMessageByKey("MsgStatementCancelled");
      }
      else
      {
        consumeResult(result);
      }
      return result;
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not run query", ex);
      result.addErrorMessage(ex);
      return result;
    }
  }

  @Override
  public void consumeResult(StatementRunnerResult toConsume)
  {
    if (batchRunner == null)
    {
      LogMgr.logError(new CallerInfo(){}, "consumeResult() called but batchRunner was not initialized", new Exception("Backtrace"));
      return;
    }

    if (toConsume == null) return;
    if (!toConsume.isSuccess()) return;

    List<DataStore> scripts = new ArrayList<>();

    if (toConsume.hasResultSets())
    {
      try
      {
        scripts = new ArrayList<>();
        for (ResultSet rs : toConsume.getResultSets())
        {
          DataStore ds = new DataStore(rs, true);
          if (ds.getRowCount() > 0)
          {
            scripts.add(ds);
          }
        }
      }
      catch (SQLException e)
      {
        toConsume.addErrorMessage(e);
        return;
      }
      finally
      {
        toConsume.clearResultSets();
      }
    }
    else if (toConsume.hasDataStores())
    {
      scripts.addAll(toConsume.getDataStores());
      toConsume.getDataStores().clear();
    }

    try
    {
      for (DataStore ds : scripts)
      {
        if (dryRun)
        {
          toConsume.addDataStore(ds);
        }
        else
        {
          String script = getScript(ds);
          runScript(script, toConsume);
          if (!toConsume.isSuccess() && batchRunner.getAbortOnError())
          {
            break;
          }
        }
      }
    }
    finally
    {
      if (!dryRun)
      {
        for (DataStore ds : scripts)
        {
          ds.reset();
        }
      }
      runner.setConsumer(null);
      toConsume.setConsumed(true);
    }
  }

  @Override
  public void cancel()
    throws SQLException
  {
    if (batchRunner != null)
    {
      batchRunner.cancel();
    }
  }

  @Override
  public void done()
  {
    super.done();
    if (batchRunner != null)
    {
      batchRunner.done();
      batchRunner = null;
    }
  }

  @Override
  public boolean ignoreMaxRows()
  {
    return true;
  }

  private String getScript(DataStore data)
  {
    if (data == null || data.getRowCount() == 0) return "";
    StringBuilder result = new StringBuilder(data.getRowCount() * 100);
    for (int row=0; row < data.getRowCount(); row ++)
    {
      String line = data.getValueAsString(row, 0);
      result.append(line);
      result.append('\n');
    }
    return result.toString();
  }

  private void runScript(String script, StatementRunnerResult result)
  {
    try
    {
      batchRunner.setScriptToRun(script);
      batchRunner.execute();

      if (batchRunner.isSuccess())
      {
        result.setSuccess();
      }
      else
      {
        result.setFailure(batchRunner.getLastError());
      }

      List<DataStore> queryResults = batchRunner.getQueryResults();
      for (DataStore ds : queryResults)
      {
        result.addDataStore(ds);
      }

      if (this.rowMonitor != null)
      {
        this.rowMonitor.jobFinished();
      }

      result.setSuccess();
    }
    catch (Exception th)
    {
      result.addErrorMessage(th);
    }
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
