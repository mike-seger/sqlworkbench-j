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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A class to analyze the {$blobfile= } and {$clobfile= }
 * parameters in a SQL statement.
 *
 * This class supports INSERT and UPDATE statements.
 *
 * To retrieve a blob from the database {@link workbench.sql.wbcommands.WbSelectBlob}
 * has to be used.
 *
 * @author Thomas Kellerer
 */
public class LobFileParameterParser
{
  private final Pattern MARKER_PATTERN = Pattern.compile(LobFileStatement.MARKER, Pattern.CASE_INSENSITIVE);
  private List<LobFileParameter> parameters = new ArrayList<>();

  public LobFileParameterParser(String sql)
  {
    Matcher m = MARKER_PATTERN.matcher(sql);
    WbStringTokenizer tok = new WbStringTokenizer(" \t", false, "\"'", false);

    while (m.find())
    {
      int start = m.start();
      int end = sql.indexOf('}', start + 1);
      if (end > -1)
      {
        String parm = sql.substring(start + 2, end);
        tok.setSourceString(parm);
        LobFileParameter param = new LobFileParameter();
        while (tok.hasMoreTokens())
        {
          String s = tok.nextToken();
          String arg = null;
          String value = null;
          int pos = s.indexOf('=');
          if (pos > -1)
          {
            arg = s.substring(0, pos);
            value = s.substring(pos + 1);
          }
          if ("encoding".equals(arg))
          {
            param.setEncoding(value);
          }
          else
          {
            param.setFilename(value);
            param.setBinary("blobfile".equals(arg));
          }
        }
        parameters.add(param);
      }
    }
  }

  public int getParameterCount()
  {
    return parameters.size();
  }

  public List<LobFileParameter> getParameters()
  {
    return Collections.unmodifiableList(parameters);
  }
}
