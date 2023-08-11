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
package workbench.db.objectcache;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.DbObjectFinder;
import workbench.db.DbSearchPath;
import workbench.db.DbSwitcher;
import workbench.db.DependencyNode;
import workbench.db.IndexDefinition;
import workbench.db.IndexReader;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectNameFilter;
import workbench.db.ObjectNameSorter;
import workbench.db.PkDefinition;
import workbench.db.ProcedureDefinition;
import workbench.db.ReaderFactory;
import workbench.db.TableDefinition;
import workbench.db.TableDependency;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * A cache for database objects to support auto-completion in the editor
 *
 * @author  Thomas Kellerer
 */
class ObjectCache
{
  private final boolean retrieveOraclePublicSynonyms;

  public static final Namespace DUMMY_NSP_KEY = new Namespace("-$$wb-null-schema$$-", "-$$wb-null-catalog$$-");
  private final Set<Namespace> schemasInCache = new HashSet<>();
  private final Map<TableIdentifier, List<DependencyNode>> referencedTables = new HashMap<>();
  private final Map<TableIdentifier, List<DependencyNode>> referencingTables = new HashMap<>();
  private final Map<TableIdentifier, List<ColumnIdentifier>> objects = new HashMap<>();
  private final Map<TableIdentifier, TableIdentifier> synonymMap = new HashMap<>();
  private final Map<TableIdentifier, List<IndexDefinition>> indexMap= new HashMap<>();
  private final Map<TableIdentifier, PkDefinition> pkMap = new HashMap<>();
  private final Map<Namespace, List<ProcedureDefinition>> procedureCache = new HashMap<>();
  private final Map<Namespace, List<ProcedureDefinition>> tableFunctionsCache = new HashMap<>();
  private final ObjectNameFilter schemaFilter;
  private final ObjectNameFilter catalogFilter;
  private final boolean supportsSchemas;
  private final boolean supportsCatalogs;
  private boolean databasesCached;
  private final List<String> availableDatabases = new ArrayList<>();
  private final DbObjectFinder finder;
  private final TableIdentifier dummyTable = new TableIdentifier("-$WB DUMMY$-", "-$WB DUMMY$-");

  ObjectCache(WbConnection conn)
  {
    retrieveOraclePublicSynonyms = DBID.Oracle.isDB(conn) && Settings.getInstance().getBoolProperty("workbench.editor.autocompletion.oracle.public_synonyms", false);
    schemaFilter = conn.getProfile().getSchemaFilter();
    catalogFilter = conn.getProfile().getCatalogFilter();
    supportsSchemas = conn.getDbSettings().supportsSchemas();
    supportsCatalogs = conn.getDbSettings().supportsCatalogs();
    finder = new DbObjectFinder(conn);
  }

  private String[] getCompletionTypes(WbConnection conn)
  {
    String dbId = conn.getDbId();
    Set<String> types = CollectionUtil.caseInsensitiveSet();

    types.addAll(CollectionUtil.caseInsensitiveSet(conn.getMetadata().getSelectableTypes()));
    types.addAll(Settings.getInstance().getListProperty("workbench.db." + dbId + ".completion.types.additional", true, null));

    List<String> excludeTypes = Settings.getInstance().getListProperty("workbench.db." + dbId + ".completion.types.exclude", true, null);
    types.removeAll(excludeTypes);

    return StringUtil.toArray(types, true);
  }

  private boolean isFiltered(TableIdentifier table)
  {
    boolean filtered = false;
    if (Settings.getInstance().getUseProfileFilterForCompletion())
    {
      if (schemaFilter != null)
      {
        filtered = schemaFilter.isExcluded(table.getSchema());
      }

      if (filtered) return true;

      if (catalogFilter != null)
      {
        filtered = catalogFilter.isExcluded(table.getCatalog());
      }
    }
    return filtered;
  }

  /**
   * Add this list of tables to the current cache.
   */
  private void setTables(List<TableIdentifier> tables, WbConnection conn)
  {
    for (TableIdentifier tbl : tables)
    {
      if (!isFiltered(tbl) && !this.objects.containsKey(tbl))
      {
        this.objects.put(tbl, null);

        Namespace key = Namespace.fromTable(tbl, conn);
        this.schemasInCache.add(key);
      }
    }
  }

