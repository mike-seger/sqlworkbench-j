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
package workbench.gui.renderer;

import java.awt.Color;

import workbench.resource.GuiSettings;

/**
 * @author Thomas Kellerer
 */
public class ColorUtils
{

  /**
   * Blend two colors.
   *
   * Taken from: From: https://www.java-gaming.org/index.php?topic=21434.0
   *
   * @param color1   the first color
   * @param color2   the second color
   * @param factor   the balance factor that assigns a "priority" to the passed colors
   *                 0 returns the first color, 256 returns the second color
   *
   * @return the blended color
   */
  public static Color blend(Color color1, Color color2, int factor)
  {
    if (color2 == null) return color1;

    if (factor <= 0) return color1;
    if (factor >= 256) return color2;
    if (color1 == null) return color2;

    int f1 = 256 - factor;
    int c1 = color1.getRGB();
    int c2 = color2.getRGB();
    int blended = ((((c1 & 0xFF00FF) * f1 + (c2 & 0xFF00FF) * factor)  & 0xFF00FF00)  | (((c1 & 0x00FF00) * f1 + (c2 & 0x00FF00) * factor ) & 0x00FF0000)) >>>8;
    return new Color(blended);
  }

  /**
   * Replacement for Color.darker() with a custom factor.
   *
   * The built-in <tt>Color.darker()</tt> uses a hard-coded
   * factor of 0.7 to make a color darker.
   *
   * This method allows to specify this factor.
   * Higher factors mean a lighter color, lower factors a darker color.
   *
   * @param color   the color
   * @parm factor   the factor to apply
   */
  public static Color darker(Color color, double factor)
  {
    if (color == null) return color;
    return new Color(Math.max((int)(color.getRed()  *factor), 0),
                     Math.max((int)(color.getGreen()*factor), 0),
                     Math.max((int)(color.getBlue() *factor), 0),
                     color.getAlpha());

  }

  /**
   * Replacement for Color.brighter() with a custom factor.
   *
   * The built-in <tt>Color.darker()</tt> uses a hard-coded
   * factor of 0.7 to make a color brighter.
   *
   * This method allows to specify this factor.
   *
   * @param color   the color
   * @parm factor   the factor to apply
   */
  public static Color brighter(Color color, double factor)
  {
    if (color == null) return color;
    int r = color.getRed();
    int g = color.getGreen();
    int b = color.getBlue();
    int alpha = color.getAlpha();

    /* From 2D group:
     * 1. black.brighter() should return grey
     * 2. applying brighter to blue will always return blue, brighter
     * 3. non pure color (non zero rgb) will eventually return white
     */
    int i = (int)(1.0/(1.0-factor));
    if ( r == 0 && g == 0 && b == 0)
    {
      return new Color(i, i, i, alpha);
    }
    if ( r > 0 && r < i ) r = i;
    if ( g > 0 && g < i ) g = i;
    if ( b > 0 && b < i ) b = i;

    return new Color(Math.min((int)(r/factor), 255),
                     Math.min((int)(g/factor), 255),
                     Math.min((int)(b/factor), 255),
                     alpha);

  }

  public static double distance(Color c1, Color c2)
  {
    double a = (c2.getRed() / 255.0) - (c1.getRed() / 255.0);
    double b = (c2.getGreen() / 255.0) - (c1.getGreen() / 255.0);
    double c = (c2.getBlue() / 255.0) - (c1.getBlue() / 255.0);
    return Math.sqrt(a * a + b * b + c * c);
  }

  public static boolean isDark(Color color)
  {
    double dWhite = distance(color, Color.WHITE);
    double dBlack = distance(color, Color.BLACK);
    return dBlack < dWhite;
  }

  public static Color getContrastColor(Color color)
  {
    if (color == null) return Color.BLACK;

    // Calculate the perceptive luminance (aka luma)
    double luma = 0.5;
    switch (GuiSettings.getConstrastColorFormula())
    {
      case 1:
        luma = ((0.2126 * color.getRed()) + (0.7152 * color.getGreen()) + (0.0722 * color.getBlue())) / 255;
        break;
      case 2:
        luma = Math.sqrt( Math.pow(0.299*color.getRed(),2) + Math.pow(0.587 * color.getGreen(), 2) + Math.pow(0.114 * color.getBlue(), 2));
        break;
      case 3:
        luma = ((0.299 * color.getRed()) + (0.587 * color.getGreen()) + (0.114 * color.getBlue())) / 255;
        break;
    }
    // Return black for bright colors, white for dark colors
    return luma >= 0.5 ? Color.BLACK : Color.WHITE;
  }
}
