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
package workbench.db.objectcache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbStringTokenizer;

/**
 *
 * @author Thomas Kellerer
 */
public class Namespace
  implements Serializable
{
  public final static Namespace NULL_NSP = new Namespace(null, null);
  private String schema;
  private String catalog;
  private String expression;

  public Namespace(String schema, String catalog)
  {
    this.schema = StringUtil.trimToNull(schema);
    this.catalog = StringUtil.trimToNull(catalog);
    this.expression = buildExpression();
  }

  public Namespace(String schemaAndCatalog)
  {
    init(schemaAndCatalog, '.', true, true);
  }

  public Namespace(String schemaAndCatalog, char catalogSeparator, boolean supportsSchemas, boolean supportsCatalogs)
  {
    init(schemaAndCatalog, catalogSeparator, supportsSchemas, supportsCatalogs);
  }

  private void init(String schemaAndCatalog, char catalogSeparator, boolean supportsSchemas, boolean supportsCatalogs)
  {
    schemaAndCatalog = StringUtil.trimToNull(StringUtil.removeTrailing(schemaAndCatalog, catalogSeparator));
    if (schemaAndCatalog == null)
    {
      this.schema = null;
      this.catalog = null;
      this.expression = "";
    }
    else
    {
      WbStringTokenizer tok = new WbStringTokenizer(catalogSeparator, "\"", true);
      tok.setSourceString(schemaAndCatalog);
      List<String> elements = tok.getAllTokens();
      if (elements.size() >= 2)
      {
        if (supportsSchemas && supportsCatalogs)
        {
          this.catalog = elements.get(0).trim();
          this.schema = elements.get(1).trim();
        }
        else if (supportsSchemas && !supportsCatalogs)
        {
          this.schema = elements.get(0).trim();
          this.catalog = null;
        }
        else if (!supportsSchemas && supportsCatalogs)
        {
          this.catalog = elements.get(0).trim();
          this.schema = null;
        }
      }
      else
      {
        if (supportsSchemas)
        {
          this.catalog = null;
          this.schema = schemaAndCatalog;
        }
        else
        {
          this.catalog = schemaAndCatalog;
          this.schema = null;
        }
      }
      this.expression = buildExpression();
    }
  }

  public void adjustCase(WbConnection conn)
  {
    if (conn == null) return;
    schema = conn.getMetadata().adjustSchemaNameCase(schema);
    catalog = conn.getMetadata().adjustSchemaNameCase(catalog);
    expression = buildExpression();
  }

  public void setNamespace(TableIdentifier tbl)
  {
    if (tbl.getSchema() == null)
    {
      tbl.setSchema(this.schema);
    }
    if (tbl.getCatalog() == null)
    {
      tbl.setCatalog(this.catalog);
    }
  }

  public void removeNamespaceIfEqual(TableIdentifier tbl)
  {
    if (tbl == null) return;
    if (StringUtil.equalStringIgnoreCase(this.schema, tbl.getRawSchema()))
    {
      tbl.setSchema(null);
    }
    if (StringUtil.equalStringIgnoreCase(this.catalog, tbl.getRawCatalog()))
    {
      tbl.setCatalog(null);
    }
  }

  public boolean isValid()
  {
    return catalog != null || schema != null;
  }

  public boolean hasCatalogAndSchema()
  {
    return this.schema != null && this.catalog != null;
  }

  public static Namespace getCurrentNamespace(WbConnection conn)
  {
    if (conn == null || conn.getDbSettings() == null) return NULL_NSP;
    return new Namespace(conn.getCurrentSchema(), conn.getCurrentCatalog());
  }


  public static Namespace fromCatalogAndSchema(WbConnection conn, String catalog, String schema)
  {
    if (conn == null)
    {
      return new Namespace(schema, catalog);
    }
    boolean supportsSchemas = conn.getDbSettings().supportsSchemas();
    boolean supportsCatalogs = conn.getDbSettings().supportsCatalogs();

    if (supportsSchemas && !supportsCatalogs)
    {
      return new Namespace(schema, null);
    }
    if (supportsCatalogs && !supportsSchemas)
    {
      return new Namespace(null, catalog);
    }
    return new Namespace(schema, catalog);
  }

  public static Namespace fromSchemaName(WbConnection conn, String catalogOrSchema)
  {
    if (conn == null)
    {
      return new Namespace(catalogOrSchema, '.', true, true);
    }
    boolean supportsSchemas = conn.getDbSettings().supportsSchemas();
    boolean supportsCatalogs = conn.getDbSettings().supportsCatalogs();

    if (supportsSchemas && !supportsCatalogs)
    {
      return new Namespace(catalogOrSchema, null);
    }
    if (supportsCatalogs && !supportsSchemas)
    {
      return new Namespace(null, catalogOrSchema);
    }
    char separator = SqlUtil.getCatalogSeparator(conn);
    return new Namespace(catalogOrSchema, separator, supportsSchemas, supportsCatalogs);
  }

  public static Namespace fromExpression(WbConnection conn, String catalogAndSchema)
  {
    if (conn == null)
    {
      return new Namespace(catalogAndSchema, '.', true, true);
    }
    boolean supportsSchemas = conn.getDbSettings().supportsSchemas();
    boolean supportsCatalogs = conn.getDbSettings().supportsCatalogs();
    char separator = SqlUtil.getCatalogSeparator(conn);
    return new Namespace(catalogAndSchema, separator, supportsSchemas, supportsCatalogs);
  }

  public static Namespace fromTable(TableIdentifier tbl, WbConnection conn)
  {
    if (tbl == null) return null;
    if (conn == null)
    {
      return new Namespace(tbl.getSchema(), tbl.getCatalog());
    }
    boolean supportsSchemas = conn.getDbSettings().supportsSchemas();
    boolean supportsCatalogs = conn.getDbSettings().supportsCatalogs();
    if (supportsSchemas && !supportsCatalogs)
    {
      return new Namespace(tbl.getSchema(), null);
    }
    if (!supportsSchemas && supportsCatalogs)
    {
      return new Namespace(null, tbl.getCatalog());
    }
    return new Namespace(tbl.getSchema(), tbl.getCatalog());
  }

  public String getSchema()
  {
    return schema;
  }

  public String getCatalog()
  {
    return catalog;
  }

  @Override
  public String toString()
  {
    return expression;
  }

  public String buildExpression()
  {
    if (catalog == null && schema == null)
    {
      return "";
    }
    if (catalog == null && schema != null)
    {
      return schema;
    }
    if (catalog != null && schema == null)
    {
      return catalog;
    }
    return catalog + "." + schema;
  }

  @Override
  public int hashCode()
  {
    int hash = 5;
    hash = 19 * hash + Objects.hashCode(this.schema);
    hash = 19 * hash + Objects.hashCode(this.catalog);
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;

    Namespace other = (Namespace)obj;
    if (!SqlUtil.objectNamesAreEqual(this.schema, other.schema)) return false;
    if (!SqlUtil.objectNamesAreEqual(this.catalog, other.catalog)) return false;
    return true;
  }

  public static List<Namespace> convertCatalogList(Collection<String> catalogs)
  {
    return convertCatalogList(catalogs, null);
  }

  public static List<Namespace> convertCatalogList(Collection<String> catalogs, WbConnection conn)
  {
    List<Namespace> result = new ArrayList<>();
    if (catalogs == null) return result;
    for (String catalog : catalogs)
    {
      if (conn != null)
      {
        catalog = conn.getMetadata().adjustSchemaNameCase(catalog);
      }
      result.add(new Namespace(null, catalog));
    }
    return result;
  }

  public static List<Namespace> convertSchemaList(Collection<String> schemas)
  {
    return convertSchemaList(schemas, null);
  }
  public static List<Namespace> convertSchemaList(Collection<String> schemas, WbConnection conn)
  {
    List<Namespace> result = new ArrayList<>();
    if (schemas == null) return result;
    for (String schema : schemas)
    {
      if (conn != null)
      {
        schema = conn.getMetadata().adjustSchemaNameCase(schema);
      }
      result.add(new Namespace(schema, null));
    }
    return result;
  }

}
