/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.storage.filter.AndExpression;
import workbench.storage.filter.StringEqualsComparator;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class DbObjectFinder
{
  private final DbMetadata meta;

  public DbObjectFinder(DbMetadata meta)
  {
    Objects.requireNonNull(meta, "Can't work without a metadata instance");
    this.meta = meta;
  }

  public DbObjectFinder(WbConnection conn)
  {
    this(conn.getMetadata());
  }

    public TableIdentifier findObject(TableIdentifier tbl)
  {
    return findObject(tbl, true, false);
  }

  public TableIdentifier findObject(TableIdentifier tbl, boolean adjustCase, boolean searchAllSchemas)
  {
    return findObject(tbl, adjustCase, searchAllSchemas, searchAllSchemas);
  }

  public TableIdentifier findObject(TableIdentifier tbl, boolean adjustCase, boolean searchAllSchemas, boolean searchAllCatalogs)
  {
    if (tbl == null) return null;
    TableIdentifier result = null;
    TableIdentifier table = tbl.createCopy();
    if (adjustCase) table.adjustCase(meta.getWbConnection());

    try
    {
      boolean schemaWasNull = false;

      String schema = table.getSchema();
      if (schema == null)
      {
        schemaWasNull = true;
        schema = meta.getCurrentSchema();
      }
      else
      {
        searchAllSchemas = false;
      }

      String catalog = table.getCatalog();
      if (catalog == null)
      {
        catalog = meta.getCurrentCatalog();
      }

      String tablename = table.getRawTableName();

      String[] types;
      if (table.getType() == null)
      {
        types = null;
      }
      else
      {
        types = new String[]{table.getType()};
      }
      ObjectListDataStore ds = meta.getObjects(catalog, schema, tablename, types);

      if (ds.getRowCount() == 0 && DBID.Oracle.isDB(meta))
      {
        // try again with PUBLIC, maybe it's a public synonym
        ds = meta.getObjects(null, "PUBLIC", tablename, null);
      }

      if (ds.getRowCount() == 0 && schemaWasNull && searchAllSchemas)
      {
        ds = meta.getObjects(null, null, tablename, null);
      }

      if (ds.getRowCount() == 1)
      {
        result = ds.getTableIdentifier(0);
      }
      else if (ds.getRowCount() > 1)
      {
        AndExpression filter = new AndExpression();
        StringEqualsComparator comp = new StringEqualsComparator();
        filter.addColumnExpression(ds.getObjectColumnName(), comp, table.getRawTableName(), true);

        if (StringUtil.isNotBlank(schema) && !searchAllSchemas)
        {
          filter.addColumnExpression(ds.getSchemaColumnName(), comp, schema, true);
        }
        if (StringUtil.isNotBlank(catalog) && !searchAllCatalogs)
        {
          filter.addColumnExpression(ds.getCatalogColumnName(), comp, catalog, true);
        }

        if (filter.hasFilter()) ds.applyFilter(filter);

        if (ds.getRowCount() >= 1)
        {
          result = ds.getTableIdentifier(0);
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, meta.getConnId() + ": Error checking table existence", e);
    }
    return result;
  }

  /**
   * Check if the given table exists in the database
   */
  public boolean tableExists(TableIdentifier aTable)
  {
    return objectExists(aTable, meta.getTableTypesArray());
  }

  public boolean objectExists(TableIdentifier aTable, String type)
  {
    String[] types = null;
    if (type != null)
    {
      types = new String[] { type };
    }
    return objectExists(aTable, types);
  }

  public boolean objectExists(TableIdentifier aTable, String[] types)
  {
    return findTable(aTable, types, false) != null;
  }

  public TableIdentifier findSelectableObject(TableIdentifier tbl)
  {
    return findTable(tbl, meta.getSelectableTypes(), false);
  }

  public TableIdentifier searchSelectableObjectOnPath(TableIdentifier tbl)
  {
    return searchObjectOnPath(tbl, meta.getSelectableTypes());
  }

  public TableIdentifier searchTableOnPath(TableIdentifier table)
  {
    return searchObjectOnPath(table, meta.getTableTypesArray());
  }

  public TableIdentifier searchObjectOnPath(TableIdentifier table, String[] types)
  {
    if (table.getSchema() != null)
    {
      return findTable(table, types, false);
    }

    List<String> searchPath = DbSearchPath.Factory.getSearchPathHandler(meta.getWbConnection()).getSearchPath(meta.getWbConnection(), null);

    if (searchPath.isEmpty())
    {
      return findTable(table, types, false);
    }

    LogMgr.logDebug(new CallerInfo(){}, meta.getConnId() + ": Looking for table " + table.getRawTableName() + " in schemas: " + searchPath);
    for (String checkSchema  : searchPath)
    {
      TableIdentifier toSearch = table.createCopy();
      toSearch.setSchema(checkSchema);

      TableIdentifier found = findTable(toSearch, types, false);
      if (found != null)
      {
        LogMgr.logDebug(new CallerInfo(){}, meta.getConnId() + ": Found table " + found.getTableExpression());
        return found;
      }
    }
    return null;
  }

  public TableDefinition findTableDefinition(TableIdentifier tbl)
  {
    TableIdentifier realTable = findTable(tbl, meta.getTableTypesArray(), false);
    if (realTable == null) return null;
    try
    {
      return meta.getTableDefinition(realTable, true);
    }
    catch (SQLException ex)
    {
      return null;
    }
  }

  public TableIdentifier findTable(TableIdentifier tbl, boolean searchAllSchemas)
  {
    return findTable(tbl, meta.getTableTypesArray(), searchAllSchemas);
  }

  public TableIdentifier findTable(TableIdentifier tbl)
  {
    return findTable(tbl, meta.getTableTypesArray(), false);
  }

  public TableIdentifier findTable(TableIdentifier tbl, String[] types)
  {
    return findTable(tbl, types == null || types.length == 0 ? meta.getTableTypesArray() : types, false);
  }

  private TableIdentifier findTable(TableIdentifier tbl, String[] types, boolean searchAllSchemas)
  {
    if (tbl == null) return null;

    TableIdentifier result = null;
    TableIdentifier table = tbl.createCopy();
    table.adjustCase(meta.getWbConnection());
    try
    {
      String schema = table.getSchema();
      if (schema == null && !searchAllSchemas)
      {
        schema = meta.getCurrentSchema();
      }

      String catalog = table.getCatalog();
      if (catalog == null)
      {
        catalog = meta.getCurrentCatalog();
      }

      String tablename = table.getRawTableName();

      ObjectListDataStore ds = meta.getObjects(catalog, schema, tablename, types);

      if (ds.getRowCount() == 1)
      {
        result = ds.getTableIdentifier(0);
        return result;
      }

      // Nothing found, try again with the original catalog and schema information
      ds = meta.getObjects(table.getRawCatalog(), table.getRawSchema(), table.getRawTableName(), types);
      if (ds.getRowCount() == 0)
      {
        return null;
      }
      else if (ds.getRowCount() == 1)
      {
        result = ds.getTableIdentifier(0);
        return result;
      }

      // if nothing was found there is nothing we can do to guess the correct
      // "searching strategy" for the current DBMS
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, meta.getConnId() + ": Error checking table existence", e);
    }
    return result;
  }


}
