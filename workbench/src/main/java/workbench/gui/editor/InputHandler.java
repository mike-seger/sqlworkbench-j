package workbench.gui.editor;

/*
 * InputHandler.java - Manages key bindings and executes actions
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;

import workbench.resource.GuiSettings;
import workbench.resource.Settings;
import workbench.resource.ShortcutManager;

import workbench.gui.actions.WbAction;
import workbench.gui.editor.actions.DelPrevWord;
import workbench.gui.editor.actions.DeleteChar;
import workbench.gui.editor.actions.DeleteCurrentLine;
import workbench.gui.editor.actions.DeleteWord;
import workbench.gui.editor.actions.DocumentEnd;
import workbench.gui.editor.actions.DocumentHome;
import workbench.gui.editor.actions.DuplicateCurrentLine;
import workbench.gui.editor.actions.EditorAction;
import workbench.gui.editor.actions.IndentSelection;
import workbench.gui.editor.actions.LineEnd;
import workbench.gui.editor.actions.LineStart;
import workbench.gui.editor.actions.NextChar;
import workbench.gui.editor.actions.NextLine;
import workbench.gui.editor.actions.NextPage;
import workbench.gui.editor.actions.NextWord;
import workbench.gui.editor.actions.PrevWord;
import workbench.gui.editor.actions.PreviousChar;
import workbench.gui.editor.actions.PreviousLine;
import workbench.gui.editor.actions.PreviousPage;
import workbench.gui.editor.actions.SelectDocumentEnd;
import workbench.gui.editor.actions.SelectDocumentHome;
import workbench.gui.editor.actions.SelectLineEnd;
import workbench.gui.editor.actions.SelectLineStart;
import workbench.gui.editor.actions.SelectNextChar;
import workbench.gui.editor.actions.SelectNextLine;
import workbench.gui.editor.actions.SelectNextPage;
import workbench.gui.editor.actions.SelectNextWord;
import workbench.gui.editor.actions.SelectPrevWord;
import workbench.gui.editor.actions.SelectPreviousChar;
import workbench.gui.editor.actions.SelectPreviousLine;
import workbench.gui.editor.actions.SelectPreviousPage;
import workbench.gui.fontzoom.DecreaseFontSize;
import workbench.gui.fontzoom.IncreaseFontSize;
import workbench.gui.fontzoom.ResetFontSize;

import workbench.util.StringUtil;


/**
 * An input handler converts the user's key strokes into concrete actions.
 * It also takes care of macro recording and action repetition.<p>
 *
 * This class provides all the necessary support code for an input
 * handler, but doesn't actually do any key binding logic. It is up
 * to the implementations of this class to do so.
 *
 * @author Slava Pestov (initial developer)
 * @author Thomas Kellerer (enhancements and bugfixes)
 */
