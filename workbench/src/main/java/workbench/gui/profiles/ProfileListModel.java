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
package workbench.gui.profiles;

import java.awt.dnd.DnDConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.ProfileGroupMap;
import workbench.db.ProfileManager;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class ProfileListModel
  extends DefaultTreeModel
  implements ProfileChangeListener
{
  private File sourceFile;
  private final GroupNode rootNode = new GroupNode("Profiles", true);
  private final List<ConnectionProfile> profiles = new ArrayList<>();;
  private final List<ConnectionProfile> filtered = new ArrayList<>();
  private boolean profilesDeleted;

  public ProfileListModel()
  {
    super(new GroupNode("Profiles", true), true);
    addDefaultGroup();
  }

  public ProfileListModel(List<ConnectionProfile> sourceProfiles)
  {
    super(new GroupNode("Profiles", true), true);

    for (ConnectionProfile prof : sourceProfiles)
    {
      profiles.add(prof.createStatefulCopy());
    }
    buildTree();
  }

  public TreePath addDefaultGroup()
  {
    return addGroup(rootNode, ResourceMgr.getString("LblDefGroup"));
  }

  public void setSourceFile(File f)
  {
    sourceFile = f;
  }

  public File getSourceFile()
  {
    return sourceFile;
  }

  private void sortList(List<ConnectionProfile> toSort)
  {
    if (toSort == null) return;
    toSort.sort(ConnectionProfile.getNameComparator());
  }

  @Override
  public void profileChanged(ConnectionProfile profile)
  {
    TreePath path = getPath(profile);
    if (path == null) return;
    if (path.getPathCount() < 3) return;
    DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode)path.getPathComponent(2);
    DefaultMutableTreeNode pNode = (DefaultMutableTreeNode)path.getLastPathComponent();
    int index = groupNode.getIndex(pNode);
    fireTreeNodesChanged(this.rootNode, path.getPath(), new int[] { index }, new Object[] { pNode });
  }

  public TreePath addProfile(ConnectionProfile profile)
  {
    if (profile == null) return null;

    profiles.add(profile);
    GroupNode group = rootNode.getOrCreatePath(profile.getGroups());
    if (group == null)
    {
      LogMgr.logError(new CallerInfo(){}, "addProfile() called, but no group defined. Adding default group.", new Exception("Backtrace"));
      TreePath grp = addDefaultGroup();
      group = (GroupNode)grp.getLastPathComponent();
    }
    ProfileNode newNode = new ProfileNode(profile);
    insertNodeInto(newNode, group, group.getChildCount());
    TreePath newPath = new TreePath(new Object[] { this.rootNode, group, newNode });
    return newPath;
  }

  public GroupNode findGroupNode(List<String> groupPath)
  {
    if (this.rootNode == null) return null;
    if (groupPath == null) return null;
    List<GroupNode> nodes = getGroupNodes(rootNode);
    for (GroupNode node : nodes)
    {
      if (node.getGroupPath().equals(groupPath))
      {
        return node;
      }
    }
    return null;
  }

  public boolean isFiltered()
  {
    return filtered.size() > 0;
  }

  public Set<String> getAllTags()
  {
    Set<String> allTags = CollectionUtil.caseInsensitiveSet();
    profiles.stream().forEach((prof) -> {
      allTags.addAll(prof.getTags());
    });

    filtered.stream().forEach((prof) ->
    {
      allTags.addAll(prof.getTags());
    });

    return allTags;
  }

  public void resetFilter()
  {
    profiles.addAll(filtered);
    filtered.clear();
    buildTree();
  }

  public void applyTagFilter(Set<String> tags)
  {
    profiles.addAll(filtered);
    filtered.clear();
    if (CollectionUtil.isNonEmpty(tags))
    {
      Iterator<ConnectionProfile> itr = profiles.iterator();
      while (itr.hasNext())
      {
        ConnectionProfile profile = itr.next();
        if (!profile.getTags().containsAll(tags))
        {
          filtered.add(profile);
          itr.remove();
        }
      }
    }
    buildTree();
  }

  public void applyNameFilter(String value)
  {
    profiles.addAll(filtered);
    filtered.clear();
    if (StringUtil.isNotBlank(value))
    {
      value = value.toLowerCase();
      Iterator<ConnectionProfile> itr = profiles.iterator();
      while (itr.hasNext())
      {
        ConnectionProfile profile = itr.next();
        String name = StringUtil.coalesce(profile.getName(), "").toLowerCase();
        String url = StringUtil.coalesce(profile.getUrl(), "").toLowerCase();
        String user = StringUtil.coalesce(profile.getUsername(), "").toLowerCase();

        boolean keep = name.contains(value);
        keep = keep || (GuiSettings.getIncludeJDBCUrlInProfileSearch() && url.contains(value));
        keep = keep || (GuiSettings.getIncludeUsernameInProfileSearch() && user.contains(value));

        if (!keep)
        {
          filtered.add(profile);
          itr.remove();
        }
      }
    }
    buildTree();
  }

  public boolean isChanged()
  {
    return profilesDeleted || profilesAreModified() || groupsChanged();
  }

  /**
   *  Returns true if any of the profile definitions has changed.
   *  (Or if a profile has been deleted or added)
   */
  public boolean profilesAreModified()
  {
    if (this.profiles.stream().anyMatch((profile) -> (profile.isChanged()))) return true;
    if (this.filtered.stream().anyMatch((profile) -> (profile.isChanged()))) return true;

    return false;
  }

  public boolean groupsChanged()
  {
    if (profiles.stream().anyMatch((profile) -> (profile.isGroupChanged()))) return true;
    if (filtered.stream().anyMatch((profile) -> (profile.isGroupChanged()))) return true;

    return false;
  }

  public TreePath[] getGroupNodes()
  {
    List<GroupNode> nodes = getGroupNodes(rootNode);
    TreePath[] result = new TreePath[nodes.size()];
    for (int i=0; i < nodes.size(); i++)
    {
      result[i] = new TreePath(nodes.get(i).getPath());
    }
    return result;
  }

  private List<GroupNode> getGroupNodes(TreeNode node)
  {
    List<GroupNode> result = new ArrayList<>();
    if (node == null) return result;
    int children = this.getChildCount(node);

    for (int i = 0; i < children; i++)
    {
      DefaultMutableTreeNode n = (DefaultMutableTreeNode)node.getChildAt(i);
      if (n instanceof GroupNode)
      {
        result.add((GroupNode)n);
        result.addAll(getGroupNodes(n));
      }
    }
    return result;
  }

  public List<String> getGroups()
  {
    List<String> result = new ArrayList<>();
    if (this.rootNode == null) return result;
    List<GroupNode> nodes = getGroupNodes(rootNode);
    for (GroupNode node : nodes)
    {
      result.add(node.getGroupPathAsString());
    }
    return result;
  }

  public void deleteGroup(List<String> groupPath)
  {
    if (CollectionUtil.isEmpty(groupPath)) return;
    GroupNode node = findGroupNode(groupPath);
    deleteGroup(node);
  }

  public void deleteGroup(GroupNode node)
  {
    if (node == null) return;
    removeGroupNode(node);
  }

  public void deleteGroupProfiles(GroupNode groupNode)
  {
    if (groupNode == null) return;
    int count = groupNode.getChildCount();
    if (count == 0) return;
    for (int i = 0; i < count; i++)
    {
      TreeNode child = (TreeNode)groupNode.getChildAt(i);
      if (child instanceof ProfileNode)
      {
        ConnectionProfile prof = ((ProfileNode)child).getProfile();
        profiles.remove(prof);
      }
      else if (child instanceof GroupNode)
      {
        deleteGroupProfiles((GroupNode)child);
      }
    }
    groupNode.removeAllChildren();
  }

  public DefaultMutableTreeNode moveOrCopyGroups(List<GroupNode> source, GroupNode target, int action)
  {
    int index = target.getFirstProfileIndex();
    if (index < 0) index = 0;
    int firstIndex = index;
    for (GroupNode node : source)
    {
      target.insert(node, index++);

      // moveGroups() is called as the result of a Drag & Drop operation
      // Swing creates a copy of the nodes (including their userobject) using serialization
      // if the Drag&Drop is done inside the same tree, we need to replae the node's
      // profile with the real one from this model
      if (action == DnDConstants.ACTION_MOVE)
      {
        replaceWithOriginalProfiles(node);
      }

      node.updateProfileGroups();
    }
    DefaultMutableTreeNode firstGroup = (DefaultMutableTreeNode)target.getChildAt(firstIndex);
    fireTreeStructureChanged(this, null, null, null);
    return firstGroup;
  }

  private void replaceWithOriginalProfiles(GroupNode group)
  {
    for (int i=0; i < group.getChildCount(); i++)
    {
      TreeNode node = group.getChildAt(i);
      if (node instanceof GroupNode)
      {
        replaceWithOriginalProfiles((GroupNode)node);
      }
      else if (node instanceof ProfileNode)
      {
        ProfileNode pnode = (ProfileNode)node;
        ConnectionProfile original = findOriginalProfile(pnode.getProfile());
        if (original != null)
        {
          pnode.setProfile(original);
        }
      }
    }
  }
  public void deleteProfile(ProfileNode node)
  {
    boolean deleted = profiles.remove(node.getProfile());
    if (deleted)
    {
      this.removeNodeFromParent(node);
      profilesDeleted = true;
    }
  }

  public TreePath getFirstProfile()
  {
    if (this.rootNode.getChildCount() == 0) return null;
    ProfileNode pnode = rootNode.findFirstProfile();
    if (pnode  == null) return null;
    return new TreePath(pnode.getPath());
  }

  public TreePath getPath(ProfileKey def)
  {
    if (def == null) return null;
    ConnectionProfile prof = ProfileManager.findProfile(profiles, def);
    if (prof != null)
    {
      return getPath(prof);
    }
    return null;
  }

  public TreePath getPath(ConnectionProfile prof)
  {
    if (prof == null) return null;
    return rootNode.getProfilePath(prof);
  }

  public int getSize()
  {
    return this.profiles.size();
  }

  public boolean containsGroup(GroupNode parentGroup, String name)
  {
    if (parentGroup == null)
    {
      parentGroup = this.rootNode;
    }
    return parentGroup.containsGroup(name);
  }

  public TreePath addGroup(GroupNode parentGroup, String name)
  {
    if (name == null) return null;
    if (parentGroup == null)
    {
      parentGroup = this.rootNode;
    }
    GroupNode node = new GroupNode(name, false);
    this.insertNodeInto(node, parentGroup, parentGroup.getChildCount());
    return new TreePath(node.getPath());
  }

  public void addEmptyProfile()
  {
    ConnectionProfile dummy = ConnectionProfile.createEmptyProfile();
    dummy.setUrl("jdbc:");
    profiles.add(dummy);
    buildTree();
  }

  public void removeGroupNode(GroupNode groupNode)
  {
    deleteGroupProfiles(groupNode);
    removeNodeFromParent(groupNode);
  }

  public void saveTo(File file)
  {
    ProfileManager mgr = new ProfileManager(file);
    mgr.applyProfiles(getAllProfiles());
    mgr.save();
    sourceFile = file;
    resetChanged();
  }

  public void saveProfiles()
  {
    applyProfiles();
    ConnectionMgr.getInstance().saveProfiles();
    resetChanged();
  }

  public void resetChanged()
  {
    for (ConnectionProfile profile : profiles)
    {
      profile.resetChangedFlags();
    }
    profilesDeleted = false;
  }

  public List<ConnectionProfile> getAllProfiles()
  {
    List<ConnectionProfile> current = new ArrayList<>(profiles.size() + filtered.size());
    for (ConnectionProfile prof : profiles)
    {
      current.add(prof);
    }
    for (ConnectionProfile prof : filtered)
    {
      current.add(prof);
    }
    return current;
  }

  public void applyProfiles()
  {
    ConnectionMgr.getInstance().applyProfiles(getAllProfiles());
  }

  private void buildTree()
  {
    ProfileGroupMap groupMap = new ProfileGroupMap(profiles);
    rootNode.removeAllChildren();

    // Build all groups first to retain the sorting
    for (List<String> group : groupMap.keySet())
    {
      rootNode.getOrCreatePath(group);
    }

    for (Map.Entry<List<String>, List<ConnectionProfile>> group : groupMap.entrySet())
    {
      List<String> path = group.getKey();
      GroupNode groupNode = rootNode.getOrCreatePath(path);
      List<ConnectionProfile> groupProfiles = group.getValue();

      sortList(groupProfiles);
      for (ConnectionProfile prof : groupProfiles)
      {
        ProfileNode profNode = new ProfileNode(prof);
        groupNode.add(profNode);
      }
    }
    this.setRoot(rootNode);
  }

  public void removeNodesFromParent(DefaultMutableTreeNode[] profileNodes)
  {
    if (profileNodes == null) return;
    for (DefaultMutableTreeNode profileNode : profileNodes)
    {
      removeNodeFromParent(profileNode);
    }
  }

  private ConnectionProfile findOriginalProfile(ConnectionProfile copy)
  {
    if (copy == null) return null;
    for (ConnectionProfile profile : this.profiles)
    {
      if (copy.equals(profile)) return profile;
    }
    return null;
  }

  public void moveProfiles(List<ProfileNode> profiles, GroupNode targetGroup)
  {
    if (CollectionUtil.isEmpty(profiles) || targetGroup == null) return;

    for (ProfileNode profile : profiles)
    {
      if (profile == null) continue;
      insertNodeInto(profile, targetGroup, targetGroup.getChildCount());
    }
    targetGroup.updateProfileGroups();
  }

  public DefaultMutableTreeNode moveProfilesToGroup(List<ConnectionProfile> droppedProfiles, GroupNode targetGroup)
  {
    if (CollectionUtil.isEmpty(droppedProfiles)) return null;
    if (targetGroup == null) return null;

    List<String> groupPath = targetGroup.getGroupPath();

    DefaultMutableTreeNode firstNode = null;
    for (ConnectionProfile profile : droppedProfiles)
    {
      if (profile == null) continue;
      ConnectionProfile original = findOriginalProfile(profile);
      if (original == null)
      {
        // this can happen if this is a Drag & Drop between two trees
        profile.setGroups(groupPath);
      }
      else
      {
        original.setGroups(groupPath);
        profile = original;
      }

      // this method is called as part of a Drag & Drop or Copy & Paste action
      // We only need to take care of inserting the new node. The TransferHandler
      // will take care of removing the original node from it's parent in the model.
      // This is necessary to support transfer between two differen trees
      ProfileNode newNode = new ProfileNode(profile);
      if (firstNode == null)
      {
        firstNode = newNode;
      }
      insertNodeInto(newNode, targetGroup, targetGroup.getChildCount());
    }
    return firstNode;
  }

  /**
   * Renames the passed profile to make the name unique in the target group.
   *
   * The "copy index" is appended to the new name in parentheses. So if a profile "Foo" is passed
   * and the group already contains "Foo", this method will rename the profile to "Foo (1)".
   * If the group already contains "Foo", "Foo (1)" and "Foo (2)" this method will rename the profile to "Foo (3)".
   *
   * @param copy       the new profile
   * @param groupNode  the group into which the profile is copied
   */
  private void adjustCopiededProfileName(ConnectionProfile copy, GroupNode groupNode)
  {
    String newName = copy.getName();
    String plainName = newName.toLowerCase();
    boolean hasNumber = false;

    Pattern p = Pattern.compile("\\(([0-9+])\\)$");
    Matcher m = p.matcher(newName);
    if (m.find())
    {
      hasNumber = true;
      plainName = newName.substring(0, m.start()).trim().toLowerCase();
    }

    int copyIndex = 0;

    int count = groupNode.getChildCount();
    for (int i=0; i < count; i++)
    {
      TreeNode child = (TreeNode)groupNode.getChildAt(i);
      if (child instanceof ProfileNode)
      {
        ProfileNode pnode = (ProfileNode)child;
        String name = pnode.getProfile().getName();
        if (name.toLowerCase().startsWith(plainName))
        {
          copyIndex ++;
        }
      }
    }

    if (copyIndex > 0)
    {
      String suffix = "(" + copyIndex + ")";
      String renamed = hasNumber ? m.replaceFirst(suffix) : newName + " " + suffix;
      copy.setName(renamed);
    }
  }

  public DefaultMutableTreeNode copyProfilesToGroup(List<ConnectionProfile> droppedProfiles, GroupNode groupNode)
  {
    if (CollectionUtil.isEmpty(droppedProfiles)) return null;
    if (groupNode == null) return null;

    List<String> groupPath = groupNode.getGroupPath();

    DefaultMutableTreeNode firstNode = null;
    for (ConnectionProfile profile : droppedProfiles)
    {
      if (profile == null) continue;
      ConnectionProfile copy = profile.createCopy();
      copy.setNew();
      copy.setGroups(groupPath);
      profiles.add(copy);

      // this method is called as part of a Drag & Drop or Copy & Paste action
      // we only need to take care of inserting the new node. The TransferHandler
      // will take care of removing the original node from its parent in the model
      // this is necessary to support transfer between two differen trees
      ProfileNode newNode = new ProfileNode(copy);
      if (firstNode == null)
      {
        firstNode = newNode;
      }

      adjustCopiededProfileName(copy, groupNode);
      insertNodeInto(newNode, groupNode, groupNode.getChildCount());
    }
    return firstNode;
  }

  public static ProfileListModel emptyModel()
  {
    return new ProfileListModel(new ArrayList<>());
  }

}
