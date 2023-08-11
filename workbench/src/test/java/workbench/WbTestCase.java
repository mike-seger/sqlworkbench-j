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
package workbench;

import java.io.File;

import org.junit.Ignore;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;

/**
 * @author Thomas Kellerer
 */
@Ignore
public class WbTestCase
{
  private String name;
  private boolean prepared;

  public WbTestCase()
  {
    name = "WbTestCase";
    prepare();
  }

  public WbTestCase(String testName)
  {
    name = testName;
    prepare();
  }

  protected final void prepare()
  {
    System.setProperty("workbench.log.console", "false");
    System.setProperty("workbench.dbmetadata.debugmetasql", "true");
    getTestUtil();
  }

  public File getResourceFile(String filename)
  {
    try
    {
      URI uri = getClass().getResource(filename).toURI();
      return new File(uri);
    }
    catch (Exception ex)
    {
      throw new RuntimeException("Could not open file " + filename);
    }
  }

  public Reader getResourceReader(String filename, String encoding)
    throws IOException
  {
    InputStream in = getClass().getResourceAsStream(filename);
    InputStreamReader reader = new InputStreamReader(in, encoding);
    return reader;
  }

  protected synchronized TestUtil getTestUtil()
  {
    TestUtil util = new TestUtil(getName());
    if (prepared) return util;

    try
    {
      util.prepareEnvironment();
      prepared = true;
    }
    catch (IOException io)
    {
      io.printStackTrace();
    }
    return util;
  }

  protected TestUtil getTestUtil(String method)
  {
    return new TestUtil(getName() + "_" + "method");
  }

  public String getName()
  {
    return name;
  }
}
