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
package workbench.db;

import java.sql.Types;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import workbench.resource.Settings;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;
import workbench.storage.RowDataListSorter;
import workbench.storage.SortDefinition;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class ObjectListDataStore
  extends DataStore
{
  public static final String RESULT_COL_OBJECT_NAME = "NAME";
  public static final String RESULT_COL_TYPE = "TYPE";
  public static final String RESULT_COL_CATALOG = "CATALOG";
  public static final String RESULT_COL_SCHEMA = "SCHEMA";
  public static final String RESULT_COL_REMARKS = "REMARKS";

  public final static int COLUMN_IDX_TABLE_LIST_NAME = 0;
  public final static int COLUMN_IDX_TABLE_LIST_TYPE = 1;
  public final static int COLUMN_IDX_TABLE_LIST_CATALOG = 2;
  public final static int COLUMN_IDX_TABLE_LIST_SCHEMA = 3;
  public final static int COLUMN_IDX_TABLE_LIST_REMARKS = 4;

  private boolean sortMviewsAsTables = false;

  public ObjectListDataStore()
  {
    this("CATALOG", "SCHEMA");
  }

  public ObjectListDataStore(String catalogTerm, String schemaTerm)
  {
    int coltypes[] = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR};
    int sizes[] = {30, 12, 10, 10, 20};
    String[] cols = new String[] {RESULT_COL_OBJECT_NAME, RESULT_COL_TYPE,
                                  RESULT_COL_CATALOG, RESULT_COL_SCHEMA, RESULT_COL_REMARKS};

    initializeStructure(cols, coltypes, sizes);
    ResultInfo info = getResultInfo();
    ColumnIdentifier catColumn = info.getColumn(COLUMN_IDX_TABLE_LIST_CATALOG);
    catColumn.setColumnAlias(catalogTerm.toUpperCase());
    ColumnIdentifier schemaCol = info.getColumn(COLUMN_IDX_TABLE_LIST_SCHEMA);
    schemaCol.setColumnAlias(schemaTerm.toUpperCase());
    this.useNaturalSort = Settings.getInstance().useNaturalSortForTableList();
  }

  public void setSortMviewsAsTables(boolean sortMviewsAsTables)
  {
    this.sortMviewsAsTables = sortMviewsAsTables;
  }

  @Override
  protected RowDataListSorter createSorter(SortDefinition sort)
  {
    TableListSorter sorter = new TableListSorter(sort);
    sorter.setSortMViewAsTable(sortMviewsAsTables);
    sorter.setUseNaturalSort(useNaturalSort);
    int typeIndex = getResultInfo().findColumn(RESULT_COL_TYPE);
    sorter.setTypeColumnIndex(typeIndex);
    return sorter;
  }

  public String getSchemaColumnName()
  {
    return RESULT_COL_SCHEMA;
  }

  public String getCatalogColumnName()
  {
    return RESULT_COL_CATALOG;
  }

  public String getTypeColumnName()
  {
    return RESULT_COL_TYPE;
  }

  public String getRemarksColumnName()
  {
    return RESULT_COL_REMARKS;
  }

  public String getObjectColumnName()
  {
    return RESULT_COL_OBJECT_NAME;
  }

  public void addObjects(Collection<? extends DbObject> objects)
  {
    if (objects == null) return;
    for (DbObject dbo : objects)
    {
      addDbObject(dbo);
    }
  }

  public int addDbObject(DbObject dbo)
  {
    if (dbo == null) return -1;
    int row = addRow();
    setDbObject(row, dbo);
    return row;
  }

  public void setDbObject(int row, DbObject dbo)
  {
    if (dbo == null) return;
    setSchema(row, dbo.getSchema());
    setCatalog(row, dbo.getCatalog());
    setObjectName(row, dbo.getObjectName());
    setType(row, dbo.getObjectType());
    setRemarks(row, dbo.getComment());
    getRow(row).setUserObject(dbo);
  }

  public String getCatalog(int row)
  {
    return getValueAsString(row, getCatalogColumnName());
  }

  public void setCatalog(int row, String catalog)
  {
    setValue(row, getCatalogColumnName(), catalog);
  }

  public String getSchema(int row)
  {
    return getValueAsString(row, getSchemaColumnName());
  }

  public void setSchema(int row, String schema)
  {
    setValue(row, getSchemaColumnName(), schema);
  }

  public String getType(int row)
  {
    return getValueAsString(row, getTypeColumnName());
  }

  public void setType(int row, String type)
  {
    setValue(row, getTypeColumnName(), type);
  }

  public String getObjectName(int row)
  {
    return getValueAsString(row, getObjectColumnName());
  }

  public void setObjectName(int row, String name)
  {
    setValue(row, getObjectColumnName(), name);
  }

  public String getRemarks(int row)
  {
    return getValueAsString(row, getRemarksColumnName());
  }

  public void setRemarks(int row, String remarks)
  {
    setValue(row, getRemarksColumnName(), remarks);
  }

  public TableIdentifier getTableIdentifier(int row)
  {
    Object uo = getUserObject(row);
    if (uo instanceof TableIdentifier)
    {
      return (TableIdentifier)uo;
    }
    String name = getObjectName(row);
    String schema = getSchema(row);
    String cat = getCatalog(row);
    TableIdentifier tbl = new TableIdentifier(cat, schema, name);
    tbl.setNeverAdjustCase(true);
    tbl.setType(getType(row));
    tbl.setComment(getValueAsString(row, getRemarksColumnName()));
    return tbl;
  }

  @Override
  public void setValue(int rowNumber, int colIndex, Object value)
    throws IndexOutOfBoundsException
  {
    super.setValue(rowNumber, colIndex, value);
    Object obj = getRow(rowNumber).getUserObject();
    if (obj instanceof TableIdentifier)
    {
      TableIdentifier tbl = (TableIdentifier)obj;
      String colName = getColumnName(colIndex);
      String val = (value == null ? null : value.toString());
      switch (colName)
      {
        case RESULT_COL_SCHEMA:
          tbl.setSchema(val);
          break;
        case RESULT_COL_CATALOG:
          tbl.setCatalog(val);
          break;
        case RESULT_COL_OBJECT_NAME:
          tbl.setName(val);
          break;
        case RESULT_COL_TYPE:
          tbl.setType(val);
          break;
        case RESULT_COL_REMARKS:
          tbl.setComment(val);
          break;
      }
    }
  }

  public Collection<String> getAllSchemas()
  {
    return getDistinctValues(getSchemaColumnName());
  }

  public Collection<String> getAllCatalogs()
  {
    return getDistinctValues(getCatalogColumnName());
  }

  public Collection<String> getDistinctValues(String columnName)
  {
    Set<String> result = new HashSet<>(20);

    for (int row=0; row < this.getRowCount(); row ++)
    {
      result.add(getValueAsString(row, columnName));
    }
    return result;
  }


  /**
   * Remove objects from this data store if the passed filters indicate the schema or catalog
   * should be excluded.
   *
   * The filters will only be applied if {@link ObjectNameFilter#isRetrievalFilter()} is true.
   *
   * A row is removed, if at least one of the filters indicate that it is excluded
   *
   * @param schemaFilter   the schema filter to apply
   * @param catalogFilter  the catalog filter to apply
   *
   * @see ObjectNameFilter#isExcluded(String)
   * @see ObjectNameFilter#isRetrievalFilter()
   */
  public void applyRetrievalFilters(ObjectNameFilter catalogFilter, ObjectNameFilter schemaFilter)
  {
    boolean applyCatalogFilter =  catalogFilter != null && catalogFilter.isRetrievalFilter();
    boolean applySchemaFilter = schemaFilter != null && schemaFilter.isRetrievalFilter();

    if (!applyCatalogFilter && !applySchemaFilter) return;

    int rowCount = getRowCount();

    for (int row=rowCount - 1; row >= 0; row --)
    {
      String schema = getSchema(row);
      String catalog = getCatalog(row);
      boolean removeRow = false;
      if (applyCatalogFilter && StringUtil.isNotBlank(catalog))
      {
        removeRow = catalogFilter.isExcluded(catalog);
      }
      if (applySchemaFilter && StringUtil.isNotBlank(schema))
      {
        removeRow = removeRow || schemaFilter.isExcluded(schema);
      }
      if (removeRow)
      {
        deleteRow(row);
      }
    }
  }

}
