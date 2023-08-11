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
package workbench.db.postgres;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import workbench.db.DbObject;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PgSubscription
  implements DbObject, Serializable
{
  public static final String TYPE_NAME = "SUBSCRIPTION";
  private String name;
  private String comment;
  private String connInfo;
  private boolean enabled;
  private boolean syncCommit;
  private String slotName;
  private final List<String> publications = new ArrayList<>();

  public PgSubscription(String name)
  {
    this.name = name;
  }

  @Override
  public String getCatalog()
  {
    return null;
  }

  @Override
  public String getSchema()
  {
    return null;
  }

  @Override
  public String getObjectType()
  {
    return TYPE_NAME;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return name;
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return name;
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return name;
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    return getSource();
  }

  public String getSource()
  {
    String source = "CREATE SUBSCRIPTION " + SqlUtil.quoteObjectname(name) +
      "\n  CONNECTION '" + connInfo + "'" +
      "\n  PUBLICATION " + StringUtil.listToString(publications, ',', false);

    source += "\n  WITH (";
    if (StringUtil.isBlank(slotName))
    {
      source += "slot_name = none";
    }
    else
    {
      source += "slot_name = '" + slotName + "'";
    }
    if (!enabled)
    {
      source += ", enabled = false";
    }
    if (syncCommit)
    {
      source += ", synchronous_commit = on";
    }
    source += ");";
    return source;
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return name;
  }

  @Override
  public String getComment()
  {
    return comment;
  }

  @Override
  public void setComment(String cmt)
  {
    this.comment = cmt;
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return "DROP SUBSCRIPTION IF EXISTS " + SqlUtil.quoteObjectname(name);
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

  @Override
  public String getObjectName()
  {
    return name;
  }

  @Override
  public void setName(String name)
  {
    this.name = name;
  }

  public void setConnectionInfo(String info)
  {
    this.connInfo = info;
  }

  public void setSyncCommitEnabled(boolean flag)
  {
    this.syncCommit = flag;
  }

  public void setSlotName(String name)
  {
    this.slotName = name;
  }

  public void setEnabled(boolean enabled)
  {
    this.enabled = enabled;
  }

  public void setPublications(List<String> pubNames)
  {
    this.publications.clear();
    if (pubNames != null)
    {
      this.publications.addAll(pubNames);
    }
  }

}
