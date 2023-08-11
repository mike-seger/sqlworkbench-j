/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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

package workbench.gui.profiles;


import workbench.gui.components.LibListUtil;

import workbench.util.ClasspathUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class LibraryElement
{
  private final String fullPath;
  private String displayString;
  private boolean fileExists = false;
  public LibraryElement(String filename)
  {
    this(new WbFile(filename));
  }

  public LibraryElement(WbFile file)
  {
    ClasspathUtil cpUtil = new ClasspathUtil();
    String extDirName = cpUtil.getExtDir().getName();
    String fname = file.getName();
    if (fname.equalsIgnoreCase("rt.jar"))
    {
      // this is for the Look & Feel dialog otherwise the "rt.jar" would be shown with a wrong file path
      displayString = fname;
      fullPath = fname;
    }
    else
    {
      LibListUtil util = new LibListUtil();
      WbFile realFile = util.replacePlaceHolder(file);

      // if replacePlaceHolder() returned the same file, no placeholder is present
      if (cpUtil.isInExtDir(realFile))
      {
        // for JAR files that are part of the ext dir, we only show the "ext" directory name
        fullPath = cpUtil.getExtDir().getFullPath();
        displayString = extDirName;
        fileExists = true;
      }
      else if (realFile == file)
      {
        if (file.isAbsolute())
        {
          fullPath = file.getFullPath();
        }
        else
        {
          // don't use getFullPath() on files that are not absolute filenames
          // otherwise driver templates that don't contain a path to the driver jar
          // would show up as defined in the current directory which is quite confusing.
          fullPath = file.getName();
        }

        if (file.exists())
        {
          fileExists = true;
          displayString = fullPath;
        }
        else
        {
          fileExists = false;
          displayString = "<html><span style='color:red'><i>" + fullPath + "</i></span></html>";
        }
      }
      else
      {
        // we can't use WbFile.getFullPath() or File.getAbsolutePath() due to the placeholder
        fullPath = file.getParent() + System.getProperty("file.separator") + file.getName();
        displayString = fullPath;
        if (realFile.exists())
        {
          fileExists = true;
        }
        else
        {
          fileExists = false;
          displayString = "<html><span style='color:red'><i>" + displayString + "</i></span></html>";
        }
      }
    }
  }

  public boolean fileExists()
  {
    return fileExists;
  }
  
  public String getRealPath()
  {
    if (fullPath.endsWith(ClasspathUtil.EXT_DIR))
    {
      ClasspathUtil util = new ClasspathUtil();
      return util.getExtDir().getFullPath();
    }
    LibListUtil util = new LibListUtil();
    WbFile file = util.replacePlaceHolder(new WbFile(fullPath));
    return file.getFullPath();
  }

  public String getPath()
  {
    return fullPath;
  }

  @Override
  public String toString()
  {
    return displayString;
  }


}
