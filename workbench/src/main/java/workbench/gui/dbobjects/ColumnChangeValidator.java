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
package workbench.gui.dbobjects;

import workbench.db.TableColumnsDatastore;
import workbench.db.WbConnection;
import workbench.db.sqltemplates.ColumnChanger;

import workbench.gui.components.DataStoreTableModel;

import workbench.storage.InputValidator;
import workbench.storage.RowData;

/**
 *
 * @author Thomas Kellerer
 */
public class ColumnChangeValidator
  implements InputValidator
{
  private ColumnChanger changer;

  public ColumnChangeValidator()
  {
  }

  @Override
  public boolean isValid(Object newValue, int row, int col, DataStoreTableModel source)
  {
    if (changer == null) return false;

    if (source.getDataStore().getRowStatus(row) == RowData.NEW)
    {
      return changer.canAddColumn();
    }

    String columnName = source.getColumnName(col);
    switch (columnName)
    {
      case TableColumnsDatastore.DATATYPE_NAME_COL_NAME:
        return changer.canAlterType();
      case TableColumnsDatastore.COLUMN_NAME_COL_NAME:
        return changer.canRenameColumn();
      case TableColumnsDatastore.REMARKS_COL_NAME:
        return changer.canChangeComment();
      case TableColumnsDatastore.DEF_VALUE_COL_NAME:
        return changer.canChangeDefault();
      case TableColumnsDatastore.NULLABLE_COL_NAME:
        return changer.canChangeNullable();
      default:
        return false;
    }
  }

  public void setConnection(WbConnection con)
  {
    if (con == null)
    {
      changer = null;
    }
    else
    {
      changer = new ColumnChanger(con);
    }
  }
}
