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
package workbench.log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import workbench.WbManager;

import workbench.util.StringUtil;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.FileManager;
import org.apache.logging.log4j.core.appender.OutputStreamManager;

import static workbench.resource.Settings.*;

/**
 * An implementation of WbLogger that acts as a facade to Log4J.
 *
 * @author Thomas Kellerer
 * @author Peter Franken
 */
public class Log4JLogger
  implements WbLogger
{
  private final List<LogListener> listenerList = new ArrayList<>(1);

  public Log4JLogger()
  {
  }

  private LogLevel toWbLevel(Level level)
  {
    if (level == Level.DEBUG)
    {
      return LogLevel.debug;
    }
    if (level == Level.ERROR)
    {
      return LogLevel.error;
    }
    if (level == Level.INFO)
    {
      return LogLevel.info;
    }
    if (level == Level.WARN)
    {
      return LogLevel.warning;
    }
    if (level == Level.TRACE)
    {
      return LogLevel.trace;
    }
    return LogLevel.error;
  }

  @Override
  public void setRootLevel(LogLevel level)
  {
    // ignored, set by configuration file
  }

  @Override
  public LogLevel getRootLevel()
  {
    return toWbLevel(LogManager.getRootLogger().getLevel());
  }

  @Override
  public void logMessage(LogLevel level, CallerInfo caller, CharSequence msg, Throwable th)
  {
    Logger logger = LogManager.getLogger(caller.getCallingClass());
    switch (level)
    {
      case trace:
        logger.trace(msg, th);
        break;
      case debug:
        logger.debug(msg, th);
        break;
      case info:
        logger.info(msg, th);
        break;
      case warning:
        logger.warn(msg, th);
        break;
      default:
        logger.error(msg, th);
    }
    if (levelEnabled(level))
    {
      notifyListener(msg);
    }
  }

  @Override
  public void setMessageFormat(String newFormat)
  {
    // ignored, should be done by log4j.xml
  }

  @Override
  public void logToSystemError(boolean flag)
  {
    // ignored, should be done by log4j.xml
  }

  @Override
  public File getCurrentFile()
  {
    Logger wb = LogManager.getRootLogger();
    return findLogFile(wb);
  }

  private File findLogFile(Logger start)
  {
    if (start instanceof org.apache.logging.log4j.core.Logger)
    {
      org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger)start;
      Map<String, Appender> appenders = coreLogger.getAppenders();
      for (Appender appender : appenders.values())
      {
        if (appender instanceof AbstractOutputStreamAppender)
        {
          AbstractOutputStreamAppender streamAppender = (AbstractOutputStreamAppender)appender;
          OutputStreamManager manager = streamAppender.getManager();
          if (manager instanceof FileManager)
          {
            FileManager fileMgr = (FileManager)manager;
            String fname = fileMgr.getFileName();
            if (fname != null)
            {
              return new File(fname);
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public void setOutputFile(File logfile, int maxFilesize, int maxBackups)
  {
    Logger log = LogManager.getLogger(getClass());
    log.info("=================== Log started ===================");
    String configFile = System.getProperty(LOG4_CONFIG_PROP);
    if (StringUtil.isNotBlank(configFile))
    {
      log.info("Log4J initialized from: " + configFile);
    }
  }

  @Override
  public void shutdownWbLog()
  {
    Logger log = LogManager.getLogger(getClass());
    log.info("=================== Log stopped ===================");
    if (WbManager.shouldDoSystemExit())
    {
      LogManager.shutdown();
    }
  }

  @Override
  public boolean levelEnabled(LogLevel tolog)
  {
    Logger root = LogManager.getRootLogger();
    switch (tolog)
    {
      case trace:
        return root.isTraceEnabled();
      case debug:
        return root.isDebugEnabled();
      case info:
        return root.isInfoEnabled();
      case warning:
        return root.isWarnEnabled();
    }
    return true;
  }

  private void notifyListener(CharSequence msg)
  {
    for (LogListener listener : listenerList)
    {
      if (listener != null)
      {
        listener.messageLogged(msg);
      }
    }
  }

  @Override
  public void addLogListener(LogListener listener)
  {
    listenerList.add(listener);
  }

  @Override
  public void removeLogListener(LogListener listener)
  {
    listenerList.remove(listener);
  }

}
