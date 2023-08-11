/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
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
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;
/**
 *
 * @author Thomas Kellerer
 */
public class ZipUtilTest
  extends WbTestCase
{

  public ZipUtilTest()
  {
    super("ZipUtilTest");
  }

  @Test
  public void testZipDirectory()
    throws Exception
  {
    TestUtil util = getTestUtil();
    util.emptyBaseDirectory();
    File sourceDir = new File(util.getBaseDir(), "sourcedata");
    sourceDir.mkdir();
    FileUtil.writeString(new File(sourceDir, "file1.txt"), "File one");
    FileUtil.writeString(new File(sourceDir, "file2.txt"), "File two");
    FileUtil.writeString(new File(sourceDir, "file3.txt"), "File three");
    FileUtil.writeString(new File(sourceDir, "file4.txt"), "File four");

    File zip = new File(util.getBaseDir(), "backup.zip");
    ZipUtil.zipDirectory(sourceDir, zip);
    assertTrue(zip.exists());
    List<String> content = ZipUtil.getFiles(zip);
    assertEquals(4, content.size());
    for (int i=1; i <=4; i++)
    {
      assertTrue(content.contains("file"+i+".txt"));
    }
  }

}