  List<Namespace> getSearchPath(WbConnection dbConn, Namespace requestedNamespace)
  {
    if (requestedNamespace != null && requestedNamespace.hasCatalogAndSchema() && supportsCatalogs && supportsSchemas)
    {
      return Collections.singletonList(requestedNamespace);
    }

    if (!dbConn.getDbSettings().useCurrentNamespaceForCompletion())
    {
      if (supportsSchemas)
      {
        return CollectionUtil.arrayList(Namespace.NULL_NSP);
      }
      else
      {
        // Databases that support catalogs don't support wildcards for the "catalog" parameter
        // when retrieving tables, so use an explicit list of catalogs here.
        List<String> catalogs = dbConn.getMetadata().getCatalogs();
        return catalogs.stream().map(cat -> new Namespace(null, cat)).collect(Collectors.toList());
      }
    }

    List<Namespace> namespaces = new ArrayList<>();
    Collection<Namespace> ignore = new ArrayList<>();

    String requestedSchema = requestedNamespace == null ? null : requestedNamespace.getSchema();
    String requestedCatalog = requestedNamespace == null ? null : requestedNamespace.getCatalog();

    Namespace nspToUse = createNamespace(requestedSchema, requestedCatalog);

    if (supportsSchemas && !supportsCatalogs)
    {
      List<String> schemas = DbSearchPath.Factory.getSearchPathHandler(dbConn).getSearchPath(dbConn, requestedSchema);
      namespaces.addAll(Namespace.convertSchemaList(schemas, null));
      ignore.addAll(Namespace.convertSchemaList(dbConn.getDbSettings().getIgnoreCompletionSchemas()));
    }
    else if (supportsCatalogs && !supportsSchemas)
    {
      if (nspToUse.isValid())
      {
        namespaces.add(nspToUse);
      }
      else
      {
        String currentCatalog = dbConn.getMetadata().getCurrentCatalog();
        namespaces.add(new Namespace(null, currentCatalog));
        // Databases that support catalogs don't support wildcards for the "catalog" parameter
        // when retrieving tables, so use an explicit list of catalogs here.
        List<String> catalogs = dbConn.getMetadata().getCatalogs();
        namespaces.addAll(catalogs.stream().
                                   filter(cat -> StringUtil.stringsAreNotEqual(cat, currentCatalog)).
                                   map(cat -> new Namespace(null, cat)).collect(Collectors.toList()));
      }
      ignore.addAll(Namespace.convertCatalogList(dbConn.getDbSettings().getIgnoreCompletionCatalogs()));
    }
    else if (supportsCatalogs && supportsSchemas)
    {
      String catalog = dbConn.getMetadata().getCurrentCatalog();
      String schema = dbConn.getMetadata().getCurrentSchema();

      if (requestedSchema == null && requestedCatalog == null)
      {
        namespaces.add(new Namespace(schema, catalog));
      }
      else
      {
        namespaces.add(new Namespace(StringUtil.coalesce(requestedSchema, schema), StringUtil.coalesce(requestedCatalog, catalog)));
      }
      ignore.addAll(Namespace.convertCatalogList(dbConn.getDbSettings().getIgnoreCompletionCatalogs()));
    }

    // the defaultSchema argument is the supplied, then this is a user defined value
    // we should not ignore it
    if (nspToUse != null)
    {
      ignore.remove(nspToUse);
    }

    namespaces.removeAll(ignore);

    if (namespaces.isEmpty())
    {
      return CollectionUtil.arrayList(Namespace.NULL_NSP);
    }
    return namespaces;
  }

  private Namespace createNamespace(String schema, String catalog)
  {
    if (supportsSchemas && !supportsCatalogs)
    {
      return new Namespace(schema, null);
    }
    if (supportsCatalogs && !supportsSchemas)
    {
      return new Namespace(schema, catalog);
    }
    return new Namespace(schema, catalog);
  }

