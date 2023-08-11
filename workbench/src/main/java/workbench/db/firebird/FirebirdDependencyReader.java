/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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
package workbench.db.firebird;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbObject;
import workbench.db.DomainIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.dependency.DependencyReader;

import workbench.gui.dbobjects.objecttree.DbObjectSorter;

import workbench.util.CollectionUtil;

import workbench.db.JdbcUtils;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class FirebirdDependencyReader
  implements DependencyReader
{
  private final Set<String> supportedTypes = CollectionUtil.caseInsensitiveSet("table", "view");

  private static final String TYPE_CASE =
    "when 0 then 'TABLE' when 1 then 'VIEW' end as type_name";

  private final String searchUsed =
    "select distinct rdb$depended_on_name, case rdb$depended_on_type " + TYPE_CASE + " \n" +
    "from rdb$dependencies \n" +
    "where rdb$dependent_name = ?";

  private final String searchUsedBy =
    "select distinct rdb$dependent_name, case rdb$dependent_type " + TYPE_CASE + " \n" +
    "from rdb$dependencies\n" +
    "where rdb$depended_on_name = ?";

  private final String usedDomains =
    "select distinct rdb$field_source, 'DOMAIN'\n" +
    "from rdb$relation_fields\n" +
    "where rdb$relation_name = ?";

  private final String domainUsage =
    "select distinct rdb$relation_name, 'TABLE'\n" +
    "from rdb$relation_fields\n" +
    "where rdb$field_source = ?";

  public FirebirdDependencyReader()
  {
  }

  @Override
  public List<DbObject> getUsedObjects(WbConnection connection, DbObject base)
  {
    if (base == null || connection == null) return Collections.emptyList();

    List<DbObject> objects = retrieveObjects(connection, base, searchUsed);
    objects.addAll(retrieveObjects(connection, base, usedDomains));

    DbObjectSorter.sort(objects, true);

    return objects;
  }

  @Override
  public List<DbObject> getUsedBy(WbConnection connection, DbObject base)
  {
    if (base == null || connection == null) return Collections.emptyList();
    List<DbObject> objects = retrieveObjects(connection, base, searchUsedBy);
    objects.addAll(retrieveObjects(connection, base, domainUsage));
    DbObjectSorter.sort(objects, true);

    return objects;
  }

  private List<DbObject> retrieveObjects(WbConnection connection, DbObject base, String sql)
  {
    PreparedStatement pstmt = null;
    ResultSet rs = null;

    List<DbObject> result = new ArrayList<>();

    String s = SqlUtil.replaceParameters(sql, base.getObjectName());
    LogMgr.logMetadataSql(new CallerInfo(){}, "dependent objects", s);

    Savepoint sp = null;
    try
    {
      connection.setSavepoint();
      pstmt = connection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, base.getObjectName());

      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String name = StringUtil.trim(rs.getString(1));
        String type = rs.getString(2);
        if ("DOMAIN".equals(type))
        {
          DomainIdentifier domain = new DomainIdentifier(null, null, name);
          result.add(domain);
        }
        else
        {
          TableIdentifier tbl = new TableIdentifier(name);
          tbl.setNeverAdjustCase(true);
          tbl.setType(type);
          result.add(tbl);
        }
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "dependent objects", s, ex);
    }
    finally
    {
      connection.rollback(sp);
      JdbcUtils.closeAll(rs, pstmt);
    }

    DbObjectSorter.sort(result, true, true);
    return result;
  }

  @Override
  public boolean supportsUsedByDependency(String objectType)
  {
    return supportedTypes.contains(objectType) || "domain".equalsIgnoreCase(objectType);
  }

  @Override
  public boolean supportsIsUsingDependency(String objectType)
  {
    return supportedTypes.contains(objectType);
  }

}
