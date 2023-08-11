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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;


/**
 * @author Thomas Kellerer
 */
public class VersionNumber
{
  private int major = -1;
  private int minor = -1;
  private int patchLevel = -1;

  public VersionNumber(int majorVersion, int minorVersion)
  {
    this.major = majorVersion;
    this.minor = minorVersion;
  }

  public VersionNumber(int majorVersion, int minorVersion, int patch)
  {
    this.major = majorVersion;
    this.minor = minorVersion;
    this.patchLevel = patch;
  }

  public VersionNumber(String number)
  {
    if (StringUtil.isEmpty(number))
    {
      return;
    }

    number = number.toLowerCase();
    if (number.contains("beta"))
    {
      number = number.replaceAll("beta[0-9.]*", "");
      minor = 0;
      patchLevel = 0;
    }
    else if (number.contains("("))
    {
      number = number.replaceAll("\\(.*\\)", "");
    }

    if ("@build_number@".equals(number))
    {
      major = 999;
      minor = 999;
    }
    else
    {
      try
      {
        String[] numbers = number.split("\\.");

        major = Integer.parseInt(numbers[0]);

        if (numbers.length > 1)
        {
          minor = parseValue(numbers[1]);
        }
        if (numbers.length > 2)
        {
          patchLevel = parseValue(numbers[2]);
        }
      }
      catch (Exception e)
      {
        minor = -1;
        major = -1;
      }
    }
  }

  private int parseValue(String value)
  {
    Pattern nonNumeric = Pattern.compile("[^0-9]");
    Matcher matcher = nonNumeric.matcher(value);
    int pos = -1;
    if (matcher.find())
    {
      pos = matcher.start();
    }
    if (pos > -1)
    {
      String plain = value.substring(0, pos);
      return Integer.parseInt(plain);
    }
    return Integer.parseInt(value);
  }

  public boolean isValid()
  {
    return this.major != -1;
  }

  public int getMajorVersion()
  {
    return this.major;
  }

  public int getMinorVersion()
  {
    return this.minor;
  }

  public int getPatchLevel()
  {
    return patchLevel;
  }

  public boolean isNewerThan(VersionNumber other)
  {
    if (!this.isValid()) return false;
    if (this.major > other.major) return true;
    if (this.major == other.major)
    {
      if (this.minor == other.minor)
      {
        return (this.patchLevel > other.patchLevel);
      }
      return (this.minor > other.minor);
    }
    return false;
  }

  public boolean isNewerOrEqual(VersionNumber other)
  {
    if (isNewerThan(other)) return true;
    return this.major == other.major && this.minor == other.minor && this.patchLevel == other.patchLevel;
  }

  @Override
  public int hashCode()
  {
    int hash = 3;
    hash = 47 * hash + this.major;
    hash = 47 * hash + this.minor;
    hash = 47 * hash + this.patchLevel;
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final VersionNumber other = (VersionNumber)obj;
    if (this.major != other.major) return false;
    if (this.minor != other.minor) return false;
    if (this.patchLevel != other.patchLevel) return false;
    return true;
  }

  @Override
  public String toString()
  {
    if (major == -1) return "n/a";
    if (major == 999) return "999";

    if (patchLevel == -1 && minor != -1)
    {
      return Integer.toString(major) + "." + Integer.toString(minor);
    }
    if (minor == -1 && patchLevel == -1) return Integer.toString(major);

    return Integer.toString(major) + "." + Integer.toString(minor) + "." + Integer.toString(patchLevel);
  }

  private static String cleanup(String input)
  {
    if (input == null) return input;
    return input.replaceAll("[^0-9]", "");
  }

  public static VersionNumber getJavaVersion()
  {
    String version = System.getProperty("java.version", null);
    if (version == null)
    {
      version = System.getProperty("java.runtime.version");
    }

    try
    {
      String[] elements = version.split("\\.");
      if (elements.length > 1)
      {
        if (elements[0].equals("1"))
        {
          return new VersionNumber(Integer.parseInt(cleanup(elements[1])), 0);
        }
        return new VersionNumber(Integer.parseInt(cleanup(elements[0])), Integer.parseInt(cleanup(elements[1])));
      }
      return new VersionNumber(Integer.parseInt(cleanup(elements[0])), 0);
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve Java version", th);
      // that's the minimum version this application needs
      return new VersionNumber(11, 0);
    }
  }
}
