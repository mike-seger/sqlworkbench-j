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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import workbench.interfaces.Disposable;
import workbench.interfaces.Restoreable;
import workbench.interfaces.ValidatingComponent;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.DividerBorder;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;

/**
 * @author Thomas Kellerer
 */
public class OptionPanelPage
{
  public static final Border PAGE_PADDING = new EmptyBorder(8, 8, 8, 8);
  private static final Set<String> NO_PADDING_PANELS = CollectionUtil.treeSet(
    "LnFOptionsPanel", "ExternalToolsPanel", "FormatterOptionsPanel",
    "DbExplorerOptionsPanel", "GlobalSshHostsPanel");

  private String label;
  private String pageClass;
  private JPanel panel;
  private Restoreable options;
  private boolean addPadding;

  public OptionPanelPage(String clz, String key)
  {
    this.label = ResourceMgr.getString(key);
    this.pageClass = "workbench.gui.settings." + clz;
    addPadding = !NO_PADDING_PANELS.contains(clz);
  }

  @Override
  public String toString()
  {
    return this.label;
  }

  public String getLabel()
  {
    return label;
  }

  public JPanel getPanel()
  {
    if (this.panel == null)
    {
      try
      {
        Class<? extends JPanel> clz = (Class<? extends JPanel>)Class.forName(this.pageClass);
        JPanel optionPanel = clz.getDeclaredConstructor().newInstance();
        this.options = (Restoreable)optionPanel;
        this.options.restoreSettings();

        JLabel title = new JLabel(this.label);
        title.setOpaque(true);
        title.setBackground(UIManager.getColor("TextArea.background"));
        title.setForeground(UIManager.getColor("TextArea.foreground"));
        title.setBorder(new EmptyBorder(6,6,6,6));
        Font f = title.getFont();
        float newSize = f.getSize2D() * 1.1f;
        title.setFont(f.deriveFont(Font.BOLD, newSize));
        panel = new JPanel(new BorderLayout());
        Color c = WbSwingUtilities.getLineBorderColor(panel);
        DividerBorder db = new DividerBorder(DividerBorder.TOP, c);
        Border pb;
        if (addPadding)
        {
          pb = new CompoundBorder(db, PAGE_PADDING);
        }
        else
        {
          pb = db;
        }
        optionPanel.setBorder(pb);
        panel.add(title, BorderLayout.NORTH);
        panel.add(optionPanel, BorderLayout.CENTER);
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not create panel", e);
        panel = new JPanel();
        panel.add(new JLabel(ExceptionUtil.getDisplay(e)));
      }
    }
    return this.panel;
  }

  public boolean validateInput()
  {
    if (this.options instanceof ValidatingComponent)
    {
      ValidatingComponent vc = (ValidatingComponent)this.options;
      return vc.validateInput();
    }
    return true;
  }

  public void saveSettings()
  {
    try
    {
      if (this.options != null)
      {
        options.saveSettings();
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not save panel settings", e);
    }
  }

  public void dispose()
  {
    if (options instanceof Disposable)
    {
      ((Disposable)options).dispose();
    }
  }

}
