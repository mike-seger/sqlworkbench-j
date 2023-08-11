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
package workbench.gui.lnf;

import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Set;

import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.StyleContext;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.Settings;

import workbench.gui.renderer.ColorUtils;

import workbench.util.ClasspathUtil;
import workbench.util.CollectionUtil;
import workbench.util.PlatformHelper;
import workbench.util.StringUtil;

/**
 * Initialize some GUI elements during startup.
 *
 * @author Thomas Kellerer
 */
public class LnFHelper
{
  public static final String MENU_FONT_KEY = "MenuItem.font";
  public static final String LABEL_FONT_KEY = "Label.font";

  private LnFManager lnfManager = new LnFManager();

  // Font properties that are automatically scaled by Java
  private final Set<String> noScale = CollectionUtil.treeSet(
    "Menu.font",
    "MenuBar.font",
    MENU_FONT_KEY,
    "PopupMenu.font",
    "CheckBoxMenuItem.font");

  private final Set<String> fontProperties = CollectionUtil.treeSet(
    "Button.font",
    "CheckBox.font",
    "CheckBoxMenuItem.font",
    "ColorChooser.font",
    "ComboBox.font",
    "EditorPane.font",
    "FileChooser.font",
    LABEL_FONT_KEY,
    "List.font",
    "Menu.font",
    "MenuBar.font",
    MENU_FONT_KEY,
    "OptionPane.font",
    "Panel.font",
    "PasswordField.font",
    "PopupMenu.font",
    "ProgressBar.font",
    "RadioButton.font",
    "RadioButtonMenuItem.font",
    "ScrollPane.font",
    "Slider.font",
    "Spinner.font",
    "TabbedPane.font",
    "TextArea.font",
    "TextField.font",
    "TextPane.font",
    "TitledBorder.font",
    "ToggleButton.font",
    "ToolBar.font",
    "ToolTip.font",
    "Table.font",
    "TableHeader.font",
    "Tree.font",
    "ViewPort.font");

  private static boolean isWebLaf;
  private static boolean isFlatLaf;
  private static boolean isJGoodies;
  private static boolean isWindowsLnF;
  private static boolean isMetalLNF;

  public static boolean isJGoodies()
  {
    return isJGoodies;
  }

  public static boolean isWebLaf()
  {
    return isWebLaf;
  }

  public static boolean isFlatLaf()
  {
    return isFlatLaf;
  }

  public static boolean isWindowsLookAndFeel()
  {
    return isWindowsLnF;
  }

  public static int getMenuFontHeight()
  {
    return getFontHeight(MENU_FONT_KEY);
  }

  public static int getLabelFontHeight()
  {
    return getFontHeight(LABEL_FONT_KEY);
  }

  private static int getFontHeight(String key)
  {
    UIDefaults def = UIManager.getDefaults();
    double factor = Toolkit.getDefaultToolkit().getScreenResolution() / 72.0;
    Font font = def.getFont(key);
    if (font == null) return 18;
    return (int)Math.ceil((double)font.getSize() * factor);
  }

  public void initUI()
  {
    initializeLookAndFeel();

    Settings settings = Settings.getInstance();
    UIDefaults def = UIManager.getDefaults();

    Font configuredStdFont = settings.getStandardFont();
    Font stdFont = configuredStdFont;

    if (stdFont == null && isWindowsLookAndFeel())
    {
      // The default Windows Look & Feel uses "Tahoma", but Windows uses Segoe UI
      Font f = def.getFont("Menu.font");

      // new Font("Segoe UI") creates a font with the family "Dialog"
      // that looks very different to a "Segoe UI" with the family "Segoe UI"
      // which StyleContext.getFont() creates
      stdFont = StyleContext.getDefaultStyleContext().getFont("Segoe UI", Font.PLAIN, f.getSize());
    }

    if (stdFont != null)
    {
      for (String property : fontProperties)
      {
        def.put(property, stdFont);
      }
    }

    if (isWindowsLookAndFeel())
    {
      // The default Windows look and feel does notx scale the fonts properly
      if (configuredStdFont == null) scaleDefaultFonts();
      adjustWindowsLnF();
    }

    if (isMetalLNF)
    {
      adjustMetalLNF();
    }

    Font dataFont = settings.getDataFont();
    if (dataFont != null)
    {
      def.put("Table.font", dataFont);
      def.put("TableHeader.font", dataFont);
    }

    def.put("Button.showMnemonics", Boolean.valueOf(GuiSettings.getShowMnemonics()));
    UIManager.put("Synthetica.extendedFileChooser.rememberLastDirectory", false);
  }

