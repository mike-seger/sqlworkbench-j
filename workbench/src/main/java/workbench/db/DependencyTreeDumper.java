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
package workbench.db;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.CollectionUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class DependencyTreeDumper
{
  private final Set<TableIdentifier> dumpedNodes = new HashSet<>();

  public void dumpNodes(Collection<DependencyNode> nodes)
  {
    for (DependencyNode node : nodes)
    {
      dumpNode(node);
    }
  }

  public void dumpNode(DependencyNode node)
  {
    if (node == null) return;
    if (CollectionUtil.isEmpty(node.getChildren())) return;
    if (dumpedNodes.contains(node.getTable())) return;

    dumpedNodes.add(node.getTable());

    PrintWriter writer = null;
    try
    {
      File configDir = Settings.getInstance().getConfigDir();
      String table = node.getTable().getTableName();
      File dumpFile = File.createTempFile("deptree_" + StringUtil.makeFilename(table) + "_", ".txt", configDir);

      writer = new PrintWriter(new FileWriter(dumpFile));
      String rootLine = "Root: " + node.getTable().getTableExpression();
      writer.println(rootLine);
      writer.println(StringUtil.padRight("=", rootLine.length(), '='));
      dumpNode(node, 0, writer);
    }
    catch (Exception ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not dump dependency node: " + node.debugString(), ex);
    }
    finally
    {
      FileUtil.closeQuietely(writer);
    }
  }

  private void dumpNode(DependencyNode node, int level, PrintWriter out)
  {
    List<DependencyNode> children = node.getChildren();
    for (DependencyNode child : children)
    {
      out.println(StringUtil.padRight("", level * 4) + child.debugString());
      dumpNode(child, level + 1, out);
    }
  }

}
