/*
 * TextAreaPainter.java - Paints the text area
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */
package workbench.gui.editor;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.text.Segment;
import javax.swing.text.TabExpander;
import javax.swing.text.Utilities;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;

import workbench.util.NumberStringCache;
import workbench.util.StringUtil;

/**
 * The text area repaint manager. It performs double buffering and paints
 * lines of text.
 *
 * @author Slava Pestov (Initial development)
 * @author Thomas Kellerer (bugfixes and enhancements)
 */
public class TextAreaPainter
  extends JComponent
  implements TabExpander, PropertyChangeListener
{
  public static final Color DEFAULT_GUTTER_TEXT_COLOR = UIManager.getColor("Label.foreground");
  public static final Color DEFAULT_GUTTER_BG  = UIManager.getColor("Label.background");;
  public static final Color DEFAULT_SELECTION_COLOR = new Color(204,204,255);
  private static final Cursor DEFAULT_CURSOR = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);

  private final Segment currentLine = new Segment();

  protected JEditTextArea textArea;
  protected SyntaxStyle[] styles;
  protected Color caretColor;
  protected Color selectionBackground;
  protected Color selectionForeground;
  protected Color currentLineColor;
  protected Color bracketHighlightColor;
  protected Color occuranceHighlightColor;

  protected boolean bracketHighlight;
  protected boolean matchBeforeCaret;
  protected boolean bracketHighlightRec;
  protected boolean bracketHighlightBoth;
  protected boolean selectionHighlightIgnoreCase;

  private final int cursorWidth = GuiSettings.getCaretWidth();
  protected int tabSize = -1;
  protected boolean showLineNumbers;
  protected int gutterWidth = 0;

  protected int gutterMargin = 8;
  private Color gutterBackground;
  private Color gutterTextColor;

  private final Object stylesLockMonitor = new Object();
  private String highlighText;

  private Map renderingHints;

  public TextAreaPainter(JEditTextArea textArea)
  {
    super();
    this.textArea = textArea;

    setDoubleBuffered(true);
    setOpaque(true);

    super.setCursor(DEFAULT_CURSOR);
    super.setFont(Settings.getInstance().getEditorFont());

    readBracketSettings();

    setColors();
    selectionHighlightIgnoreCase = Settings.getInstance().getSelectionHighlightIgnoreCase();
    showLineNumbers = Settings.getInstance().getShowLineNumbers();

    Settings.getInstance().addPropertyChangeListener(this,
      Settings.PROPERTY_EDITOR_TAB_WIDTH,
      Settings.PROPERTY_EDITOR_FG_COLOR,
      Settings.PROPERTY_EDITOR_BG_COLOR,
      Settings.PROPERTY_EDITOR_CURSOR_COLOR,
      Settings.PROPERTY_EDITOR_GUTTER_COLOR,
      Settings.PROPERTY_EDITOR_LINENUMBER_COLOR,
      Settings.PROPERTY_EDITOR_CURRENT_LINE_COLOR,
      Settings.PROPERTY_EDITOR_SELECTION_BG_COLOR,
      Settings.PROPERTY_EDITOR_SELECTION_FG_COLOR,
      Settings.PROPERTY_EDITOR_OCCURANCE_HIGHLIGHT_COLOR,
      Settings.PROPERTY_EDITOR_OCCURANCE_HIGHLIGHT_IGNORE_CASE,
      Settings.PROPERTY_EDITOR_BRACKET_HILITE,
      Settings.PROPERTY_EDITOR_BRACKET_HILITE_COLOR,
      Settings.PROPERTY_EDITOR_BRACKET_HILITE_LEFT,
      Settings.PROPERTY_EDITOR_BRACKET_HILITE_REC,
      Settings.PROPERTY_EDITOR_BRACKET_HILITE_BOTH,
      SyntaxStyle.PROP_COMMENT1,
      SyntaxStyle.PROP_COMMENT2,
      SyntaxStyle.PROP_KEYWORD1,
      SyntaxStyle.PROP_KEYWORD2,
      SyntaxStyle.PROP_KEYWORD3,
      SyntaxStyle.PROP_LITERAL1,
      SyntaxStyle.PROP_LITERAL2,
      SyntaxStyle.PROP_OPERATOR,
      SyntaxStyle.PROP_DATATYPE,
      SyntaxStyle.PREFIX_STYLE + SyntaxStyle.COMMENT1,
      SyntaxStyle.PREFIX_STYLE + SyntaxStyle.COMMENT2,
      SyntaxStyle.PREFIX_STYLE + SyntaxStyle.KEYWORD1,
      SyntaxStyle.PREFIX_STYLE + SyntaxStyle.KEYWORD2,
      SyntaxStyle.PREFIX_STYLE + SyntaxStyle.KEYWORD3,
      SyntaxStyle.PREFIX_STYLE + SyntaxStyle.LITERAL1,
      SyntaxStyle.PREFIX_STYLE + SyntaxStyle.LITERAL2,
      SyntaxStyle.PREFIX_STYLE + SyntaxStyle.OPERATOR,
      SyntaxStyle.PREFIX_STYLE + SyntaxStyle.DATATYPE,
      Settings.PROPERTY_SHOW_LINE_NUMBERS);

    if (Settings.getInstance().getBoolProperty("workbench.editor.desktophints.enabled", true))
    {
      Toolkit tk = Toolkit.getDefaultToolkit();
      renderingHints = (Map) tk.getDesktopProperty("awt.font.desktophints");
    }
  }

  @Override
  public void setCursor(Cursor current)
  {
    if (current != null && current.equals(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)))
    {
      super.setCursor(current);
    }
    else
    {
      super.setCursor(DEFAULT_CURSOR);
    }
  }

  public void setHighlightValue(String text)
  {
    boolean changed = false;
    if (StringUtil.isNotBlank(text))
    {
      changed = StringUtil.stringsAreNotEqual(highlighText, text);
      highlighText = text;
    }
    else
    {
      changed = highlighText != null;
      highlighText = null;
    }
    if (changed)
    {
      invalidateVisibleLines();
    }
  }

  public void dispose()
  {
    Settings.getInstance().removePropertyChangeListener(this);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (Settings.PROPERTY_EDITOR_TAB_WIDTH.equals(evt.getPropertyName()))
    {
      if (textArea != null)
      {
        textArea.setTabSize(Integer.valueOf(Settings.getInstance().getEditorTabWidth()));
      }
      WbSwingUtilities.invoke(this::calculateTabSize);
    }
    else if (Settings.PROPERTY_SHOW_LINE_NUMBERS.equals(evt.getPropertyName()))
    {
      showLineNumbers = Settings.getInstance().getShowLineNumbers();
    }
    else if (evt.getPropertyName().startsWith(Settings.PROPERTY_EDITOR_BRACKET_HILITE_BASE))
    {
      readBracketSettings();
      textArea.invalidateBracketLine();
    }
    else if (Settings.PROPERTY_EDITOR_OCCURANCE_HIGHLIGHT_IGNORE_CASE.equals(evt.getPropertyName()))
    {
      selectionHighlightIgnoreCase = Settings.getInstance().getSelectionHighlightIgnoreCase();
    }
    else
    {
      WbSwingUtilities.invoke(this::setColors);
    }
    invalidate();
    WbSwingUtilities.repaintLater(this);
  }

  private void readBracketSettings()
  {
    bracketHighlight = Settings.getInstance().isBracketHighlightEnabled();
    matchBeforeCaret = Settings.getInstance().getBracketHighlightLeft();
    bracketHighlightColor = Settings.getInstance().getEditorBracketHighlightColor();
    bracketHighlightRec = Settings.getInstance().getBracketHighlightRectangle();
    bracketHighlightBoth = bracketHighlight && Settings.getInstance().getBracketHighlightBoth();
  }

  private Color getDefaultColor(String key, Color fallback)
  {
    Color c = UIManager.getColor(key);
    return c == null ? fallback : c;
  }

  private void setColors()
  {
    Color textColor = GuiSettings.getEditorForeground();
    setForeground(textColor);
    Color bg = GuiSettings.getEditorBackground();
    setBackground(bg);

    gutterBackground = Settings.getInstance().getColor(Settings.PROPERTY_EDITOR_GUTTER_COLOR, DEFAULT_GUTTER_BG);
    gutterTextColor = Settings.getInstance().getColor(Settings.PROPERTY_EDITOR_LINENUMBER_COLOR, DEFAULT_GUTTER_TEXT_COLOR);

    setStyles(SyntaxUtilities.getDefaultSyntaxStyles());
    caretColor = Settings.getInstance().getEditorCursorColor(UIManager.getColor("TextArea.foreground"));
    currentLineColor = Settings.getInstance().getEditorCurrentLineColor();
    bracketHighlightColor = Settings.getInstance().getEditorBracketHighlightColor();
    occuranceHighlightColor = Settings.getInstance().geSelectionHighlightColor();
    selectionBackground = Settings.getInstance().getEditorSelectionColor();
    if (selectionBackground == null)
    {
      selectionBackground = getDefaultColor("TextArea.selectionBackground", DEFAULT_SELECTION_COLOR);
    }
    selectionForeground = Settings.getInstance().getEditorSelectedTextColor();
    if (selectionForeground == null)
    {
      selectionForeground = getDefaultColor("TextArea.selectionForeground", null);
    }
  }

  /**
   * Returns if this component can be traversed by pressing the
   * Tab key. This returns false.
   */
  @SuppressWarnings("deprecation")
  @Override
  public boolean isManagingFocus()
  {
    return false;
  }

  @Override
  public boolean isFocusable()
  {
    return false;
  }

  public FontMetrics getStyleFontMetrics(byte tokenId)
  {
    if (tokenId == Token.NULL || styles == null || tokenId < 0 || tokenId >= styles.length)
    {
      return this.getFontMetrics();
    }
    else
    {
      return this.getFontMetrics(styles[tokenId].getStyledFont(getFont()));
    }
  }

  /**
   * Returns the syntax styles used to paint colorized text. Entry <i>n</i>
   * will be used to paint tokens with id = <i>n</i>.
   * @see Token
   */
  public SyntaxStyle[] getStyles()
  {
    synchronized (stylesLockMonitor)
    {
      return styles;
    }
  }

  /**
   * Sets the syntax styles used to paint colorized text. Entry <i>n</i>
   * will be used to paint tokens with id = <i>n</i>.
   * @param newStyles The syntax styles
   * @see Token
   */
  public void setStyles(SyntaxStyle[] newStyles)
  {
    synchronized (stylesLockMonitor)
    {
      this.styles = new SyntaxStyle[newStyles.length];
      System.arraycopy(newStyles, 0, this.styles, 0, newStyles.length);
    }
    repaint();
  }

  @Override
  public void invalidate()
  {
    super.invalidate();
    invalidateLineRange(0, textArea.getLineCount());
  }

  @Override
  public void validate()
  {
    super.validate();
    invalidateLineRange(0, textArea.getLineCount());
  }

  /**
   * Returns the caret color.
   */
  public final Color getCaretColor()
  {
    return caretColor;
  }

  /**
   * Sets the caret color.
   * @param caretColor The caret color
   */
  public final void setCaretColor(Color caretColor)
  {
    this.caretColor = caretColor;
    invalidateSelectedLines();
  }

  /**
   * Returns the selection color.
   */
  public final Color getSelectionBackground()
  {
    return selectionBackground;
  }

  /**
   * Sets the selection color.
   * @param selectionColor The selection color
   */
  public final void setSelectionBackground(Color selectionColor)
  {
    this.selectionBackground = selectionColor;
    invalidateSelectedLines();
  }

  /**
   * Returns true if bracket highlighting is enabled, false otherwise.
   * When bracket highlighting is enabled, the bracket matching the
   * one before the caret (if any) is highlighted using the caret color
   */
  public final boolean isBracketHighlightEnabled()
  {
    return bracketHighlight;
  }

  /**
   * Enables or disables bracket highlighting.
   *
   * When bracket highlighting is enabled, the bracket matching the one before
   * the caret (if any) is highlighted.
   *
   * @param bracketHighlight True if bracket highlighting should be enabled, false otherwise
   */
  public final void setBracketHighlightEnabled(boolean bracketHighlight)
  {
    this.bracketHighlight = bracketHighlight;
    invalidateLine(textArea.getBracketLine());
  }

  /**
   * Returns the font metrics used by this component.
   */
  public FontMetrics getFontMetrics()
  {
    return this.getFontMetrics(getFont());
  }

  @Override
  public FontMetrics getFontMetrics(Font f)
  {
    return getGraphics() == null ? super.getFontMetrics(f) : getGraphics().getFontMetrics(f);
  }

  private void fontChanged()
  {
    calculateTabSize();
    calculateGutterWidth(getGraphics());
    invalidate();
    textArea.fontChanged();
  }

  /**
   * Sets the font for this component.
   *
   * This is overridden to update the cached font metrics and to recalculate which lines are visible.
   *
   * @param font The font
   */
  @Override
  public void setFont(Font font)
  {
    super.setFont(font);
    fontChanged();
  }

  private void calculateGutterWidth(Graphics gfx)
  {
    FontMetrics fm = null;
    if (gfx != null)
    {
      fm = gfx.getFontMetrics();
    }
    if (fm == null)
    {
      fm = getFontMetrics();
    }
    calculateGutterWidth(fm);
  }

  private void calculateGutterWidth(FontMetrics fm)
  {
    if (this.showLineNumbers)
    {
      gutterMargin = (int)(fm.stringWidth("9") * 0.8);
      int lastLine = textArea.getLineCount();
      String lineStr = NumberStringCache.getNumberString(lastLine);
      this.gutterWidth = fm.stringWidth(lineStr) + (int)(gutterMargin * 2.5);
    }
    else
    {
      this.gutterWidth = 0;
    }
  }

  public void calculateTabSize()
  {
    this.tabSize = -1;
    if (textArea == null) return;
    FontMetrics cfm = getFontMetrics();
    if (cfm == null) return;
    this.tabSize = cfm.charWidth(' ') * textArea.getTabSize();
  }

  @Override
  public void paint(Graphics g)
  {
    final Graphics2D gfx = (Graphics2D)g;

    if (renderingHints != null)
    {
      gfx.addRenderingHints(renderingHints);
    }

    gfx.setFont(getFont());
    calculateGutterWidth(gfx);

    final FontMetrics fm = gfx.getFontMetrics(getFont());
    final Rectangle clipRect = gfx.getClipBounds();

    int editorWidth = getWidth() - gutterWidth;
    int editorHeight = getHeight();

    final int lastLine = textArea.getLineCount();
    final int visibleCount = textArea.getVisibleLines();
    final int firstVisible = textArea.getFirstLine();

    int firstInvalid = firstVisible;
    int lastInvalid = firstVisible + visibleCount;
    int fheight = fm.getHeight();

    if (clipRect != null)
    {
      gfx.setColor(this.getBackground());
      gfx.fillRect(clipRect.x, clipRect.y, clipRect.width, clipRect.height);

      if (this.showLineNumbers)
      {
        gfx.setColor(gutterBackground);
        gfx.fillRect(clipRect.x, clipRect.y, gutterWidth - clipRect.x, clipRect.height);
      }

      firstInvalid += (clipRect.y / fheight);
      if (firstInvalid > 1) firstInvalid --;
      lastInvalid = firstVisible + ((clipRect.y + clipRect.height) / fheight);
    }

    if (lastInvalid > lastLine)
    {
      lastInvalid = lastLine;
    }
    else
    {
      lastInvalid++;
    }

    Font defaultFont = gfx.getFont();
    Color defaultColor = getForeground();

    try
    {
      final float x = (float)textArea.getHorizontalOffset();

      int endLine = firstVisible + visibleCount + 1;
      if (endLine > lastLine) endLine = lastLine;

      final int gutterX = this.gutterWidth - gutterMargin;
      final int caretLine = textArea.getCaretLine();
      TokenMarker tokenMarker = textArea.getDocument().getTokenMarker();

      for (int line = firstVisible; line <= endLine; line++)
      {
        final int y = textArea.lineToY(line);

        if (this.showLineNumbers)
        {
          // It seems that the Objects created by Integer.toString()
          // that are passed to drawString() are not garbage collected
          // correctly (as seen in the profiler). So each time
          // the editor gets redrawn a small amount of memory is lost.
          // To workaround this, I'm caching (some of) the values
          // that are needed here.
          final String s = NumberStringCache.getNumberString(line);
          final int w = fm.stringWidth(s);

          // make sure the line numbers do not show up outside the gutter
          gfx.setClip(0, 0, gutterWidth, editorHeight);
          gfx.setColor(gutterTextColor);
          gfx.drawString(s, gutterX - w, y);
        }

        if (line >= firstInvalid && line < lastInvalid)
        {
          if (this.showLineNumbers)
          {
            gfx.setClip(this.gutterWidth, 0, editorWidth, editorHeight);
            gfx.translate(this.gutterWidth, 0);
          }

          textArea.getLineText(line, currentLine);
          Token tokens =  tokenMarker == null ? null : tokenMarker.markTokens(currentLine, line);

          if (line == caretLine)
          {
            if (this.currentLineColor != null)
            {
              gfx.setColor(currentLineColor);
              gfx.fillRect(0, y + fm.getMaxDescent(), editorWidth, fheight);
              gfx.setColor(getBackground());
            }
            paintCaret(gfx, currentLine, line, y + fm.getMaxDescent(), fheight, tokens);
          }

          if (tokenMarker == null)
          {
            paintPlainLine(gfx, currentLine, line, defaultFont, defaultColor, x, y);
          }
          else
          {
            paintSyntaxLine(gfx, currentLine, tokens, line, defaultFont, defaultColor, x, y);
          }

          if (this.showLineNumbers)
          {
            gfx.translate(-this.gutterWidth,0);
            gfx.setClip(null);
          }
        }
      }
    }
    catch (Throwable e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error repainting line range {" + firstInvalid + "," + lastInvalid + "}", e);
    }
  }

  /**
   * Marks a line as needing a repaint.
   * @param line The line to invalidate
   */
  public final void invalidateLine(int line)
  {
    final FontMetrics fm = getFontMetrics();
    repaint(0, textArea.lineToY(line) + fm.getMaxDescent(), getWidth(), fm.getHeight());
  }

  public int getGutterWidth()
  {
    return this.gutterWidth;
  }

  /**
   * Marks a range of lines as needing a repaint.
   * @param firstLine The first line to invalidate
   * @param lastLine The last line to invalidate
   */
  public final void invalidateLineRange(int firstLine, int lastLine)
  {
    final FontMetrics fm = getFontMetrics();
    repaint(0, textArea.lineToY(firstLine) + fm.getMaxDescent(), getWidth(), (lastLine - firstLine + 1) * fm.getHeight());
  }

  public void invalidateVisibleLines()
  {
    int firstLine = textArea.getFirstLine();
    int lastLine = firstLine + textArea.getVisibleLines();
    invalidateLineRange(firstLine, lastLine);
  }
  /**
   * Repaints the lines containing the selection.
   */
  public final void invalidateSelectedLines()
  {
    invalidateLineRange(textArea.getSelectionStartLine(), textArea.getSelectionEndLine());
  }

  /**
   * Implementation of TabExpander interface.
   *
   * Returns next tab stop after a specified point.
   */
  @Override
  public float nextTabStop(float x, int tabOffset)
  {
    if (tabSize == -1)
    {
      this.calculateTabSize();
    }
    int offset = textArea.getHorizontalOffset();
    int ntabs = (int) ((x - offset) / tabSize);
    return ((ntabs + 1) * tabSize) + offset;
  }

  protected void paintPlainLine(Graphics2D gfx, Segment lineSegment, int line, Font defaultFont, Color defaultColor, float x, float y)
  {
    final FontMetrics fm = getFontMetrics(defaultFont);

    paintHighlight(gfx, lineSegment, line, y, null);

    gfx.setFont(defaultFont);
    gfx.setColor(defaultColor);

    y += fm.getHeight();
    Utilities.drawTabbedText(lineSegment, x, y, gfx, this, 0);
  }

  protected void paintSyntaxLine(Graphics2D gfx, Segment lineSegment, Token tokens, int line, Font defaultFont, Color defaultColor, float x, float y)
  {
    final FontMetrics fm = getFontMetrics(defaultFont);

    paintHighlight(gfx, lineSegment, line, y, tokens);

    gfx.setFont(defaultFont);
    gfx.setColor(defaultColor);
    y += fm.getHeight();
    SyntaxUtilities.paintSyntaxLine(lineSegment, tokens, styles, this, gfx, x, y, 0);
  }

  protected void paintHighlight(Graphics2D gfx, Segment lineSegment, int line, float y, Token token)
  {
    final FontMetrics fm = getFontMetrics(gfx.getFont());
    int height = fm.getHeight();
    y += fm.getMaxDescent();

    if (line >= textArea.getSelectionStartLine()  && line <= textArea.getSelectionEndLine())
    {
      paintLineHighlight(gfx, lineSegment, line, y, height, token);
    }

    if (bracketHighlight && line == textArea.getBracketLine())
    {
      paintBracketHighlight(gfx, lineSegment, line, y, height, textArea.getBracketPosition(), token);
    }

    if (this.highlighText != null)
    {
      int pos = SyntaxUtilities.findMatch(lineSegment, highlighText, 0, selectionHighlightIgnoreCase);
      int lineStart = textArea.getLineStartOffset(line);
      while (pos > -1)
      {
        if (pos + lineStart != textArea.getSelectionStart())
        {
          float x = textArea.offsetToX(gfx, line, pos);
          int width = Math.round(textArea.offsetToX(gfx, line, pos + highlighText.length()) - x);
          gfx.setColor(occuranceHighlightColor);
          gfx.fillRect(Math.round(x), Math.round(y), width, height);
          gfx.setColor(getBackground());
        }
        pos = SyntaxUtilities.findMatch(lineSegment, highlighText, pos + 1, selectionHighlightIgnoreCase);
      }
    }
  }

  protected void paintLineHighlight(Graphics2D gfx, Segment lineSegment, int line, float y, int height, Token token)
  {
    int selectionStart = textArea.getSelectionStart();
    int selectionEnd = textArea.getSelectionEnd();

    if (selectionStart == selectionEnd) return;

    Color c = this.textArea.getAlternateSelectionColor();
    if (c != null)
    {
      gfx.setColor(c);
    }
    else
    {
      gfx.setColor(selectionBackground);
    }

    int selectionStartLine = textArea.getSelectionStartLine();
    int selectionEndLine = textArea.getSelectionEndLine();
    int lineStart = textArea.getLineStartOffset(line);

    float x1, x2;
    if (textArea.isSelectionRectangular())
    {
      int lineLen = textArea.getLineLength(line);
      x1 = textArea.offsetToX(gfx, line, Math.min(lineLen,selectionStart - textArea.getLineStartOffset(selectionStartLine)), token);
      x2 = textArea.offsetToX(gfx, line, Math.min(lineLen,selectionEnd - textArea.getLineStartOffset(selectionEndLine)), token);
      if (x1 == x2) x2++;
    }
    else if (selectionStartLine == selectionEndLine)
    {
      x1 = textArea.offsetToX(gfx, line, selectionStart - lineStart, token);
      x2 = textArea.offsetToX(gfx, line, selectionEnd - lineStart, token);
    }
    else if (line == selectionStartLine)
    {
      x1 = textArea.offsetToX(gfx, line, selectionStart - lineStart, token);
      x2 = getWidth();
    }
    else if (line == selectionEndLine)
    {
      x1 = 0;
      x2 = textArea.offsetToX(gfx, line, selectionEnd - lineStart, token);
    }
    else
    {
      x1 = 0;
      x2 = getWidth();
    }

    // "inlined" min/max()
    gfx.fillRect(Math.round(x1 > x2 ? x2 : x1), Math.round(y), Math.round(x1 > x2 ? (x1 - x2) : (x2 - x1)),height);
  }

  protected void paintBracketHighlight(Graphics2D gfx, Segment lineSegment, int line, float y, int height, int position, Token token)
  {
    if (position == -1) return;

    float x = textArea.offsetToX(gfx, line, position, token);
    if (x > 1)
    {
      x--;
    }

    final FontMetrics fm = gfx.getFontMetrics();
    int width = fm.charWidth('(') + 1;

    if (bracketHighlightColor != null)
    {
      gfx.setColor(bracketHighlightColor);
      gfx.fillRect(Math.round(x), Math.round(y), width, height - 1);
    }

    if (bracketHighlightRec)
    {
      gfx.setColor(getForeground());
      gfx.drawRect(Math.round(x), Math.round(y), width, height - 1);
    }
  }

  protected void paintCaret(Graphics2D gfx, Segment lineSegment, int line, float y, int height, Token token)
  {
    int offset = textArea.getCaretPosition() - textArea.getLineStartOffset(line);

    if (bracketHighlightBoth && textArea.getBracketPosition() > -1)
    {
      boolean matchBefore = Settings.getInstance().getBracketHighlightLeft();
      int charOffset = matchBefore ? -1 : 0;
      paintBracketHighlight(gfx, lineSegment, line, y, height, offset + charOffset, token);
    }

    int caretX = Math.round(textArea.offsetToX(gfx, line, offset, token));
    if (textArea.isCaretVisible())
    {
      gfx.setColor(caretColor);

      if (textArea.isOverwriteEnabled())
      {
        final FontMetrics fm = gfx.getFontMetrics();
        gfx.fillRect(caretX, Math.round(y + height - 2),  fm.getMaxAdvance(), 2);
      }
      else
      {
        gfx.fillRect(caretX, Math.round(y), cursorWidth, height - 1);
      }
    }
  }

}
