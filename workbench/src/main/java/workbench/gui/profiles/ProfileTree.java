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

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.swing.ActionMap;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DropMode;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import workbench.interfaces.ClipboardSupport;
import workbench.interfaces.ExpandableTree;
import workbench.interfaces.FileActions;
import workbench.interfaces.GroupTree;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ConnectionProfile;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.DeleteListEntryAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.ValidatingDialog;
import workbench.gui.menu.CutCopyPastePopup;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * A tree to display connection profiles and profile groups.
 *
 * It supports drag & drop from profiles into different groups.
 *
 * @author Thomas Kellerer
 */
public class ProfileTree
  extends JTree
  implements TreeModelListener, MouseListener, ClipboardSupport, ActionListener, TreeSelectionListener,
             GroupTree, ExpandableTree
{
  private ProfileListModel profileModel;
  private final CutCopyPastePopup popup;
  private final WbAction pasteToFolderAction;
  private final WbAction renameGroup;
  private final Insets autoscrollInsets = new Insets(20, 20, 20, 20);
  private final ProfileTreeTransferHandler transferHandler = new ProfileTreeTransferHandler(this);
  private final NewGroupAction newGroupAction;
  private final DeleteListEntryAction deleteAction;

  public ProfileTree()
  {
    super(ProfileListModel.emptyModel());
    setRootVisible(false);
    putClientProperty("JTree.lineStyle", "Angled");
    setShowsRootHandles(true);
    setCellRenderer(new ProfileTreeCellRenderer());
    setEditable(true);
    setExpandsSelectedPaths(true);
    addMouseListener(this);
    getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    addTreeSelectionListener(this);

    InputMap im = this.getInputMap(WHEN_FOCUSED);
    ActionMap am = this.getActionMap();

    popup = new CutCopyPastePopup(this);

    newGroupAction = new NewGroupAction(this, "LblNewProfileGroup");
    deleteAction = new DeleteListEntryAction(new FileActions()
    {
      @Override
      public void saveItem()
        throws Exception
      {
      }

      @Override
      public void deleteItem()
        throws Exception
      {
        deleteSelectedItem();
      }

      @Override
      public void newItem(boolean copyCurrent)
      {
      }
    });

    WbAction a = popup.getPasteAction();
    a.addToInputMap(im, am);

    a = popup.getCopyAction();
    a.addToInputMap(im, am);

    a = popup.getCutAction();
    a.addToInputMap(im, am);

    pasteToFolderAction = new WbAction(this, "pasteToFolder");
    pasteToFolderAction.removeIcon();
    pasteToFolderAction.initMenuDefinition("MnuTxtPasteNewFolder");

    popup.addAction(newGroupAction, true);
    popup.addAction(pasteToFolderAction, false);
    renameGroup = new RenameGroupAction(this);
    popup.addAction(renameGroup, false);

    popup.addAction(deleteAction, true);
    deleteAction.addToInputMap(im, am);

    setAutoscrolls(true);
    setDragEnabled(true);
    setDropMode(DropMode.ON);
    setTransferHandler(transferHandler);

    // setting the row height to 0 makes it dynamic
    // so it will adjust properly to the font of the renderer
    setRowHeight(0);
    setBorder(WbSwingUtilities.EMPTY_BORDER);
  }

  public NewGroupAction getNewGroupAction()
  {
    return newGroupAction;
  }

  public DeleteListEntryAction getDeleteAction()
  {
    return deleteAction;
  }

  public void deleteSelectedItem()
    throws Exception
  {
    TreePath[] path = getSelectionPaths();
    if (path == null) return;
    if (path.length == 0) return;

    int indexInLastParent = -1;
    TreeNode lastParent = null;

    for (TreePath element : path)
    {
      TreeNode node = (TreeNode) element.getLastPathComponent();

      lastParent = node.getParent();
      indexInLastParent = lastParent.getIndex(node);

      if (node instanceof ProfileNode)
      {
        getModel().deleteProfile((ProfileNode)node);
      }
      else if (node instanceof GroupNode)
      {
        if (checkGroupWithProfiles((GroupNode)node))
        {
          getModel().removeGroupNode((GroupNode)node);
        }
      }
    }

    if (lastParent != null && lastParent.getChildCount() > 0)
    {
      int newIndex = indexInLastParent > 1 ? indexInLastParent - 1 : 0;
      DefaultMutableTreeNode toSelect = (DefaultMutableTreeNode)lastParent.getChildAt(newIndex);
      TreePath newPath = new TreePath(toSelect.getPath());
      selectPath(newPath);
    }
  }

  private boolean checkGroupWithProfiles(GroupNode groupNode)
  {
    if (groupNode.getChildCount() == 0) return true;

    List<String> groups = getModel().getGroups();
    JPanel p = new JPanel();

    DefaultComboBoxModel m = new DefaultComboBoxModel(groups.toArray());
    JComboBox groupBox = new JComboBox(m);
    groupBox.setSelectedIndex(0);
    p.setLayout(new BorderLayout(0, 5));
    String groupName = (String)groupNode.getUserObject();
    String lbl = ResourceMgr.getFormattedString("LblDeleteNonEmptyGroup", groupName);
    p.add(new JLabel(lbl), BorderLayout.NORTH);
    p.add(groupBox, BorderLayout.SOUTH);
    String[] options = new String[]{ResourceMgr.getString("LblMoveProfiles"), ResourceMgr.getString("LblDeleteProfiles")};

    Dialog parent = (Dialog)SwingUtilities.getWindowAncestor(this);

    ValidatingDialog dialog = new ValidatingDialog(parent, ResourceMgr.TXT_PRODUCT_NAME, p, options);
    WbSwingUtilities.center(dialog, parent);
    dialog.setVisible(true);
    if (dialog.isCancelled())
    {
      return false;
    }

    int result = dialog.getSelectedOption();
    if (result == 0)
    {
      // move profiles
      String group = (String)groupBox.getSelectedItem();
      if (group == null)
      {
        return false;
      }
      GroupNode targetGroup = getModel().findGroupNode(ProfileKey.parseGroupPath(group));
      getModel().moveProfiles(groupNode.getProfileNodes(), targetGroup);
      return true;
    }
    else if (result == 1)
    {
      return true;
    }

    return false;
  }

  @Override
  public void setModel(TreeModel model)
  {
    super.setModel(model);
    if (model instanceof ProfileListModel)
    {
      this.profileModel = (ProfileListModel)model;
      model.addTreeModelListener(this);
    }
  }

  @Override
  public ProfileListModel getModel()
  {
    if (profileModel == null)
    {
      return (ProfileListModel)super.getModel();
    }
    return profileModel;
  }

  @Override
  public boolean isPathEditable(TreePath path)
  {
    if (path == null) return false;
    // Only allow editing of groups
    if (path.getPathCount() != 2) return false;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();

    return node.getAllowsChildren();
  }

  @Override
  public void treeNodesChanged(TreeModelEvent e)
  {
    Object[] changed = e.getChildren();
    DefaultMutableTreeNode group = (DefaultMutableTreeNode)changed[0];
    Object data = group.getUserObject();

    if (group instanceof GroupNode)
    {
      String newGroupName = (String)data;
      ((GroupNode)group).setName(newGroupName);
    }
    else if (data instanceof ConnectionProfile)
    {
      // If the connection profile has changed, the title
      // of the profile possibly changed as well, so we need to
      // trigger a repaint to display the correct title
      // in the tree
      WbSwingUtilities.repaintLater(this);
    }
  }

  @Override
  public void expandAll()
  {
    TreePath[] groups = this.profileModel.getGroupNodes();
    for (TreePath group : groups)
    {
      if (group != null)
      {
        expandPath(group);
      }
    }
  }

  @Override
  public void collapseAll()
  {
    TreePath[] groups = this.profileModel.getGroupNodes();
    for (TreePath group : groups)
    {
      if (group != null)
      {
        collapsePath(group);
      }
    }
  }

  /**
   * Expand the groups that are contained in the list.
   *
   * Each element of the list is expected to be a path string to the group.
   *
   * @see GroupNode#getGroupPathAsString()
   */
  public void expandGroups(List<String> groupList)
  {
    if (groupList == null) return;
    for (String path : groupList)
    {
      TreePath groupPath = toTreePath(path);
      if (groupPath != null)
      {
        expandPath(groupPath);
      }
    }
  }

  /**
   * Return the names of the expaned groups.
   */
  public List<String> getExpandedGroupNames()
  {
    Set<String> result = CollectionUtil.caseInsensitiveSet();
    TreePath[] groupNodes = this.profileModel.getGroupNodes();
    for (TreePath groupNode : groupNodes)
    {
      if (isExpanded(groupNode))
      {
        result.add(toPathString(groupNode));
      }
    }
    return new ArrayList<>(result);
  }

  private TreePath toTreePath(String path)
  {
    List<String> groupPath = ProfileKey.parseGroupPath(path);
    GroupNode groupNode = profileModel.findGroupNode(groupPath);
    if (groupNode == null) return null;
    return new TreePath(groupNode.getPath());
  }

  private String toPathString(TreePath groupNode)
  {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) groupNode.getLastPathComponent();
    if (node instanceof GroupNode)
    {
      return ((GroupNode)node).getGroupPathAsString();
    }
    return node.toString();
  }

  @Override
  public void treeNodesInserted(TreeModelEvent e)
  {
  }

  @Override
  public void treeNodesRemoved(TreeModelEvent e)
  {
  }

  @Override
  public void treeStructureChanged(TreeModelEvent e)
  {
  }

  public boolean isGroup(TreePath p)
  {
    if (p == null) return false;
    TreeNode n = (TreeNode)p.getLastPathComponent();
    return n.getAllowsChildren();
  }

  private boolean canPaste()
  {
    // On some Linux distributions isDataFlavorAvailable() throws an exception
    // ignoring that exception is a workaround for that.
    try
    {
      return getToolkit().getSystemClipboard().isDataFlavorAvailable(ProfileFlavor.FLAVOR);
    }
    catch (Throwable th)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Could not check clipboard", th);
      return false;
    }
  }

  /**
   * Enable/disable the cut/copy/paste actions
   * according to the current selection and the content
   * of the "clipboard"
   */
  private void checkActions()
  {
    boolean groupSelected = onlyGroupSelected();
    boolean canPaste = canPaste();
    boolean canCopy = onlyProfilesSelected();
    boolean profileSelected = getSelectedProfile() != null;

    pasteToFolderAction.setEnabled(canPaste);

    WbAction a = popup.getPasteAction();
    a.setEnabled(canPaste);

    a = popup.getCopyAction();
    a.setEnabled(canCopy);

    a = popup.getCutAction();
    a.setEnabled(canCopy);

    renameGroup.setEnabled(groupSelected);
    newGroupAction.setEnabled(!profileSelected);
  }

  @Override
  public void mouseClicked(MouseEvent e)
  {
    if (e.getButton() == MouseEvent.BUTTON3 && e.getClickCount() == 1)
    {
      TreePath p = this.getPathForLocation(e.getX(), e.getY());
      checkActions();
      if (p == null)
      {
        newGroupAction.setEnabled(true);
      }
      else if (p.getLastPathComponent() instanceof TreeNode)
      {
        TreeNode node = (TreeNode)p.getLastPathComponent();
        newGroupAction.setEnabled(node.getAllowsChildren());
      }
      popup.show(this, e.getX(), e.getY());
    }
  }

  /**
   * Finds and selects the connection profile with the given name.
   *
   * If the profile is not found, the first profile
   * will be selected and its group expanded
   */
  public void selectProfile(ProfileKey key)
  {
    selectProfile(key, true);
  }

  public void selectProfile(ProfileKey key, boolean selectFirst)
  {
    if (profileModel == null) return;
    TreePath path = this.profileModel.getPath(key);
    if (path == null && selectFirst)
    {
      path = this.profileModel.getFirstProfile();
    }
    selectPath(path); // selectPath can handle a null value
  }

  public void selectFirstProfile()
  {
    if (profileModel == null) return;
    selectPath(profileModel.getFirstProfile());
  }

  /**
   * Checks if the current selection contains only profiles
   */
  public boolean onlyProfilesSelected()
  {
    TreePath[] selection = getSelectionPaths();
    if (selection == null) return false;

    for (TreePath element : selection)
    {
      TreeNode n = (TreeNode)element.getLastPathComponent();
      if (n.getAllowsChildren()) return false;
    }
    return true;
  }

  /**
   * Checks if the current selection contains only groups
   */
  public boolean onlyGroupSelected()
  {
    if (getSelectionCount() > 1) return false;
    TreePath[] selection = getSelectionPaths();
    if (selection == null) return false;
    for (TreePath element : selection)
    {
      TreeNode n = (TreeNode) element.getLastPathComponent();
      if (!n.getAllowsChildren()) return false;
    }
    return true;
  }

  protected GroupNode getSelectedGroupNode()
  {
    TreePath[] selection = getSelectionPaths();
    if (selection == null) return null;
    if (selection.length != 1) return null;

    TreeNode node = (TreeNode)getLastSelectedPathComponent();
    if (node instanceof GroupNode)
    {
      return (GroupNode)node;
    }
    return null;
  }

  /**
   * Checks if the current selection contains only profiles
   */
  public List<ConnectionProfile> getSelectedProfiles()
  {
    TreePath[] selection = getSelectionPaths();
    if (selection == null) return Collections.emptyList();

    List<ConnectionProfile> result = new ArrayList<>(selection.length);

    for (TreePath element : selection)
    {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)element.getLastPathComponent();
      if (!node.getAllowsChildren())
      {
        result.add((ConnectionProfile)node.getUserObject());
      }
    }
    return result;
  }

  /**
   * Returns the currently selected Profile. If either more then one
   * entry is selected or a group is selected, null is returned
   *
   * @return the selected profile if any
   */
  public ConnectionProfile getSelectedProfile()
  {
    TreePath[] selection = getSelectionPaths();
    if (selection == null) return null;
    if (selection.length != 1) return null;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)getLastSelectedPathComponent();
    if (node == null) return null;

    Object o = node.getUserObject();
    if (o instanceof ConnectionProfile)
    {
      ConnectionProfile prof = (ConnectionProfile)o;
      return prof;
    }
    return null;
  }

  @Override
  public void mousePressed(MouseEvent e)
  {
  }

  @Override
  public void mouseReleased(MouseEvent e)
  {
  }

  @Override
  public void mouseEntered(MouseEvent e)
  {
  }

  @Override
  public void mouseExited(MouseEvent e)
  {
  }

  @Override
  public void copy()
  {
    try
    {
      transferHandler.exportToClipboard(this, getToolkit().getSystemClipboard(), DnDConstants.ACTION_COPY);
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not put Profile to clipboard", th);
    }
  }

  @Override
  public void selectAll()
  {
  }

  @Override
  public void clear()
  {
  }

  @Override
  public void cut()
  {
    try
    {
      transferHandler.exportToClipboard(this, getToolkit().getSystemClipboard(), DnDConstants.ACTION_MOVE);
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not put Profile to clipboard", th);
    }
  }

  @Override
  public void paste()
  {
    try
    {
      Clipboard clipboard = getToolkit().getSystemClipboard();
      Transferable contents = clipboard.getContents(this);
      transferHandler.importData(new TransferHandler.TransferSupport(this, contents));
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not access clipboard", ex);
    }
  }

  public void handleDroppedNodes(List<DefaultMutableTreeNode> nodes, GroupNode newParent, int action)
  {
    if (CollectionUtil.isEmpty(nodes)) return;
    if (newParent == null) return;

    DefaultMutableTreeNode firstNode = null;
    for (DefaultMutableTreeNode node : nodes)
    {
      DefaultMutableTreeNode newNode = null;
      if (node instanceof ProfileNode)
      {
        ProfileNode pnode = (ProfileNode)node;
        if (action == DnDConstants.ACTION_MOVE)
        {
          newNode = profileModel.moveProfilesToGroup(List.of(pnode.getProfile()), newParent);
        }
        else if (action == DnDConstants.ACTION_COPY)
        {
          newNode = profileModel.copyProfilesToGroup(List.of(pnode.getProfile()), newParent);
        }
      }
      else if (node instanceof GroupNode)
      {
        GroupNode gnode = (GroupNode)node;
        newNode = profileModel.moveOrCopyGroups(List.of(gnode), newParent, action);
      }

      if (firstNode == null)
      {
        firstNode = newNode;
      }
    }
    selectNode(firstNode);
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    // invoked from the "paste into new folder" action
    String group = addGroup(true);
    if (group != null)
    {
      paste();
    }
  }

  /**
   * Prompts the user for a new group name and renames the currently selected group
   * to the supplied name.
   */
  public void renameGroup()
  {
    GroupNode group = this.getSelectedGroupNode();
    if (group == null) return;
    String oldName = (String)group.getUserObject();
    String newName = WbSwingUtilities.getUserInput(SwingUtilities.getWindowAncestor(this), ResourceMgr.getString("LblRenameProfileGroup"), oldName);
    if (StringUtil.isEmpty(newName)) return;
    group.setUserObject(newName);
    group.setName(newName);
  }

  /**
   * Prompts the user for a group name and creates a new group
   * with the provided name.
   * <p>
   * The new group node is automatically selected after creation.
   * </p>
   * @return the name of the new group or null if the user cancelled the name input
   */
  @Override
  public String addGroup(boolean isCtrlPressed)
  {
    String group = WbSwingUtilities.getUserInput(SwingUtilities.getWindowAncestor(this), ResourceMgr.getString("LblNewProfileGroup"), "");
    // user cancelled input
    if (StringUtil.isEmpty(group)) return null;

    group = group.trim();
    if (group.contains("/"))
    {
      WbSwingUtilities.showErrorMessage(SwingUtilities.getWindowAncestor(this), "The character / is not allowed in a group name");
      return null;
    }

    GroupNode currentGroup = isCtrlPressed ? null : getSelectedGroupNode();
    if (profileModel.containsGroup(currentGroup, group))
    {
      WbSwingUtilities.showErrorMessageKey(SwingUtilities.getWindowAncestor(this), "ErrGroupNotUnique");
      return null;
    }
    TreePath path = this.profileModel.addGroup(currentGroup, group);
    selectPath(path);
    return group;
  }

  public void selectPath(TreePath path)
  {
    if (path == null) return;
    expandPath(path);
    setSelectionPath(path);
    scrollPathToVisible(path);
  }

  private void selectNode(DefaultMutableTreeNode node)
  {
    if (node == null) return;
    TreeNode[] nodes = this.profileModel.getPathToRoot(node);
    TreePath path = new TreePath(nodes);
    this.selectPath(path);
  }

  @Override
  public void valueChanged(TreeSelectionEvent e)
  {
    checkActions();
  }

  public void autoscroll(Point cursorLocation)
  {
    Rectangle outer = getVisibleRect();
    Rectangle inner = new Rectangle(
            outer.x + autoscrollInsets.left,
            outer.y + autoscrollInsets.top,
            outer.width - (autoscrollInsets.left + autoscrollInsets.right),
            outer.height - (autoscrollInsets.top+autoscrollInsets.bottom)
          );

    if (!inner.contains(cursorLocation))
    {
      Rectangle scrollRect = new Rectangle(
              cursorLocation.x - autoscrollInsets.left,
              cursorLocation.y - autoscrollInsets.top,
              autoscrollInsets.left + autoscrollInsets.right,
              autoscrollInsets.top + autoscrollInsets.bottom
            );
      scrollRectToVisible(scrollRect);
    }
  }

}
