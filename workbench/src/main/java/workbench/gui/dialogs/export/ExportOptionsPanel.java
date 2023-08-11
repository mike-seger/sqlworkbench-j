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
package workbench.gui.dialogs.export;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;

import workbench.interfaces.EncodingSelector;
import workbench.interfaces.ValidatingComponent;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.WbConnection;
import workbench.db.exporter.ExportType;
import workbench.db.exporter.PoiHelper;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ColumnSelectorPanel;
import workbench.gui.components.DbUnitHelper;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.ExtensionFileFilter;
import workbench.gui.components.ValidatingDialog;
import workbench.gui.components.WbFilePicker;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

import static workbench.db.exporter.ExportType.*;

/**
 *
 * @author  Thomas Kellerer
 */
public class ExportOptionsPanel
  extends JPanel
  implements EncodingSelector, ActionListener, ValidatingComponent, PropertyChangeListener
{
  private GeneralExportOptionsPanel generalOptions;
  private JPanel typeOptionsPanel;
  private CardLayout card;
  private WbFilePicker picker;
  private JCheckBox openFile;
  private JComboBox typeSelector;
  private JLabel pickerLabel;
  private JPanel pickerPanel;
  private TextOptionsPanel textOptions;
  private SqlOptionsPanel sqlOptions;
  private HtmlOptionsPanel htmlOptions;
  private XmlOptionsPanel xmlOptions;
  private SpreadSheetOptionsPanel odsOptions;
  private SpreadSheetOptionsPanel xlsOptions;
  private SpreadSheetOptionsPanel xlsmOptions;
  private SpreadSheetOptionsPanel xlsxOptions;
  private FormattedTextOptionsPanel formattedTextOptions;
  private ExportType currentType;
  private List<ColumnIdentifier> selectedColumns;
  private Object columnSelectEventSource;
  private ResultInfo dataStoreColumns;
  private String query;
  private WbConnection dbConnection;
  private boolean poiAvailable = false;
  private boolean xlsxAvailable = false;
  private boolean allowOpenFile = true;
  private boolean forClipboard;
  private String settingsKey = "export";

  private final String ODS_ITEM = ResourceMgr.getString("TxtOdsName");
  private final String XLS_ITEM = ResourceMgr.getString("TxtXlsName");
  private final String FORMATTED_TEXT_ITEM = ResourceMgr.getString("LblCopyFormatedTxt");
  private final String DBUNIT_XML = "DBUnit XML";

  private final String XLSM_ITEM = "Excel XML Spreadsheet (xml)";
  private final String XLSX_ITEM = "Excel Workbook (xlsx)";

  public ExportOptionsPanel(ResultInfo columns)
  {
    this(columns, false);
  }

  public ExportOptionsPanel(ResultInfo columns, boolean forClipboard)
  {
    super();
    this.setLayout(new BorderLayout());
    poiAvailable = PoiHelper.isPoiAvailable();
    xlsxAvailable = PoiHelper.isXLSXAvailable();

    this.forClipboard = forClipboard;
    if (!forClipboard)
    {
      picker = new WbFilePicker(true);
      picker.addPropertyChangeListener(WbFilePicker.PROP_FILENAME, this);
    }
    else
    {
      settingsKey = "clipboard";
    }
    typeSelector = new JComboBox();
    if (forClipboard)
    {
      typeSelector.addItem(FORMATTED_TEXT_ITEM);
    }
    typeSelector.addItem("Text");
    typeSelector.addItem("SQL");
    typeSelector.addItem("XML");
    typeSelector.addItem("JSON");
    if (!forClipboard)
    {
      typeSelector.addItem(ODS_ITEM);
    }
    typeSelector.addItem("HTML");
    if (!forClipboard)
    {
      if (poiAvailable)
      {
        typeSelector.addItem(XLS_ITEM);
      }
      typeSelector.addItem(XLSM_ITEM);
      if (xlsxAvailable)
      {
        typeSelector.addItem(XLSX_ITEM);
      }
    }

    if (DbUnitHelper.isDbUnitAvailable())
    {
      typeSelector.addItem(DBUNIT_XML);
    }

    pickerPanel = createPickerPanel();

    boolean allowColumnSelection = (columns != null);
    this.dataStoreColumns = columns;
    this.generalOptions = new GeneralExportOptionsPanel();
    generalOptions.allowSelectColumns(allowColumnSelection);
    if (forClipboard)
    {
      generalOptions.hideEncodingPanel();
    }
    else
    {
      generalOptions.hideSelectedRowsCbx();
    }

    if (allowColumnSelection)
    {
      generalOptions.showSelectColumnsLabel();
      this.columnSelectEventSource = generalOptions.addColumnSelectListener(this);
    }

    JPanel baseOptionsPanel = new JPanel(new BorderLayout(0, 8));

    baseOptionsPanel.add(pickerPanel, BorderLayout.PAGE_START);
    baseOptionsPanel.add(this.generalOptions, BorderLayout.CENTER);

    this.add(baseOptionsPanel, BorderLayout.PAGE_START);

    this.formattedTextOptions = new FormattedTextOptionsPanel();

    this.textOptions = new TextOptionsPanel();
    this.typeOptionsPanel = new JPanel();
    this.card = new CardLayout();
    this.typeOptionsPanel.setLayout(card);

    if (forClipboard)
    {
      this.typeOptionsPanel.add(this.formattedTextOptions, "formatted-text");
    }
    this.typeOptionsPanel.add(this.textOptions, "text");

    this.sqlOptions = new SqlOptionsPanel(columns);
    this.typeOptionsPanel.add(this.sqlOptions, "sql");

    xmlOptions = new XmlOptionsPanel();
    this.typeOptionsPanel.add(xmlOptions, "xml");

    if (!forClipboard)
    {
      odsOptions = new SpreadSheetOptionsPanel("ods");
      this.typeOptionsPanel.add(odsOptions, "ods");
    }

    htmlOptions = new HtmlOptionsPanel();
    this.typeOptionsPanel.add(htmlOptions, "html");

    xlsmOptions = new SpreadSheetOptionsPanel("xlsm");
    this.typeOptionsPanel.add(xlsmOptions, "xlsm");

    this.typeOptionsPanel.add(new JPanel(), "empty");

    if (!forClipboard)
    {
      if (poiAvailable)
      {
        xlsOptions = new SpreadSheetOptionsPanel("xls");
        this.typeOptionsPanel.add(xlsOptions, "xls");
      }

      if (xlsxAvailable)
      {
        xlsxOptions = new SpreadSheetOptionsPanel("xlsx");
        typeOptionsPanel.add(xlsxOptions, "xlsx");
      }
    }
    this.add(typeOptionsPanel, BorderLayout.CENTER);
    typeSelector.addActionListener(this);
  }

  public void setExportInfo(String info)
  {
    if (pickerPanel == null) return;
    if (StringUtil.isBlank(info)) return;

    int gap = IconMgr.getInstance().getSizeForLabel() / 2;
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 2;
    gc.gridwidth = 3;
    gc.weightx = 1;
    gc.weighty = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.anchor = GridBagConstraints.LINE_START;
    gc.insets = new Insets(gap, 0, gap, 0);
    JLabel l = new JLabel(ResourceMgr.getFormattedString("LblExportInfo", info));
    l.setToolTipText(info);
    pickerPanel.add(l, gc);
  }

  public void setAllowOpenFile(boolean flag)
  {
    this.allowOpenFile = flag;
    this.openFile.setEnabled(flag);
    if (!openFile.isEnabled())
    {
      this.openFile.setSelected(false);
    }
  }

  private JPanel createPickerPanel()
  {
    int gap = 8;

    JPanel panel = new JPanel(new GridBagLayout());
    if (!forClipboard)
    {
      pickerLabel = new JLabel(ResourceMgr.getString("LblExportOutput"));
      openFile = new JCheckBox(ResourceMgr.getString("LblExportOpenOutput"));
      openFile.setMargin(new Insets(0,0,0,0));
      boolean desktopSupported = allowOpenFile && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.OPEN);
      openFile.setEnabled(desktopSupported);
      if (!desktopSupported)
      {
        openFile.setSelected(false);
      }
    }

    // first line
    GridBagConstraints gc = new GridBagConstraints();
    gc.anchor = GridBagConstraints.LINE_START;
    gc.fill = GridBagConstraints.NONE;
    gc.gridwidth = 1;
    gc.gridy = 0;
    gc.gridx = 0;
    gc.weightx = 0;

    if (picker != null)
    {
      gc.insets = new Insets(0, 0, gap, gap);
      panel.add(pickerLabel, gc);

      gc.gridx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weightx = 1.0;
      panel.add(picker, gc);

      gc.gridx = 2;
      gc.weightx = 0;
      gc.fill = GridBagConstraints.NONE;
      gc.insets = new Insets(0, gap, gap, gap);
      panel.add(openFile, gc);
    }

    // second line
    gc.gridx = 0;
    gc.gridy++;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    gc.insets = new Insets(0, 0, 0, gap);
    JLabel typeLabel = new JLabel(ResourceMgr.getString("LblExportType"));
    panel.add(typeLabel, gc);

    gc.gridx = 1;
    gc.gridwidth = picker == null ? 1 : 2;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(typeSelector, gc);

    Border b = new CompoundBorder(DividerBorder.BOTTOM_DIVIDER, new EmptyBorder(0, 0, gap * 2, 0));
    panel.setBorder(b);

    return panel;
  }

  public void setSelectDirectoriesOnly(boolean selectDirs)
  {
    if (picker == null) return;

    this.picker.setSelectDirectoryOnly(selectDirs);
    if (pickerLabel != null)
    {
      String label = selectDirs ? ResourceMgr.getString("LblExportOutputDir") : ResourceMgr.getString("LblExportOutput");
      pickerLabel.setText(label);
      picker.setDialogTitle(label);
      this.invalidate();
    }
  }

  public void setLastDirProperty(String prop)
  {
    if (picker != null) this.picker.setLastDirProperty(prop);
  }

  public boolean doOpenFile()
  {
    if (picker == null) return false;
    return this.openFile.isEnabled() && this.openFile.isSelected();
  }

  public WbFile getSelectedFile()
  {
    if (picker == null) return null;
    return new WbFile(picker.getSelectedFile());
  }

  public FileFilter getFileFilter()
  {
    if (picker == null) return null;
    return picker.getFileFilter();
  }

  @Override
  public boolean validateInput()
  {
    if (forClipboard) return true;

    if (getSelectedFile() == null)
    {
      WbSwingUtilities.showErrorMessageKey(this, "ErrExportFileRequired");
      return false;
    }
    return true;
  }

  @Override
  public void componentDisplayed()
  {
  }

  @Override
  public void componentWillBeClosed()
  {
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getSource() == this.picker)
    {
      File selectedFile = picker.getSelectedFile();
      boolean valid = selectedFile != null;
      firePropertyChange(ValidatingDialog.PROPERTY_VALID_STATE, false, valid);
      if (selectedFile != null)
      {
        WbFile fl = new WbFile(selectedFile);
        String filename = fl.getFullPath();
        FileFilter ff = picker.getFileFilter();
        if (!picker.getSelectDirectoryOnly() && ff instanceof ExtensionFileFilter)
        {
          ExtensionFileFilter eff = (ExtensionFileFilter)ff;
          String ext = ExtensionFileFilter.getExtension(fl);
          if (StringUtil.isEmpty(ext))
          {
            if (!filename.endsWith(".")) filename += ".";
            filename += eff.getDefaultExtension();
            fl = new WbFile(filename);
            picker.setSelectedFile(fl);
          }
        }
      }
    }
  }

  public void setSelectedRowCount(int count)
  {
    this.generalOptions.setSelectedRowCount(count);
  }

  public void updateSqlOptions(DataStore source)
  {
    WbConnection conn = source == null ? null : source.getOriginalConnection();
    dataStoreColumns = (source == null ? null : source.getResultInfo());
    boolean insert = (source != null && source.canSaveAsSqlInsert());
    boolean update = (source != null && source.hasPkColumns());
    sqlOptions.setIncludeUpdate(update);
    sqlOptions.setIncludeDeleteInsert(insert && update);
    sqlOptions.setIncludeMerge(insert && update);
    if (conn != null)
    {
      sqlOptions.setDbId(conn.getDbId());
    }
  }

  public void setQuerySql(String sql, WbConnection con)
  {
    this.query = sql;
    this.dbConnection = con;
    this.dataStoreColumns = null;
    generalOptions.allowSelectColumns(true);
    generalOptions.showRetrieveColumnsLabel();
    if (this.columnSelectEventSource == null)
    {
      this.columnSelectEventSource = generalOptions.addColumnSelectListener(this);
    }
  }

  public void setIncludeMerge(boolean flag)
  {
    this.sqlOptions.setIncludeMerge(flag);
  }

  public void setIncludeSqlUpdate(boolean flag)
  {
    this.sqlOptions.setIncludeUpdate(flag);
  }

  public void setIncludeSqlDeleteInsert(boolean flag)
  {
    this.sqlOptions.setIncludeDeleteInsert(flag);
  }

  public void setSelectedColumns(ResultInfo result, List<ColumnIdentifier> selected)
  {
    this.dataStoreColumns = result;

    if (CollectionUtil.isEmpty(selected))
    {
      this.selectedColumns = null;
    }
    else
    {
      this.selectedColumns = new ArrayList<>(selected);
    }
    updateSelectedColumnsInfo();
  }

  private void updateSelectedColumnsInfo()
  {
    if (allColumnsSelected(dataStoreColumns, selectedColumns))
    {
      this.generalOptions.setSelectedColumnsInfo(null);
    }
    else
    {
      String columns = this.selectedColumns.stream().map(c -> c.getColumnName()).collect(Collectors.joining(", "));
      this.generalOptions.setSelectedColumnsInfo(columns);
    }
  }

  private boolean allColumnsSelected(ResultInfo result, List<ColumnIdentifier> selected)
  {
    if (result == null || CollectionUtil.isEmpty(selected)) return true;
    return result.getColumnCount() == selected.size();
  }

  public List<ColumnIdentifier> getColumnsToExport()
  {
    return this.selectedColumns;
  }

  public void saveSettings()
  {
    this.generalOptions.saveSettings(settingsKey);
    this.sqlOptions.saveSettings(settingsKey);
    this.textOptions.saveSettings(settingsKey);
    this.htmlOptions.saveSettings(settingsKey);
    this.xmlOptions.saveSettings(settingsKey);
    if (forClipboard)
    {
      this.formattedTextOptions.saveSettings(settingsKey);
    }
    else
    {
      this.odsOptions.saveSettings(settingsKey);
      this.xlsmOptions.saveSettings(settingsKey);
      if (this.xlsOptions != null)
      {
        this.xlsOptions.saveSettings(settingsKey);
      }
      if (this.xlsxOptions != null)
      {
        this.xlsxOptions.saveSettings(settingsKey);
      }
    }
    Settings.getInstance().setProperty("workbench." + settingsKey + ".type", this.currentType.getCode());
    if (!forClipboard)
    {
      Settings.getInstance().setProperty("workbench.export.open.output", doOpenFile());
    }
  }

  public void restoreSettings()
  {
    this.generalOptions.restoreSettings(settingsKey);
    this.sqlOptions.restoreSettings(settingsKey);
    this.textOptions.restoreSettings(settingsKey);
    this.htmlOptions.restoreSettings(settingsKey);
    this.xmlOptions.restoreSettings(settingsKey);
    if (forClipboard)
    {
      this.formattedTextOptions.restoreSettings(settingsKey);
    }
    else
    {
      this.odsOptions.restoreSettings(settingsKey);
      this.xlsmOptions.restoreSettings(settingsKey);
      if (this.xlsOptions != null)
      {
        this.xlsOptions.restoreSettings(settingsKey);
      }
      if (this.xlsxOptions != null)
      {
        this.xlsxOptions.restoreSettings(settingsKey);
      }
    }
    String code = Settings.getInstance().getProperty("workbench." + settingsKey + ".type", ExportType.TEXT.getCode());
    ExportType type = ExportType.getTypeFromCode(code);
    this.setExportType(type);

    if (!forClipboard)
    {
      boolean openOutput = Settings.getInstance().getBoolProperty("workbench.export.open.output", false);
      if (this.openFile.isEnabled())
      {
        openFile.setSelected(openOutput);
      }
    }
  }

  /**
   *  Sets the displayed options according to
   *  DataExporter.EXPORT_XXXX types
   */
  public void setExportType(ExportType type)
  {
    if (type == null)
    {
      setTypeText();
      return;
    }
    switch (type)
    {
      case HTML:
        setTypeHtml();
        break;
      case SQL_INSERT:
      case SQL_UPDATE:
      case SQL_DELETE_INSERT:
      case SQL_MERGE:
        setTypeSql();
        break;
      case TEXT:
        setTypeText();
        break;
      case FORMATTED_TEXT:
        setTypeFormattedText();
        break;
      case XML:
        setTypeXml();
        break;
      case DBUNIT_XML:
        setTypeDBUnitXML();
        break;
      case ODS:
        setTypeOds();
        break;
      case JSON:
        setTypeJson();
        break;
      case XLSM:
        setTypeXlsM();
        break;
      case XLSX:
        if (xlsxAvailable) setTypeXlsX();
        break;
      case XLS:
        if (poiAvailable) setTypeXls();
        break;
    }

    // This can happen if XLSX or XLS was passed, but the POI
    // libraries aren't present.
    if (this.currentType == null)
    {
      setTypeText();
    }
  }

  public ExportType getExportType()
  {
    return this.currentType;
  }

  private void showTextOptions()
  {
    this.card.show(this.typeOptionsPanel, "text");
    if (picker != null) picker.setFileFilter(ExtensionFileFilter.getTextFileFilter());
  }

  private void showFormattedTextOptions()
  {
    this.card.show(this.typeOptionsPanel, "formatted-text");
    if (picker != null) picker.setFileFilter(ExtensionFileFilter.getTextFileFilter());
  }

  public void setTypeFormattedText()
  {
    showFormattedTextOptions();
    this.currentType = ExportType.FORMATTED_TEXT;
    typeSelector.setSelectedItem(FORMATTED_TEXT_ITEM);
  }

  public void setTypeText()
  {
    showTextOptions();
    this.currentType = ExportType.TEXT;
    typeSelector.setSelectedItem("Text");
  }

  private void showSqlOptions()
  {
    this.card.show(this.typeOptionsPanel, "sql");
    if (picker != null) this.picker.setFileFilter(ExtensionFileFilter.getSqlFileFilter());
  }

  public void setTypeSql()
  {
    showSqlOptions();
    this.currentType = ExportType.SQL_INSERT;
    typeSelector.setSelectedItem("SQL");
  }

  private void showXmlOptions()
  {
    this.card.show(this.typeOptionsPanel, "xml");
    if (picker != null) this.picker.setFileFilter(ExtensionFileFilter.getXmlFileFilter());
  }

  public void setTypeXml()
  {
    showXmlOptions();
    this.currentType = ExportType.XML;
    typeSelector.setSelectedItem("XML");
  }

  private void showJsonOption()
  {
    showEmptyOptions();
    if (picker != null) this.picker.setFileFilter(ExtensionFileFilter.getJsonFilterFilter());
  }

  private void showHtmlOptions()
  {
    this.card.show(this.typeOptionsPanel, "html");
    if (picker != null) this.picker.setFileFilter(ExtensionFileFilter.getHtmlFileFilter());
  }

  public void setTypeHtml()
  {
    showHtmlOptions();
    this.currentType = ExportType.HTML;
    typeSelector.setSelectedItem("HTML");
  }

  private void showOdsOptions()
  {
    this.card.show(this.typeOptionsPanel, "ods");
    if (picker != null) picker.setFileFilter(ExtensionFileFilter.getOdsFileFilter());
  }

  public void setTypeOds()
  {
    showOdsOptions();
    this.currentType = ExportType.ODS;
    typeSelector.setSelectedItem(ODS_ITEM);
  }

  private void showXlsOptions()
  {
    this.card.show(this.typeOptionsPanel, "xls");
    if (picker != null) this.picker.setFileFilter(ExtensionFileFilter.getXlsFileFilter());
  }

  private void showXlsXOptions()
  {
    this.card.show(this.typeOptionsPanel, "xlsx");
    if (picker != null) this.picker.setFileFilter(ExtensionFileFilter.getXlsXFileFilter());
  }

  private void showXlsMOptions()
  {
    this.card.show(this.typeOptionsPanel, "xlsm");
    if (picker != null) this.picker.setFileFilter(ExtensionFileFilter.getXlsMFileFilter());
  }

  private void showEmptyOptions()
  {
    this.card.show(this.typeOptionsPanel, "empty");
  }

  public void showDBUnitOptions()
  {
    showEmptyOptions();
    if (picker != null) this.picker.setFileFilter(ExtensionFileFilter.getXmlFileFilter());
  }

  public void setTypeDBUnitXML()
  {
    showEmptyOptions();
    currentType = ExportType.DBUNIT_XML;
    typeSelector.setSelectedItem(DBUNIT_XML);
  }

  public void setTypeXls()
  {
    showXlsOptions();
    this.currentType = ExportType.XLS;
    typeSelector.setSelectedItem(XLS_ITEM);
  }

  public void setTypeXlsX()
  {
    showXlsXOptions();
    this.currentType = ExportType.XLSX;
    typeSelector.setSelectedItem(XLSX_ITEM);
  }

  public void setTypeJson()
  {
    showJsonOption();
    this.currentType = ExportType.JSON;
    typeSelector.setSelectedItem("JSON");
  }

  public void setTypeXlsM()
  {
    showXlsMOptions();
    this.currentType = ExportType.XLSM;
    typeSelector.setSelectedItem(XLSM_ITEM);
  }

  public SpreadSheetOptions getXlsMOptions()
  {
    return xlsmOptions;
  }

  public SpreadSheetOptions getXlsOptions()
  {
    return xlsOptions;
  }

  public SpreadSheetOptions getXlsXOptions()
  {
    return xlsxOptions;
  }

  public SpreadSheetOptions getOdsOptions()
  {
    return odsOptions;
  }

  public XmlOptions getXmlOptions()
  {
    return xmlOptions;
  }

  public HtmlOptions getHtmlOptions()
  {
    return htmlOptions;
  }

  public SqlOptions getSqlOptions()
  {
    return sqlOptions;
  }

  public ExportOptions getExportOptions()
  {
    return generalOptions;
  }

  public TextOptions getTextOptions()
  {
    return textOptions;
  }

  public FormattedTextOptions getFormattedTextOptions()
  {
    return formattedTextOptions;
  }

  @Override
  public String getEncoding()
  {
    return generalOptions.getEncoding();
  }

  @Override
  public void setEncoding(String enc)
  {
    generalOptions.setEncoding(enc);
  }

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == this.typeSelector)
    {
      Object item = typeSelector.getSelectedItem();
      String itemValue = item.toString();
      ExportType type = null;

      this.card.show(this.typeOptionsPanel, itemValue);

      if ("text".equalsIgnoreCase(itemValue))
      {
        type = ExportType.TEXT;
        showTextOptions();
      }
      else if ("sql".equalsIgnoreCase(itemValue))
      {
        type = ExportType.SQL_INSERT;
        showSqlOptions();
      }
      else if ("xml".equalsIgnoreCase(itemValue))
      {
        type = ExportType.XML;
        showXmlOptions();
      }
      else if (item == ODS_ITEM)
      {
        type = ExportType.ODS;
        showOdsOptions();
      }
      else if (item == XLSX_ITEM && xlsxAvailable)
      {
        type = ExportType.XLSX;
        showXlsXOptions();
      }
      else if (item == XLS_ITEM && poiAvailable)
      {
        type = ExportType.XLS;
        showXlsOptions();
      }
      else if (item == XLSM_ITEM)
      {
        type = ExportType.XLSM;
        showXlsMOptions();
      }
      else if ("html".equalsIgnoreCase(itemValue))
      {
        type = ExportType.HTML;
        showHtmlOptions();
      }
      else if ("json".equalsIgnoreCase(itemValue))
      {
        type = ExportType.JSON;
        showJsonOption();
      }
      else if (item == FORMATTED_TEXT_ITEM)
      {
        type = ExportType.FORMATTED_TEXT;
        showFormattedTextOptions();
      }
      else if (item == DBUNIT_XML)
      {
        type = ExportType.DBUNIT_XML;
        showDBUnitOptions();
      }

      this.currentType = type;
      firePropertyChange("exportType", null, type);
    }
    else if (event.getSource() == this.columnSelectEventSource)
    {
      this.selectColumns();
    }
  }

  private void retrieveQueryColumns()
  {
    try
    {
      WbSwingUtilities.showWaitCursor(this);
      this.dataStoreColumns = SqlUtil.getResultInfoFromQuery(this.query, this.dbConnection);
      sqlOptions.setResultInfo(this.dataStoreColumns);
    }
    catch (Exception e)
    {
      this.dataStoreColumns = null;
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve query columns", e);
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  private void selectColumns()
  {
    if (this.dataStoreColumns == null)
    {
      if (this.query != null)
      {
        retrieveQueryColumns();
      }
    }
    if (this.dataStoreColumns == null) return;

    ColumnSelectorPanel columnSelectorPanel = new ColumnSelectorPanel(this.dataStoreColumns.getColumns());
    columnSelectorPanel.selectColumns(this.selectedColumns);

    boolean ok = ValidatingDialog.showOKCancelDialog((Dialog)SwingUtilities.getWindowAncestor(this),
                                                      columnSelectorPanel,
                                                      ResourceMgr.getString("MsgSelectColumnsWindowTitle"));
    if (ok)
    {
      this.selectedColumns = columnSelectorPanel.getSelectedColumns();
    }
    updateSelectedColumnsInfo();
  }

}
