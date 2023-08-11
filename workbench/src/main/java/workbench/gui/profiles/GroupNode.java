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
package workbench.gui.profiles;

import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import workbench.db.ConnectionProfile;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class GroupNode
  extends DefaultMutableTreeNode
{
  private final boolean isDummyRoot;

  public GroupNode(String name, boolean isDummy)
  {
    super(name, true);
    this.isDummyRoot = isDummy;
  }

  public void setName(String newName)
  {
    this.setUserObject(newName);
    updateProfileGroups(this);
  }

  public int getFirstProfileIndex()
  {
    int count = getChildCount();
    for (int i = 0; i < count; i++)
    {
      TreeNode child  = (TreeNode)getChildAt(i);
      if (!child.getAllowsChildren()) return i;
    }
    return -1;
  }

  /**
   * Returns all profile nodes from this group and any child group.
   */
  public List<ProfileNode> getProfileNodes()
  {
    return getProfileNodes(this);
  }

  private List<ProfileNode> getProfileNodes(GroupNode node)
  {
    List<ProfileNode> profiles = new ArrayList<>();
    int count = node.getChildCount();
    for (int i = 0; i < count; i++)
    {
      DefaultMutableTreeNode child  = (DefaultMutableTreeNode)node.getChildAt(i);
      if (child instanceof ProfileNode)
      {
        profiles.add((ProfileNode)child);
      }
      else if (child instanceof GroupNode)
      {
        profiles.addAll(getProfileNodes((GroupNode)child));
      }
    }
    return profiles;
  }

  public void updateProfileGroups()
  {
    updateProfileGroups(this);
  }

  public void updateProfileGroups(GroupNode node)
  {
    List<String> groups = getGroupPath();
    int count = node.getChildCount();
    for (int i = 0; i < count; i++)
    {
      DefaultMutableTreeNode child  = (DefaultMutableTreeNode)node.getChildAt(i);
      if (child instanceof ProfileNode)
      {
        ConnectionProfile p = ((ProfileNode)child).getProfile();
        p.setGroups(groups);
      }
      else if (child instanceof GroupNode)
      {
        updateProfileGroups((GroupNode)child);
      }
    }
  }

  public ProfileNode findFirstProfile()
  {
    return findFirstProfile(this);
  }

  private ProfileNode findFirstProfile(GroupNode node)
  {
    if (node == null) return null;
    int count = node.getChildCount();
    for (int i = 0; i < count; i++)
    {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
      if (child instanceof ProfileNode)
      {
        return (ProfileNode)child;
      }
      else if (child instanceof GroupNode)
      {
        return findFirstProfile((GroupNode)child);
      }
    }
    return null;
  }

  public boolean containsGroup(String group)
  {
    if (group == null) return false;
    int count = this.getChildCount();
    for (int i = 0; i < count; i++)
    {
      DefaultMutableTreeNode node  = (DefaultMutableTreeNode)this.getChildAt(i);
      if (node instanceof GroupNode)
      {
        if (group.equals(node.toString())) return true;
      }
    }
    return false;
  }

  public boolean containsProfile(ConnectionProfile profile)
  {
    if (profile == null) return false;
    int count = this.getChildCount();
    for (int i = 0; i < count; i++)
    {
      DefaultMutableTreeNode node  = (DefaultMutableTreeNode)this.getChildAt(i);
      if (node instanceof ProfileNode)
      {
        ConnectionProfile p = ((ProfileNode)node).getProfile();
        if (profile.equals(p))
        {
          return true;
        }
      }
    }
    return false;
  }

  public GroupNode getOrCreatePath(List<String> groupPath)
  {
    if (CollectionUtil.isEmpty(groupPath)) return null;
    GroupNode firstGroup = findChildGroup(groupPath.get(0));
    if (firstGroup == null)
    {
      firstGroup = new GroupNode(groupPath.get(0), false);
      this.add(firstGroup);
    }
    if (groupPath.size() > 1)
    {
      return firstGroup.getOrCreatePath(groupPath.subList(1, groupPath.size()));
    }
    return firstGroup;
  }

  private GroupNode findChildGroup(String name)
  {
    int count = this.getChildCount();
    for (int i = 0; i < count; i++)
    {
      DefaultMutableTreeNode node  = (DefaultMutableTreeNode)this.getChildAt(i);
      if (node instanceof GroupNode)
      {
        String gname = node.toString();
        if (name.equals(gname))
        {
          return (GroupNode)node;
        }
      }
    }
    return null;
  }

  /**
   * Returns the TreePath for the given ConnectionProfile in this group.
   *
   * If the given profile is not part of this group, null is returned.
   *
   * @param profile  the profile to find
   * @return null if the profile is not part of this group
   */
  public TreePath getProfilePath(ConnectionProfile profile)
  {
    if (profile == null) return null;
    ProfileNode profileNode = null;
    GroupNode groupNode = findGroupNode(profile.getGroups());
    if (groupNode == null) return null;

    int count = groupNode.getChildCount();
    for (int i = 0; i < count; i++)
    {
      DefaultMutableTreeNode node  = (DefaultMutableTreeNode)groupNode.getChildAt(i);
      if (node instanceof ProfileNode)
      {
        ConnectionProfile p = ((ProfileNode)node).getProfile();
        if (profile.equals(p))
        {
          profileNode = (ProfileNode)node;
        }
      }
    }
    if (profileNode == null) return null;
    return new TreePath(profileNode.getPath());
  }

  public GroupNode findGroupNode(List<String> path)
  {
    if (CollectionUtil.isEmpty(path)) return null;
    int count = this.getChildCount();
    for (int i = 0; i < count; i++)
    {
      DefaultMutableTreeNode node  = (DefaultMutableTreeNode)this.getChildAt(i);
      if (node instanceof GroupNode)
      {
        GroupNode group = (GroupNode)node;
        if (path.equals(group.getGroupPath()))
        {
          return group;
        }
        else if (path.size() > 1)
        {
          GroupNode next = group.findGroupNode(path);
          if (next != null) return next;
        }
      }
    }
    return null;
  }

  public List<String> getGroupPath()
  {
    List<String> path = new ArrayList<>();
    path.add(this.userObject.toString());

    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)getParent();
    while (parentNode != null)
    {
      if (parentNode instanceof GroupNode)
      {
        GroupNode parentGroup = (GroupNode)parentNode;
        if (!parentGroup.isDummyRoot)
        {
          path.add(0, (String)parentNode.getUserObject());
        }
      }
      parentNode = (DefaultMutableTreeNode)parentNode.getParent();
    }
    return path;
  }

  public String getGroupPathAsString()
  {
    return ProfileKey.getGroupPathAsString(getGroupPath());
  }
}
