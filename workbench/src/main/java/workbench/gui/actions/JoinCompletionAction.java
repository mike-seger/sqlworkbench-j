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

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.util.List;

import javax.swing.KeyStroke;

import workbench.interfaces.StatusBar;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.sql.EditorPanel;
import workbench.gui.sql.SqlPanel;

import workbench.sql.fksupport.JoinCondition;
import workbench.sql.fksupport.JoinCreator;
import workbench.sql.parser.ScriptParser;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;
import workbench.util.TableAlias;

/**
 * Action do an automatic completion of a join in a SQL statement.
 *
 * @author Thomas Kellerer
 */
public class JoinCompletionAction
  extends WbAction
{
  protected SqlPanel client;

  public JoinCompletionAction(SqlPanel panel)
  {
    super();
    this.client = panel;
    this.initMenuDefinition("MnuTxtAutoCompleteJoin", KeyStroke.getKeyStroke(KeyEvent.VK_J, KeyEvent.ALT_DOWN_MASK));
    this.setMenuItemName(ResourceMgr.MNU_TXT_SQL);
    setEnabled(false);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    WbConnection conn = client.getConnection();
    if (conn == null) return;

    ScriptParser parser = ScriptParser.createScriptParser(client.getConnection());

    final EditorPanel editor = client.getEditor();
    parser.setScript(editor.getText());
    int cursorPos = editor.getCaretPosition();

    int index = parser.getCommandIndexAtCursorPos(cursorPos);
    int commandCursorPos = parser.getIndexInCommand(index, cursorPos);
    String sql = parser.getCommand(index, false);

    if (sql == null)
    {
      LogMgr.logWarning(new CallerInfo(){}, "No SQL found!");
      return;
    }

    String verb = conn.getParsingUtil().getSqlVerb(sql);
    if (!"SELECT".equalsIgnoreCase(verb))
    {
      String msg = "'" + verb + "' " + ResourceMgr.getString("MsgCompletionNotSupported");
      setStatusMessage(msg, 2500);
      return;
    }

    try
    {
      setStatusMessage(ResourceMgr.getString("MsgCompletionRetrievingObjects"), 0);
      final JoinCreator creator = new JoinCreator(sql, commandCursorPos, conn);
      List<JoinCondition> joinConditions = creator.getJoinConditions();
      final List<TableAlias> tables = creator.getPossibleJoinTables();

      if (CollectionUtil.isEmpty(joinConditions) && tables.size() == 1)
      {
        joinConditions = creator.getJoinConditions(tables.get(0));
      }

      if (joinConditions.size() > 1)
      {
        setStatusMessage("", 0);
        final List<JoinCondition> joins = joinConditions;
        EventQueue.invokeLater(() ->
        {
          JoinCompletionPopup popup = new JoinCompletionPopup(editor, joins, creator);
          popup.setStatusBar(client.getStatusBar());
          popup.showPopup();
        });
      }
      else if (joinConditions.size() == 1)
      {
        String condition = creator.getJoinCondition(joinConditions.get(0));
        editor.insertText(condition + " ");
        setStatusMessage("", 0);
      }
      else if (tables.size() > 1)
      {
        setStatusMessage("", 0);
        EventQueue.invokeLater(() ->
        {
          JoinCompletionPopup popup = new JoinCompletionPopup(editor, tables, creator);
          popup.setStatusBar(client.getStatusBar());
          popup.showPopup();
        });
      }
      else
      {
        setStatusMessage(ResourceMgr.getString("MsgCompletionNothingFound"), 2500);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error retrieving condition", ex);
      setStatusMessage("", 0);
    }
  }

  private void setStatusMessage(final String msg, final int duration)
  {
    final StatusBar statusbar = client.getStatusBar();
    if (statusbar == null)
    {
      return;
    }

    WbSwingUtilities.invoke(() ->
    {
      if (StringUtil.isEmpty(msg))
      {
        statusbar.clearStatusMessage();
      }
      else if (duration > 0)
      {
        statusbar.setStatusMessage(msg, duration);
      }
      else
      {
        statusbar.setStatusMessage(msg);
      }
      statusbar.doRepaint();
    });
  }

}
