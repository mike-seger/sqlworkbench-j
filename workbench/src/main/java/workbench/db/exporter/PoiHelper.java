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
package workbench.db.exporter;

import java.awt.Point;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.StringUtil;
import workbench.util.VersionNumber;

/**
 * Test if POI classes are available on the classpath.
 *
 * @author Thomas Kellerer
 */
public class PoiHelper
{
  private static boolean tested;
  private static boolean available;
  private static boolean xlsxTested;
  private static boolean xlsxAvailable;

  public static boolean isPoiAvailable()
  {
    if (tested)
    {
      return available;
    }

    try
    {
      tested = true;
      String v = getPOIVersionString();
      int pos = v.indexOf('-');
      if (pos > -1)
      {
        v = v.substring(0, pos);
      }
      VersionNumber version = new VersionNumber(v);
      VersionNumber needed = new VersionNumber(4, 1);
      LogMgr.logInfo(new CallerInfo(){}, "POI version: " + v + " available");
      available = version.isNewerOrEqual(needed);
      if (!available)
      {
        LogMgr.logError(new CallerInfo(){}, "POI on classpath has version: " + version.toString() + ". But version " + needed.toString() + " or later is required", null);
      }
    }
    catch (ClassNotFoundException  cnf)
    {
      LogMgr.logInfo(new CallerInfo(){}, "Apache POI not available");
      available = false;
    }
    catch (Throwable th)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Error loading Apache POI classes", th);
      available = false;
    }
    return available;
  }

  public static String getPOIVersionString() throws ClassNotFoundException
  {
    Class c = Class.forName("org.apache.poi.ss.usermodel.Workbook");
    c.getPackage();

    Package poi = c.getPackage();
    return poi.getImplementationVersion();
  }

  public static VersionNumber getPOIVersion()
  {
    try
    {
      return getPOIVersion(getPOIVersionString());
    }
    catch (Throwable th)
    {
      return new VersionNumber(0,0);
    }
  }
  
  public static VersionNumber getPOIVersion(String poiVersion)
  {
    int pos = poiVersion.indexOf('-');
    if (pos > -1)
    {
      poiVersion = poiVersion.substring(0, pos);
    }
    return new VersionNumber(poiVersion);
  }

  public static boolean isXLSXAvailable()
  {
    if (!isPoiAvailable())
    {
      return false;
    }

    if (xlsxTested)
    {
      return xlsxAvailable;
    }

    try
    {
      xlsxTested = true;
      Class c = Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
      xlsxAvailable = (c != null);
    }
    catch (ClassNotFoundException  cnf)
    {
      LogMgr.logWarning(new CallerInfo(){}, "XLSX from Apache POI not available. Maybe incomplete POI libraries?");
      available = false;
    }
    catch (Throwable th)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Error loading Apache POI classes for XLSX", th);
      xlsxAvailable = false;
    }
    return xlsxAvailable;
  }

  public static Point excelToNumbers(String excelAddress)
  {
    if (StringUtil.isEmpty(excelAddress)) return null;

    String col = excelAddress.toLowerCase().replaceAll("[^a-z]", "");
    String row = excelAddress.toLowerCase().replaceAll("[^0-9]", "");

    if (StringUtil.isEmpty(col)) return null;
    if (StringUtil.isEmpty(row)) return null;

    int y = StringUtil.getIntValue(row, -1);
    if (y == -1) return null;

    int x = 0;
    for (int i=0; i < col.length(); i++)
    {
      x *= 26;
      x += (int)col.charAt(i) - 96;
    }
    return new Point(x-1,y-1);
  }
}
