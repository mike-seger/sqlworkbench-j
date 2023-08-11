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
package workbench.gui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.border.AbstractBorder;

import workbench.gui.WbSwingUtilities;

/**
 *
 * @author Thomas Kellerer
 */
public class DividerBorder
  extends AbstractBorder
{
  public static final int LEFT = 1;
  public static final int RIGHT = 2;
  public static final int TOP = 4;
  public static final int BOTTOM = 8;
  public static final int LEFT_RIGHT = 3;

  public static final int VERTICAL_MIDDLE = 16;
  public static final int HORIZONTAL_MIDDLE = 32;

  public static final DividerBorder BOTTOM_DIVIDER = new DividerBorder(BOTTOM);
  public static final DividerBorder LEFT_DIVIDER = new DividerBorder(LEFT);
  public static final DividerBorder RIGHT_DIVIDER = new DividerBorder(RIGHT);
  public static final DividerBorder LEFT_RIGHT_DIVIDER = new DividerBorder(LEFT + RIGHT);
  public static final DividerBorder TOP_DIVIDER = new DividerBorder(TOP);
  public static final DividerBorder TOP_BOTTOM_DIVIDER = new DividerBorder(TOP + BOTTOM);

  private final int borderType;
  private Color lineColor;

  /**
   * Creates a divider border with the specified type
   * @param type (LEFT, RIGHT, TOP, BOTTOM)
   */
  public DividerBorder(int type)
  {
    super();
    this.borderType = type;
  }

  public DividerBorder(int type, Color lineColor)
  {
    super();
    this.borderType = type;
    this.lineColor= lineColor;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
  {
    Color oldColor = g.getColor();
    Color bg = c.getBackground();
    Color lColor = this.lineColor == null ? WbSwingUtilities.getLineBorderColor(bg) : this.lineColor;

    if ((this.borderType & TOP) == TOP)
    {
      g.setColor(lColor);
      g.drawLine(x, y, x + width, y);
    }

    if ((this.borderType & BOTTOM) == BOTTOM)
    {
      g.setColor(lColor);
      g.drawLine(x, y + height - 2, x + width, y + height - 2);
    }

    if ((this.borderType & LEFT) == LEFT)
    {
      g.setColor(lColor);
      g.drawLine(x, y, x, y + height);
    }

    if ((this.borderType & RIGHT) == RIGHT)
    {
      g.setColor(lColor);
      g.drawLine(x + width - 2, y, x + width - 2, y + height);
    }

    if ((this.borderType & VERTICAL_MIDDLE) == VERTICAL_MIDDLE)
    {
      int w2 = (int)width / 2;
      g.setColor(lColor);
      g.drawLine(x + w2, y, x + w2, y + height);
    }
    if ((this.borderType & HORIZONTAL_MIDDLE) == HORIZONTAL_MIDDLE)
    {
      int h2 = (int)height / 2;
      g.setColor(lColor);
      g.drawLine(0, y + h2, width, y + h2);
    }
    g.setColor(oldColor);
  }

  @Override
  public Insets getBorderInsets(Component c)
  {
    return new Insets(2, 2, 2, 2);
  }

  @Override
  public Insets getBorderInsets(Component c, Insets insets)
  {
    insets.left = insets.top = insets.right = insets.bottom = 2;
    return insets;
  }

}

