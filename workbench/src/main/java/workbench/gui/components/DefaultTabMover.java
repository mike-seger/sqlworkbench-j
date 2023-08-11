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
package workbench.gui.components;

import java.awt.Component;

import javax.swing.JTabbedPane;

import workbench.interfaces.Moveable;

/**
 *
 * @author Thomas Kellerer
 */
public class DefaultTabMover
  implements Moveable
{
  private final JTabbedPane tab;

  public DefaultTabMover(JTabbedPane tab)
  {
    this.tab = tab;
  }

  @Override
  public boolean startMove(int index)
  {
    return true;
  }

  @Override
  public void endMove(int finalIndex)
  {
  }

  @Override
  public boolean moveTab(int oldIndex, int newIndex)
  {
    Component panel = tab.getComponentAt(oldIndex);
    String oldTitle = tab.getTitleAt(oldIndex);
    tab.removeTabAt(oldIndex);
    tab.add(panel, newIndex);
    tab.setTitleAt(newIndex, oldTitle);
    tab.setSelectedIndex(newIndex);
    tab.invalidate();
    return true;
  }

  @Override
  public void moveCancelled()
  {

  }

}
