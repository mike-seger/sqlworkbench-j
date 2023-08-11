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
package workbench.gui.components;

import java.awt.Component;

import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.border.Border;

import workbench.gui.WbSwingUtilities;
import workbench.gui.lnf.LnFHelper;

/**
 *
 * @author  Thomas Kellerer
 */
public class WbScrollPane
  extends JScrollPane
{
  public WbScrollPane()
  {
    super();
    initDefaultBorder();
    setDoubleBuffered(true);
  }

  public WbScrollPane(Component view)
  {
    this(view, null);
  }

  public WbScrollPane(Component view, Border border)
  {
    super(view);
    if (border == null)
    {
      initDefaultBorder();
    }
    else
    {
      setBorder(border);
    }
    setDoubleBuffered(true);
  }

  public WbScrollPane(Component view, int vsbPolicy, int hsbPolicy)
  {
    super(view, vsbPolicy, hsbPolicy);
    initDefaultBorder();
    setDoubleBuffered(true);
  }

  public WbScrollPane(int vsbPolicy, int hsbPolicy)
  {
    super(vsbPolicy, hsbPolicy);
    initDefaultBorder();
    setDoubleBuffered(true);
  }

  private void initDefaultBorder()
  {
    if (LnFHelper.isWindowsLookAndFeel())
    {
      Border myBorder = WbSwingUtilities.createLineBorder(UIManager.getColor("Label.background"));
      super.setBorder(myBorder);
    }
  }

}