  /**
   * Get the tables (and views) the are currently in the cache
   */
  synchronized Set<TableIdentifier> getTables(WbConnection dbConnection, Namespace requestedNamespace, Collection<String> types)
  {
    if (dbConnection.isBusy()) return Collections.emptySet();

    List<Namespace> searchPath = getSearchPath(dbConnection, requestedNamespace);
    LogMgr.logDebug(new CallerInfo(){}, "Getting tables using schema: " + requestedNamespace + ", filter: " + types + ", search path: " + searchPath);

    DbMetadata meta = dbConnection.getMetadata();

    for (Namespace namespace : searchPath)
    {
      if (this.objects.isEmpty() || !schemasInCache.contains(namespace))
      {
        try
        {
          List<TableIdentifier> tables = meta.getObjectList(null, namespace, getCompletionTypes(dbConnection));
          for (TableIdentifier tbl : tables)
          {
            tbl.checkQuotesNeeded(dbConnection);
          }
          this.setTables(tables, dbConnection);
          LogMgr.logDebug(new CallerInfo(){}, "Namespace \"" + namespace + "\" not found in cache. Retrieved " + tables.size() + " objects");
        }
        catch (Exception e)
        {
          LogMgr.logError(new CallerInfo(){}, "Could not retrieve table list for namespace: " + namespace, e);
        }
      }
    }

    if (types != null)
    {
      return filterTablesByType(dbConnection, searchPath, types);
    }
    else
    {
      return filterTablesBySchema(dbConnection, searchPath);
    }
  }

  public List<DependencyNode> getReferencingTables(WbConnection dbConn, TableIdentifier table)
  {
    if (table == null) return Collections.emptyList();

    TableIdentifier tbl = finder.findTable(table, false);
    List<DependencyNode> referencing = referencingTables.get(tbl);
    if (referencing == null && dbConn.isBusy()) return Collections.emptyList();

    if (referencing == null)
    {
      TableDependency deps = new TableDependency(dbConn, tbl);
      deps.setRetrieveDirectChildrenOnly(true);
      deps.readTreeForChildren();
      referencing = deps.getLeafs();
      referencingTables.put(table, referencing);
    }
    return referencing;
  }

  public List<DependencyNode> getReferencedTables(WbConnection dbConn, TableIdentifier table)
  {
    if (table == null || dbConn.isBusy()) return Collections.emptyList();

    TableIdentifier tbl = finder.findTable(table, false);
    List<DependencyNode> referenced = referencedTables.get(tbl);
    if (referenced == null)
    {
      TableDependency deps = new TableDependency(dbConn, tbl);
      deps.setRetrieveDirectChildrenOnly(true);
      deps.readTreeForParents();
      referenced = deps.getLeafs();
      referencedTables.put(table, referenced);
    }
    return referenced;
  }

