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

import javax.swing.tree.DefaultMutableTreeNode;

import workbench.db.ConnectionProfile;
/**
 *
 * @author Thomas Kellerer
 */
public class ProfileNode
  extends DefaultMutableTreeNode
{

  public ProfileNode(ConnectionProfile profile)
  {
    super(profile, false);
  }

  public void setProfile(ConnectionProfile profile)
  {
    setUserObject(profile);
  }

  public ConnectionProfile getProfile()
  {
    return (ConnectionProfile)getUserObject();
  }

  @Override
  public String toString()
  {
    return getProfile().getName();
  }

}
