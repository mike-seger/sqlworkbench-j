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
package workbench.gui.actions;

import java.awt.event.ActionEvent;

import javax.swing.JTabbedPane;

import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;


/**
 * Action to select the next tab in a JTabbedPane (typically the result display the SqlPanel).
 *
 * @author Thomas Kellerer
 */
public class NextResultAction
  extends WbAction
{
  private JTabbedPane resultPanel;

  public NextResultAction(JTabbedPane aClient)
  {
    super();
    this.resultPanel = aClient;
    this.initMenuDefinition("MnuTxtShowNextResult");
    this.setMenuItemName(ResourceMgr.MNU_TXT_VIEW);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (this.resultPanel == null) return;

    int count = this.resultPanel.getTabCount();
    if (count <= 1) return;

    int currentIndex = resultPanel.getSelectedIndex();

    // The last tab is the "Messages" panel, don't include that when cycling through the results
    final int next = (currentIndex + 1) % (count - 1);
    WbSwingUtilities.invoke(() ->
    {
      resultPanel.setSelectedIndex(next);
    });
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
