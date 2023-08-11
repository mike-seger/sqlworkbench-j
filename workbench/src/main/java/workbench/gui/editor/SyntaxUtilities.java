package workbench.gui.editor;

/*
 * SyntaxUtilities.java - Utility functions used by syntax colorizing
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

import javax.swing.text.Segment;
import javax.swing.text.TabExpander;
import javax.swing.text.Utilities;

import workbench.resource.GuiSettings;
import workbench.resource.Settings;

import static workbench.gui.editor.SyntaxStyle.*;

/**
 * Class with several utility functions used by jEdit's syntax colorizing
 * subsystem.
 *
 * @author Slava Pestov
 */
public class SyntaxUtilities
{

  public static int findMatch(Segment line, String needle, int startAt, boolean ignoreCase)
  {
    char[] haystack = line.array;
    int needleLen = needle.length();

    int searchPos = 0;
    int textLength = line.offset + line.count;
    if (textLength > haystack.length)
    {
      textLength = haystack.length;
    }

    for (int textPos = line.offset + startAt; textPos < textLength; textPos++)
    {
      char c1 = haystack[textPos];
      char c2 = needle.charAt(searchPos);

      if (ignoreCase)
      {
        c1 = Character.toUpperCase(c1);
        c2 = Character.toUpperCase(c2);
      }

      if (c1 == c2)
      {
        searchPos++;
        if (searchPos == needleLen)
        {
          return (textPos + 1) - needleLen - line.offset;
        }
      }
      else
      {
        textPos -= searchPos;
        searchPos = 0;
      }
    }
    return -1;
  }

  /**
   * Checks if a subregion of a <code>Segment</code> is equal to a
   * character array.
   *
   * @param ignoreCase True if case should be ignored, false otherwise
   * @param text       The segment
   * @param offset     The offset into the segment
   * @param match      The character array to match
   */
  public static boolean regionMatches(boolean ignoreCase, Segment text, int offset, char[] match)
  {
    int length = offset + match.length;
    char[] textArray = text.array;
    if (length > text.offset + text.count)
    {
      return false;
    }
    for (int i = offset, j = 0; i < length; i++, j++)
    {
      char c1 = textArray[i];
      char c2 = match[j];
      if (ignoreCase)
      {
        c1 = Character.toUpperCase(c1);
        c2 = Character.toUpperCase(c2);
      }
      if (c1 != c2)
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the default style table. This can be passed to the
   * <code>setStyles()</code> method of <code>SyntaxDocument</code>
   * to use the default syntax styles.
   */
  public static SyntaxStyle[] getDefaultSyntaxStyles()
  {
    SyntaxStyle[] styles = new SyntaxStyle[Token.ID_COUNT];

    // Block comments
    styles[Token.COMMENT1] = getStyle(COMMENT1, Color.GRAY, Font.ITALIC);

    // Single line comments
    styles[Token.COMMENT2] = getStyle(COMMENT2, Color.GRAY, Font.ITALIC);

    // Standard SQL Keywords
    styles[Token.KEYWORD1] = getStyle(KEYWORD1, Color.BLUE, Font.PLAIN);

    // workbench commands
    styles[Token.KEYWORD2] = getStyle(KEYWORD2, Color.MAGENTA, Font.PLAIN);

    // functions
    styles[Token.KEYWORD3] = getStyle(KEYWORD3, new Color(0x009600), Font.PLAIN);

    // String literals
    styles[Token.LITERAL1] = getStyle(LITERAL1, new Color(0x650099), Font.PLAIN);

    // Quoted identifiers
    styles[Token.LITERAL2] = getStyle(LITERAL2, new Color(0x650099), Font.PLAIN);

    styles[Token.DATATYPE] = getStyle(DATATYPE, new Color(0x990033), Font.PLAIN);
    styles[Token.OPERATOR] = getStyle(OPERATOR, Color.BLACK, Font.PLAIN);

    // Not used
    styles[Token.INVALID] = getStyle(INVALID, Color.RED, Font.PLAIN);

    return styles;
  }

  private static SyntaxStyle getStyle(String suffix, Color defaultColor, int defaultStyle)
  {
    Color color = Settings.getInstance().getColor(PREFIX_COLOR + suffix, defaultColor);
    int style = GuiSettings.getFontStyle(PREFIX_STYLE + suffix, defaultStyle);
    return new SyntaxStyle(color, style);
  }

  /**
   * Paints the specified line onto the graphics context. Note that this
   * method munges the offset and count values of the segment.
   *
   * @param line     The line segment
   * @param tokens   The token list for the line
   * @param styles   The syntax style list
   * @param expander The tab expander used to determine tab stops. May
   *                 be null
   * @param gfx      The graphics context
   * @param x        The x co-ordinate
   * @param y        The y co-ordinate
   * @param addwidth Additional spacing to be added to the line width
   *
   * @return The x co-ordinate, plus the width of the painted string
   */
  public static float paintSyntaxLine(Segment line,
                                      Token tokens,
                                      SyntaxStyle[] styles,
                                      TabExpander expander,
                                      Graphics2D gfx,
                                      float x, float y,
                                      int addwidth)
  {
    if (tokens == null) return x;

    Font defaultFont = gfx.getFont();
    Color defaultColor = gfx.getColor();

    while (true)
    {
      if (tokens == null)
      {
        gfx.setColor(defaultColor);
        gfx.setFont(defaultFont);
        break;
      }

      if (tokens.id == Token.NULL)
      {
        gfx.setColor(defaultColor);
        gfx.setFont(defaultFont);
      }
      else
      {
        styles[tokens.id].setGraphicsFlags(gfx, defaultFont);
      }
      line.count = tokens.length;
      x = Utilities.drawTabbedText(line, x, y, gfx, expander, addwidth);
      line.offset += tokens.length;

      tokens = tokens.next;
    }

    return x;
  }

}
