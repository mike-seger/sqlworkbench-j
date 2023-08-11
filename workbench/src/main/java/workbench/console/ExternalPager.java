/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.console;

import java.io.OutputStream;
import java.io.PrintStream;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.WbFile;

/**
 * An output stream to send console output through an external pager.
 * @author Thomas Kellerer
 */
public class ExternalPager
{
  private WbFile pagerExecutable;
  private Process process;
  private PrintStream output;

  public ExternalPager(WbFile executable)
  {
    this.pagerExecutable = executable;
  }

  public PrintStream getOutput()
  {
    return this.output == null ? System.out : this.output;
  }

  public boolean isValid()
  {
    return (pagerExecutable != null && pagerExecutable.exists() && System.console() != null);
  }

  public void waitFor()
  {
    if (process == null) return;

    try
    {
      if (this.output != null)
      {
        this.output.flush();
        this.output.close();
      }
      process.waitFor();
    }
    catch (Throwable th)
    {
      // ignore
    }
  }

  public void initialize()
  {

    if (!isValid())
    {
      process = null;
      output = null;
      return;
    }

    try
    {
      ProcessBuilder pb = new ProcessBuilder(pagerExecutable.getFullPath());
      pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
      pb.redirectErrorStream(true);
      pb.redirectInput(ProcessBuilder.Redirect.PIPE);
      process = pb.start();

      OutputStream processIn = process.getOutputStream();
      if (processIn != null)
      {
        output = new PrintStream(processIn, true);
        LogMgr.logDebug(new CallerInfo(){}, "Started external pager: \"" + pagerExecutable.getFullPath() + "");
      }
      else
      {
        LogMgr.logDebug(new CallerInfo(){}, "No output stream for the pager!");
      }
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not start external pager: \"" + pagerExecutable.getFullPath() + "\"", th);
      done();
    }
  }

  public void done()
  {
    output = null;
    if (process != null)
    {
      try
      {
        process.destroyForcibly();
        process = null;
      }
      catch (Throwable th)
      {
        // ignore
      }
    }
  }
}
