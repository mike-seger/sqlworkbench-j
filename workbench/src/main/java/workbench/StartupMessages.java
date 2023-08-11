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
package workbench;

import java.awt.Font;

import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

import workbench.resource.ResourceMgr;

import workbench.gui.components.WbOptionPane;

import workbench.util.StringUtil;

/**
 * A class to collect important messages that should be displayed
 * during application startup before the connection dialog is shown.
 *
 * @author Thomas Kellerer
 */
public class StartupMessages
{
  private String messages = null;
  private boolean retrieved = false;

  private static final StartupMessages INSTANCE = new StartupMessages();

  public static StartupMessages getInstance()
  {
    return INSTANCE;
  }

  public void setMessage(String message)
  {
    this.messages = message;
  }

  public void appendMessage(String message)
  {
    if (this.messages == null)
    {
      this.messages = message;
    }
    else
    {
      this.messages += "\n" + messages;
    }
  }

  /**
   * Return startup messages.
   *
   * @return
   */
  public String getMessage()
  {
    retrieved = true;
    return messages;
  }

  public boolean wasRetrieved()
  {
    return retrieved;
  }

  public void showMessages()
  {
    if (StringUtil.isBlank(messages)) return;

    JEditorPane editor = new JEditorPane();
    editor.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    Font f = UIManager.getDefaults().getFont("Label.font");
    editor.setFont(f);
    editor.setContentType("text/html");
    editor.setEditable(false);
    JScrollPane scroll = new JScrollPane(editor);
    if (messages.startsWith("<html>"))
    {
      editor.setText(messages);
    }
    else
    {
      editor.setText("<html><body>" + messages + "<br></body></html>");
    }
    JOptionPane ignorePane = new WbOptionPane(scroll, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION);
    JDialog dialog = ignorePane.createDialog(WbManager.getInstance().getCurrentWindow(), ResourceMgr.TXT_PRODUCT_NAME);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setResizable(true);
    dialog.pack();
    dialog.setVisible(true);
  }
}
