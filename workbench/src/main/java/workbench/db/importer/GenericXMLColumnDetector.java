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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.FileUtil;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A class to return all attributes or sub-tags from the first occurance of a given (row) tag.
 *
 * @author Thomas Kellerer
 */
public class GenericXMLColumnDetector
  extends DefaultHandler
{
  private String rowTag;
  private List<String> columnNames = new ArrayList<>();
  private final boolean useAttributes;
  private boolean collectTags;

  public GenericXMLColumnDetector(Reader reader, String tag, boolean useAttributes)
    throws IOException, SAXException
  {
    this.rowTag = tag;
    this.useAttributes = useAttributes;
    parseTableStructure(reader);
  }

  public List<String> getColumns()
  {
    return columnNames;
  }

  private void parseTableStructure(Reader in)
    throws IOException, SAXException
  {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setValidating(false);
    try
    {
      SAXParser saxParser = factory.newSAXParser();
      InputSource source = new InputSource(in);
      saxParser.parse(source, this);
    }
    catch (ParserConfigurationException | ParsingEndedException ce)
    {
      // should not happen
    }
    catch (IOException e)
    {
      throw e;
    }
    catch (SAXException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error reading table structure", e);
      throw e;
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName)
    throws SAXException
  {
    if (qName.equals(rowTag))
    {
      this.collectTags = false;
      throw new ParsingEndedException();
    }
  }

  @Override
  public void startElement(String namespaceURI, String sName, String qName, Attributes attrs)
    throws SAXException
  {
    if (qName.equals(rowTag))
    {
      if (useAttributes)
      {
        int length = attrs.getLength();
        for (int i=0; i < length; i++)
        {
          this.columnNames.add(attrs.getQName(i));
        }
        throw new ParsingEndedException();
      }
      collectTags = true;
    }
    else if (collectTags)
    {
      columnNames.add(qName);
    }
  }

}
