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
package workbench.db.ibm;

import workbench.db.AbstractConstraintReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class InformixConstraintReader
  extends AbstractConstraintReader
{
  private WbConnection dbConn;

  public InformixConstraintReader(WbConnection conn)
  {
    super(conn.getDbId());
    dbConn = conn;
  }

  @Override
  public String getColumnConstraintSql(TableIdentifier tbl)
  {
    return null;
  }

  @Override
  public String getTableConstraintSql(TableIdentifier tbl)
  {
    if (tbl == null) return null;

    String catalog = tbl.getCatalog();
    InformixSystemTables systemTables = new InformixSystemTables(catalog, dbConn);

    String sysTables = systemTables.getSysTables();
    String sysChecks = systemTables.getSysChecks();
    String sysConstraints = systemTables.getSysConstraints();

    String sql =
      "select c.constrname, ch.checktext\n" +
      "from " + sysConstraints + " c \n" +
      "  join " + sysTables + " t on t.tabid = c.tabid\n" +
        "  join " + sysChecks + " ch on ch.constrid = c.constrid\n" +
      "where c.constrtype = 'C'\n" +
      "  and ch.type = 'T' \n" +
      "  and t.tabname = ? ";

    return sql;
  }

}
