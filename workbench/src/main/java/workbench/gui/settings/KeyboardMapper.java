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
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;

import workbench.resource.ResourceMgr;
import workbench.resource.StoreableKeyStroke;

/**
 *
 * @author  Thomas Kellerer
 */
public class KeyboardMapper
  extends JPanel
  implements KeyListener, FocusListener
{
  private JTextField display;
  private JTextField alternateKey;
  private KeyStroke newPrimaryKey;
  private KeyStroke newAlternateKey;
  private boolean cancelled;
  private Border focusBorder;
  private Border originalBorder = null;

  public KeyboardMapper()
  {
    this(true);
  }

  public KeyboardMapper(boolean includeAlternate)
  {
    super(new GridBagLayout());
    JLabel primary = new JLabel(ResourceMgr.getString("LblKeyDefKeyCol"));
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.LINE_START;
    gc.fill = GridBagConstraints.NONE;
    gc.insets = new Insets(0,0,8,8);
    this.add(primary, gc);
    gc.gridx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1.0;
    this.display = createField();
    this.originalBorder = this.display.getBorder();
    this.add(display, gc);

    if (includeAlternate)
    {
      this.alternateKey = createField();
      JLabel alternate = new JLabel(ResourceMgr.getString("LblKeyDefAlternate"));
      gc.gridx = 0;
      gc.gridy = 1;
      gc.anchor = GridBagConstraints.LINE_START;
      gc.fill = GridBagConstraints.NONE;
      this.add(alternate, gc);
      gc.gridx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weightx = 1.0;
      this.add(alternateKey, gc);

      display.addFocusListener(this);
      alternateKey.addFocusListener(this);
    }
    focusBorder = new CompoundBorder(new LineBorder(Color.YELLOW, 1), originalBorder);
  }

  public void setCurrentPrimaryKey(KeyStroke key)
  {
    this.newPrimaryKey = key;
    updateDisplay();
  }

  public void setCurrentAlternateKey(KeyStroke key)
  {
    this.newAlternateKey = key;
    updateDisplay();
  }

  private JTextField createField()
  {
    JTextField field = new JTextField(20);
    field.addKeyListener(this);
    field.setEditable(false);
    field.setDisabledTextColor(field.getForeground());
    field.setBackground(UIManager.getColor("TextArea.background"));
    field.setFocusable(true);
    return field;
  }

  @Override
  public void focusGained(FocusEvent e)
  {
    if (e.isTemporary()) return;
    ((JComponent)e.getComponent()).setBorder(focusBorder);
  }

  @Override
  public void focusLost(FocusEvent e)
  {
    ((JComponent)e.getComponent()).setBorder(originalBorder);
  }

  @Override
  public void grabFocus()
  {
    this.display.grabFocus();
    this.display.requestFocusInWindow();
  }

  @Override
  public void keyPressed(KeyEvent e)
  {
  }

  @Override
  public void keyReleased(KeyEvent e)
  {
    int modifier = e.getModifiersEx();
    int code = e.getKeyCode();

    if (modifier == 0 && code == KeyEvent.VK_BACK_SPACE)
    {
      if (e.getSource() == display)
      {
        this.newPrimaryKey = null;
      }
      else if (e.getSource() == alternateKey)
      {
        this.newAlternateKey = null;
      }
      updateDisplay();
      return;
    }

    // only allow regular keys with modifier
    if (modifier == 0 && !e.isActionKey()) return;

    // keyReleased is also called when the Ctrl or Shift keys are release
    // in that case the keycode is 0 --> ignore it
    if (code >= 32
      || code == KeyEvent.VK_ENTER
      || code == KeyEvent.VK_TAB
      || code == KeyEvent.VK_ESCAPE)
    {

      if (e.getSource() == display)
      {
        this.newPrimaryKey = KeyStroke.getKeyStroke(code, modifier);
      }
      else if (e.getSource() == alternateKey)
      {
        this.newAlternateKey= KeyStroke.getKeyStroke(code, modifier);
      }
      updateDisplay();
    }
  }

  private void updateDisplay()
  {
    showKey(display, newPrimaryKey);
    if (alternateKey != null)
    {
      showKey(alternateKey, newAlternateKey);
    }
  }

  private void showKey(JTextField keyDisplay, KeyStroke keyStroke)
  {
    keyDisplay.setText(StoreableKeyStroke.displayString(keyStroke));
  }

  public KeyStroke getAltenateKeyStroke()
  {
    return this.newAlternateKey;
  }

  public KeyStroke getKeyStroke()
  {
    return this.newPrimaryKey;
  }

  @Override
  public void keyTyped(KeyEvent e)
  {
  }

  public boolean isCancelled()
  {
    return this.cancelled;
  }

  public boolean show(JComponent parent)
  {
    String[] options = new String[] {
      ResourceMgr.getPlainString("LblOK"),
      ResourceMgr.getPlainString("LblCancel")
    };

    JOptionPane mapperPanel = new JOptionPane(this, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, options);
    JDialog dialog = mapperPanel.createDialog(parent, ResourceMgr.getString("LblEnterKeyWindowTitle"));

    this.cancelled = true;
    dialog.setResizable(true);
    EventQueue.invokeLater(this::grabFocus);

    dialog.setVisible(true);
    Object result = mapperPanel.getValue();
    dialog.dispose();

    if (options[0].equals(result))
    {
      this.cancelled = false;
      return true;
    }
    return false;
  }

}
