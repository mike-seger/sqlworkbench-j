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

import java.awt.Color;
import java.sql.SQLException;
import java.util.List;

import javax.swing.*;

import workbench.interfaces.StatusBar;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.gui.completion.CompletionPopup;
import workbench.gui.editor.JEditTextArea;

import workbench.sql.fksupport.JoinCondition;
import workbench.sql.fksupport.JoinCreator;

import workbench.util.StringUtil;
import workbench.util.TableAlias;

/**
 *
 * @author Thomas Kellerer
 */
public class JoinCompletionPopup
  extends CompletionPopup
{
  private JoinCreator creator;
  private StatusBar statusBar;

  public JoinCompletionPopup(JEditTextArea editor, List options, JoinCreator joinCreator)
  {
    super(editor, getHeader(), getModel(options));
    elementList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.creator = joinCreator;
  }

  public void setStatusBar(StatusBar status)
  {
    this.statusBar = status;
  }

  private static JLabel getHeader()
  {
    JLabel header = new JLabel(ResourceMgr.getString("LblCompletionListTables"));
    header.setForeground(Color.BLUE);
    header.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
    return header;
  }

  private static ListModel getModel(List options)
  {
    DefaultListModel result = new DefaultListModel();
    for (Object option : options)
    {
      result.addElement(option);
    }
    return result;
  }

  public void showPopup()
  {
    super.showPopup(null, false);
  }

  @Override
  protected void doPaste()
  {
    Object selected = elementList.getSelectedValue();
    if (selected == null) return;
    try
    {
      String condition = null;

      if (selected instanceof JoinCondition)
      {
        condition = this.creator.getJoinCondition((JoinCondition)selected);
      }
      else if (selected instanceof TableAlias)
      {
        List<JoinCondition> conditions = creator.getJoinConditions((TableAlias)selected);
        if (conditions.size() > 1)
        {
          condition = creator.getJoinCondition(conditions.get(0));
        }
      }
      if (StringUtil.isNotBlank(condition))
      {
        editor.insertText(condition + " ");
      }
      else if (this.statusBar != null)
      {
        this.statusBar.setStatusMessage(ResourceMgr.getString("MsgComplNoFK"), 2500);
      }
    }
    catch (SQLException sql)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve join condition", sql);
    }
  }
}
