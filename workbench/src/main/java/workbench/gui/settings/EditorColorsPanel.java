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
package workbench.gui.settings;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import workbench.interfaces.Restoreable;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.WbColorPicker;
import workbench.gui.components.WbFontStylePicker;
import workbench.gui.editor.SyntaxStyle;
import workbench.gui.editor.SyntaxUtilities;
import workbench.gui.editor.Token;

import static workbench.gui.editor.SyntaxStyle.*;

/**
 *
 * @author Thomas Kellerer
 */
public class EditorColorsPanel
  extends JPanel
  implements Restoreable
{

  public EditorColorsPanel()
  {
    initComponents();
    editorColors.setBorder(WbSwingUtilities.createTitleBorderByKey(this, "LblEditorColors", 2));
    syntaxColors.setBorder(WbSwingUtilities.createTitleBorderByKey(this, "LblSyntaxColors", 2));
  }

  @Override
  public void restoreSettings()
  {
    Settings sett = Settings.getInstance();

    SyntaxStyle[] defaultStyles = SyntaxUtilities.getDefaultSyntaxStyles();

    textColor.setDefaultLabelKey("LblDefaultIndicator");
    bgColor.setDefaultLabelKey("LblDefaultIndicator");
    selectionColor.setDefaultLabelKey("LblDefaultIndicator");
    lineNumberColor.setDefaultLabelKey("LblDefaultIndicator");
    lineNumberBgColor.setDefaultLabelKey("LblDefaultIndicator");
    cursorColor.setDefaultLabelKey("LblDefaultIndicator");

    Color fg = sett.getColor(Settings.PROPERTY_EDITOR_FG_COLOR, null);
    textColor.setSelectedColor(fg);

    Color bg = sett.getColor(Settings.PROPERTY_EDITOR_BG_COLOR, null);
    bgColor.setSelectedColor(bg);

    blockComments.setStyle(defaultStyles[Token.COMMENT1]);
    lineComments.setStyle(defaultStyles[Token.COMMENT2]);
    keyword1.setStyle(defaultStyles[Token.KEYWORD1]);
    functions.setStyle(defaultStyles[Token.KEYWORD3]);
    wbKeywords.setStyle(defaultStyles[Token.KEYWORD2]);
    literals.setStyle(defaultStyles[Token.LITERAL1]);
    quotedIds.setStyle(defaultStyles[Token.LITERAL2]);
    operators.setStyle(defaultStyles[Token.OPERATOR]);
    datatypes.setStyle(defaultStyles[Token.DATATYPE]);

    lineNumberBgColor.setSelectedColor(Settings.getInstance().getColor(Settings.PROPERTY_EDITOR_GUTTER_COLOR));
    lineNumberColor.setSelectedColor(Settings.getInstance().getColor(Settings.PROPERTY_EDITOR_LINENUMBER_COLOR));

    errorColor.setSelectedColor(Settings.getInstance().getEditorErrorColor());
    currentStmtColor.setSelectedColor(Settings.getInstance().getEditorCurrentStmtColor());
    selectionColor.setSelectedColor(Settings.getInstance().getEditorSelectionColor());
    currLineColor.setSelectedColor(Settings.getInstance().getEditorCurrentLineColor());
    cursorColor.setSelectedColor(Settings.getInstance().getEditorCursorColor());
  }

  @Override
  public void saveSettings()
  {
    saveStyle(blockComments, COMMENT1);
    saveStyle(lineComments, COMMENT2);
    saveStyle(keyword1, KEYWORD1);
    saveStyle(wbKeywords, KEYWORD2);
    saveStyle(functions, KEYWORD3);
    saveStyle(literals, LITERAL1);
    saveStyle(quotedIds, LITERAL2);
    saveStyle(operators, OPERATOR);
    saveStyle(datatypes, DATATYPE);

    Settings sett = Settings.getInstance();
    sett.setEditorErrorColor(errorColor.getSelectedColor());
    sett.setEditorCurrentLineColor(currLineColor.getSelectedColor());
    sett.setEditorSelectionColor(selectionColor.getSelectedColor());
    sett.setEditorBackgroundColor(bgColor.getSelectedColor());
    sett.setEditorTextColor(textColor.getSelectedColor());
    sett.setEditorCursorColor(cursorColor.getSelectedColor());
    sett.setEditorCurrentStmtColor(currentStmtColor.getSelectedColor());
    sett.setColor(Settings.PROPERTY_EDITOR_GUTTER_COLOR, lineNumberBgColor.getSelectedColor());
    sett.setColor(Settings.PROPERTY_EDITOR_LINENUMBER_COLOR, lineNumberColor.getSelectedColor());
  }

  private void saveStyle(WbFontStylePicker picker, String type)
  {
    Settings sett = Settings.getInstance();
    sett.setColor(PREFIX_COLOR + type, picker.getSelectedColor());
    sett.setProperty(PREFIX_STYLE + type, picker.getFontStyle());
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    GridBagConstraints gridBagConstraints;

    syntaxColors = new JPanel();
    lineCommentsLabel = new JLabel();
    wbCommandsLabel = new JLabel();
    literalsLabel = new JLabel();
    functionsLabel = new JLabel();
    blockCommentsLabel = new JLabel();
    operatorsLabel = new JLabel();
    keywordsLabel = new JLabel();
    dataTypesLabel = new JLabel();
    quoteIdLabel = new JLabel();
    keyword1 = new WbFontStylePicker();
    datatypes = new WbFontStylePicker();
    operators = new WbFontStylePicker();
    functions = new WbFontStylePicker();
    wbKeywords = new WbFontStylePicker();
    literals = new WbFontStylePicker();
    quotedIds = new WbFontStylePicker();
    blockComments = new WbFontStylePicker();
    lineComments = new WbFontStylePicker();
    editorColors = new JPanel();
    currLineLabel = new JLabel();
    currLineColor = new WbColorPicker(true);
    selectionColorLabel = new JLabel();
    selectionColor = new WbColorPicker(true);
    errorColorLabel = new JLabel();
    errorColor = new WbColorPicker();
    textColor = new WbColorPicker(true);
    textColorLabel = new JLabel();
    bgColorLabel = new JLabel();
    bgColor = new WbColorPicker(true);
    cursorLabel = new JLabel();
    cursorColor = new WbColorPicker(true);
    currentStmtColor = new WbColorPicker();
    currentStmtLabel = new JLabel();
    lineNumberBgLabel = new JLabel();
    lineNumberBgColor = new WbColorPicker(true);
    lineNumberLabel = new JLabel();
    lineNumberColor = new WbColorPicker(true);

    setLayout(new GridBagLayout());

    syntaxColors.setBorder(BorderFactory.createTitledBorder(ResourceMgr.getString("LblSyntaxColors"))); // NOI18N
    syntaxColors.setLayout(new GridBagLayout());

    lineCommentsLabel.setText(ResourceMgr.getString("LblColorComment2")); // NOI18N
    lineCommentsLabel.setToolTipText(ResourceMgr.getString("d_LblColorComment2")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 10, 0, 0);
    syntaxColors.add(lineCommentsLabel, gridBagConstraints);

    wbCommandsLabel.setText(ResourceMgr.getString("LblColorKeyword2")); // NOI18N
    wbCommandsLabel.setToolTipText(ResourceMgr.getString("d_LblColorKeyword2")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 5, 0, 0);
    syntaxColors.add(wbCommandsLabel, gridBagConstraints);

    literalsLabel.setText(ResourceMgr.getString("LblColorLiteral")); // NOI18N
    literalsLabel.setToolTipText(ResourceMgr.getString("d_LblColorLiteral")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 10, 0, 0);
    syntaxColors.add(literalsLabel, gridBagConstraints);

    functionsLabel.setText(ResourceMgr.getString("LblColorKeyword3")); // NOI18N
    functionsLabel.setToolTipText(ResourceMgr.getString("d_LblColorKeyword3")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 5, 0, 0);
    syntaxColors.add(functionsLabel, gridBagConstraints);

    blockCommentsLabel.setText(ResourceMgr.getString("LblColorComment1")); // NOI18N
    blockCommentsLabel.setToolTipText(ResourceMgr.getString("d_LblColorComment1")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 10, 0, 0);
    syntaxColors.add(blockCommentsLabel, gridBagConstraints);

    operatorsLabel.setText(ResourceMgr.getString("LblColorOperator")); // NOI18N
    operatorsLabel.setToolTipText(ResourceMgr.getString("d_LblColorOperator")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 5, 0, 0);
    syntaxColors.add(operatorsLabel, gridBagConstraints);

    keywordsLabel.setText(ResourceMgr.getString("LblColorKeyword1")); // NOI18N
    keywordsLabel.setToolTipText(ResourceMgr.getString("d_LblColorKeyword1")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 5, 0, 0);
    syntaxColors.add(keywordsLabel, gridBagConstraints);

    dataTypesLabel.setText(ResourceMgr.getString("LblColorDatatype")); // NOI18N
    dataTypesLabel.setToolTipText(ResourceMgr.getString("d_LblColorDatatype")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 5, 0, 0);
    syntaxColors.add(dataTypesLabel, gridBagConstraints);

    quoteIdLabel.setText(ResourceMgr.getString("LblColorQuotedIds")); // NOI18N
    quoteIdLabel.setToolTipText(ResourceMgr.getString("d_LblColorQuotedIds")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 10, 0, 0);
    syntaxColors.add(quoteIdLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 2, 0, 17);
    syntaxColors.add(keyword1, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 2, 0, 17);
    syntaxColors.add(datatypes, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 2, 0, 17);
    syntaxColors.add(operators, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 2, 0, 17);
    syntaxColors.add(functions, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 2, 0, 17);
    syntaxColors.add(wbKeywords, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 2, 0, 0);
    syntaxColors.add(literals, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 2, 0, 0);
    syntaxColors.add(quotedIds, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 2, 0, 0);
    syntaxColors.add(blockComments, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 2, 0, 0);
    syntaxColors.add(lineComments, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 0, 0, 9);
    add(syntaxColors, gridBagConstraints);

    editorColors.setBorder(BorderFactory.createTitledBorder("Editor Colors"));
    editorColors.setLayout(new GridBagLayout());

    currLineLabel.setText(ResourceMgr.getString("LblCurrLineColor")); // NOI18N
    currLineLabel.setToolTipText(ResourceMgr.getString("d_LblCurrLineColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 15, 0, 0);
    editorColors.add(currLineLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 3, 0, 0);
    editorColors.add(currLineColor, gridBagConstraints);

    selectionColorLabel.setText(ResourceMgr.getString("LblSelectionColor")); // NOI18N
    selectionColorLabel.setToolTipText(ResourceMgr.getString("d_LblSelectionColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 5, 0, 0);
    editorColors.add(selectionColorLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 3, 0, 0);
    editorColors.add(selectionColor, gridBagConstraints);

    errorColorLabel.setText(ResourceMgr.getString("LblSelectErrorColor")); // NOI18N
    errorColorLabel.setToolTipText(ResourceMgr.getString("d_LblSelectErrorColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 15, 0, 0);
    editorColors.add(errorColorLabel, gridBagConstraints);

    errorColor.setToolTipText(ResourceMgr.getString("d_LblSelectErrorColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 3, 0, 0);
    editorColors.add(errorColor, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 3, 0, 0);
    editorColors.add(textColor, gridBagConstraints);

    textColorLabel.setText(ResourceMgr.getString("LblEditorFgColor")); // NOI18N
    textColorLabel.setToolTipText(ResourceMgr.getString("d_LblEditorFgColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 5, 0, 0);
    editorColors.add(textColorLabel, gridBagConstraints);

    bgColorLabel.setText(ResourceMgr.getString("LblEditorBgColor")); // NOI18N
    bgColorLabel.setToolTipText(ResourceMgr.getString("d_LblEditorBgColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 5, 0, 0);
    editorColors.add(bgColorLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 3, 0, 0);
    editorColors.add(bgColor, gridBagConstraints);

    cursorLabel.setText(ResourceMgr.getString("LblEditorCursorColor")); // NOI18N
    cursorLabel.setToolTipText(ResourceMgr.getString("d_LblEditorCursorColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 15, 0, 0);
    editorColors.add(cursorLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 3, 0, 0);
    editorColors.add(cursorColor, gridBagConstraints);

    currentStmtColor.setToolTipText(ResourceMgr.getString("d_LblCurrentStmtColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 3, 0, 0);
    editorColors.add(currentStmtColor, gridBagConstraints);

    currentStmtLabel.setText(ResourceMgr.getString("LblCurrentStmtColor")); // NOI18N
    currentStmtLabel.setToolTipText(ResourceMgr.getString("d_LblCurrentStmtColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 15, 0, 0);
    editorColors.add(currentStmtLabel, gridBagConstraints);

    lineNumberBgLabel.setText(ResourceMgr.getString("LblLineNrBg")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 15, 0, 0);
    editorColors.add(lineNumberBgLabel, gridBagConstraints);

    lineNumberBgColor.setToolTipText(ResourceMgr.getString("d_LblCurrentStmtColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 3, 0, 0);
    editorColors.add(lineNumberBgColor, gridBagConstraints);

    lineNumberLabel.setText(ResourceMgr.getString("LblLineNrText")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 15, 0, 0);
    editorColors.add(lineNumberLabel, gridBagConstraints);

    lineNumberColor.setToolTipText(ResourceMgr.getString("d_LblCurrentStmtColor")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 3, 0, 0);
    editorColors.add(lineNumberColor, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new Insets(0, 0, 7, 9);
    add(editorColors, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private WbColorPicker bgColor;
  private JLabel bgColorLabel;
  private WbFontStylePicker blockComments;
  private JLabel blockCommentsLabel;
  private WbColorPicker currLineColor;
  private JLabel currLineLabel;
  private WbColorPicker currentStmtColor;
  private JLabel currentStmtLabel;
  private WbColorPicker cursorColor;
  private JLabel cursorLabel;
  private JLabel dataTypesLabel;
  private WbFontStylePicker datatypes;
  private JPanel editorColors;
  private WbColorPicker errorColor;
  private JLabel errorColorLabel;
  private WbFontStylePicker functions;
  private JLabel functionsLabel;
  private WbFontStylePicker keyword1;
  private JLabel keywordsLabel;
  private WbFontStylePicker lineComments;
  private JLabel lineCommentsLabel;
  private WbColorPicker lineNumberBgColor;
  private JLabel lineNumberBgLabel;
  private WbColorPicker lineNumberColor;
  private JLabel lineNumberLabel;
  private WbFontStylePicker literals;
  private JLabel literalsLabel;
  private WbFontStylePicker operators;
  private JLabel operatorsLabel;
  private JLabel quoteIdLabel;
  private WbFontStylePicker quotedIds;
  private WbColorPicker selectionColor;
  private JLabel selectionColorLabel;
  private JPanel syntaxColors;
  private WbColorPicker textColor;
  private JLabel textColorLabel;
  private JLabel wbCommandsLabel;
  private WbFontStylePicker wbKeywords;
  // End of variables declaration//GEN-END:variables

}
