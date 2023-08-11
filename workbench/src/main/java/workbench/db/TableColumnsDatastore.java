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

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import workbench.storage.DataStore;

import workbench.util.StringUtil;

/**
 * A DataStore to display a List of ColumnIdentifier objects.
 *
 * @author Thomas Kellerer
 * @see DbMetadata#getTableDefinition(workbench.db.TableIdentifier)
 * @see DbMetadata#getTableColumns(workbench.db.TableIdentifier)
 */
public class TableColumnsDatastore
  extends DataStore
{
  /**
   * The name of the datastore column that contains the table's column name.
   */
  public static final String COLUMN_NAME_COL_NAME = "COLUMN_NAME";

  /**
   * The name of the datastore column that contains the DBMS specific data type.
   */
  public static final String DATATYPE_NAME_COL_NAME = "DATA_TYPE";

  /**
   * The name of the datastore column that contains the PK flag (YES/NO).
   */
  public static final String PKFLAG_COL_NAME = "PK";

  /**
   * The name of the datastore column that contains the NULLABLE flag (YES/NO).
   */
  public static final String NULLABLE_COL_NAME = "NULLABLE";
  /**
   * The name of the datastore column that contains the default value of the table's column.
   */
  public static final String DEF_VALUE_COL_NAME = "DEFAULT";
  /**
   * The name of the datastore column that contains the AUTOINCREMENT flag (YES/NO).
   */
  public static final String AUTO_INC_COL_NAME = "AUTOINCREMENT";
  /**
   * The name of the datastore column that contains the computed column flag (YES/NO).
   */
  public static final String COMPUTED_COL_COL_NAME = "COMPUTED";
  /**
   * The name of the datastore column that contains table's column remarks (comments).
   */
  public static final String REMARKS_COL_NAME = "REMARKS";
  /**
   * The name of the datastore column that contains table's column position in the table.
   */
  public static final String COLPOSITION_COL_NAME = "POSITION";
  /**
   * The name of the datastore column that contains table column's JDBC type value (int)
   * as returned by the driver.
   */
  public static final String JAVA_SQL_TYPE_COL_NAME = "JDBC Type";
  /**
   * The name of the datastore column that contains table column's SCALE or SIZE value (int)
   * as returned by the driver.
   */
  public static final String COLSIZE_COL_NAME = "SCALE/SIZE";
  /**
   * The name of the datastore column that contains table column's PRECISION value (int)
   * as returned by the driver.
   */
  public static final String NUMERIC_DIGITS_COL_NAME = "PRECISION";

  public static final String[] TABLE_DEFINITION_COLS = {
    COLUMN_NAME_COL_NAME,
    DATATYPE_NAME_COL_NAME,
    PKFLAG_COL_NAME,
    NULLABLE_COL_NAME,
    DEF_VALUE_COL_NAME,
    AUTO_INC_COL_NAME,
    COMPUTED_COL_COL_NAME,
    REMARKS_COL_NAME,
    JAVA_SQL_TYPE_COL_NAME,
    COLSIZE_COL_NAME,
    NUMERIC_DIGITS_COL_NAME,
    COLPOSITION_COL_NAME};
  private static final int[] TYPES = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER};
  private static final int[] SIZES = {20, 18, 5, 8, 10, 10, 25, 18, 2, 2, 2, 2};

  private final TableIdentifier sourceTable;

  public TableColumnsDatastore(List<ColumnIdentifier> columns)
  {
    super(TABLE_DEFINITION_COLS, TYPES, SIZES);
    this.sourceTable = null;
    setColumns(columns);
  }

  public TableColumnsDatastore(TableDefinition table)
  {
    super(TABLE_DEFINITION_COLS, TYPES, SIZES);
    this.sourceTable = table.getTable();
    setColumns(table.getColumns());
  }

  private void setColumns(List<ColumnIdentifier> columns)
  {
    if (columns != null)
    {
      for (ColumnIdentifier col : columns)
      {
        int row = addRow();
        setValue(row, COLUMN_NAME_COL_NAME, col.getColumnName());
        setValue(row, JAVA_SQL_TYPE_COL_NAME, Integer.valueOf(col.getDataType()));
        setValue(row, PKFLAG_COL_NAME, col.isPkColumn() ? "YES" : "NO");
        setValue(row, COLSIZE_COL_NAME, Integer.valueOf(col.getColumnSize()));
        setValue(row, NUMERIC_DIGITS_COL_NAME, Integer.valueOf(col.getDecimalDigits()));
        setValue(row, NULLABLE_COL_NAME, col.isNullable() ? "YES" : "NO");
        setValue(row, AUTO_INC_COL_NAME, col.isAutoincrement() ? "YES" : "NO");
        boolean isComputed = col.getGeneratedColumnType() == GeneratedColumnType.computed;
        setValue(row, COMPUTED_COL_COL_NAME, isComputed ? "YES" : "NO");
        setValue(row, COLPOSITION_COL_NAME, Integer.valueOf(col.getPosition()));
        setValue(row, DATATYPE_NAME_COL_NAME, col.getDbmsType());
        setValue(row, DEF_VALUE_COL_NAME, col.getDefaultValue());
        setValue(row, REMARKS_COL_NAME, col.getComment());
        getRow(row).setUserObject(col);
      }
    }
    this.resetStatus();
  }

  public TableIdentifier getSourceTable()
  {
    return sourceTable;
  }

  /**
   * Convert the contents of a DataStore back to a List of ColumnIdentifier
   *
   * @param meta the DbMetadata for the current connection (used to quote names correctly)
   * @param ds the datastore to be converted
   * @return the ColumnIdentifiers
   */
  public static List<ColumnIdentifier> createColumnIdentifiers(DbMetadata meta, DataStore ds)
  {
    int count = ds.getRowCount();
    List<ColumnIdentifier> result = new ArrayList<>(count);
    for (int i=0; i < count; i++)
    {
      ColumnIdentifier ci = ds.getUserObject(i, ColumnIdentifier.class);
      if (ci == null)
      {
        String col = ds.getValueAsString(i, COLUMN_NAME_COL_NAME);
        int type = ds.getValueAsInt(i, JAVA_SQL_TYPE_COL_NAME, Types.OTHER);
        String dbmstype = ds.getValueAsString(i, DATATYPE_NAME_COL_NAME);
        boolean pk = "YES".equals(ds.getValueAsString(i, PKFLAG_COL_NAME));
        ci = new ColumnIdentifier(meta.quoteObjectname(col), meta.getDataTypeResolver().fixColumnType(type, dbmstype), pk);
        int size = ds.getValueAsInt(i, COLSIZE_COL_NAME, 0);
        int digits = ds.getValueAsInt(i, NUMERIC_DIGITS_COL_NAME, -1);
        String nullable = ds.getValueAsString(i, NULLABLE_COL_NAME);
        int position = ds.getValueAsInt(i, COLPOSITION_COL_NAME, 0);
        String comment = ds.getValueAsString(i, REMARKS_COL_NAME);
        String def = ds.getValueAsString(i, DEF_VALUE_COL_NAME);
        ci.setColumnSize(size);
        ci.setDecimalDigits(digits);
        ci.setIsNullable(StringUtil.stringToBool(nullable));
        ci.setDbmsType(dbmstype);
        ci.setComment(comment);
        ci.setDefaultValue(def);
        ci.setPosition(position);
      }
      result.add(ci);
    }
    return result;
  }

}
