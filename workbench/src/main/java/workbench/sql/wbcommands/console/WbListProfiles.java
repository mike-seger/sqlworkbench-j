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
package workbench.sql.wbcommands.console;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import workbench.resource.ResourceMgr;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.ProfileGroupMap;

import workbench.gui.profiles.ProfileKey;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * List all defined profiles
 *
 * @author Thomas Kellerer
 */
public class WbListProfiles
  extends SqlCommand
{
  public static final String VERB = "WbListProfiles";
  public static final String ARG_GROUP = "group";
  public static final String ARG_GROUPS_ONLY = "groupsOnly";

  public WbListProfiles()
  {
    super();
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(ARG_GROUP);
    cmdLine.addArgument(ARG_GROUPS_ONLY, ArgumentType.BoolSwitch);
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  protected boolean isConnectionRequired()
  {
    return false;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = new StatementRunnerResult();

    cmdLine.parse(getCommandLine(sql));
    String groupToShow = null;
    if (cmdLine.isArgPresent(ARG_GROUP))
    {
      groupToShow = cmdLine.getValue(ARG_GROUP);
    }

    boolean groupsOnly = cmdLine.getBoolean(ARG_GROUPS_ONLY);

    ProfileGroupMap map = new ProfileGroupMap(ConnectionMgr.getInstance().getProfiles());

    String userTxt = ResourceMgr.getString("TxtUsername");

    for (Map.Entry<List<String>, List<ConnectionProfile>> group : map.entrySet())
    {
      List<String> path = group.getKey();

      if (groupToShow == null || containsGroupName(path, groupToShow))
      {
        result.addMessage(ProfileKey.getGroupPathAsString(path));
        if (groupsOnly) continue;

        List<ConnectionProfile> profiles = group.getValue();
        profiles.sort(ConnectionProfile.getNameComparator());
        for (ConnectionProfile profile : profiles)
        {
          String msg = "  " + profile.getName();
          if (StringUtil.isNotBlank(profile.getUsername()))
          {
            msg += ", " + userTxt + "=" + profile.getUsername();
          }
          msg += ", URL=" + profile.getUrl();
          result.addMessage(msg);
        }
      }
    }
    result.setSuccess();
    return result;
  }

  private boolean containsGroupName(List<String> path, String name)
  {
    if (CollectionUtil.isEmpty(path)) return false;
    Optional<String> found = path.stream().filter(s -> s.equalsIgnoreCase(name)).findAny();
    return found.isPresent();
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
