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
package workbench.sql.annotations;

import java.util.List;

import workbench.util.ArgumentParser;
import workbench.util.StringUtil;

/**
 * A class to mark the next result to be shown as "text", rather than a tab in the GUI.
 *
 * @author Thomas Kellerer
 */
public class ResultAsTextAnnotation
  extends WbAnnotation
{
  public static final String ANNOTATION = "WbResultAsText";

  private boolean applyFormat = true;
  private boolean showHeader = true;
  private String mode = null;

  public ResultAsTextAnnotation()
  {
    super(ANNOTATION);
  }

  @Override
  public boolean needsValue()
  {
    return false;
  }

  public String getMode()
  {
    return mode;
  }

  public boolean showResultHeader()
  {
    return showHeader;
  }

  public boolean applyConsoleFormat()
  {
    return applyFormat;
  }

  @Override
  public void setValue(String annotationValue)
  {
    if (StringUtil.isNotBlank(annotationValue))
    {
      parseValue(annotationValue);
    }
  }

  private void parseValue(String value)
  {
    ArgumentParser parser = new ArgumentParser(false);
    parser.addArgument("format");
    parser.addArgument("mode");
    parser.addArgument("header");
    parser.parse(value);

    applyFormat = parser.getBoolean("format", applyFormat);
    showHeader = parser.getBoolean("header", showHeader);

    if (parser.isArgPresent("mode"))
    {
      mode = parser.getValue("mode");
    }
    else
    {
      mode = StringUtil.trimToNull(parser.getNonArguments());
    }
  }

  public static boolean doShowContinuationLines(List<WbAnnotation> annotations)
  {
    for (WbAnnotation toCheck : annotations)
    {
      if (toCheck instanceof ResultAsTextAnnotation)
      {
        return ((ResultAsTextAnnotation)toCheck).applyConsoleFormat();
      }
    }
    return false;
  }

    public static boolean showResultHeader(List<WbAnnotation> annotations)
  {
    for (WbAnnotation toCheck : annotations)
    {
      if (toCheck instanceof ResultAsTextAnnotation)
      {
        return ((ResultAsTextAnnotation)toCheck).showResultHeader();
      }
    }
    return false;
  }

  public static ResultAsTextMode getMode(List<WbAnnotation> annotations)
  {
    for (WbAnnotation toCheck : annotations)
    {
      if (toCheck instanceof ResultAsTextAnnotation)
      {
        String mode = ((ResultAsTextAnnotation)toCheck).getMode();
        if (mode == null)
        {
          return ResultAsTextMode.onceOnly;
        }
        if ("on".equalsIgnoreCase(mode))
        {
          return ResultAsTextMode.turnOn;
        }
        if ("off".equalsIgnoreCase(mode))
        {
          return ResultAsTextMode.turnOff;
        }
      }
    }
    return ResultAsTextMode.noChange;
  }

}
