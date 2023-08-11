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
package workbench.db.h2database;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.GenerationOptions;
import workbench.db.JdbcUtils;
import workbench.db.SequenceDefinition;
import workbench.db.SequenceReader;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * SequenceReader for <a href="https://www.h2database.com">H2 Database</a>
 *
 * @author  Thomas Kellerer
 */
public class H2SequenceReader
  implements SequenceReader
{
  private WbConnection dbConnection;

  public H2SequenceReader(WbConnection conn)
  {
    this.dbConnection = conn;
  }

  /**
   *  Return the source SQL for a H2 sequence definition.
   *
   *  @return The SQL to recreate the given sequence
   */
  @Override
  public CharSequence getSequenceSource(String catalog, String owner, String aSequence, GenerationOptions options)
  {
    SequenceDefinition def = getSequenceDefinition(catalog, owner, aSequence);
    if (def == null) return "";
    return def.getSource();
  }

  @Override
  public List<SequenceDefinition> getSequences(String catalog, String schema, String namePattern)
  {
    DataStore ds = getRawSequenceDefinition(catalog, schema, namePattern);
    if (ds == null) return Collections.emptyList();
    List<SequenceDefinition> result = new ArrayList<>();

    for (int row=0; row < ds.getRowCount(); row++)
    {
      result.add(createSequenceDefinition(ds, row));
    }
    return result;
  }

  @Override
  public SequenceDefinition getSequenceDefinition(String catalog, String owner, String sequence)
  {
    DataStore ds = getRawSequenceDefinition(catalog, owner, sequence);
    if (ds == null || ds.getRowCount() == 0) return null;

    return createSequenceDefinition(ds, 0);
  }

  private SequenceDefinition createSequenceDefinition(DataStore ds, int row)
  {
    SequenceDefinition result = null;

    if (ds == null || ds.getRowCount() == 0) return null;

    boolean is20 = JdbcUtils.hasMinimumServerVersion(dbConnection, "2.0");
    String name = ds.getValueAsString(row, "SEQUENCE_NAME");
    String schema = ds.getValueAsString(row, "SEQUENCE_SCHEMA");
    result = new SequenceDefinition(schema, name);

    if (is20)
    {
      result.setSequenceProperty(PROP_CURRENT_VALUE, ds.getValue(row, "BASE_VALUE"));
    }
    else
    {
      result.setSequenceProperty(PROP_CURRENT_VALUE, ds.getValue(row, "CURRENT_VALUE"));
    }
    result.setSequenceProperty(PROP_INCREMENT, ds.getValue(row, "INCREMENT"));
    result.setSequenceProperty(PROP_CACHE_SIZE, ds.getValue(row, "CACHE"));
    result.setSequenceProperty(PROP_CYCLE, ds.getValue(row, "CYCLE_OPTION"));

    String comment = ds.getValueAsString(row, "REMARKS");
    result.setComment(comment);
    generateSource(result);

    return result;
  }

  private void generateSource(SequenceDefinition def)
  {
    if (def == null) return;

    StringBuilder result = new StringBuilder(100);
    String nl = Settings.getInstance().getInternalEditorLineEnding();

    result.append("CREATE SEQUENCE ");
    result.append(def.getSequenceName());

    Long inc = (Long)def.getSequenceProperty(PROP_INCREMENT);
    if (inc != null && inc != 1)
    {
      result.append("\n       INCREMENT BY ");
      result.append(inc);
    }

    result.append("\n       CACHE ");
    result.append(def.getSequenceProperty(PROP_CACHE_SIZE).toString());

    String cycle = def.getSequenceProperty(PROP_CYCLE).toString();
    if ("YES".equals(cycle))
    {
      result.append("\n      CYCLE");
    }

    result.append(';');
    result.append(nl);

    String comments = def.getComment();
    if (StringUtil.isNotBlank(comments))
    {
      result.append("\nCOMMENT ON SEQUENCE ");
      result.append(def.getSequenceName());
      result.append(" IS '");
      result.append(comments.replace("'", "''"));
      result.append("';");
    }

    def.setSource(result);
  }

  @Override
  public DataStore getRawSequenceDefinition(String catalog, String schema, String sequence)
  {
    Statement stmt = null;
    ResultSet rs = null;
    DataStore ds = null;

    StringBuilder sql = new StringBuilder(100);
    boolean is20 = JdbcUtils.hasMinimumServerVersion(dbConnection, "2.0");

    if (is20)
    {
      sql.append(
        "SELECT SEQUENCE_CATALOG, \n" +
        "       SEQUENCE_SCHEMA, \n" +
        "       SEQUENCE_NAME, \n" +
        "       BASE_VALUE, \n" +
        "       INCREMENT, \n" +
        "       REMARKS, \n" +
        "       CACHE, \n" +
        "       cycle_option \n" +
        "FROM information_schema.sequences ");
    }
    else
    {
      sql.append(
        "SELECT SEQUENCE_CATALOG, \n" +
        "       SEQUENCE_SCHEMA, \n" +
        "       SEQUENCE_NAME, \n" +
        "       CURRENT_VALUE, \n" +
        "       INCREMENT, \n" +
        "       REMARKS, \n" +
        "       CACHE, \n" +
        "       case when is_cycle then 'YES' else 'NO' end as cycle_option \n" +
        "FROM information_schema.sequences ");
    }

    boolean whereAdded = false;

    if (StringUtil.isNotBlank(schema))
    {
      if (!whereAdded)
      {
        sql.append("\nWHERE ");
        whereAdded = true;
      }
      else
      {
        sql.append("\n  AND ");
      }
      sql.append(" sequence_schema = '" + schema + "'");
    }

    if (StringUtil.isNotBlank(sequence))
    {
      if (!whereAdded)
      {
        sql.append("\nWHERE ");
        whereAdded = true;
      }
      else
      {
        sql.append("\n  AND ");
      }
      SqlUtil.appendExpression(sql, "sequence_name", sequence, dbConnection);
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "sequence definition", sql);

    try
    {
      stmt = this.dbConnection.createStatement();
      rs = stmt.executeQuery(sql.toString());
      ds = new DataStore(rs, true);
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "sequence definition", sql);
      ds = null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return ds;
  }

  @Override
  public String getSequenceTypeName()
  {
    return SequenceReader.DEFAULT_TYPE_NAME;
  }
}
