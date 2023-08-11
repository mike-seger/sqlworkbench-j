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
package workbench.db.firebird;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.GenerationOptions;
import workbench.db.JdbcUtils;
import workbench.db.SequenceDefinition;
import workbench.db.SequenceReader;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * SequenceReader for Firebird
 *
 * @author  Thomas Kellerer
 */
public class FirebirdSequenceReader
  implements SequenceReader
{
  private WbConnection dbConnection;

  public FirebirdSequenceReader(WbConnection conn)
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
  public List<SequenceDefinition> getSequences(String catalog, String owner, String namePattern)
  {
    DataStore ds = getRawSequenceDefinition(catalog, owner, namePattern);
    if (ds == null) return Collections.emptyList();
    List<SequenceDefinition> result = new ArrayList<>(ds.getRowCount());

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

    String name = ds.getValueAsString(row, 0);
    result = new SequenceDefinition(null, name);
    int start = ds.getValueAsInt(row, 1, 0);
    int increment = ds.getValueAsInt(row, 2, 1);
    String comment = ds.getValueAsString(row, 3);
    result.setComment(comment);
    result.setSequenceProperty(PROP_START_VALUE, start);
    result.setSequenceProperty(PROP_INCREMENT, increment);
    generateSource(result);

    return result;
  }

  private void generateSource(SequenceDefinition def)
  {
    if (def == null) return;

    StringBuilder result = new StringBuilder(100);

    result.append("CREATE SEQUENCE ");
    result.append(def.getSequenceName());

    Integer start = (Integer)def.getSequenceProperty(PROP_START_VALUE);
    if (start != null && start.intValue() > 1)
    {
      result.append(" START WITH ");
      result.append(start);
    }
    Integer increment = (Integer)def.getSequenceProperty(PROP_INCREMENT);
    if (increment != null && increment.intValue() > 1)
    {
      result.append(" INCREMENT BY ");
      result.append(increment);
    }

    result.append(";\n");

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
  public DataStore getRawSequenceDefinition(String catalog, String owner, String sequence)
  {
    Statement stmt = null;
    ResultSet rs = null;
    DataStore ds = null;

    StringBuilder sql = new StringBuilder(100);
    sql.append(
      "SELECT trim(rdb$generator_name) AS SEQUENCE_NAME, \n" +
      "       rdb$initial_value as START_VALUE, \n " +
      "       rdb$generator_increment as INCREMENT, \n" +
      "       trim(rdb$description) AS REMARKS \n" +
      "FROM rdb$generators \n" +
      "WHERE (rdb$system_flag = 0 OR rdb$system_flag IS NULL) \n");

    SqlUtil.appendAndCondition(sql, "rdb$generator_name", sequence, dbConnection);
    sql.append("\n ORDER BY 1");

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