  private void adjustMetalLNF()
  {
    if (Settings.getInstance().getBoolProperty("workbench.gui.adjust.metal.gridcolor", true))
    {
      adjustTableGridColor();
    }

    if (Settings.getInstance().getBoolProperty("workbench.gui.adjust.metal.borders", true))
    {
      adjustBorders();
    }
  }

  private void adjustWindowsLnF()
  {
    if (!Settings.getInstance().getBoolProperty("workbench.gui.adjust.windows.theme", true)) return;
    adjustBorders();
    adjustTableGridColor();
  }

  private void adjustBorders()
  {
    UIDefaults def = UIManager.getDefaults();
    Border b = new CompoundBorder(new LineBorder(ColorUtils.brighter(Color.LIGHT_GRAY, 0.90), 1), new EmptyBorder(2, 1, 1, 1));
    def.put("TextField.border", b);
    def.put("TextArea.border", b);
    def.put("PasswordField.border", b);
  }

  private void adjustTableGridColor()
  {
    UIDefaults def = UIManager.getDefaults();
    Color c = Settings.getInstance().getColor("workbench.table.gridcolor", new Color(215,215,215));
    def.put("Table.gridColor", c);
  }

  private void scaleDefaultFonts()
  {
    if (!Settings.getInstance().getScaleFonts())
    {
      return;
    }
    FontScaler scaler = new FontScaler();
    scaler.logSettings();

    LogMgr.logInfo(new CallerInfo(){}, "Scaling default fonts by: " + scaler.getScaleFactor());

    UIDefaults def = UIManager.getDefaults();

    // when the user configures a scale factor, don't check the menu fonts
    boolean checkJavaFonts = Settings.getInstance().getScaleFactor() < 0;

    for (String property : fontProperties)
    {
      if (checkJavaFonts && noScale.contains(property)) continue;
      Font base = def.getFont(property);
      if (base != null)
      {
        Font scaled = scaler.scaleFont(base);
        def.put(property, scaled);
      }
    }
  }

  private String getDefaultLookAndFeel()
  {
    if (PlatformHelper.isLinux() && lnfManager.isFlatLafLibPresent())
    {
      return LnFDefinition.FLATLAF_LIGHT_CLASS;
    }
    return UIManager.getSystemLookAndFeelClassName();
  }

  protected void initializeLookAndFeel()
  {
    String className = GuiSettings.getLookAndFeelClass();

    try
    {
      if (StringUtil.isEmpty(className))
      {
        className = getDefaultLookAndFeel();
      }
      LnFDefinition def = lnfManager.findLookAndFeel(className);

      if (def == null)
      {
        LogMgr.logError(new CallerInfo(){}, "Specified Look & Feel " + className + " not available!", null);
        setSystemLnF();
      }
      else
      {
        // Fix for bug: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8179014
        // under Windows 10 with the "Creators Update"
        if (className.contains(".plaf.windows.") && Settings.getInstance().getBoolProperty("workbench.gui.fix.filechooser.bug", false))
        {
          UIManager.put("FileChooser.useSystemExtensionHiding", false);
        }
        UIManager.put("FileChooser.useSystemIcons", Boolean.TRUE);

        // I hate the bold menu font in the Metal LnF
        UIManager.put("swing.boldMetal", Boolean.FALSE);

        // Remove Synthetica's own window decorations
        UIManager.put("Synthetica.window.decoration", Boolean.FALSE);

        // Remove the extra icons for read only text fields and
        // the "search bar" in the main menu for the Substance Look & Feel
        System.setProperty("substancelaf.noExtraElements", "");

        if (className.startsWith("org.jb2011.lnf.beautyeye"))
        {
          UIManager.put("RootPane.setupButtonVisible", false);
        }

        LnFLoader loader = new LnFLoader(def);

        // Enable configuration of FlatLaf options
        if (className.startsWith("com.formdev.flatlaf"))
        {
          isFlatLaf = true;
          configureFlatLaf(loader);
        }
        else if (className.contains("plaf.windows"))
        {
          isWindowsLnF = true;
        }
        else if (className.startsWith("com.jgoodies.looks.plastic"))
        {
          isJGoodies = true;
        }
        else if (className.endsWith("MetalLookAndFeel"))
        {
          isMetalLNF = true;
        }

        LookAndFeel lnf = null;
        if (def.getThemeFile() != null)
        {
          lnf = loadFlatLafTheme(loader, def.getThemeFile());
        }

        if (lnf == null)
        {
          lnf = loader.getLookAndFeel();
        }

        UIManager.setLookAndFeel(lnf);

        if (className.startsWith("com.alee.laf"))
        {
          isWebLaf = true;
          initializeWebLaf();
        }
        PlatformHelper.installGtkPopupBugWorkaround();
      }
    }
    catch (Throwable e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not set look and feel to [" + className + "]. Look and feel will be ignored", e);
      setSystemLnF();
    }
  }

