/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
package workbench.gui.editor;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Thomas Kellerer
 */
public class ClipboardCleanerTest
{

  public ClipboardCleanerTest()
  {
  }

  @Test
  public void testCleanupText()
  {
    String input = "VALUES (\u2019foobar\u2018, 'some \u201ctext\u201d')";
    ClipboardCleaner cleaner = new ClipboardCleaner(false);
    assertEquals("VALUES ('foobar', 'some \u201ctext\u201d')", cleaner.cleanupText(input));

    input = "from \u00abfoobar\u00ab";
    cleaner = new ClipboardCleaner(true);
    assertEquals("from \"foobar\"", cleaner.cleanupText(input));
  }

}
