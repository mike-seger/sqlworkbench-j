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
package workbench.util.download;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.StringUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 *
 * @author Thomas Kellerer
 */
public class MavenResultParser
{
  private final List<MavenArtefact> artefacts = new ArrayList<>();

  public MavenResultParser(String xml)
  {
    extractResults(xml);
  }

  public List<MavenArtefact> getResult()
  {
    return Collections.unmodifiableList(artefacts);
  }

  private void extractResults(String xml)
  {
    artefacts.clear();
    Document doc = readXml(xml);
    if (doc == null) return;

    try
    {
      XPath xpath = XPathFactory.newInstance().newXPath();
      NodeList nodes = (NodeList)xpath.evaluate("/response/result/doc", doc, XPathConstants.NODESET);
      int numResults = nodes.getLength();
      for (int i=0; i < numResults; i++)
      {
        Element item = (Element)nodes.item(i);
        String name = item.getNodeName();
        if (!"doc".equals(name)) continue; // shouldn't happen

        String version = null;
        String artefactId = null;
        String groupId = null;

        NodeList children = item.getElementsByTagName("str");
        int count = children.getLength();
        for (int c = 0; c < count; c++)
        {
          Element child = (Element)children.item(c);
          String childName = child.getAttribute("name");
          if (StringUtil.isBlank(childName)) continue;

          if (childName.equals("v"))
          {
            version = child.getTextContent();
          }
          if (childName.equals("a"))
          {
            artefactId = child.getTextContent();
          }
          if (childName.equals("g"))
          {
            groupId = child.getTextContent();
          }
        }

        if (StringUtil.allNonEmpty(groupId, artefactId, version))
        {
          if (ignoreVersion(groupId, version))
          {
            LogMgr.logDebug(new CallerInfo(){}, "Ignoring version " + version + " for artefact: " + groupId + ":" + artefactId);
          }
          else
          {
            MavenArtefact ma = new MavenArtefact(groupId, artefactId, version);
            artefacts.add(ma);
          }
        }
      }
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not extract search result", th);
    }
    LogMgr.logDebug(new CallerInfo(){}, "Retrieved "  + artefacts.size() + " artefacts from Maven Central");
  }

  private boolean ignoreVersion(String groupId, String version)
  {
    List<String> ignore = Settings.getInstance().getListProperty("workbench.maven.download.filter." + groupId, true);
    version = version.toLowerCase();
    for (String keyWord : ignore)
    {
      if (version.contains(keyWord)) return true;
    }
    return false;
  }

  private Document readXml(String xml)
  {
    try
    {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      InputSource source = new InputSource(new StringReader(xml));
      return dBuilder.parse(source);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not parse XML", ex);
    }
    return null;
  }
}
