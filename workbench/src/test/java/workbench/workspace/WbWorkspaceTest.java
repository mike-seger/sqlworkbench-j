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
package workbench.workspace;

import java.io.File;
import java.util.Map;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.gui.sql.EditorHistory;
import workbench.gui.sql.PanelType;

import workbench.util.WbFile;
import workbench.util.WbProperties;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
/**
 *
 * @author Thomas Kellerer
 */
public class WbWorkspaceTest
  extends WbTestCase
{

  public WbWorkspaceTest()
  {
    super("DirectoryWorkspacePersistenceTest");
  }

  @Before
  public void setup()
  {
    TestUtil util = getTestUtil();
    util.emptyBaseDirectory();
  }
  @Test
  public void testDirectoryPersistence()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbFile wdir = new WbFile(util.getBaseDir(), "my_workspace");
    wdir.mkdir();
    WbWorkspace wksp = createWorkspace(wdir.getAbsolutePath(), true);
    wksp.save();
    testOne(wksp, wdir.getAbsolutePath(), true);

    File backup1 = wksp.createBackup();
    assertNotNull(backup1);
    assertTrue(backup1.getAbsolutePath().endsWith("1"));

    File backup2 = wksp.createBackup();
    assertNotNull(backup2);
    assertTrue(backup2.getAbsolutePath().endsWith("2"));
  }

  @Test
  public void testZipPersistence()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbFile wFile = new WbFile(util.getBaseDir(), "my_workspace.wksp");
    WbWorkspace wksp = createWorkspace(wFile.getAbsolutePath(), false);
    wksp.save();
    testOne(wksp, wFile.getAbsolutePath(), false);
  }

  private void testOne(WbWorkspace toTest, String name, boolean useDir)
    throws Exception
  {
    try (WbWorkspace wksp2 = new WbWorkspace(name, useDir))
    {
      wksp2.openForReading();
      assertEquals(toTest.getSelectedTab(), wksp2.getSelectedTab());
      assertEquals(toTest.getTabTitle(0), wksp2.getTabTitle(0));
      assertEquals(toTest.getPanelType(0), wksp2.getPanelType(0));

      assertEquals(toTest.getToolProperties().keySet(), wksp2.getToolProperties().keySet());
      assertEquals(toTest.getSettings(), wksp2.getSettings());
    }
  }

  private WbWorkspace createWorkspace(String name, boolean allowDirectory)
    throws Exception
  {
    WbWorkspace wksp = new WbWorkspace(name, allowDirectory);
    EditorHistory h1 = new EditorHistory(1);
    h1.addContent("This is the editor content", 0, 0, 0);
    wksp.addEditorHistory(0, h1);

    WbProperties props = wksp.getSettings();
    props.setProperty("first.prop", "one");
    props.setProperty("second.prop", "two");
    String propStart = WbWorkspace.TAB_PROP_PREFIX + 0;
    props.setProperty(propStart + ".divider.location", Integer.toString(200));
    props.setProperty(propStart + ".type", PanelType.sqlPanel.toString());
    wksp.setSelectedTab(42);

    WbProperties vars = new WbProperties(1);
    vars.setProperty("var1", "42");
    wksp.setVariables(props);
    wksp.setTabTitle(0, "First tab");

    Map<String, WbProperties> toolProps = wksp.getToolProperties();
    WbProperties dbt = new WbProperties(1);
    dbt.setProperty("dbt.one", "one");
    dbt.setProperty("dbt.two", "two");
    toolProps.put("dbtree", dbt);
    return wksp;
  }
}
