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
package workbench.gui.components;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class StringSelectionAdapterTest
{

  public StringSelectionAdapterTest()
  {
  }

  @Test
  public void testTsvToHtml()
  {
    String data =
      "id\tfirstname\tlastname\n" +
      "1\tArthur\tDent\n" +
      "2\tFord\tPrefect";

    StringSelectionAdapter sel = new StringSelectionAdapter(data, true, "\t", "\"");
    String html = sel.getHTMLContent();
    assertTrue(html.contains("<tr><td>id</td><td>firstname</td><td>lastname</td></tr>"));
    assertTrue(html.contains("<tr><td>1</td><td>Arthur</td><td>Dent</td></tr>"));
    assertTrue(html.contains("<tr><td>2</td><td>Ford</td><td>Prefect</td></tr>"));
  }

  @Test
  public void testTsvWithNullToHtml()
  {
    String data =
      "id\tfirstname\tlastname\n" +
      "1\tArthur\tDent\n" +
      "2\t\tPrefect\n" +
      "3\tMarvin\t";

    StringSelectionAdapter sel = new StringSelectionAdapter(data, true, "\t", "\"");
    String html = sel.getHTMLContent();
    assertTrue(html.contains("<tr><td>id</td><td>firstname</td><td>lastname</td></tr>"));
    assertTrue(html.contains("<tr><td>1</td><td>Arthur</td><td>Dent</td></tr>"));
    assertTrue(html.contains("<tr><td>2</td><td></td><td>Prefect</td></tr>"));
    assertTrue(html.contains("<tr><td>3</td><td>Marvin</td><td></td></tr>"));
  }

  @Test
  public void testCsvToHtml()
  {
    String data =
      "id,firstname,lastname\n" +
      "1,Arthur,Dent\n" +
      "2,Ford,Prefect\n" +
      "3,Foo,\"Foo, Bar\"";

    StringSelectionAdapter sel = new StringSelectionAdapter(data, true, ",", "\"");
    String html = sel.getHTMLContent();
    assertTrue(html.contains("<tr><td>id</td><td>firstname</td><td>lastname</td></tr>"));
    assertTrue(html.contains("<tr><td>1</td><td>Arthur</td><td>Dent</td></tr>"));
    assertTrue(html.contains("<tr><td>2</td><td>Ford</td><td>Prefect</td></tr>"));
    assertTrue(html.contains("<tr><td>3</td><td>Foo</td><td>Foo, Bar</td></tr>"));
  }

}