public class InputHandler
  extends KeyAdapter
  implements ChangeListener, PropertyChangeListener
{
  /**
   * If this client property is set to Boolean.TRUE on the text area,
   * the home/end keys will support 'smart' BRIEF-like behaviour
   * (one press = start/end of line, two presses = start/end of
   * viewscreen, three presses = start/end of document). By default,
   * this property is not set.
   */
  public static final String SMART_HOME_END_PROPERTY = "InputHandler.homeEnd";

  private final ActionListener backspaceAction = new Backspace();
  private final ActionListener overWriteAction = new Overwrite();

  private final EditorAction delete = new DeleteChar();

  private final EditorAction deleteWord = new DeleteWord();
  private final EditorAction delPrevWord = new DelPrevWord();

  private final EditorAction documentEnd = new DocumentEnd();
  private final EditorAction selectDocEnd = new SelectDocumentEnd();

  private final EditorAction lineEnd = new LineEnd();
  private final EditorAction selectLineEnd = new SelectLineEnd();

  private final EditorAction lineStart = new LineStart();
  private final EditorAction selectLineStart = new SelectLineStart();

  private final EditorAction documentHome = new DocumentHome();
  private final EditorAction selectDocHome = new SelectDocumentHome();

  private final ActionListener insertBreak = new InsertBreak();
  private final ActionListener insertTab = new InsertTab();

  private final EditorAction prevWord = new PrevWord();
  private final EditorAction selectPrevWord = new SelectPrevWord();
  private final EditorAction nextWord = new NextWord();
  private final EditorAction selectNextWord = new SelectNextWord();

  private final EditorAction nextChar = new NextChar();
  private final EditorAction selectNextChar = new SelectNextChar();
  private final EditorAction prevChar = new PreviousChar();
  private final EditorAction selectPrevChar = new SelectPreviousChar();

  private final EditorAction nextPage = new NextPage();
  private final EditorAction prevPage = new PreviousPage();
  private final EditorAction selectPrevPage = new SelectPreviousPage();
  private final EditorAction selectNextPage = new SelectNextPage();

  private final EditorAction nextLine = new NextLine();
  private final EditorAction selectNextLine = new SelectNextLine();
  private final EditorAction selectPrevLine = new SelectPreviousLine();
  private final EditorAction prevLine = new PreviousLine();

  private final WbAction increaseFont = new IncreaseFontSize();
  private final WbAction decreaseFont = new DecreaseFontSize();
  private final WbAction resetFont = new ResetFontSize();
  private final WbAction dupeLine = new DuplicateCurrentLine();

  // Default action
  private final ActionListener INSERT_CHAR = new InsertChar();

  private Map<KeyStroke, ActionListener> bindings;

  private boolean sequenceIsMapped = false;
  private boolean enabled = true;

  private KeyStroke expandKey;

  public InputHandler()
  {
    initKeyBindings();
    ShortcutManager.getInstance().addChangeListener(this);
    Settings.getInstance().addPropertyChangeListener(this, GuiSettings.PROPERTY_EXPAND_KEYSTROKE);
  }

  /**
   * Adds the default key bindings to this input handler.
   */
  public final void initKeyBindings()
  {
    bindings = new HashMap<>();
    addKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), backspaceAction);

    addKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), delete);

    addKeyBinding(delPrevWord);
    addKeyBinding(deleteWord);

    addKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), insertBreak);
    addKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), insertTab);

    addKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), overWriteAction);

    addKeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), new CancelRectangleSelect());

    addKeyBinding(lineStart);
    addKeyBinding(selectLineStart);

    addKeyBinding(lineEnd);
    addKeyBinding(selectLineEnd);

    addKeyBinding(documentHome);
    addKeyBinding(selectDocHome);

    addKeyBinding(documentEnd);
    addKeyBinding(selectDocEnd);


    addKeyBinding(prevPage);
    addKeyBinding(selectPrevPage);

    addKeyBinding(nextPage);
    addKeyBinding(selectNextPage);

    addKeyBinding(prevChar);
    addKeyBinding(selectPrevChar);

    addKeyBinding(prevWord);
    addKeyBinding(selectPrevWord);

    addKeyBinding(nextChar);
    addKeyBinding(selectNextChar);

    addKeyBinding(nextWord);
    addKeyBinding(selectNextWord);

    addKeyBinding(prevLine);
    addKeyBinding(selectPrevLine);

    addKeyBinding(nextLine);
    addKeyBinding(selectNextLine);

    addKeyBinding(increaseFont);
    addKeyBinding(decreaseFont);
    addKeyBinding(resetFont);
    addKeyBinding(new DeleteCurrentLine());
    addKeyBinding(dupeLine);
    expandKey = GuiSettings.getExpansionKey();
  }

  public void addKeyBinding(WbAction action)
  {
    KeyStroke key = action.getAccelerator();
    if (key != null)
    {
      bindings.put(action.getAccelerator(), action);
    }
  }

  @SuppressWarnings("unchecked")
  public void addKeyBinding(KeyStroke key, ActionListener action)
  {
    bindings.put(key, action);
  }

  /**
   * Removes a key binding from this input handler.
   *
   * @param key The key binding
   */
  public void removeKeyBinding(KeyStroke key)
  {
    bindings.remove(key);
  }

  /**
   * Removes the key binding for the given action from this input handler.
   *
   * @param key The key binding
   */
  public void removeKeyBinding(WbAction action)
  {
    KeyStroke key = action.getAccelerator();
    if (key != null)
    {
      this.bindings.remove(key);
    }
    key = action.getAlternateAccelerator();
    if (key != null)
    {
      bindings.remove(key);
    }
  }

  /**
   * Removes all key bindings from this input handler.
   */
  public void removeAllKeyBindings()
  {
    bindings.clear();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    expandKey = GuiSettings.getExpansionKey();
  }

  @Override
  public void stateChanged(ChangeEvent e)
  {
    initKeyBindings();
  }

  /**
   * Clears all keybindings and un-registers the changelistener from the shortcutmanager
   */
  public void dispose()
  {
    ShortcutManager.getInstance().removeChangeListener(this);
    Settings.getInstance().removePropertyChangeListener(this);
    removeAllKeyBindings();
  }

  public void setEnabled(boolean flag)
  {
    this.enabled = flag;
  }

  @Override
  public void keyPressed(final KeyEvent evt)
  {
    if (!enabled) return;

    int keyCode = evt.getKeyCode();

    KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(evt);

    if (!evt.isActionKey() && !sequenceIsMapped)
    {
      sequenceIsMapped = isMapped(evt);
    }

    if (keyCode == KeyEvent.VK_CONTEXT_MENU)
    {
      evt.consume();
      final JEditTextArea area = getTextArea(evt);
      EventQueue.invokeLater(area::showContextMenu);
      return;
    }

    if (expandKey != null && expandKey.equals(keyStroke))
    {
      JEditTextArea area = getTextArea(evt);
      if (area.expandWordAtCursor())
      {
        // setting sequencedMapped to true will prevent keyTyped() to do the expansion again
        // in case the triggering keystroke is a regular space character.
        sequenceIsMapped = true;
        evt.consume();
        return;
      }
    }

    ActionListener l = bindings.get(keyStroke);

    // workaround to enable Shift-Backspace to behave like Backspace
    if (l == null && keyCode == KeyEvent.VK_BACK_SPACE && evt.isShiftDown())
    {
      l = backspaceAction;
    }

    if (l != null)
    {
      evt.consume();
      executeAction(l, evt.getSource(), null);
    }
  }

  void resetStatus()
  {
    sequenceIsMapped = false;
  }

  @Override
  public void keyTyped(KeyEvent evt)
  {
    if (!enabled) return;
    if (evt.isConsumed()) return;

    boolean isMapped = sequenceIsMapped;
    sequenceIsMapped = false;

    if (isMapped)
    {
      return;
    }

    char c = evt.getKeyChar();

    // For some reason we still wind up here even if Ctrl-Space was
    // already handled by keyPressed
    if (c == 0x20 && evt.getModifiersEx() == KeyEvent.CTRL_DOWN_MASK)
    {
      KeyStroke pressed = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_DOWN_MASK);
      if (bindings.get(pressed) != null)
      {
        // already processed!
        evt.consume();
        return;
      }
    }

    if (c >= 0x20 && c != 0x7f)
    {
      KeyStroke key = KeyStroke.getKeyStrokeForEvent(evt);
      ActionListener l = bindings.get(key);

      if (l == null)
      {
        // Nothing mapped --> insert a character
        l = INSERT_CHAR;
      }
      evt.consume();
      executeAction(l, evt.getSource(), String.valueOf(c));
    }
  }

  @Override
  public void keyReleased(KeyEvent evt)
  {
    resetStatus();
  }

  public List<KeyStroke> getKeys(JMenu menu)
  {
    if (menu == null) return Collections.emptyList();
    List<KeyStroke> allKeys = new ArrayList<>();

    for (int i=0; i < menu.getItemCount(); i++)
    {
      JMenuItem item = menu.getItem(i);
      allKeys.addAll( getKeys(item));
      if (item instanceof JMenu)
      {
        allKeys.addAll(getKeys((JMenu)item));
      }
    }
    return allKeys;
  }

  public List<KeyStroke> getKeys(JComponent c)
  {
    if (c == null) return Collections.emptyList();
    int types[] = new int[] {JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, JComponent.WHEN_IN_FOCUSED_WINDOW};

    List<KeyStroke> allKeys = new ArrayList<>();
    for (int when : types)
    {
      InputMap map = c.getInputMap(when);
      KeyStroke[] keys = (map != null ? map.allKeys() : null);

      if (keys != null)
      {
        allKeys.addAll(Arrays.asList(keys));
      }
    }
    return allKeys;
  }

  public boolean isMapped(KeyEvent evt)
  {
    if (evt == null) return false;

    KeyStroke toTest = KeyStroke.getKeyStrokeForEvent(evt);
    if (toTest.getModifiers() == 0) return false;

    int code = toTest.getKeyCode();

    // if the keycode indicates a modifier key, only that key was
    // pressed, the modifier alone cannot be mapped...
    if (code == KeyEvent.VK_ALT || code == KeyEvent.VK_CONTROL ||
        code == KeyEvent.VK_META || code == KeyEvent.VK_CONTEXT_MENU)
    {
      return false;
    }

    JEditTextArea area = getTextArea(evt);

    List<KeyStroke> allKeys = new ArrayList<>();
    allKeys.addAll(getKeys(area));

    Window w = SwingUtilities.getWindowAncestor(area);
    if (w instanceof JFrame)
    {
      JMenuBar bar = ((JFrame)w).getJMenuBar();
      if (bar != null && bar.getComponents() != null)
      {
        for (Component c : bar.getComponents())
        {
          allKeys.addAll( getKeys((JComponent)c) );
          if (c instanceof JMenu)
          {
            JMenu m = (JMenu)c;
            allKeys.addAll(getKeys(m));
          }
        }
      }
    }
    return allKeys.contains(toTest);
  }


  /**
   * Executes the specified action
   *
   * @param listener The action listener
   * @param source The event source
   * @param actionCommand The action command
   */
  public void executeAction(ActionListener listener, Object source, String actionCommand)
  {
    if (!enabled) return;
    ActionEvent evt = new ActionEvent(source, ActionEvent.ACTION_PERFORMED, actionCommand);
    listener.actionPerformed(evt);
  }

  /**
   * Returns the text area that fired the specified event.
   * @param evt The event
   */
  public static JEditTextArea getTextArea(EventObject evt)
  {
    if (evt != null)
    {
      Object o = evt.getSource();
      if (o instanceof Component)
      {
        // find the parent text area
        Component c = (Component) o;
        for (;;)
        {
          if (c instanceof JEditTextArea)
          {
            return (JEditTextArea) c;
          }
          else if (c == null)
          {
            break;
          }
          if (c instanceof JPopupMenu)
          {
            c = ((JPopupMenu) c).getInvoker();
          }
          else
          {
            c = c.getParent();
          }
        }
      }
    }
    return null;
  }

  public static class Backspace implements ActionListener
  {
    @Override
    public void actionPerformed(ActionEvent evt)
    {
      JEditTextArea textArea = getTextArea(evt);

      if (!textArea.isEditable())
      {
        return;
      }

      if (textArea.getSelectionStart() != textArea.getSelectionEnd())
      {
        if (textArea.isEmptyRectangleSelection())
        {
          textArea.doRectangleBackspace();
        }
        else
        {
          textArea.setSelectedText("");
        }
      }
      else
      {
        int caret = textArea.getCaretPosition();
        if (caret == 0) return;

        boolean removeNext = textArea.removeClosingBracket(caret);
        int len = removeNext ? 2 : 1;

        SyntaxDocument doc = textArea.getDocument();
        try
        {
          doc.remove(caret - 1, len);
        }
        catch (BadLocationException bl)
        {
          bl.printStackTrace();
        }
      }
    }
  }

  public static class InsertBreak implements ActionListener
  {
    @Override
    public void actionPerformed(ActionEvent evt)
    {
      JEditTextArea textArea = getTextArea(evt);

      if (!textArea.isEditable())
      {
        textArea.getToolkit().beep();
        return;
      }

      textArea.setSelectedText("\n");
    }
  }

  public static class ShiftTab implements ActionListener
  {
    @Override
    public void actionPerformed(ActionEvent evt)
    {
      JEditTextArea textArea = getTextArea(evt);
      if (!textArea.isEditable())
      {
        textArea.getToolkit().beep();
        return;
      }

      int start = textArea.getSelectionStart();
      int end = textArea.getSelectionEnd();

      if (start < end)
      {
        TextIndenter indenter = new TextIndenter(textArea);
        indenter.unIndentSelection();
      }
    }
  }

  public static class InsertTab implements ActionListener
  {
    @Override
    public void actionPerformed(ActionEvent evt)
    {
      JEditTextArea textArea = getTextArea(evt);

      if (!textArea.isEditable())
      {
        textArea.getToolkit().beep();
        return;
      }

      String action = ShortcutManager.getInstance().getActionClassForKey(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
      // If the  "IndentSelection" action isn't mapped to the Tab key, then we shouldn't
      // do it here. But we can't completely remove it here, as then we wouldn't be able
      // to deal with the TAB key in the editor.
      if (IndentSelection.class.getName().equals(action) && textArea.getSelectionLength() > 1)
      {
        TextIndenter indenter = new TextIndenter(textArea);
        indenter.indentSelection();
      }
      else
      {
        boolean useTab = Settings.getInstance().getEditorUseTabCharacter();
        if (useTab)
        {
          textArea.overwriteSetSelectedText("\t");
        }
        else
        {
          int tabSize = Settings.getInstance().getEditorTabWidth();
          int lineStart = textArea.getLineStartOffset(textArea.getCaretLine());
          int posInLine = textArea.getCaretPosition() - lineStart;
          int inc = (tabSize - (posInLine % tabSize));
          char[] indent = new char[inc];
          Arrays.fill(indent, ' ');
          String spaces = new String(indent);
          textArea.overwriteSetSelectedText(spaces);
        }
      }
    }
  }

  public static class Overwrite implements ActionListener
  {
    @Override
    public void actionPerformed(ActionEvent evt)
    {
      JEditTextArea textArea = getTextArea(evt);
      textArea.setOverwriteEnabled(!textArea.isOverwriteEnabled());
    }
  }

  public static class CancelRectangleSelect
    implements ActionListener
  {
    @Override
    public void actionPerformed(ActionEvent evt)
    {
      JEditTextArea textArea = getTextArea(evt);
      if (textArea.isSelectionRectangular())
      {
        // re-setting the position will clear the selection
        textArea.setCaretPosition(textArea.getCaretPosition());
      }
    }
  }

  public static class InsertChar
    implements ActionListener
  {
    private final String openChars = Settings.getInstance().getEditorAutoQuoteOpenChars();
    private final String closeChars = Settings.getInstance().getEditorAutoQuoteCloseChars();

    @Override
    public void actionPerformed(ActionEvent evt)
    {
      JEditTextArea textArea = getTextArea(evt);
      String str = evt.getActionCommand();

      boolean autoCloseEnabled = textArea.getAutoQuoteSelection() &&
                                 StringUtil.isNoneBlank(openChars, closeChars) &&
                                 openChars.length() == closeChars.length();
      if (textArea.isEditable())
      {
        char typedChar = str.charAt(0);
        boolean selectionSupportsAutoQuote = false;
        if (textArea.isSelectionRectangular())
        {
          selectionSupportsAutoQuote = textArea.getRectangularSelectionColumns() > 1;
        }
        else
        {
          selectionSupportsAutoQuote = textArea.getSelectionLength() > 0;
        }

        if (autoCloseEnabled
            && selectionSupportsAutoQuote
            && (openChars.indexOf(typedChar) > -1 && str.length() == 1)
            && !StringUtil.equalString(str, textArea.getSelectedText()))
        {
          String newSelection = applyAutoQuote(textArea, typedChar);
          textArea.setSelectedText(newSelection);
        }
        else if (textArea.shouldInsert(typedChar))
        {
          textArea.overwriteSetSelectedText(str);
          if (str.length() == 1)
          {
            textArea.completeBracket(typedChar);
          }
        }
        else
        {
          int caret = textArea.getCaretPosition();
          textArea.setCaretPosition(caret + 1);
        }
      }
      else
      {
        textArea.getToolkit().beep();
      }
    }

    private String applyAutoQuote(JEditTextArea textArea, char quote)
    {
      String selected = textArea.getSelectedText();
      // Shouldn't happen
      if (selected == null || selected.length() < 1) return selected;

      List<String> lines = StringUtil.getLines(selected);
      StringBuilder result = new StringBuilder(selected.length() + (lines.size() * 3));
      for (int i=0; i < lines.size(); i++)
      {
        if (i > 0) result.append('\n');
        result.append(quote);
        result.append(lines.get(i));
        result.append(closeChars.charAt(openChars.indexOf(quote)));
      }
      return result.toString();
    }

  }
}
