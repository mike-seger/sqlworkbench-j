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
package workbench;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * This is a wrapper to kick-off the actual WbManager class.
 *
 * It should run with any JDK >= 1.5 as it does not reference any other classes.
 * <br/>
 * This class is compiled separately in build.xml to allow for a different
 * class file version between this class and the rest of the application.
 * Thus a check for the correct JDK version can be done inside the Java code.
 *
 * @author Thomas Kellerer
 */
@SuppressWarnings("unchecked")
public class WbStarter
{

  public static void main(String[] args)
  {
    final String version = System.getProperty("java.version", System.getProperty("java.runtime.version"));
    String cleanVersion = version;

    int versionNr = -1;

    try
    {
      int p1 = findFirstNonDigit(cleanVersion);
      if (p1 > 0)
      {
        cleanVersion = cleanVersion.substring(0,p1);
      }

      int pos = cleanVersion.indexOf('.');
      int part1 = -1;
      int part2 = -1;

      if (pos < 0)
      {
        part1 = Integer.parseInt(cleanVersion);
      }
      else
      {
        part1 = Integer.parseInt(cleanVersion.substring(0,pos));
        part2 = Integer.parseInt(cleanVersion.substring(pos + 1, pos + 2)); // we only consider one digit at the second position
      }

      // Before Java 9 the Java version was reported as 1.8 or 1.7
      if (cleanVersion.startsWith("1."))
      {
        versionNr = part2;
      }
      else
      {
        versionNr = part1;
      }
    }
    catch (Exception e)
    {
      versionNr = -1;
    }

    final int minVersion = 11;
    if (versionNr < minVersion)
    {
      final String javaHome = System.getProperty("java.home");
      String error =
        "SQL Workbench/J requires Java " + minVersion + ", but the Java version used (" + javaHome + ") is " + version + "\n\n" +
        "If you do have Java "+ minVersion + " installed, please point JAVA_HOME to the location of your Java " + minVersion + " installation, \n" +
        "or refer to the manual for details on how to specify the Java runtime to be used.";

      System.err.println("*** Cannot run this application ***");
      System.err.println(error);
      try
      {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        // The dummy Frame is needed because otherwise the dialog will not appear in the Windows task bar
        Frame dummy = new Frame("SQL Workbench/J - Wrong Java version");
        dummy.setBounds(-10000, -10000, 0, 0);
        dummy.setVisible(true);

        ImageIcon icon = null;
        try
        {
          URL iconUrl = WbStarter.class.getClassLoader().getResource("workbench/resource/images/workbench16.png");
          icon = new ImageIcon(iconUrl);
          dummy.setIconImage(icon.getImage());
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }

        final JDialog d = new JDialog(dummy, "SQL Workbench/J - Wrong Java version", true);

        d.getContentPane().setLayout(new BorderLayout(5, 5));
        JButton b = new JButton("Close");
        b.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent e)
          {
            d.setVisible(false);
            d.dispose();
          }
        });
        JOptionPane pane = new JOptionPane(error, JOptionPane.WARNING_MESSAGE, JOptionPane.DEFAULT_OPTION, (Icon)null, new Object[] { b } );
        d.getContentPane().add(pane, BorderLayout.CENTER);
        d.pack();
        d.setLocationRelativeTo(null);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setVisible(true);
      }
      catch (Throwable e)
      {
        e.printStackTrace();
        // Ignore
      }
      System.exit(1);
    }

    try
    {
      // Do not reference WbManager directly, otherwise a compile
      // of this class will trigger a compile of the other classes, but they
      // should be compiled with a different class file version (see build.xml)
      Class mgr = Class.forName("workbench.WbManager");
      Method main = mgr.getDeclaredMethod("main", new Class[] { String[].class });
      main.invoke(null, new Object[] { args });
    }
    catch (Throwable e)
    {
      e.printStackTrace();
    }
  }

  private static int findFirstNonDigit(String input)
  {
    int len = input.length();
    for (int i=0; i < len; i++)
    {
      char c = input.charAt(i);
      if (c != '.' && (c < '0' || c > '9'))
      {
        return i;
      }
    }
    return -1;
  }

}
