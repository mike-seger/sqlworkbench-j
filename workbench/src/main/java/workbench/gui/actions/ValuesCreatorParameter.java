/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.gui.actions;

import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.WbScrollPane;
import workbench.gui.editor.ValuesListCreator;

import workbench.util.StringUtil;
import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class ValuesCreatorParameter
  extends JPanel
  implements DocumentListener
{
  private final String delimiterProp = "workbench.gui.values.creator.delimiter";
  private final String regexProp = "workbench.gui.values.creator.regex";
  private final String emptyStringProp = "workbench.gui.values.creator.emptystring.null";
  private final String trimSepProp = "workbench.gui.values.creator.trim.delimiter";
  private final String nullStringProp = "workbench.gui.values.creator.nullstring";
  private final String replaceQuotesProp = "workbench.gui.values.creator.replacequotes";
  private final String addValuesKwProp = "workbench.gui.values.creator.addvalueskw";
  private final String ignoreFirstLineProp = "workbench.gui.values.creator.ignorefirstline";
  private final String addSemicolonProp = "workbench.gui.values.creator.addsemicolon";

  private String input;
  private Thread previewThread;

  public ValuesCreatorParameter(String text)
  {
    initComponents();
    restoreSettings();
    this.input = text;
    this.previewArea.setFont(Settings.getInstance().getEditorFont());
    this.previewArea.setText(input);
    Color bg = GuiSettings.getEditorBackground();
    if (bg == null)
    {
      bg = this.previewArea.getBackground();
    }
    this.previewArea.setEditable(false);
    // Setting the background must be done after turning off the editable flag,
    // otherwise the edit area will be shown "disabled" with a gray background
    this.previewArea.setBackground(bg);
    this.previewArea.setForeground(GuiSettings.getEditorForeground());
    this.previewArea.setTabSize(Settings.getInstance().getEditorTabWidth());
    this.delimiter.getDocument().addDocumentListener(this);
    this.nullString.getDocument().addDocumentListener(this);
    startPreview();
  }

  public boolean getAddValuesClause()
  {
    return addValuesKw.isSelected();
  }

  public String getNullString()
  {
    return StringUtil.trimToNull(nullString.getText());
  }

  public String getDelimiter()
  {
    return delimiter.getText();
  }

  public boolean getReplaceDoubleQuotes()
  {
    return replaceQuotes.isSelected();
  }

  public boolean isRegex()
  {
    return isRegex.isSelected();
  }

  public boolean getEmptyStringIsNull()
  {
    return emptyString.isSelected();
  }

  public boolean getTrimDelimiter()
  {
    return trimDelimiter.isSelected();
  }

  public boolean getIgnoreFirstLine()
  {
    return ignoreFirstLine.isSelected();
  }

  public boolean getAddSemicolon()
  {
    return addSemicolon.isSelected();
  }

  public void setFocusToInput()
  {
    this.delimiter.requestFocus();
    this.delimiter.selectAll();
  }

  public void restoreSettings()
  {
    String delim = Settings.getInstance().getProperty(delimiterProp, null);
    delimiter.setText(delim);
    isRegex.setSelected(Settings.getInstance().getBoolProperty(regexProp, false));
    emptyString.setSelected(Settings.getInstance().getBoolProperty(emptyStringProp, false));
    trimDelimiter.setSelected(Settings.getInstance().getBoolProperty(trimSepProp, true));
    replaceQuotes.setSelected(Settings.getInstance().getBoolProperty(replaceQuotesProp, true));
    nullString.setText(Settings.getInstance().getProperty(nullStringProp, null));
    addValuesKw.setSelected(Settings.getInstance().getBoolProperty(addValuesKwProp, false));
    addSemicolon.setSelected(Settings.getInstance().getBoolProperty(addSemicolonProp, false));
    ignoreFirstLine.setSelected(Settings.getInstance().getBoolProperty(ignoreFirstLineProp, false));

  }

  public void saveSettings()
  {
    Settings.getInstance().setProperty(delimiterProp, getDelimiter());
    Settings.getInstance().setProperty(regexProp, isRegex());
    Settings.getInstance().setProperty(emptyStringProp, getEmptyStringIsNull());
    Settings.getInstance().setProperty(trimSepProp, getTrimDelimiter());
    Settings.getInstance().setProperty(nullStringProp, getNullString());
    Settings.getInstance().setProperty(replaceQuotesProp, getReplaceDoubleQuotes());
    Settings.getInstance().setProperty(addValuesKwProp, getAddValuesClause());
    Settings.getInstance().setProperty(ignoreFirstLineProp, getIgnoreFirstLine());
    Settings.getInstance().setProperty(addSemicolonProp, getAddSemicolon());
  }

  public void startPreview()
  {
    if (previewThread != null)
    {
      // schedule for later
      WbSwingUtilities.invokeLater(this::startPreview);
      return;
    }

    previewThread = new WbThread("ValuesCreator Preview")
    {
      @Override
      public void run()
      {
        doPreview();
      }
    };
    previewThread.start();
  }

  private synchronized void doPreview()
  {
    try
    {
      ValuesListCreator creator = new ValuesListCreator(input, getDelimiter(), isRegex());
      creator.setEmptyStringIsNull(getEmptyStringIsNull());
      creator.setTrimDelimiter(getTrimDelimiter());
      creator.setLineEnding("\n");
      creator.setNullString(getNullString());
      creator.setIgnoreFirstLine(getIgnoreFirstLine());
      creator.setAddSemicolon(getAddSemicolon());
      creator.setAddValuesClause(getAddValuesClause());
      creator.setReplaceDoubleQuotes(getReplaceDoubleQuotes());
      String result = creator.createValuesList();
      WbSwingUtilities.invokeLater(() -> previewArea.setText(result));
    }
    catch (Throwable th)
    {
      // This can happen if the current delimiter isn't a valid regex
      previewArea.setText(input);
    }
    finally
    {
      previewThread = null;
    }
  }

  private void textChanged()
  {
    startPreview();
  }

  @Override
  public void insertUpdate(DocumentEvent e)
  {
    textChanged();
  }

  @Override
  public void removeUpdate(DocumentEvent e)
  {
    textChanged();
  }

  @Override
  public void changedUpdate(DocumentEvent e)
  {
    textChanged();
  }


  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    java.awt.GridBagConstraints gridBagConstraints;

    jLabel1 = new javax.swing.JLabel();
    delimiter = new javax.swing.JTextField();
    jScrollPane1 = new WbScrollPane();
    previewArea = new javax.swing.JTextArea();
    previewButton = new javax.swing.JButton();
    jLabel2 = new javax.swing.JLabel();
    nullString = new javax.swing.JTextField();
    jPanel1 = new javax.swing.JPanel();
    trimDelimiter = new javax.swing.JCheckBox();
    isRegex = new javax.swing.JCheckBox();
    emptyString = new javax.swing.JCheckBox();
    replaceQuotes = new javax.swing.JCheckBox();
    addValuesKw = new javax.swing.JCheckBox();
    addSemicolon = new javax.swing.JCheckBox();
    ignoreFirstLine = new javax.swing.JCheckBox();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText(ResourceMgr.getString("LblFieldDelimiter")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(jLabel1, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 0);
    add(delimiter, gridBagConstraints);

    previewArea.setColumns(60);
    previewArea.setRows(10);
    jScrollPane1.setViewportView(previewArea);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
    add(jScrollPane1, gridBagConstraints);

    previewButton.setText(ResourceMgr.getString("LblPreview")); // NOI18N
    previewButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        previewButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
    add(previewButton, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblNullString")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 0, 0);
    add(jLabel2, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(7, 12, 0, 0);
    add(nullString, gridBagConstraints);

    jPanel1.setLayout(new java.awt.GridBagLayout());

    trimDelimiter.setText(ResourceMgr.getString("LblTrimSeparator")); // NOI18N
    trimDelimiter.setBorder(null);
    trimDelimiter.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        trimDelimiterActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 14, 0, 0);
    jPanel1.add(trimDelimiter, gridBagConstraints);

    isRegex.setText(ResourceMgr.getString("LblDelimIsRegex")); // NOI18N
    isRegex.setBorder(null);
    isRegex.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        isRegexActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    jPanel1.add(isRegex, gridBagConstraints);

    emptyString.setText(ResourceMgr.getString("LblEmptyStringIsNull")); // NOI18N
    emptyString.setBorder(null);
    emptyString.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        emptyStringActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    jPanel1.add(emptyString, gridBagConstraints);

    replaceQuotes.setText(ResourceMgr.getString("LblReplaceDQuotes")); // NOI18N
    replaceQuotes.setBorder(null);
    replaceQuotes.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        replaceQuotesActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 14, 0, 0);
    jPanel1.add(replaceQuotes, gridBagConstraints);

    addValuesKw.setText(ResourceMgr.getString("LblAddValuesKw")); // NOI18N
    addValuesKw.setBorder(null);
    addValuesKw.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        addValuesKwActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    jPanel1.add(addValuesKw, gridBagConstraints);

    addSemicolon.setText(ResourceMgr.getString("LblAddSemi")); // NOI18N
    addSemicolon.setBorder(null);
    addSemicolon.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        addSemicolonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(6, 14, 0, 0);
    jPanel1.add(addSemicolon, gridBagConstraints);

    ignoreFirstLine.setText(ResourceMgr.getString("LblIgnoreFirstLine")); // NOI18N
    ignoreFirstLine.setBorder(null);
    ignoreFirstLine.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        ignoreFirstLineActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    jPanel1.add(ignoreFirstLine, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.gridheight = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 9, 0);
    add(jPanel1, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void previewButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_previewButtonActionPerformed
  {//GEN-HEADEREND:event_previewButtonActionPerformed
    doPreview();
  }//GEN-LAST:event_previewButtonActionPerformed

  private void isRegexActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_isRegexActionPerformed
  {//GEN-HEADEREND:event_isRegexActionPerformed
    doPreview();
  }//GEN-LAST:event_isRegexActionPerformed

  private void emptyStringActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_emptyStringActionPerformed
  {//GEN-HEADEREND:event_emptyStringActionPerformed
    doPreview();
  }//GEN-LAST:event_emptyStringActionPerformed

  private void trimDelimiterActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_trimDelimiterActionPerformed
  {//GEN-HEADEREND:event_trimDelimiterActionPerformed
    doPreview();
  }//GEN-LAST:event_trimDelimiterActionPerformed

  private void replaceQuotesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_replaceQuotesActionPerformed
  {//GEN-HEADEREND:event_replaceQuotesActionPerformed
    doPreview();
  }//GEN-LAST:event_replaceQuotesActionPerformed

  private void ignoreFirstLineActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ignoreFirstLineActionPerformed
  {//GEN-HEADEREND:event_ignoreFirstLineActionPerformed
    doPreview();
  }//GEN-LAST:event_ignoreFirstLineActionPerformed

  private void addSemicolonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_addSemicolonActionPerformed
  {//GEN-HEADEREND:event_addSemicolonActionPerformed
    doPreview();
  }//GEN-LAST:event_addSemicolonActionPerformed

  private void addValuesKwActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_addValuesKwActionPerformed
  {//GEN-HEADEREND:event_addValuesKwActionPerformed
    doPreview();
  }//GEN-LAST:event_addValuesKwActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JCheckBox addSemicolon;
  private javax.swing.JCheckBox addValuesKw;
  private javax.swing.JTextField delimiter;
  private javax.swing.JCheckBox emptyString;
  private javax.swing.JCheckBox ignoreFirstLine;
  private javax.swing.JCheckBox isRegex;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTextField nullString;
  private javax.swing.JTextArea previewArea;
  private javax.swing.JButton previewButton;
  private javax.swing.JCheckBox replaceQuotes;
  private javax.swing.JCheckBox trimDelimiter;
  // End of variables declaration//GEN-END:variables
}
