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

import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;

import workbench.storage.DataStore;

import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;

/**
 *
 * @author Thomas Kellerer
 */
public class DbUnitCopier
{
  public String createDBUnitXMLDataString(DataStore data, int selectedRows[])
    throws Exception
  {
    if (data == null) return null;
    if (data.getRowCount() <= 0) return null;

    DBUnitTableAdapter dataTable = new DBUnitTableAdapter(data);
    dataTable.setSelectedRows(selectedRows);
    IDataSet fullDataSet = new DefaultDataSet(dataTable);
    StringWriter s = new StringWriter();
    FlatXmlDataSet.write(fullDataSet, s);
    return s.toString();
  }

  public void writeToFile(File output, DataStore data, int selectedRows[], Charset encoding)
    throws Exception
  {
    if (data == null) return;
    if (data.getRowCount() <= 0) return;

    DBUnitTableAdapter dataTable = new DBUnitTableAdapter(data);
    dataTable.setSelectedRows(selectedRows);
    IDataSet fullDataSet = new DefaultDataSet(dataTable);
    FileWriter writer = new FileWriter(output, encoding);
    FlatXmlDataSet.write(fullDataSet, writer);
  }
}
