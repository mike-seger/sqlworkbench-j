/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
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
package workbench.db;

/**
 *
 * @author Thomas Kellerer
 */
public enum GeneratedColumnType
{
  none,

  /**
   * Identity column as defined in the SQL standard or used by SQL Server
   */
  identity,

  /**
   * Auto increment column.
   *
   * e.g. PostgreSQL's serial or MySQL's auto_increment type
   */
  autoIncrement,

  /**
   * Some kind of "generator" column.
   *
   * This is not a "real" computed column, but a
   * system maintained generation.
   *
   * e.g. DB2's GENERATED AS TRANSACTION START ID
   */
  generator,

  /**
   * A computed column based on an expression.
   *
   * e.g. PostgreSQL's "generated always as (...) stored".
   */
  computed,

  /**
   * An expression used in a query at runtime.
   *
   * This should never be used for columns of a table definition.
   */
  runtime;
}
