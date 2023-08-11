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
package workbench.storage.reader;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Set;
import java.util.TreeSet;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.WbConnection;

import workbench.storage.ResultInfo;

/**
 * A RowDataReader that deals with SQLite's lack of proper data types.
 *
 * As the JDBC driver very often has a hard time converting values,
 * all date/time values are retrieved as strings.
 *
 * For other "data types" we use getObject() hoping that the JDBC driver
 * gets that right, but revert to getString() if an error occurs.
 *
 * @author Thomas Kellerer
 */
public class SQLiteRowDataReader
  extends RowDataReader
{
  private final Set<Integer> columnsWithErrors = new TreeSet<>();

  public SQLiteRowDataReader(ResultInfo info, WbConnection conn)
  {
    super(info, conn);
  }

  @Override
  public Object readColumnData(ResultHolder rs, int type, int column, boolean trimCharData)
    throws SQLException
  {
    switch (type)
    {
      case Types.DATE:
      case Types.TIMESTAMP:
      case Types.TIME:
      case Types.TIME_WITH_TIMEZONE:
      case Types.TIMESTAMP_WITH_TIMEZONE:
        // SQLite does not have proper data types and more often than not
        // chokes when trying to retrieve dates or timestamps.
        return rs.getString(column);
    }

    try
    {
      if (columnsWithErrors.contains(column))
      {
        return rs.getString(column);
      }
      // other "data types" seem to work properly
      return rs.getObject(column);
    }
    catch (Throwable th)
    {
      // Only log errors for each column once, to avoid flooding the logfile
      if (!columnsWithErrors.contains(column))
      {
        LogMgr.logWarning(new CallerInfo(){}, "Could not retrieve value from column " + column +  " using getObject(), using getString()", th);
        columnsWithErrors.add(column);
      }
      return rs.getString(column);
    }
  }
}
