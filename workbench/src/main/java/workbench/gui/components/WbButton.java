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

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;

import workbench.resource.ResourceMgr;

/**
 *
 * @author Thomas Kellerer
 */
public class WbButton
  extends JButton
{
  public WbButton()
  {
    super();
    init();
  }

  public WbButton(Action a)
  {
    super(a);
    init();
  }

  public WbButton(String aText)
  {
    super(aText);
    init();
  }

  public WbButton(Icon i)
  {
    super(i);
    init();
  }

  private void init()
  {
    putClientProperty("jgoodies.isNarrow", Boolean.FALSE);
    setRolloverEnabled(true);
  }

  public void setResourceKey(String key)
  {
    this.setText(ResourceMgr.getString(key));
    this.setToolTipText(ResourceMgr.getDescription(key));
  }

  @Override
  public void setText(String newText)
  {
    if (newText == null)
    {
      super.setText(null);
      return;
    }
    int pos = newText.indexOf('&');
    if (pos > -1)
    {
      char mnemonic = newText.charAt(pos + 1);
      newText = newText.substring(0, pos) + newText.substring(pos + 1);
      this.setMnemonic((int)mnemonic);
    }
    super.setText(newText);
  }

  public void enableToolbarRollover()
  {
    this.setBorderPainted(false);
    this.setRolloverEnabled(false);
  }

}