  public void addReferencedTables(TableIdentifier table, List<DependencyNode> referenced)
  {
    if (table == null) return;
    List<DependencyNode> old = referencedTables.put(table, referenced);
    if (old == null)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Added referenced tables for " + table + "(" + referenced + ")");
    }
    else
    {
      LogMgr.logDebug(new CallerInfo(){}, "Replaced existing referenced tables for " + table);
    }
  }

  public void addReferencingTables(TableIdentifier table, List<DependencyNode> referencing)
  {
    if (table == null) return;
    List<DependencyNode> old = referencingTables.put(table, referencing);
    if (old == null)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Added referencing tables for " + table + "(" + referencing + ")");
    }
    else
    {
      LogMgr.logDebug(new CallerInfo(){}, "Replaced existing referencing tables for " + table);
    }
  }

  Map<Namespace, List<ProcedureDefinition>> getProcedures()
  {
    if (procedureCache == null) return new HashMap<>(0);
    return procedureCache;
  }

  /**
   * Return available table functions (aka "set returning" functions).
   */
  public List<ProcedureDefinition> getTableFunctions(WbConnection dbConnection, Namespace requestedNsp)
  {
    List<Namespace> path = getSearchPath(dbConnection, requestedNsp);
    List<ProcedureDefinition> result = new ArrayList<>();

    String currentSchema = null;
    String currentCatalog = null;
    if (requestedNsp != null)
    {
      currentSchema = requestedNsp.getSchema();
      currentCatalog = requestedNsp.getCatalog();
    }
    else if (path.size() > 0)
    {
      currentSchema = path.get(0).getSchema();
      currentCatalog = dbConnection.getMetadata().getCurrentCatalog();
    }

    boolean alwaysUseSchema = dbConnection.getDbSettings().alwaysUseSchemaForCompletion() || path.size() > 1;
    for (Namespace nsp : path)
    {
      List<ProcedureDefinition> functions = tableFunctionsCache.get(nsp);
      if (functions == null)
      {
        // nothing in the cache. We can only retrieve this from the database if the connection isn't busy
        if (dbConnection.isBusy())
        {
          continue;
        }

        try
        {
          functions = dbConnection.getMetadata().getProcedureReader().getTableFunctions(nsp.getCatalog(), nsp.getSchema(), "%");
          if (dbConnection.getDbSettings().getRetrieveProcParmsForAutoCompletion())
          {
            for (ProcedureDefinition func : functions)
            {
              func.readParameters(dbConnection);
            }
          }
          tableFunctionsCache.put(nsp, functions);
        }
        catch (SQLException e)
        {
          LogMgr.logError(new CallerInfo(){}, "Error retrieving table functions", e);
        }
      }
      for (ProcedureDefinition func : functions)
      {
        ProcedureDefinition copy = func.createCopy();
        adjustSchemaAndCatalog(dbConnection, copy, currentSchema, currentCatalog, alwaysUseSchema);
        result.add(copy);
      }
    }

    return result;
  }

  /**
   * Get the procedures the are currently in the cache
   */
  public List<ProcedureDefinition> getProcedures(WbConnection dbConnection, String catalog, String schema)
  {
    Namespace nsp = createNamespace(schema, catalog);
    return getProcedures(dbConnection, nsp);
  }

  public List<ProcedureDefinition> getProcedures(WbConnection dbConnection, Namespace requestedNsp)
  {
    List<Namespace> path = getSearchPath(dbConnection, requestedNsp);
    List<ProcedureDefinition> result = new ArrayList<>();
    for (Namespace nsp : path)
    {
      List<ProcedureDefinition> procs = procedureCache.get(nsp);
      if (procs == null)
      {
        // nothing in the cache. We can only retrieve this from the database if the connection isn't busy
        if (dbConnection.isBusy())
        {
          continue;
        }

        try
        {
          procs = dbConnection.getMetadata().getProcedureReader().getProcedureList(nsp.getCatalog(), nsp.getSchema(), "%");
          if (dbConnection.getDbSettings().getRetrieveProcParmsForAutoCompletion())
          {
            for (ProcedureDefinition proc : procs)
            {
              proc.readParameters(dbConnection);
            }
          }
          procedureCache.put(nsp, procs);
        }
        catch (SQLException e)
        {
          LogMgr.logError(new CallerInfo(){}, "Error retrieving procedures", e);
        }
      }
      result.addAll(procs);
    }
    return result;
  }

  private Set<TableIdentifier> filterTablesByType(WbConnection conn, List<Namespace> schemas, Collection<String> requestedTypes)
  {
    SortedSet<TableIdentifier> result = new TreeSet<>(new ObjectNameSorter());
    String currentSchema = null;
    if (schemas.size() == 1)
    {
      currentSchema = schemas.get(0).getSchema();
    }

    boolean alwaysUseSchema = conn.getDbSettings().alwaysUseSchemaForCompletion() || schemas.size() > 1;

    String catalog = conn.getMetadata().getCurrentCatalog();
    for (TableIdentifier tbl : objects.keySet())
    {
      String ttype = tbl.getType();
      Namespace tns = Namespace.fromTable(tbl, conn);
      if ( requestedTypes.contains(ttype) && schemas.contains(tns) )
      {
        TableIdentifier copy = tbl.createCopy();
        adjustSchemaAndCatalog(conn, copy, currentSchema, catalog, alwaysUseSchema);
        result.add(copy);
      }
    }
    return result;
  }

  private void adjustSchemaAndCatalog(WbConnection conn, DbObject table, String currentSchema, String currentCatalog, boolean alwaysUseSchema)
  {
    if (!conn.getDbSettings().useCurrentNamespaceForCompletion()) return;

    DbMetadata meta = conn.getMetadata();
    String tSchema = table.getSchema();
    boolean ignoreSchema = (alwaysUseSchema ? false : meta.ignoreSchema(tSchema, currentSchema));
    if (ignoreSchema)
    {
      table.setSchema(null);
    }

    boolean alwaysUseCatalog = conn.getDbSettings().alwaysUseCatalogForCompletion();

    boolean ignoreCatalog = (alwaysUseCatalog ? false : meta.ignoreCatalog(table.getCatalog(), currentCatalog));
    if (ignoreCatalog)
    {
      table.setCatalog(null);
    }
  }

  private Set<TableIdentifier> filterTablesBySchema(WbConnection dbConnection, List<Namespace> schemas)
  {
    SortedSet<TableIdentifier> result = new TreeSet<>(new ObjectNameSorter(true));
    DbMetadata meta = dbConnection.getMetadata();

    List<Namespace> schemasToCheck = schemas.stream().filter(ns -> ns != null).collect(Collectors.toList());

    boolean alwaysUseSchema = dbConnection.getDbSettings().alwaysUseSchemaForCompletion() || schemasToCheck.size() > 1;

    String currentSchema = null;
    if (schemasToCheck.size() == 1)
    {
      currentSchema = supportsSchemas ? meta.getCurrentSchema() : meta.getCurrentCatalog();
    }

    String currentCatalog = meta.getCurrentCatalog();

    for (TableIdentifier tbl : objects.keySet())
    {
      Namespace tns = Namespace.fromTable(tbl, dbConnection);

      if (schemasToCheck.contains(tns) || schemasToCheck.isEmpty())
      {
        TableIdentifier copy = tbl.createCopy();
        adjustSchemaAndCatalog(dbConnection, copy, currentSchema, currentCatalog, alwaysUseSchema);
        result.add(copy);
      }
    }

    return result;
  }

  public synchronized void addSynonym(TableIdentifier synonym, TableIdentifier baseTable)
  {
    this.synonymMap.put(synonym, baseTable);
  }

  /**
   * Return the underlying table for a possible synonym.
   *
   * If the passed table is not a synonym, the passed table will be returned.
   *
   * i.e. if <tt>getSynonymTable(conn, someTable) == someTable</tt>, then <tt>someTable</tt>
   * is not a synonym.
   *
   * @param dbConn   then connection to use
   * @param toCheck  the table to check
   *
   * @return  the underlying table for the passed table, or the table if no synonym was found
   */
  public synchronized TableIdentifier getSynonymTable(WbConnection dbConn, TableIdentifier toCheck)
  {
    if (!dbConn.getMetadata().supportsSynonyms()) return toCheck;

    TableIdentifier key = findInCache(toCheck, this.synonymMap.keySet());
    TableIdentifier baseTable = null;

    if (key != null)
    {
      baseTable = this.synonymMap.get(key);
    }

    if (baseTable != null && baseTable.equals(dummyTable))
    {
      // we already tested for a synonym but did not find any
      return toCheck;
    }

    if (baseTable != null)
    {
      // we found a synonym
      return baseTable;
    }

    if (baseTable == null)
    {
      // no synonym found and we did not yet test for one, so hit the database and try to find one.
      baseTable = dbConn.getMetadata().resolveSynonym(toCheck);
    }

    TableIdentifier synKey = toCheck.createCopy();
    synKey.adjustCase(dbConn);

    if (baseTable == null || baseTable == toCheck)
    {
      // "negative caching". Avoid repeated lookup for non-synonyms
      synonymMap.put(synKey, dummyTable);
    }
    else
    {
      synonymMap.put(synKey, baseTable);
    }

    return baseTable == null ? toCheck : baseTable;
  }

  /**
   * Return the columns for the given table.
   *
   * If the table columns are not in the cache they are retrieved from the database.
   *
   * @return the columns of the table.
   * @see DbMetadata#getTableDefinition(workbench.db.TableIdentifier)
   */
  public synchronized List<ColumnIdentifier> getColumns(WbConnection dbConnection, TableIdentifier tbl)
  {
    long start = System.currentTimeMillis();

    TableIdentifier toSearch = findEntry(dbConnection, tbl);

    List<ColumnIdentifier> cols = null;

    if (toSearch != null)
    {
      cols = this.objects.get(toSearch);
    }
    else if (!dbConnection.isBusy())
    {
      toSearch = finder.searchSelectableObjectOnPath(tbl);
      if (toSearch == null) return null;

      toSearch.checkQuotesNeeded(dbConnection);
    }

    // nothing in the cache. We can only retrieve this from the database if the connection isn't busy
    if (cols == null && dbConnection.isBusy())
    {
      LogMgr.logDebug(new CallerInfo(){}, "No columns found for table " + tbl.getTableExpression() + ", but connection " + dbConnection.getId() + " is busy.");
      return Collections.emptyList();
    }

    // To support Oracle public synonyms, try to find a table with that name but without a schema
    if (retrieveOraclePublicSynonyms && toSearch.getSchema() != null && cols == null)
    {
      toSearch.setSchema(null);
      toSearch.setType(null);
      cols = this.objects.get(toSearch);
      if (cols == null)
      {
        // retrieve Oracle PUBLIC synonyms
        this.getTables(dbConnection, new Namespace("PUBLIC", null), null);
        cols = this.objects.get(toSearch);
      }
    }

    if (CollectionUtil.isEmpty(cols))
    {
      try
      {
        LogMgr.logDebug(new CallerInfo(){}, "Table not in cache, retrieving columns for " + toSearch.getTableExpression());
        cols = dbConnection.getMetadata().getTableColumns(toSearch);
      }
      catch (Throwable e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error retrieving columns for " + toSearch, e);
        cols = null;
      }

      if (toSearch != null && CollectionUtil.isNonEmpty(cols))
      {
        LogMgr.logDebug(new CallerInfo(){}, "Adding columns for " + toSearch.getTableExpression() + " to cache");
        this.objects.put(toSearch, cols);
      }
    }

    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Checking columns for: " + tbl.getTableExpression(dbConnection) + " took " + duration + "ms");

    return Collections.unmodifiableList(cols);
  }

  synchronized void removeProcedure(WbConnection dbConn, ProcedureDefinition toRemove)
  {
    if (toRemove == null) return;
    String fullName = toRemove.getObjectNameForDrop(dbConn);

    Namespace nsp = createNamespace(toRemove.getSchema(), toRemove.getCatalog());
    List<ProcedureDefinition> procedures = getProcedures(dbConn, nsp);
    Iterator<ProcedureDefinition> itr = procedures.iterator();
    while (itr.hasNext())
    {
      ProcedureDefinition proc = itr.next();
      String procName = proc.getObjectNameForDrop(dbConn);
      if (procName.equals(fullName))
      {
        LogMgr.logDebug(new CallerInfo(){}, "Procedure " + fullName + " removed from the cache");
        itr.remove();
        break;
      }
    }
  }

  synchronized void removeSchema(WbConnection dbConn, String schema)
  {
    Namespace nsp = Namespace.fromSchemaName(dbConn, schema);
    schemasInCache.remove(nsp);
    tableFunctionsCache.remove(nsp);
    procedureCache.remove(nsp);
    Set<TableIdentifier> keys = new HashSet(objects.keySet());
    for (TableIdentifier tbl : keys)
    {
      Namespace tnsp = Namespace.fromTable(tbl, dbConn);
      if (nsp.equals(tnsp))
      {
        objects.remove(tbl);
        referencedTables.remove(tbl);
        referencingTables.remove(tbl);
      }
    }
    LogMgr.logDebug(new CallerInfo(){}, "Removed objects for schema " + nsp + " from the cache");
  }

  synchronized void removeTable(WbConnection dbConn, TableIdentifier tbl)
  {
    if (tbl == null) return;

    TableIdentifier toRemove = findEntry(dbConn, tbl);
    if (toRemove == null) return;

    this.objects.remove(toRemove);
    LogMgr.logDebug(new CallerInfo(){}, "Removed " + tbl.getTableName() + " from the cache");
  }

  synchronized void addTableList(WbConnection dbConnection, ObjectListDataStore tables, String schema)
  {
    Set<String> selectable = dbConnection.getMetadata().getObjectsWithData();

    int count = 0;

    for (int row = 0; row < tables.getRowCount(); row++)
    {
      String type = tables.getType(row);
      if (selectable.contains(type))
      {
        TableIdentifier tbl = tables.getTableIdentifier(row);
        tbl.checkQuotesNeeded(dbConnection);
        if (objects.get(tbl) == null)
        {
          objects.put(tbl, null);
          count ++;
        }
        this.schemasInCache.add(Namespace.fromTable(tbl, dbConnection));
      }
    }
    LogMgr.logDebug(new CallerInfo(){}, "Added " + count + " objects");
  }

  synchronized void addProcedureList(DataStore procs, String catalog, String schema)
  {
    if (schema == null) return;
    Namespace nsp = createNamespace(schema, catalog);
    int count = procs.getRowCount();
    List<ProcedureDefinition> procList = new ArrayList<>();
    for (int row=0; row < count; row++)
    {
      Object uo = procs.getRow(row).getUserObject();
      if (uo instanceof ProcedureDefinition)
      {
        ProcedureDefinition proc = (ProcedureDefinition)uo;
        procList.add(proc);
      }
    }
    procedureCache.put(nsp, procList);
    LogMgr.logDebug(new CallerInfo(){}, "Added " + procList.size() + " procedures");
  }

  public synchronized void addTable(TableIdentifier table, WbConnection con)
  {
    if (table == null) return;
    if (findInCache(table) == null)
    {
      this.objects.put(table, null);
    }
  }

  public synchronized void addTable(TableDefinition definition, WbConnection conn)
  {
    if (definition == null) return;
    this.objects.put(definition.getTable(), definition.getColumns());
  }

  /**
   * Return the stored key according to the passed TableIdentifier.
   *
   * The stored key might carry additional properties that the passed key does not have
   * (even though they are equal)
   */
  synchronized TableIdentifier findEntry(WbConnection con, TableIdentifier toSearch)
  {
    if (toSearch == null) return null;

    String schemaCat = supportsSchemas ? toSearch.getSchema() : toSearch.getCatalog();

    if (schemaCat == null)
    {
      TableIdentifier key = toSearch.createCopy();
      key.adjustCase(con);

      String schema = con.getCurrentSchema();
      List<Namespace> schemas = getSearchPath(con, new Namespace(schema, null));

      for (Namespace nsp : schemas)
      {
        if (nsp != null)
        {
          key.setCatalog(nsp.getCatalog());
          key.setSchema(nsp.getSchema());
        }
        TableIdentifier tbl = findInCache(key);
        if (tbl != null)
        {
          return tbl;
        }
      }
    }
    return findInCache(toSearch);
  }

  PkDefinition getPrimaryKey(WbConnection con, TableIdentifier table)
  {
    synchronized (pkMap)
    {
      // Prefer the column definitions in the regular cache
      // over the plain PK
      PkDefinition pk = getPkFromTableCache(con, table);

      if (pk == null)
      {
        // No column definitions in the cache, check the PK cache
        TableIdentifier tbl = findInCache(table, pkMap.keySet());
        if (tbl != null)
        {
          pk = pkMap.get(table);
        }
      }
      if (pk == null)
      {
        pk = con.getMetadata().getIndexReader().getPrimaryKey(table);
        pkMap.put(table, pk);
      }
      return pk;
    }
  }

  private PkDefinition getPkFromTableCache(WbConnection con, TableIdentifier table)
  {
    TableIdentifier toSearch = findEntry(con, table);

    if (toSearch == null) return null;

    List<ColumnIdentifier> columns = this.objects.get(toSearch);
    if (CollectionUtil.isEmpty(columns)) return null;

    List<String> pkCols = new ArrayList<>(1);
    for (ColumnIdentifier col : columns)
    {
      if (col.isPkColumn())
      {
        pkCols.add(col.getColumnName());
      }
    }
    return new PkDefinition(pkCols);
  }

  List<IndexDefinition> getUniqueIndexes(WbConnection con, TableIdentifier table)
  {
    synchronized (indexMap)
    {
      TableIdentifier tbl = findInCache(table, indexMap.keySet());
      List<IndexDefinition> indexes = null;
      if (tbl != null)
      {
        indexes = indexMap.get(tbl);
      }

      if (indexes  == null)
      {
        IndexReader reader = ReaderFactory.getIndexReader(con.getMetadata());
        indexes = reader.getUniqueIndexes(table);
        if (indexes == null) indexes = new ArrayList<>(0);
        indexMap.put(table, indexes);
      }
      return indexes;
    }
  }

  void flushCachedDatabase()
  {
    synchronized (this)
    {
      databasesCached = false;
      availableDatabases.clear();
    }
  }

  List<String> getAvailableDatabases(WbConnection conn)
  {
    synchronized (this)
    {
      if (databasesCached)
      {
        return new ArrayList<>(this.availableDatabases);
      }

      if (conn.isBusy())
      {
        LogMgr.logWarning(new CallerInfo(){}, "Connection is marked as busy!", new Exception("Backtrace"));
      }

      DbSwitcher switcher = DbSwitcher.Factory.createDatabaseSwitcher(conn);
      if (switcher == null) return null;

      this.availableDatabases.clear();

      try
      {
        List<String> dbs = switcher.getAvailableDatabases(conn);
        if (dbs != null)
        {
          this.availableDatabases.addAll(dbs);
        }
        this.databasesCached = true;
      }
      catch (Exception e)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Could not retrieve available databases", e);
        this.databasesCached = false;
      }
      return new ArrayList<>(this.availableDatabases);
    }
  }

  private TableIdentifier findInCache(TableIdentifier toSearch)
  {
    return findInCache(toSearch, objects.keySet());
  }

  private TableIdentifier findInCache(TableIdentifier toSearch, Set<TableIdentifier> keys)
  {
    if (toSearch == null) return null;

    for (TableIdentifier key : keys)
    {
      if (toSearch.compareNames(key)) return key;
    }
    return null;
  }

  /**
   * Disposes any db objects held in the cache.
   */
  public void clear()
  {
    objects.clear();
    schemasInCache.clear();
    referencedTables.clear();
    referencingTables.clear();
    procedureCache.clear();
    synonymMap.clear();
    availableDatabases.clear();
    databasesCached = false;
    LogMgr.logDebug(new CallerInfo(){}, "Removed all entries from the cache");
  }

  Collection<String> getSchemasInCache()
  {
    return schemasInCache.stream().map(key -> key.toString()).collect(Collectors.toList());
  }

  Collection<Namespace> getNamespacesInCache()
  {
    return Collections.unmodifiableSet(schemasInCache);
  }

  Map<TableIdentifier, List<DependencyNode>> getReferencedTables()
  {
    return Collections.unmodifiableMap(referencedTables);
  }

  Map<TableIdentifier, List<DependencyNode>> getReferencingTables()
  {
    return Collections.unmodifiableMap(referencingTables);
  }

  Map<TableIdentifier, List<ColumnIdentifier>> getObjects()
  {
    return Collections.unmodifiableMap(objects);
  }

  public Map<TableIdentifier, TableIdentifier> getSynonyms()
  {
    return Collections.unmodifiableMap(synonymMap);
  }

  public Map<TableIdentifier, PkDefinition> getPKMap()
  {
    return Collections.unmodifiableMap(pkMap);
  }

  public Map<TableIdentifier, List<IndexDefinition>> getIndexes()
  {
    return Collections.unmodifiableMap(indexMap);
  }

  void initExternally(
    Map<TableIdentifier, List<ColumnIdentifier>> newObjects, Collection<Namespace> schemas,
    Map<TableIdentifier, List<DependencyNode>> referencedTables,
    Map<TableIdentifier, List<DependencyNode>> referencingTables,
    Map<Namespace, List<ProcedureDefinition>> procs,
    Map<TableIdentifier, TableIdentifier> synonyms,
    Map<TableIdentifier, List<IndexDefinition>> indexes,
    Map<TableIdentifier, PkDefinition> pk)
  {
    if (newObjects == null || schemas == null) return;

    clear();

    objects.putAll(newObjects);
    schemasInCache.addAll(schemas);

    int refCount = 0;
    if (referencedTables != null)
    {
      this.referencedTables.putAll(referencedTables);
      refCount += referencedTables.size();
    }

    if (referencingTables != null)
    {
      this.referencingTables.putAll(referencingTables);
      refCount += referencingTables.size();
    }
    if (procs != null)
    {
      this.procedureCache.putAll(procs);
    }
    if (synonyms != null)
    {
      synonymMap.putAll(synonyms);
    }
    if (indexes != null)
    {
      indexMap.putAll(indexes);
    }
    if (pk != null)
    {
      pkMap.putAll(pk);
    }

    LogMgr.logDebug(new CallerInfo(){},
        "Added " + objects.size() + " objects, " +
        procedureCache.values().size() + " procedures, " +
        synonymMap.size() + " synonyms and " + refCount + " foreign key definitions from local storage");
  }
}
