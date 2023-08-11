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

import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class InformixSystemTables
{
  private static final String DEFAULT_SYS_SCHEMA = "informix";

  private String systemSchema = DEFAULT_SYS_SCHEMA;
  private WbConnection dbConnection;
  private String catalog;

  public InformixSystemTables(String catalog, WbConnection conn)
  {
    this.systemSchema = conn.getDbSettings().getProperty("systemschema", DEFAULT_SYS_SCHEMA);
    this.dbConnection = conn;
    this.catalog = catalog;
  }

  public String getSysTables()
  {
    return getSystemTable(catalog, "systables");
  }

  public String getSysColumns()
  {
    return getSystemTable(catalog, "syscolumns");
  }

  public String getSysConstraints()
  {
    return getSystemTable(catalog, "sysconstraints");
  }

  public String getSysChecks()
  {
    return getSystemTable(catalog, "syschecks");
  }

  public String getSysSynonyms()
  {
    return getSystemTable(catalog, "syssyntable");
  }

  public String getSysSequences()
  {
    return getSystemTable(catalog, "syssequences");
  }

  public String getSysProceduresTable()
  {
    return getSystemTable(catalog, "sysprocedures");
  }

  public String getSysProcColumnsTable()
  {
    return getSystemTable(catalog, "sysproccolumns");
  }

  public String getSystemTable(String catalog, String tableName)
  {
    TableIdentifier tbl = new TableIdentifier(catalog, systemSchema, tableName);
    return tbl.getFullyQualifiedName(dbConnection);

  }
}
