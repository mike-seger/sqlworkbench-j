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

import java.io.Reader;
import java.util.List;

import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class GenericXMLColumnDetectorTest
  extends WbTestCase
{

  public GenericXMLColumnDetectorTest()
  {
    super("AttributesDetectorTest");
  }

  @Test
  public void testGetAttributes()
    throws Exception
  {
    try (Reader reader = getResourceReader("person_custom.xml", "UTF-8");)
    {
      GenericXMLColumnDetector detector = new GenericXMLColumnDetector(reader, "row", true);
      List<String> attributes = detector.getColumns();
      assertEquals(3, attributes.size());
      assertEquals("pid", attributes.get(0));
      assertEquals("fname", attributes.get(1));
      assertEquals("lname", attributes.get(2));
    }
  }

  @Test
  public void testGetTags()
    throws Exception
  {
    try (Reader reader = getResourceReader("person_custom2.xml", "UTF-8");)
    {
      GenericXMLColumnDetector detector = new GenericXMLColumnDetector(reader, "person", false);
      List<String> attributes = detector.getColumns();
      assertEquals(3, attributes.size());
      assertEquals("pid", attributes.get(0));
      assertEquals("fname", attributes.get(1));
      assertEquals("lname", attributes.get(2));
    }
  }

}
