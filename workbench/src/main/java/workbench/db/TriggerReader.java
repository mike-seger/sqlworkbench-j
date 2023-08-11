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
package workbench.db;

import java.sql.SQLException;
import java.util.List;

import workbench.storage.DataStore;

/**
 *
 * @author Thomas Kellerer
 */
public interface TriggerReader {

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the comment of the trigger.
   */
  String TRIGGER_COMMENT_COLUMN = "REMARKS";

  /**
   * The column index in the DataStore returned by getTableTriggers which identifies
   * the event (before, after) of the trigger.
   */
  String TRIGGER_EVENT_COLUMN = "EVENT";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the name of the trigger.
   */
  String TRIGGER_NAME_COLUMN = "TRIGGER";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the schema in which the trigger is stored.
   *
   * This might not apply for all databases.
   */
  String TRIGGER_SCHEMA_COLUMN = "SCHEMA";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the catalog in which the trigger is stored.
   *
   * This might not apply for all databases.
   */
  String TRIGGER_CATALOG_COLUMN = "CATALOG";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the table of the trigger.
   *
   * This might contain a fully qualified table name (schema.table_name).
   */
  String TRIGGER_TABLE_COLUMN = "TABLE";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the schema of the trigger table.
   *
   * If this column contains a value, the table name column {@link TRIGGER_TABLE_COLUMN}
   * should not contain a full qualified name.
   */
  String TRIGGER_TABLE_SCHEMA_COLUMN = "TABLE_SCHEMA";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the catalog of the trigger table.
   *
   * If this column contains a value, the table name column {@link TRIGGER_TABLE_COLUMN}
   * should not contain a full qualified name.
   */
  String TRIGGER_TABLE_CATALOG_COLUMN = "TABLE_CATALOG";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the type of the object for which the trigger is defined.
   *
   * This is needed for DBMS that support triggers on other object types like views
   */
  String TRIGGER_TABLE_TYPE_COLUMN = "TABLE_TYPE";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the type (INSERT, UPDATE etc) of the trigger.
   */
  String TRIGGER_TYPE_COLUMN = "TYPE";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the status of the trigger.
   */
  String TRIGGER_STATUS_COLUMN = "STATUS";

  /**
   * The column name in the DataStore returned by getTableTriggers which identifies
   * the level (row/statement) of the trigger.
   */
  String TRIGGER_LEVEL_COLUMN = "LEVEL";

  String TYPE_NAME = "TRIGGER";

  TriggerDefinition findTrigger(String catalog, String schema, String name)
    throws SQLException;

  /**
   * Return the list of defined triggers for the given table.
   */
  DataStore getTableTriggers(TableIdentifier table)
    throws SQLException;

  List<TriggerDefinition> getTriggerList(String catalog, String schema, String baseTable)
    throws SQLException;

  String getTriggerSource(TriggerDefinition trigger, boolean includeDependencies)
    throws SQLException;

  /**
   * Retrieve the SQL Source of the given trigger.
   *
   * @param aCatalog      The catalog in which the trigger is defined. This should be null if the DBMS does not support catalogs
   * @param aSchema       The schema in which the trigger is defined. This should be null if the DBMS does not support schemas
   * @param aTriggername  the name of the trigger
   * @param triggerTable  the table for which the trigger is defined
   * @param trgComment    the comment for the trigger
   * @param includeDependencies  if true dependent objects should be included in the source (e.g. the trigger function in Postgres)
   * @throws SQLException
   * @return the trigger source
   */
  String getTriggerSource(String aCatalog, String aSchema, String aTriggername, TableIdentifier triggerTable, String trgComment, boolean includeDependencies)
    throws SQLException;

  /**
   * Retriev any additional source that is needed for the specified trigger.
   *
   * @param triggerCatalog The catalog in which the trigger is defined. This should be null if the DBMS does not support catalogs
   * @param triggerSchema The schema in which the trigger is defined. This should be null if the DBMS does not support schemas
   * @param triggerName the name of the trigger
   * @param triggerTable the table for which the trigger is defined
   * @return source of additional DB objects.
   */
  CharSequence getDependentSource(String triggerCatalog, String triggerSchema, String triggerName, TableIdentifier triggerTable)
    throws SQLException;

  /**
   * Return a list of triggers available in the given schema.
   */
  TriggerListDataStore getTriggers(String catalog, String schema)
    throws SQLException;

  /**
   * Checks if the DBMS supports triggers on views.
   */
  boolean supportsTriggersOnViews();
}
