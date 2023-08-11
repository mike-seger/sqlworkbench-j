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
package workbench.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import workbench.resource.GuiSettings;
import workbench.resource.Settings;

/**
 *
 * @author Thomas Kellerer
 */
public class DurationFormatter
{
  private final DecimalFormat numberFormatter;
  public static final long ONE_SECOND = 1000;
  public static final long ONE_MINUTE = ONE_SECOND * 60;
  public static final long ONE_HOUR = ONE_MINUTE * 60;

  public DurationFormatter()
  {
    numberFormatter = createTimingFormatter();
  }

  public DurationFormatter(char decimalSep)
  {
    numberFormatter = createTimingFormatter(decimalSep);
  }

  public DurationFormatter(char decimalSep, int digits)
  {
    numberFormatter = createTimingFormatter(decimalSep, digits);
  }

  public static DecimalFormat createTimingFormatter(char decimalSep)
  {
    return createTimingFormatter(decimalSep, Settings.getInstance().getIntProperty(Settings.PROP_DURATION_DIGITS, 2));
  }

  public static DecimalFormat createTimingFormatter(char decimalSep, int digits)
  {
    DecimalFormatSymbols symb = new DecimalFormatSymbols();
    symb.setDecimalSeparator(decimalSep);
    DecimalFormat numberFormatter = new DecimalFormat("0.#s", symb);
    numberFormatter.setMaximumFractionDigits(digits);
    return numberFormatter;
  }

  /**
   * Create a timing formatter using the decimal separator defined
   * through the property {@link Settings#PROP_DURATION_DECIMAL}.
   *
   * @return a properly initialized DecimalFormat
   */
  public static DecimalFormat createTimingFormatter()
  {
    String sep = Settings.getInstance().getProperty(Settings.PROP_DURATION_DECIMAL, ".");
    return createTimingFormatter(sep.charAt(0));
  }

  public String getDurationAsSeconds(long millis)
  {
    double time = ((double)millis) / 1000.0;
    synchronized (numberFormatter)
    {
      return numberFormatter.format(time);
    }
  }

  /**
   * Formats the duration using a dynamic format.
   *
   * @param millis the duration to format
   * @return the formatted duration
   * @see DurationFormat#dynamic
   * @see #formatDuration(long, workbench.util.DurationFormat, boolean, boolean, int)
   */
  public String formatDuration(long millis)
  {
    return formatDuration(millis, DurationFormat.dynamic, (millis < ONE_MINUTE), true);
  }

  /**
   * Formats the number of milliseconds according to the given DurationFormat.
   *
   * When using {@link DurationFormat#dynamic} <tt>includeFractionalSeconds</tt> controls if
   * fractional seconds should be included.
   *
   * @param millis                   the duration to format
   * @param format                   the format to use
   *
   * @return the formatted duration
   * @see Settings#getDurationFormat()
   * @see #formatDuration(long, workbench.util.DurationFormat, boolean, boolean, int)
   */
  public String formatDuration(long millis, DurationFormat format)
  {
    return formatDuration(millis, format, (millis < ONE_MINUTE), true);
  }

  /**
   * Formats the number of milliseconds according to the given DurationFormat.
   *
   * When using {@link DurationFormat#dynamic} <tt>includeFractionalSeconds</tt> controls if
   * fractional seconds should be included.
   *
   * @param millis                   the duration to format
   * @param format                   the format to use
   * @param includeFractionalSeconds only used for format = dynamic, ignored otherwise
   *
   * @return the formatted duration
   * @see Settings#getDurationFormat()
   */
  public String formatDuration(long millis, DurationFormat format, boolean includeFractionalSeconds)
  {
    return formatDuration(millis, format, includeFractionalSeconds, true);
  }

  public String formatDuration(long millis, DurationFormat format, boolean includeFractionalSeconds, boolean includeZeroSeconds)
  {
    int millisThreshold = GuiSettings.useMillisForDurationThreshohold();
    return formatDuration(millis, format, includeFractionalSeconds, includeZeroSeconds, millisThreshold);
  }

  /**
   * Formats the number of milliseconds according to the given DurationFormat.
   *
   * When using {@link DurationFormat#dynamic} <tt>includeFractionalSeconds</tt> controls if
   * fractional seconds should be included.
   *
   * If a dynamic format is used and millistThreshold is greater than zero, then durations smaller than that
   * will be shown as milliseconds directly.
   *
   * @param millis                   the duration to format
   * @param format                   the format to use
   * @param includeFractionalSeconds only used for format = dynamic, ignored otherwise
   * @param includeZeroSeconds       only used for format = dynamic, ignored otherwise
   * @param millisThreshold          only used for format = dynamic, ignored otherwise
   *
   * @return the formatted duration
   * @see Settings#getDurationFormat()
   */
  public String formatDuration(long millis, DurationFormat format, boolean includeFractionalSeconds, boolean includeZeroSeconds, int millisThreshold)
  {
    StringBuilder result = new StringBuilder(20);

    if (format == DurationFormat.millis)
    {
      result.append(Long.toString(millis));
      result.append("ms");
    }
    else if (format == DurationFormat.seconds)
    {
      result.append(getDurationAsSeconds(millis));
    }
    else if (millisThreshold > 0 && millis <= millisThreshold)
    {
      result.append(millis);
      result.append("ms");
    }
    else
    {
      long hours = (millis / ONE_HOUR);
      millis -= (hours * ONE_HOUR);
      long minutes = millis / ONE_MINUTE;
      millis -= (minutes * ONE_MINUTE);

      if (hours > 0)
      {
        result.append(hours);
        result.append("h ");
      }

      if (minutes == 0 && hours > 0 || minutes > 0)
      {
        result.append(minutes);
        result.append("m ");
      }

      if (includeFractionalSeconds)
      {
        result.append(getDurationAsSeconds(millis));
      }
      else
      {
        int seconds = (int)millis / 1000;
        if (seconds > 0 || includeZeroSeconds)
        {
          result.append(Long.toString(seconds));
          result.append('s');
        }
      }
    }
    return result.toString().trim();
  }
}
