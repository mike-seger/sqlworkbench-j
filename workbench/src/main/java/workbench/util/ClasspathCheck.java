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
package workbench.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import workbench.StartupMessages;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;

import workbench.db.exporter.PoiHelper;

/**
 *
 * @author Thomas Kellerer
 */
public class ClasspathCheck
{
  private static final String OOXML_SCHEMAS = "poi-ooxml-schemas.jar";

  public void checAll()
  {
    checkLibsToMove();
    checkObsoleteLibs();
  }

  public void checkLibsToMove()
  {
    if (!GuiSettings.checkExtDir()) return;

    long start = System.currentTimeMillis();

    ClasspathUtil cp = new ClasspathUtil();
    List<File> libs = cp.checkLibsToMove();
    if (libs.size() > 1)
    {
      String names = libs.stream().map(f -> "<li>" + f.getName() + "</li>").collect(Collectors.joining(""));
      WbFile extDir = new ClasspathUtil().getExtDir();
      String msg = ResourceMgr.getFormattedString("MsgExtDirWarning", cp.getJarPath(), names, extDir.getFullPath());
      StartupMessages.getInstance().appendMessage(msg);
      String logMsg = "Please move the following files to " + extDir.getFullPath() + "\n" +
        libs.stream().map(f -> f.getAbsolutePath()).collect(Collectors.joining("\n"));
      LogMgr.logWarning(new CallerInfo(){}, logMsg);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logInfo(new CallerInfo(){}, "Checking for ext libs took: " + duration + "ms");
  }

  public void checkObsoleteLibs()
  {
    if (!GuiSettings.checkObsoleteJars()) return;

    long start = System.currentTimeMillis();
    ClasspathUtil cp = new ClasspathUtil();
    List<File> libs = cp.getExtLibs();
    List<WbFile> toDelete = new ArrayList<>();
    int poiLibs = 0;
    WbFile schemaLib = null;
    for (File lib : libs)
    {
      if (lib.getName().startsWith("poi"))
      {
        poiLibs ++;
      }
      if (OOXML_SCHEMAS.equalsIgnoreCase(lib.getName()))
      {
        schemaLib = new WbFile(lib);
      }
    }

    if (poiLibs > 0 && schemaLib != null && shouldDeleteSchemas())
    {
      toDelete.add(schemaLib);
    }

    if (toDelete.size() > 0)
    {
      String names = toDelete.stream().map(f -> "<li>" + f.getFullPath() + "</li>").collect(Collectors.joining(""));
      String msg = ResourceMgr.getFormattedString("MsgDeleteObsoleteJar", names);
      StartupMessages.getInstance().appendMessage(msg);
      LogMgr.logWarning(new CallerInfo(){}, "The following libraries are no longer needed and should be deleted: " + names);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logInfo(new CallerInfo(){}, "Checking for obsolete jar files took: " + duration + "ms");
  }

  private boolean shouldDeleteSchemas()
  {
    try
    {
      VersionNumber poiVersion = PoiHelper.getPOIVersion();
      VersionNumber min = new VersionNumber(5,1);
      return (poiVersion.isNewerOrEqual(min));
    }
    catch (Throwable th)
    {
      return false;
    }
  }
}
