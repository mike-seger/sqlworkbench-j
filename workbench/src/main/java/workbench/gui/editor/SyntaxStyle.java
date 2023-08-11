/*
 * SyntaxStyle.java - A simple text style class
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */
package workbench.gui.editor;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

import javax.swing.text.StyleContext;

/**
 * A simple text style class.
 *
 * It can specify the color, italic flag, and bold flag of a run of text.
 *
 * @author Slava Pestov
 * @author Thomas Kellerer
 */
public class SyntaxStyle
{
  /**
   * The property prefix for storing a color in the settings file.
   */
  public static final String PREFIX_COLOR = "workbench.editor.color.";

  /**
   * The property prefix for storing the font style (bold, ital) flag in the settings file.
   * This needs to be combined with the actual style keyword, e.g.
   * <code>PREFIX_STYLE + COMMENT1</code>
   */
  public static final String PREFIX_STYLE = "workbench.editor.syntax.style.";

  /**
   * The property suffix for storing the block comment style.
   * This needs to be combined with the color, italic or bold prefix.
   */
  public static final String COMMENT1 = "comment1";
  /**
   * The full property key for the block comment style.
   */
  public static final String PROP_COMMENT1 = PREFIX_COLOR + COMMENT1;

  /**
   * The property suffix for storing the style for block comments.
   */
  public static final String COMMENT2 = "comment2";
  /**
   * The full property key for the line comment style.
   */
  public static final String PROP_COMMENT2 = PREFIX_COLOR + COMMENT2;

  /**
   * The property suffix for the keyword1 style.
   */
  public static final String KEYWORD1 = "keyword1";
  /**
   * The full property key for the keyword1 style.
   */
  public static final String PROP_KEYWORD1 = PREFIX_COLOR + KEYWORD1;

  /**
   * The property suffix for the keyword2 style.
   */
  public static final String KEYWORD2 = "keyword2";
  /**
   * The full property key for the keyword2 style.
   */
  public static final String PROP_KEYWORD2 = PREFIX_COLOR + KEYWORD2;

  /**
   * The property suffix for the keyword3 style.
   */
  public static final String KEYWORD3 = "keyword3";
  /**
   * The full property key for the keyword3 style.
   */
  public static final String PROP_KEYWORD3 = PREFIX_COLOR + KEYWORD3;

  /**
   * The property suffix for the literal1 style.
   */
  public static final String LITERAL1 = "literal1";
  /**
   * The full property key for the literal1 style.
   */
  public static final String PROP_LITERAL1 = PREFIX_COLOR + LITERAL1;

  /**
   * The property suffix for the literal2 style.
   */
  public static final String LITERAL2 = "literal2";
  /**
   * The full property key for the literal2 style.
   */
  public static final String PROP_LITERAL2 = PREFIX_COLOR + LITERAL2;

  /**
   * The property suffix for the operator style.
   */
  public static final String OPERATOR = "operator";
  /**
   * The full property key for the operator style.
   */
  public static final String PROP_OPERATOR = PREFIX_COLOR + OPERATOR;

  /**
   * The property suffix for the datatype style.
   */
  public static final String DATATYPE = "datatype";
  /**
   * The full property key for the datatype style.
   */
  public static final String PROP_DATATYPE = PREFIX_COLOR + DATATYPE;

  public static final String INVALID = "invalid";

  private Color color;
  private final int style;

  /**
   * Creates a new SyntaxStyle.
   * @param color The text color
   * @param italic True if the text should be italics
   * @param bold True if the text should be bold
   */
  public SyntaxStyle(Color color, int fontStyle)
  {
    this.color = color;
    if (fontStyle < 0 || fontStyle > 3)
    {
      this.style = 0;
    }
    else
    {
      this.style = fontStyle;
    }
  }

  /**
   * Returns the color specified in this style.
   */
  public Color getColor()
  {
    return color;
  }

  public int getFontStyle()
  {
    return style;
  }

  /**
   * Returns the specified font, but with the style's bold and
   * italic flags applied.
   */
  public Font getStyledFont(Font font)
  {
    return StyleContext.getDefaultStyleContext().getFont(font.getFamily(), style, font.getSize());
  }

  /**
   * Sets the foreground color and font of the specified graphics context to that specified in this style.
   *
   * @param gfx The graphics context
   * @param font The font to add the styles to
   */
  public void setGraphicsFlags(Graphics gfx, Font font)
  {
    gfx.setFont(getStyledFont(font));
    gfx.setColor(color);
  }

}
