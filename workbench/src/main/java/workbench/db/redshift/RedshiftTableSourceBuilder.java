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
package workbench.db.redshift;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbSettings;
import workbench.db.DropType;
import workbench.db.IndexDefinition;
import workbench.db.JdbcUtils;
import workbench.db.TableGrantReader;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/*
 * @author  Miguel Cornejo Silva
 */
public class RedshiftTableSourceBuilder
  extends TableSourceBuilder
{

  public RedshiftTableSourceBuilder(WbConnection con)
  {
    super(con);
  }

  @Override
  public String getTableSource(TableIdentifier table, DropType drop, boolean includeFk, boolean includeGrants)
    throws SQLException
  {
    if ("TABLE".equals(table.getType()))
    {
      String sql = getBaseTableSource(table, drop != DropType.none);
      if (sql != null) return sql;
    }
    return super.getTableSource(table, drop, includeFk, includeGrants);
  }

  private String getBaseTableSource(TableIdentifier table, boolean includeDrop)
    throws SQLException
  {
    String sql =
      "SELECT DDL FROM (\n" +
      "  SELECT\n" +
      "   table_id\n" +
      "   ,REGEXP_REPLACE (schemaname, '^zzzzzzzz', '') AS schemaname\n" +
      "   ,REGEXP_REPLACE (tablename, '^zzzzzzzz', '') AS tablename\n" +
      "   ,seq\n" +
      "   ,ddl\n" +
      "  FROM\n" +
      "   (\n" +
      "   SELECT\n" +
      "    table_id\n" +
      "    ,schemaname\n" +
      "    ,tablename\n" +
      "    ,seq\n" +
      "    ,ddl\n" +
      "   FROM\n" +
      "    (\n" +
      "    SELECT";
    if (includeDrop)
    {
      sql +=
        "    --DROP TABLE\n" +
        "     c.oid::bigint as table_id\n" +
        "     ,n.nspname AS schemaname\n" +
        "     ,c.relname AS tablename\n" +
        "     ,0 AS seq\n" +
        "     ,'DROP TABLE ' + QUOTE_IDENT(n.nspname) + '.' + QUOTE_IDENT(c.relname) + ';\\n\\n' AS ddl\n" +
        "    FROM pg_namespace AS n\n" +
        "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
        "    WHERE c.relkind = 'r'\n" +
        "    UNION SELECT";
    }
    sql +=
      "    --CREATE TABLE\n" +
      "     c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "     ,c.relname AS tablename\n" +
      "     ,2 AS seq\n" +
      "     ,'CREATE TABLE IF NOT EXISTS ' + QUOTE_IDENT(n.nspname) + '.' + QUOTE_IDENT(c.relname) + '\\n' AS ddl\n" +
      "    FROM pg_namespace AS n\n" +
      "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "    WHERE c.relkind = 'r'\n" +
      "    --OPEN PAREN COLUMN LIST\n" +
      "    UNION SELECT c.oid::bigint as table_id,n.nspname AS schemaname, c.relname AS tablename, 5 AS seq, '(\\n' AS ddl\n" +
      "    FROM pg_namespace AS n\n" +
      "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "    WHERE c.relkind = 'r'\n" +
      "    --COLUMN LIST\n" +
      "    UNION SELECT\n" +
      "     table_id\n" +
      "     ,schemaname\n" +
      "     ,tablename\n" +
      "     ,seq\n" +
      "     ,'\\t' + col_delim + col_name + ' ' + col_datatype + ' ' + col_nullable + ' ' + col_default + ' ' + col_encoding + '\\n' AS ddl\n" +
      "    FROM\n" +
      "     (\n" +
      "     SELECT\n" +
      "      c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "      ,c.relname AS tablename\n" +
      "      ,100000000 + a.attnum AS seq\n" +
      "      ,CASE WHEN a.attnum > 1 THEN ',' ELSE '' END AS col_delim\n" +
      "      ,QUOTE_IDENT(a.attname) AS col_name\n" +
      "      ,CASE WHEN STRPOS(UPPER(format_type(a.atttypid, a.atttypmod)), 'CHARACTER VARYING') > 0\n" +
      "        THEN REPLACE(UPPER(format_type(a.atttypid, a.atttypmod)), 'CHARACTER VARYING', 'VARCHAR')\n" +
      "       WHEN STRPOS(UPPER(format_type(a.atttypid, a.atttypmod)), 'CHARACTER') > 0\n" +
      "        THEN REPLACE(UPPER(format_type(a.atttypid, a.atttypmod)), 'CHARACTER', 'CHAR')\n" +
      "       ELSE UPPER(format_type(a.atttypid, a.atttypmod))\n" +
      "       END AS col_datatype\n" +
      "      ,CASE WHEN format_encoding((a.attencodingtype)::integer) = 'none'\n" +
      "       THEN ''\n" +
      "       ELSE 'ENCODE ' + format_encoding((a.attencodingtype)::integer)\n" +
      "       END AS col_encoding\n" +
      "      ,CASE WHEN a.atthasdef IS TRUE THEN 'DEFAULT ' + adef.adsrc ELSE '' END AS col_default\n" +
      "      ,CASE WHEN a.attnotnull IS TRUE THEN 'NOT NULL' ELSE '' END AS col_nullable\n" +
      "     FROM pg_namespace AS n\n" +
      "     INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "     INNER JOIN pg_attribute AS a ON c.oid = a.attrelid\n" +
      "     LEFT OUTER JOIN pg_attrdef AS adef ON a.attrelid = adef.adrelid AND a.attnum = adef.adnum\n" +
      "     WHERE c.relkind = 'r'\n" +
      "       AND a.attnum > 0\n" +
      "     ORDER BY a.attnum\n" +
      "     )\n" +
      "    --CONSTRAINT LIST\n" +
      "    UNION (SELECT\n" +
      "     c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "     ,c.relname AS tablename\n" +
      "     ,200000000 + CAST(con.oid AS INT) AS seq\n" +
      "     ,'\\t,' + pg_get_constraintdef(con.oid) + '\\n' AS ddl\n" +
      "    FROM pg_constraint AS con\n" +
      "    INNER JOIN pg_class AS c ON c.relnamespace = con.connamespace AND c.oid = con.conrelid\n" +
      "    INNER JOIN pg_namespace AS n ON n.oid = c.relnamespace\n" +
      "    WHERE c.relkind = 'r' AND pg_get_constraintdef(con.oid) NOT LIKE 'FOREIGN KEY%'\n" +
      "    ORDER BY seq)\n" +
      "    --CLOSE PAREN COLUMN LIST\n" +
      "    UNION SELECT c.oid::bigint as table_id,n.nspname AS schemaname, c.relname AS tablename, 299999999 AS seq, ')\\n' AS ddl\n" +
      "    FROM pg_namespace AS n\n" +
      "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "    WHERE c.relkind = 'r'\n" +
      "    --BACKUP\n" +
      "    UNION SELECT\n" +
      "    c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "     ,c.relname AS tablename\n" +
      "     ,300000000 AS seq\n" +
      "     ,'BACKUP NO\\n' as ddl\n" +
      "  FROM pg_namespace AS n\n" +
      "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "    INNER JOIN (SELECT\n" +
      "      SPLIT_PART(key,'_',5) id\n" +
      "      FROM pg_conf\n" +
      "      WHERE key LIKE 'pg_class_backup_%'\n" +
      "      AND SPLIT_PART(key,'_',4) = (SELECT\n" +
      "        oid\n" +
      "        FROM pg_database\n" +
      "        WHERE datname = current_database())) t ON t.id=c.oid\n" +
      "    WHERE c.relkind = 'r'\n" +
      "    --BACKUP WARNING\n" +
      "    UNION SELECT\n" +
      "    c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "     ,c.relname AS tablename\n" +
      "     ,1 AS seq\n" +
      "     ,'--WARNING: This DDL inherited the BACKUP NO property from the source table\\n' as ddl\n" +
      "  FROM pg_namespace AS n\n" +
      "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "    INNER JOIN (SELECT\n" +
      "      SPLIT_PART(key,'_',5) id\n" +
      "      FROM pg_conf\n" +
      "      WHERE key LIKE 'pg_class_backup_%'\n" +
      "      AND SPLIT_PART(key,'_',4) = (SELECT\n" +
      "        oid\n" +
      "        FROM pg_database\n" +
      "        WHERE datname = current_database())) t ON t.id=c.oid\n" +
      "    WHERE c.relkind = 'r'\n" +
      "    --DISTSTYLE\n" +
      "    UNION SELECT\n" +
      "     c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "     ,c.relname AS tablename\n" +
      "     ,300000001 AS seq\n" +
      "     ,CASE WHEN c.reldiststyle = 0 THEN 'DISTSTYLE EVEN'\n" +
      "      WHEN c.reldiststyle = 1 THEN 'DISTSTYLE KEY'\n" +
      "      WHEN c.reldiststyle = 8 THEN 'DISTSTYLE ALL'\n" +
      "      ELSE '<<Error - UNKNOWN DISTSTYLE>>'\n" +
      "      END + '\\n' AS ddl\n" +
      "    FROM pg_namespace AS n\n" +
      "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "    WHERE c.relkind = 'r'\n" +
      "    --DISTKEY COLUMNS\n" +
      "    UNION SELECT\n" +
      "     c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "     ,c.relname AS tablename\n" +
      "     ,400000000 + a.attnum AS seq\n" +
      "     ,'DISTKEY (' + QUOTE_IDENT(a.attname) + ')\\n' AS ddl\n" +
      "    FROM pg_namespace AS n\n" +
      "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "    INNER JOIN pg_attribute AS a ON c.oid = a.attrelid\n" +
      "    WHERE c.relkind = 'r'\n" +
      "      AND a.attisdistkey IS TRUE\n" +
      "      AND a.attnum > 0\n" +
      "    --SORTKEY COLUMNS\n" +
      "    UNION select table_id,schemaname, tablename, seq,\n" +
      "         case when min_sort <0 then 'INTERLEAVED SORTKEY (' else 'SORTKEY (' end as ddl\n" +
      "  from (SELECT\n" +
      "     c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "     ,c.relname AS tablename\n" +
      "     ,499999999 AS seq\n" +
      "     ,min(attsortkeyord) min_sort FROM pg_namespace AS n\n" +
      "    INNER JOIN  pg_class AS c ON n.oid = c.relnamespace\n" +
      "    INNER JOIN pg_attribute AS a ON c.oid = a.attrelid\n" +
      "    WHERE c.relkind = 'r'\n" +
      "    AND abs(a.attsortkeyord) > 0\n" +
      "    AND a.attnum > 0\n" +
      "    group by 1,2,3,4 )\n" +
      "    UNION (SELECT\n" +
      "     c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "     ,c.relname AS tablename\n" +
      "     ,500000000 + abs(a.attsortkeyord) AS seq\n" +
      "     ,CASE WHEN abs(a.attsortkeyord) = 1\n" +
      "      THEN QUOTE_IDENT(a.attname)\n" +
      "      ELSE ', ' + QUOTE_IDENT(a.attname)\n" +
      "      END AS ddl\n" +
      "    FROM  pg_namespace AS n\n" +
      "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "    INNER JOIN pg_attribute AS a ON c.oid = a.attrelid\n" +
      "    WHERE c.relkind = 'r'\n" +
      "      AND abs(a.attsortkeyord) > 0\n" +
      "      AND a.attnum > 0\n" +
      "    ORDER BY abs(a.attsortkeyord))\n" +
      "    UNION SELECT\n" +
      "     c.oid::bigint as table_id\n" +
      "     ,n.nspname AS schemaname\n" +
      "     ,c.relname AS tablename\n" +
      "     ,599999999 AS seq\n" +
      "     ,')\\n' AS ddl\n" +
      "    FROM pg_namespace AS n\n" +
      "    INNER JOIN  pg_class AS c ON n.oid = c.relnamespace\n" +
      "    INNER JOIN  pg_attribute AS a ON c.oid = a.attrelid\n" +
      "    WHERE c.relkind = 'r'\n" +
      "      AND abs(a.attsortkeyord) > 0\n" +
      "      AND a.attnum > 0\n" +
      "    --END SEMICOLON\n" +
      "    UNION SELECT c.oid::bigint as table_id ,n.nspname AS schemaname, c.relname AS tablename, 600000000 AS seq, ';\\n' AS ddl\n" +
      "    FROM  pg_namespace AS n\n" +
      "    INNER JOIN pg_class AS c ON n.oid = c.relnamespace\n" +
      "    WHERE c.relkind = 'r' )\n" +
      "    UNION (\n" +
      "      SELECT c.oid::bigint as table_id,'zzzzzzzz' || n.nspname AS schemaname,\n" +
      "         'zzzzzzzz' || c.relname AS tablename,\n" +
      "         700000000 + CAST(con.oid AS INT) AS seq,\n" +
      "         'ALTER TABLE ' + QUOTE_IDENT(n.nspname) + '.' + QUOTE_IDENT(c.relname) + ' ADD ' + pg_get_constraintdef(con.oid)::VARCHAR(1024) + ';' AS ddl\n" +
      "      FROM pg_constraint AS con\n" +
      "        INNER JOIN pg_class AS c\n" +
      "               ON c.relnamespace = con.connamespace\n" +
      "               AND c.oid = con.conrelid\n" +
      "        INNER JOIN pg_namespace AS n ON n.oid = c.relnamespace\n" +
      "      WHERE c.relkind = 'r'\n" +
      "      AND con.contype = 'f'\n" +
      "      ORDER BY seq\n" +
      "    )\n" +
      "   ORDER BY table_id,schemaname, tablename, seq\n" +
      "   )\n" +
      " ) X WHERE schemaname = ? and tablename = ? \n" +
      " order by schemaname, tablename, seq";

    StringBuilder createSql = new StringBuilder(100);

    LogMgr.logMetadataSql(new CallerInfo(){}, "table source", sql, table.getSchema(), table.getTableName());

    PreparedStatement stmt = null;
    ResultSet rs = null;

    try
    {
      stmt = dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, table.getSchema());
      stmt.setString(2, table.getTableName());
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String create = rs.getString(1);
        createSql.append(create);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "table source", sql, table.getSchema(), table.getTableName());
      return null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    TableGrantReader grantReader = TableGrantReader.createReader(dbConnection);
    StringBuilder grants = grantReader.getTableGrantSource(this.dbConnection, table);
    if (StringUtil.isNotBlank(grants))
    {
      createSql.append("\n");
      createSql.append(grants);
    }

    return createSql.toString();
  }

  @Override
  public String getAdditionalTableInfo(TableIdentifier table, List<ColumnIdentifier> columns,
                                       List<IndexDefinition> indexList)
  {
    CharSequence sequences = getColumnSequenceInformation(table, columns);
    String owner = getOwnerSql(table);

    if (StringUtil.allEmpty(sequences, owner))
      return null;

    StringBuilder result = new StringBuilder(200);

    if (sequences != null) result.append(sequences);
    if (owner != null) result.append(owner);

    return result.toString();
  }

  private String getOwnerSql(TableIdentifier table)
  {
    try
    {
      DbSettings.GenerateOwnerType genType = dbConnection.getDbSettings().getGenerateTableOwner();
      if (genType == DbSettings.GenerateOwnerType.never) return null;

      String owner = table.getOwner();
      if (StringUtil.isBlank(owner)) return null;

      if (genType == DbSettings.GenerateOwnerType.whenNeeded)
      {
        String user = dbConnection.getCurrentUser();
        if (user.equalsIgnoreCase(owner)) return null;
      }

      return "\nALTER TABLE " + table.getFullyQualifiedName(dbConnection) + " OWNER TO " +
        SqlUtil.quoteObjectname(owner) + ";";
    }
    catch (Exception ex)
    {
      return null;
    }
  }

  private CharSequence getColumnSequenceInformation(TableIdentifier table, List<ColumnIdentifier> columns)
  {
    if (!JdbcUtils.hasMinimumServerVersion(this.dbConnection, "8.4")) return null;
    if (table == null) return null;
    if (CollectionUtil.isEmpty(columns)) return null;

    String tblname = table.getTableExpression(dbConnection);
    ResultSet rs = null;
    Statement stmt = null;
    StringBuilder b = new StringBuilder(100);

    Savepoint sp = null;
    String sql = null;
    try
    {
      sp = dbConnection.setSavepoint();
      stmt = dbConnection.createStatementForQuery();
      for (ColumnIdentifier col : columns)
      {
        String defaultValue = col.getDefaultValue();
        // if the default value is shown as nextval, the sequence name is already
        // visible
        if (defaultValue != null && defaultValue.toLowerCase().contains("nextval"))
          continue;

        String colname = StringUtil.trimQuotes(col.getColumnName());
        sql = "select pg_get_serial_sequence('" + tblname + "', '" + colname + "')";
        rs = stmt.executeQuery(sql);
        if (rs.next())
        {
          String seq = rs.getString(1);
          if (StringUtil.isNotBlank(seq))
          {
            String msg = ResourceMgr.getFormattedString("TxtSequenceCol", col.getColumnName(), seq);
            b.append("\n-- ");
            b.append(msg);
          }
        }
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      dbConnection.rollback(sp);
      LogMgr.logWarning(new CallerInfo(){}, "Error reading sequence information using: " + sql, e);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (b.length() == 0) return null;
    return b;
  }

}
