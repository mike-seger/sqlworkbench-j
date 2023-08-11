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
package workbench.db.h2database;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.db.AbstractConstraintReader;
import workbench.db.DBID;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 * Constraint reader for <a href="https://www.h2database.com">H2 Database</a>
 *
 * @author  Thomas Kellerer
 */
public class H2ConstraintReader
  extends AbstractConstraintReader
{

  private final String COLUMN_SQL_1x =
    "select column_name, " +
    "       case when \n" +
    "           check_constraint is not null and trim(check_constraint) <> '' then 'CHECK '||check_constraint \n" +
    "           else null \n" +
    "       end as check_constraint \n" +
    "from information_schema.columns \n" +
    "where table_name = ? \n" +
    "  and table_schema = ?";

  private final String TABLE_SQL_1x =
    "select constraint_name, \n" +
    "       check_expression \n" +
    "from information_schema.constraints \n" +
    "where constraint_type = 'CHECK'  \n" +
    "and table_name = ? \n" +
    " and table_schema = ?";

  private final String TABLE_SQL_2x =
    "select tc.constraint_name, \n" +
    "       cc.check_clause\n" +
    "from information_schema.table_constraints tc\n" +
    "  join information_schema.check_constraints cc \n" +
    "    on cc.constraint_schema = tc.constraint_schema\n" +
    "   and cc.constraint_name = tc.constraint_name\n" +
    "where tc.constraint_type = 'CHECK'" +
    "and table_name = ? \n" +
    " and table_schema = ?";

  private Pattern systemNamePattern = Pattern.compile("^(CONSTRAINT_[0-9A-F][0-9A-F])");
  private boolean is20 = false;
  public H2ConstraintReader(WbConnection conn)
  {
    super(DBID.H2.getId());
    this.is20 = JdbcUtils.hasMinimumServerVersion(conn, "2.0");
  }

  @Override
  public int getIndexForSchemaParameter()
  {
    return 2;
  }

  @Override
  public int getIndexForTableNameParameter()
  {
    return 1;
  }

  @Override
  public String getColumnConstraintSql(TableIdentifier tbl)
  {
    return this.is20 ? null : this.COLUMN_SQL_1x;
  }

  @Override
  public String getTableConstraintSql(TableIdentifier tbl)
  {
    return this.is20 ? this.TABLE_SQL_2x : this.TABLE_SQL_1x;
  }

  @Override
  public boolean isSystemConstraintName(String name)
  {
    Matcher m = systemNamePattern.matcher(name);
    return m.matches();
  }


}
