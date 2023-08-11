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
package workbench.db.exporter;

import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ExcelDataFormatTest
{

  @Test
  public void testCleanup()
  {
    ExcelDataFormat f = new ExcelDataFormat(null, null, null, null);
    Map<String, String> formats = Map.of(
      "yyyy-MM-dd", "yyyy-MM-dd",
      "dd.MM.yyyy", "dd.MM.yyyy",
      "dd.MM.yyyy HH:mm", "dd.MM.yyyy HH:mm",
      "dd/MM/yyyy", "dd/MM/yyyy",
      "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss",
      "yyyy-MM-dd HH:mm:ss.nnnn", "yyyy-MM-dd HH:mm:ss",
      "yyyy-MM-ddQ HH:mm:ss.nnnn", "yyyy-MM-dd HH:mm:ss"
    );
    for (Map.Entry<String, String> entry : formats.entrySet())
    {
      String clean = f.cleanupDateTimeFormat(entry.getKey());
      assertEquals(clean, entry.getValue());
    }
  }

}
