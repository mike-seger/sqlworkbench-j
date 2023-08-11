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
package workbench.sql.macros;

import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.swing.KeyStroke;

import workbench.TestUtil;
import workbench.resource.StoreableKeyStroke;

import workbench.util.FileUtil;
import workbench.util.WbFile;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DirectoryMacroPersistenceTest
{

  @Test
  public void testPersistence()
    throws Exception
  {
    MacroGroup mg1 = new MacroGroup();
    mg1.setName("First Group");
    mg1.setSortOrder(1);
    MacroDefinition d1 = new MacroDefinition("macro one_one", "select 1.1");
    d1.setSortOrder(1);
    d1.setShortcut(new StoreableKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_CUT, 0)));
    MacroDefinition d2 = new MacroDefinition("macro one_two", "select 1.2");
    d2.setSortOrder(2);
    mg1.addMacro(d1);
    mg1.addMacro(d2);

    MacroGroup mg2 = new MacroGroup();
    mg2.setSortOrder(2);
    mg2.setName("Second Group");
    MacroDefinition t1 = new MacroDefinition("macro two_one", "select 2.1");
    t1.setSortOrder(1);
    MacroDefinition t2 = new MacroDefinition("macro two_two", "select 2.2");
    t2.setSortOrder(2);
    mg2.addMacro(t1);
    mg2.addMacro(t2);
    List<MacroGroup> groups = new ArrayList<>();
    groups.add(mg1);
    groups.add(mg2);

    File tmpDir = Files.createTempDirectory("storage-test").toFile();
    tmpDir.deleteOnExit();
    WbFile macroDir = new WbFile(tmpDir, "macros");
    macroDir.mkdir();

    DirectoryMacroPersistence persistence = new DirectoryMacroPersistence();
    persistence.saveMacros(macroDir, groups, false);

    File[] dirs = macroDir.listFiles();
    assertEquals(2, dirs.length);

    List<MacroGroup> loaded = persistence.loadMacros(macroDir);
    assertEquals(2, loaded.size());
    assertEquals("First Group", loaded.get(0).getName());
    assertEquals(2, loaded.get(0).getSize());
    MacroDefinition m = loaded.get(0).getMacros().get(0);
    assertEquals("select 1.1", m.getText());
    assertEquals(KeyEvent.VK_CUT, m.getShortcut().getKeyCode());
    assertEquals("select 1.2", loaded.get(0).getMacros().get(1).getText());
    assertEquals("Second Group", loaded.get(1).getName());
    assertEquals(2, loaded.get(1).getSize());
  }

  @Test
  public void testSingleDirectory()
    throws Exception
  {
    TestUtil util = new TestUtil("MacroTest");
    util.emptyBaseDirectory();
    File macroDir = new File(util.getBaseDir(), "macro-test");
    macroDir.mkdirs();
    macroDir.deleteOnExit();
    FileUtil.writeString(new File(macroDir, "macro-1.sql"), "select 1");
    FileUtil.writeString(new File(macroDir, "macro-2.sql"), "select 1");

    DirectoryMacroPersistence persistence = new DirectoryMacroPersistence();
    List<MacroGroup> groups = persistence.loadMacros(macroDir);
    assertEquals(1, groups.size());
    assertEquals(2, groups.get(0).getSize());

    groups.get(0).setName("group-01");
    MacroGroup g2 = new MacroGroup();
    g2.setName("group-02");
    MacroDefinition m3 = new MacroDefinition("macro-3", "select 3");
    MacroDefinition m4 = new MacroDefinition("macro-4", "select 4");
    MacroDefinition m5 = new MacroDefinition("macro-5", "select 5");
    g2.addMacro(m3);
    g2.addMacro(m4);
    g2.addMacro(m5);

    groups.add(g2);
    persistence.saveMacros(new WbFile(macroDir), groups, true);
    File[] files = macroDir.listFiles();
    assertEquals(2, files.length);
    assertTrue(files[0].isDirectory());
    assertTrue(files[1].isDirectory());

    File d1 = new File(macroDir, "group-01");
    File[] d1files = d1.listFiles();
    assertEquals(3, d1files.length);

    File d2 = new File(macroDir, "group-02");
    File[] d2files = d2.listFiles();
    assertEquals(4, d2files.length);
  }

}
