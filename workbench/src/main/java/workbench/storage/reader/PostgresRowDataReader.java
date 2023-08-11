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
package workbench.storage.reader;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.TimeZone;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.storage.ResultInfo;

import static java.time.temporal.ChronoField.*;

/**
 * A RowDataReader for Postgres to handle time zones correctly.
 *
 * It also implements a workaround for reading numeric infinity values.
 *
 * @author Thomas Kellerer
 */
class PostgresRowDataReader
  extends RowDataReader
{
  private static final int NO_ADJUST = 0;
  private static final int ADJUST_OFFSET = 1;
  private static final int PARSE_STRING = 2;

  private static boolean java8InfoLogged = false;
  private static boolean strategyLogged = false;

  private final boolean useJava8Time;
  private int timeTzStrategy = PARSE_STRING;

  private final DateTimeFormatter timeParser = new DateTimeFormatterBuilder()
      .parseLenient()
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .appendLiteral(':')
      .appendValue(SECOND_OF_MINUTE, 2)
      .appendFraction(NANO_OF_SECOND, 0, 9, true)
      .appendOffset("+HHmm", "Z")
      .toFormatter();

  private long tzOffset;
  private ZoneId sessionTimeZone;
  private WbConnection dbConnection;

  PostgresRowDataReader(ResultInfo info, WbConnection conn)
  {
    super(info, conn);
    dbConnection = conn;
    timeTzStrategy = getTimeTZStrategy(conn);
    if (timeTzStrategy != NO_ADJUST && !strategyLogged)
    {
      LogMgr.logInfo(new CallerInfo(){}, "Adjusting timetz values to LocalTime by " +
        (timeTzStrategy == PARSE_STRING ? "parsing the string value" : "adjusting the time zone offset"));
      strategyLogged = true;
    }

    useJava8Time = TimestampTZHandler.Factory.supportsJava8Time(conn);
    if (useJava8Time)
    {
      useGetObjectForTime = true;
      useGetObjectForTimestamps = true;
      useGetObjectForTimestampTZ = true;
      useGetObjectForDates = true;
      if (!java8InfoLogged)
      {
        java8InfoLogged = true;
        LogMgr.logInfo(new CallerInfo(){}, "Using ZonedDateTime to read TIMESTAMP WITH TIME ZONE columns");
      }
    }
  }

  /**
   * Read a BigDecimal handling Postgres' "infinity" values.
   *
   * Postgres supports "infinity" for numeric values, but Java's BigDecimal does not.
   *
   * This method handles the exception that results from calling getObject()
   * when an infinity value is returned in the ResulSet and returns a Double value instead.
   *
   */
  @Override
  protected Object readNumeric(ResultHolder rs, int column)
    throws SQLException
  {
    try
    {
      return rs.getObject(column);
    }
    catch (SQLException ex)
    {
      // SQLState 22003 is numeric_value_out_of_range
      if ("22003".equals(ex.getSQLState()))
      {
        String val = rs.getString(column);
        // Not sure if this is a good idea...
        if ("infinity".equalsIgnoreCase(val))
        {
          return Double.POSITIVE_INFINITY;
        }
        else if ("-infinity".equalsIgnoreCase(val))
        {
          return Double.NEGATIVE_INFINITY;
        }
        throw ex;
      }
      else
      {
        throw ex;
      }
    }
  }

  private int getTimeTZStrategy(WbConnection conn)
  {
    if (conn == null) return NO_ADJUST;
    String type = conn.getDbSettings().getProperty("timetz.adjustment", "parse_string");
    if (type == null) return NO_ADJUST;
    if ("parse_string".equalsIgnoreCase(type)) return PARSE_STRING;
    if ("adjust_offset".equalsIgnoreCase(type)) return ADJUST_OFFSET;
    return NO_ADJUST;
  }

  @Override
  protected Object readTimeTZValue(ResultHolder rs, int column)
    throws SQLException
  {
    if (timeTzStrategy == PARSE_STRING)
    {
      // the String returned by getString() returns the proper time (and time zone) information.
      // This is however slower than adjusting the time based on the current time zone (see below)
      // But it seems more reliable that dealing with TimeZone.getRawOffset()
      String timeStr = rs.getString(column);
      if (timeStr == null) return null;

      try
      {
        TemporalAccessor temp = timeParser.parse(timeStr);
        return LocalTime.from(temp);
      }
      catch (Throwable ex)
      {
        LogMgr.logDebug(new CallerInfo(){}, "Could not parse time string: " + timeStr, ex);
        // Apparently our parser doesn't match the string returned by the driver so don't try this again
        timeTzStrategy = ADJUST_OFFSET;
      }
    }

    Time time = rs.getTime(column);
    if (time == null) return null;

    if (timeTzStrategy == ADJUST_OFFSET)
    {
      try
      {
        initTimeZone();
        return time.toLocalTime().plus(tzOffset, ChronoUnit.MILLIS);
      }
      catch (Throwable th)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not adjust java.sql.Time to LocalTime", th);
        timeTzStrategy = NO_ADJUST;
      }
    }
    return time;
  }

  @Override
  protected Object readTimestampTZValue(ResultHolder rs, int column)
    throws SQLException
  {
    if (useJava8Time)
    {
      return readZonedDateTime(rs, column);
    }
    return super.readTimestampTZValue(rs, column);
  }

  private ZonedDateTime readZonedDateTime(ResultHolder rs, int column)
    throws SQLException
  {
    initTimeZone();

    OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
    if (odt == null) return null;
    // This is how the JDBC returns Infinity values
    if (odt.equals(OffsetDateTime.MAX) || odt.equals(OffsetDateTime.MIN))
    {
      //TODO: is returning ZondedDateTime better,  or simply returning the OffsetDateTime directly?
      return odt.atZoneSimilarLocal(ZoneId.of("+0"));
    }
    return odt.atZoneSameInstant(sessionTimeZone);
  }

  private void initTimeZone()
  {
    if (sessionTimeZone != null) return;
    sessionTimeZone = getSessionTimezone(dbConnection);
    TimeZone tz = TimeZone.getTimeZone(sessionTimeZone);
    tzOffset = tz.getRawOffset();
  }

  private ZoneId getSessionTimezone(WbConnection con)
  {
    if (con == null) return ZoneId.systemDefault();

    // TODO: can we cache this in the connection instance?
    // this would require the SET command to trap changing the time zone.
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = con.createStatement();
      rs = stmt.executeQuery("show time zone");
      if (rs.next())
      {
        String zone = rs.getString(1);
        // apparently Postgres and Java disagree on what - and + means in the TimeZone offset
        // at least this hack gives me the same output values as psql (which should be reference)
        if (zone.contains("-"))
        {
          zone = zone.replace('-', '+');
        }
        else if (zone.contains("+"))
        {
          zone = zone.replace('+', '-');
        }

        ZoneId tzid = ZoneId.of(zone);
        LogMgr.logDebug(new CallerInfo(){}, "Using session time zone: " + tzid);
        return tzid;
      }
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve session time zone, using system default", th);
    }
    finally
    {
      JdbcUtils.close(rs, stmt);
    }
    return ZoneId.systemDefault();
  }

}