  private LookAndFeel loadFlatLafTheme(LnFLoader loader, File themeFile)
  {
    if (!themeFile.exists()) return null;
    try (InputStream in = new FileInputStream(themeFile))
    {
      Class theme = loader.loadClass("com.formdev.flatlaf.IntelliJTheme", false);
      Method createLaf = theme.getMethod("createLaf", InputStream.class);
      LookAndFeel lnf = (LookAndFeel)createLaf.invoke(theme, in);
      LogMgr.logInfo(new CallerInfo(){}, "Loaded FlatLaf theme from " + themeFile);
      return lnf;
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not load FlatLaf theme " + themeFile.getAbsolutePath());
      return loadDefaultFlatLaf(loader);
    }
  }

  private LookAndFeel loadDefaultFlatLaf(LnFLoader loader)
  {
    try
    {
      return loader.getLookAndFeel(LnFDefinition.FLATLAF_LIGHT_CLASS);
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not load " + LnFDefinition.FLATLAF_LIGHT_CLASS);
      return null;
    }
  }

  private void configureFlatLaf(LnFLoader loader)
  {
    try
    {
      Class flatLaf = loader.loadClass("com.formdev.flatlaf.FlatLaf", false);
      Method registerPackage = flatLaf.getMethod("registerCustomDefaultsSource", String.class, ClassLoader.class);
      registerPackage.invoke(null, "workbench.resource", getClass().getClassLoader());

      Method registerDir = flatLaf.getMethod("registerCustomDefaultsSource", File.class);

      // This can be used when SQL Workbench is started from within an IDE
      String props = Settings.getInstance().getProperty("workbench.flatlaf.propdir", null);
      if (StringUtil.isNotBlank(props))
      {
        File propdir = new File(props);
        registerDir.invoke(null, propdir);
      }

      ClasspathUtil util = new ClasspathUtil();
      File extDir = util.getExtDir();
      File jarDir = util.getJarDir();
      registerDir.invoke(null, extDir);
      registerDir.invoke(null, jarDir);
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not initialize FlatLaf");
    }
  }

  private void initializeWebLaf()
  {
    try
    {
      LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
      Method init = lookAndFeel.getClass().getMethod("initializeManagers");
      init.invoke(null, (Object[])null);

      UIManager.getDefaults().put("ToolBarUI", "com.alee.laf.toolbar.WebToolBarUI");
      UIManager.getDefaults().put("TabbedPaneUI", "com.alee.laf.toolbar.WebTabbedPaneUI");
      UIManager.getDefaults().put("SplitPaneUI", "com.alee.laf.splitpane.WebSplitPaneUI");
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not initialize WebLaf", th);
    }
  }

  private void setSystemLnF()
  {
    try
    {
      String cls = UIManager.getSystemLookAndFeelClassName();
      UIManager.setLookAndFeel(cls);
      isWindowsLnF = cls.contains("plaf.windows");
      isFlatLaf = false;
      isJGoodies = false;
      isWebLaf = false;
    }
    catch (Exception ex)
    {
      // should not happen
    }
  }
}
