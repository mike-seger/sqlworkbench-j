/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */

package workbench.sql.macros;

import java.awt.Component;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import workbench.WbManager;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ExtensionFileFilter;
import workbench.gui.components.WbFileChooser;

import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class MacroFileSelector
{
  public static final String LAST_DIR_PROPERTY = "workbench.macros.lastdir";

  public boolean canLoadMacros(int clientId)
  {
    if (!MacroManager.getInstance().getMacros(clientId).isModified()) return true;

    int result = WbSwingUtilities.getYesNoCancel(WbManager.getInstance().getCurrentWindow(), ResourceMgr.getString("MsgConfirmUnsavedMacros"));
    if (result == JOptionPane.CANCEL_OPTION)
    {
      return false;
    }
    if (result == JOptionPane.YES_OPTION)
    {
      MacroManager.getInstance().save();
    }
    return true;
  }

  public WbFile selectMacroFile(Component parent)
  {
    return selectStorageFile(parent, false, null);
  }

  public WbFile selectStorageForLoad(Component parent, int clientId)
  {
    if (!canLoadMacros(clientId)) return null;
    return selectStorageFile(parent, false, null);
  }

  public WbFile selectStorageForSave(Component parent, int clientId)
  {
    return selectStorageFile(parent, true, MacroManager.getInstance().getMacros(clientId).getCurrentFile());
  }

  private WbFile selectStorageFile(Component parent, boolean forSave, File currentFile)
  {
    String lastDir = Settings.getInstance().getProperty(LAST_DIR_PROPERTY, Settings.getInstance().getConfigDir().getAbsolutePath());

    JFileChooser fc = new WbFileChooser(lastDir);
    if (Settings.getInstance().enableDirectoryBasedMacroStorage())
    {
      fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    }
    else
    {
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    }
    fc.addChoosableFileFilter(ExtensionFileFilter.getXmlFileFilter());
    fc.setFileFilter(ExtensionFileFilter.getXmlFileFilter());
    fc.setDialogTitle(ResourceMgr.getString("MsgSelectMacroFile"));

    int answer = JFileChooser.CANCEL_OPTION;

    if (forSave)
    {
      if (currentFile != null)
      {
        fc.setSelectedFile(currentFile);
      }
      answer = fc.showSaveDialog(parent);
    }
    else
    {
      answer = fc.showOpenDialog(parent);
    }

    File selectedFile = null;

    if (answer == JFileChooser.APPROVE_OPTION)
    {
      selectedFile = fc.getSelectedFile();
      if (forSave)
      {
        WbFile wb = new WbFile(selectedFile);
        String ext = wb.getExtension();
        if (!wb.isDirectory() && !ext.equalsIgnoreCase("xml"))
        {
          String fullname = wb.getFullPath();
          fullname += ".xml";
          selectedFile = new File(fullname);
        }
      }
      lastDir = fc.getCurrentDirectory().getAbsolutePath();
      Settings.getInstance().setProperty(LAST_DIR_PROPERTY, lastDir);
    }
    if (selectedFile == null)
    {
      return null;
    }
    return new WbFile(selectedFile);
  }

}
