/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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
package workbench.db.importer;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CaseInsensitiveComparator;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Thomas Kellerer
 */
public class GenericXmlFileParserTest
  extends WbTestCase
{

  private TableIdentifier currentTable;
  private List<Map<String, Object>> processedRows;
  private List<ColumnIdentifier> targetColumns;

  public GenericXmlFileParserTest()
  {
    super("GenericXMLImport");
  }

  @Test
  public void testAttributesMapping()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection conn = util.getConnection();
    try
    {
      GenericXmlFileParser parser = new GenericXmlFileParser();
      currentTable = null;
      processedRows = new ArrayList<>();
      TestUtil.executeScript(conn, "create table person (id integer, lastname varchar(100), firstname varchar(100))");

      List<ColumnIdentifier> columns = conn.getMetadata().getTableColumns(new TableIdentifier("PERSON"));

      Map<String, String> colMap = new HashMap<>();
      colMap.put("pid", ColumnIdentifier.findColumnInList(columns, "id").getColumnName());
      colMap.put("fname", ColumnIdentifier.findColumnInList(columns, "firstname").getColumnName());
      colMap.put("lname", ColumnIdentifier.findColumnInList(columns, "lastname").getColumnName());
      parser.setTableName("person");

      File input = getResourceFile("person_custom.xml");
      parser.setInputFile(input);
      parser.setRowAttributeMap("row", colMap);
      parser.setReceiver(getReceiver());
      parser.setConnection(conn);
      parser.start();

      assertNotNull(this.currentTable);
      assertEquals("PERSON", currentTable.getTableName());
      assertEquals(4, processedRows.size());
      assertEquals(Integer.valueOf(1), processedRows.get(0).get("ID"));
      assertEquals("Arthur", processedRows.get(0).get("firstname"));
      assertEquals("Dent", processedRows.get(0).get("lastname"));

      assertEquals(Integer.valueOf(4), processedRows.get(3).get("ID"));
      assertEquals("Mary", processedRows.get(3).get("firstname"));
      assertEquals("Moviestar", processedRows.get(3).get("lastname"));
    }
    finally
    {
      util.dropAll(conn);
    }
  }

  @Test
  public void testColumnTags()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection conn = util.getConnection();
    try
    {
      GenericXmlFileParser parser = new GenericXmlFileParser();
      currentTable = null;
      processedRows = new ArrayList<>();
      TestUtil.executeScript(conn, "create table person (id integer, lastname varchar(100), firstname varchar(100))");

      List<ColumnIdentifier> columns = conn.getMetadata().getTableColumns(new TableIdentifier("PERSON"));

      Map<String, String> colMap = new HashMap<>();
      colMap.put("pid", ColumnIdentifier.findColumnInList(columns, "id").getColumnName());
      colMap.put("fname", ColumnIdentifier.findColumnInList(columns, "firstname").getColumnName());
      colMap.put("lname", ColumnIdentifier.findColumnInList(columns, "lastname").getColumnName());
      parser.setTableName("person");

      File input = getResourceFile("person_custom2.xml");
      parser.setInputFile(input);
      parser.setRowAndColumnTags("person", colMap);
      parser.setReceiver(getReceiver());
      parser.setConnection(conn);
      parser.start();

      assertNotNull(this.currentTable);
      assertEquals("PERSON", currentTable.getTableName());
      assertEquals(4, processedRows.size());
      assertEquals(Integer.valueOf(1), processedRows.get(0).get("ID"));
      assertEquals("Arthur", processedRows.get(0).get("firstname"));
      assertEquals("Dent", processedRows.get(0).get("lastname"));

      assertEquals(Integer.valueOf(4), processedRows.get(3).get("ID"));
      assertEquals("Mary", processedRows.get(3).get("firstname"));
      assertEquals("Moviestar", processedRows.get(3).get("lastname"));
    }
    finally
    {
      util.dropAll(conn);
    }
  }

  private DataReceiver getReceiver()
  {
    return new DataReceiver()
    {
      @Override
      public boolean getCreateTarget()
      {
        return false;
      }

      @Override
      public boolean isColumnExpression(String colName)
      {
        return false;
      }

      @Override
      public boolean shouldProcessNextRow()
      {
        return true;
      }

      @Override
      public void nextRowSkipped()
      {

      }

      @Override
      public void setTableList(List<TableIdentifier> targetTables)
      {
      }

      @Override
      public void deleteTargetTables()
        throws SQLException
      {
      }

      @Override
      public void beginMultiTable()
        throws SQLException
      {
      }

      @Override
      public void endMultiTable()
      {
      }

      @Override
      public void processFile(StreamImporter stream)
        throws SQLException, IOException
      {
      }

      @Override
      public void processRow(Object[] row)
        throws SQLException
      {
        Map<String, Object> rowData = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
        for (int i=0; i < targetColumns.size(); i++)
        {
          rowData.put(targetColumns.get(i).getColumnName(), row[i]);
        }
        processedRows.add(rowData);
      }

      @Override
      public void setTableCount(int total)
      {
      }

      @Override
      public void setCurrentTable(int current)
      {
      }

      @Override
      public void setTargetTable(TableIdentifier table, List<ColumnIdentifier> columns, File currentFile)
        throws SQLException
      {
        currentTable = table;
        targetColumns = new ArrayList<>();
        targetColumns.addAll(columns);
      }

      @Override
      public void importFinished()
      {
      }

      @Override
      public void importCancelled()
      {
      }

      @Override
      public void tableImportError()
      {
      }

      @Override
      public void tableImportFinished()
        throws SQLException
      {
      }

      @Override
      public void recordRejected(String record, long importRow, Throwable e)
      {
      }

      @Override
      public boolean isTransactionControlEnabled()
      {
        return true;
      }
    };
  }
}
