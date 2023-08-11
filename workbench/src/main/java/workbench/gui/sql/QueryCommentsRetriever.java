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
package workbench.gui.sql;

import workbench.interfaces.StatusBar;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.storage.DataStore;
import workbench.storage.ResultColumnMetaData;

import workbench.sql.StatementRunnerResult;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class QueryCommentsRetriever
{
  private final StatementRunnerResult result;
  private final StatusBar statusBar;

  public QueryCommentsRetriever(StatementRunnerResult result, StatusBar statusBar)
  {
    this.result = result;
    this.statusBar = statusBar;
  }

  public void retrieveComments()
  {
    if (!result.isSuccess()) return;
    if (!result.hasDataStores()) return;

    final CallerInfo ci = new CallerInfo(){};
    String currentMessage = statusBar.getText();
    try
    {
      statusBar.setStatusMessage(ResourceMgr.getString("MsgRetrievingColComments"));
      for (DataStore ds : result.getDataStores())
      {
        long start = System.currentTimeMillis();
        ResultColumnMetaData meta = new ResultColumnMetaData(ds);
        meta.retrieveColumnRemarks(ds.getResultInfo());
        long duration = System.currentTimeMillis() - start;
        String query = SqlUtil.makeCleanSql(StringUtil.getMaxSubstring(ds.getGeneratingSql(), 40), false, false);
        LogMgr.logInfo(ci, "Retrieving query column remarks took " + duration + "ms for query: " + query);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error reading comments", e);
    }
    finally
    {
      statusBar.setStatusMessage(currentMessage);
    }
  }

}
