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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;
/**
 *
 * @author Thomas Kellerer
 */
public class SODSReaderTest
  extends WbTestCase
{

  public SODSReaderTest()
  {
    super("OdsReaderTest");
  }

  @Test
  public void testReadSecondSheet()
    throws Exception
  {
    File input = TestUtil.getResourceFile(this, "data.ods");
    SODSReader reader = new SODSReader(input, 1, null);
    try
    {
      reader.load();
      assertEquals(5, reader.getRowCount());
      reader.setActiveWorksheet("orders");
      assertEquals(5, reader.getRowCount());
      reader.setActiveWorksheet("person");
      assertEquals(3, reader.getRowCount());
    }
    finally
    {
      reader.done();
    }
  }

  @Test
  public void testDateTimeTypes()
    throws Exception
  {
    File input = TestUtil.getResourceFile(this, "date-time-types.ods");
    SODSReader reader = new SODSReader(input, 0, null);
    reader.load();
    List<String> header = reader.getHeaderColumns();
    assertEquals(3, header.size());
    assertEquals("date_col", header.get(0));
    assertEquals("time_col", header.get(1));
    assertEquals("ts_col", header.get(2));

    List<Object> values = reader.getRowValues(1);
    assertEquals(3, values.size());
    LocalDate ld = (LocalDate)values.get(0);
    assertEquals(LocalDate.of(1980,11,1), ld);
    LocalTime lt = (LocalTime)values.get(1);
    assertEquals(LocalTime.of(23,54,14), lt);
    LocalDateTime ldt = (LocalDateTime)values.get(2);
    assertEquals(LocalDateTime.of(1990,10,1,17,4,6), ldt);
  }

  @Test
  public void testReader()
    throws Exception
  {
    File input = TestUtil.getResourceFile(this, "data.ods");
    SODSReader reader = new SODSReader(input, 0, null);

    try
    {
      reader.load();
      List<String> header = reader.getHeaderColumns();
      assertNotNull(header);
      assertEquals(6, header.size());
      assertEquals("id", header.get(0));
      assertEquals("firstname", header.get(1));
      assertEquals("lastname", header.get(2));
      assertEquals("hiredate", header.get(3));
      assertEquals("salary", header.get(4));
      assertEquals("last_login", header.get(5));
      assertEquals(3, reader.getRowCount());

      // check first data row
      List<Object> values = reader.getRowValues(1);
      assertEquals(6, values.size());
      Number n = (Number)values.get(0);
      assertEquals(1, n.intValue());
      String s = (String)values.get(1);
      assertEquals("Arthur", s);
      s = (String)values.get(2);
      assertEquals("Dent", s);
      LocalDate hire = (LocalDate)values.get(3);
      assertNotNull(hire);
      assertEquals(LocalDate.of(2010, 6, 7), hire);

      Double sal = (Double)values.get(4);
      assertNotNull(sal);
      assertEquals(4200.24, sal.doubleValue(), 0.01);

      LocalDateTime ts = (LocalDateTime)values.get(5);
      assertEquals(LocalDateTime.of(2012,4,5,16,17,18), ts);

      values = reader.getRowValues(2);
      assertEquals(6, values.size());
      n = (Number)values.get(0);
      assertEquals(2, n.intValue());
      s = (String)values.get(1);
      assertEquals("Ford", s);
      s = (String)values.get(2);
      assertEquals("Prefect", s);
      hire = (LocalDate)values.get(3);
      assertNotNull(hire);

      assertEquals(LocalDate.of(1980,7,24), hire);
      sal = (Double)values.get(4);
      assertNotNull(sal);
      assertEquals(1234.56, sal.doubleValue(), 0.01);

      ts = (LocalDateTime)values.get(5);
      assertEquals(LocalDateTime.of(2012,7,8,15,16,17), ts);
    }
    finally
    {
      reader.done();
    }
  }

}
