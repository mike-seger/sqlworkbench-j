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
package workbench.db.postgres;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbSettings;
import workbench.db.DependencyNode;
import workbench.db.DomainIdentifier;
import workbench.db.DropType;
import workbench.db.EnumIdentifier;
import workbench.db.IndexDefinition;
import workbench.db.JdbcUtils;
import workbench.db.ObjectSourceOptions;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresTableSourceBuilder
  extends TableSourceBuilder
{
  private boolean isPostgres10 = false;

  public static final String FORCE_RLS_OPTION = "FORCE_RLS";
  public static final String RLS_ENABLED_OPTION = "RLS_ENABLED";
  public static final String REPLICA_IDENT_OPTION = "REPLICA_IDENTITY";
  public static final String REPLICA_INDEX = "REPLICA_INDEX";

  public PostgresTableSourceBuilder(WbConnection con)
  {
    super(con);
    isPostgres10 = JdbcUtils.hasMinimumServerVersion(con, "10.0");
  }

  protected CharSequence baseCreateTable(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList, List<DependencyNode> fkDefinitions, DropType dropType, boolean includeFk, boolean includePK, boolean useFQN)
  {
    return super.getCreateTable(table, columns, indexList, fkDefinitions, dropType, includeFk, includePK, useFQN);
  }

  @Override
  public CharSequence getCreateTable(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList, List<DependencyNode> fkDefinitions, DropType dropType, boolean includeFk, boolean includePK, boolean useFQN)
  {
    PostgresPartition partition = null;

    if (isPostgres10)
    {
      partition = PostgresPartitionReader.getPartitionDefinition(table, dbConnection);
    }

    if (partition == null)
    {
      return super.getCreateTable(table, columns, indexList, fkDefinitions, dropType, includeFk, includePK, useFQN);
    }

    StringBuilder ddl = new StringBuilder(500);
    ddl.append(generateDrop(table, dropType));
    ddl.append("\n\n");
    ddl.append(PostgresPartitionReader.generatePartitionDDL(partition, null, dbConnection));
    ddl.append(";\n\n");

    return ddl;
  }

  @Override
  public void readTableOptions(TableIdentifier table, List<ColumnIdentifier> columns)
  {
    ObjectSourceOptions option = table.getSourceOptions();
    if (option.isInitialized()) return;

    PostgresRuleReader ruleReader = new PostgresRuleReader();
    CharSequence rule = ruleReader.getTableRuleSource(dbConnection, table);
    if (rule != null)
    {
      option.setAdditionalSql(rule.toString());
    }

    if ("FOREIGN TABLE".equals(table.getType()))
    {
      readForeignTableOptions(table);
    }
    else
    {
      readTableOptions(table);
    }
    option.setInitialized();
  }

  private void readTableOptions(TableIdentifier tbl)
  {
    if (!JdbcUtils.hasMinimumServerVersion(dbConnection, "8.1")) return;

    ObjectSourceOptions option = tbl.getSourceOptions();
    StringBuilder inherit = readInherits(tbl);

    StringBuilder tableSql = new StringBuilder();

    String persistenceCol = null;
    if (JdbcUtils.hasMinimumServerVersion(dbConnection, "9.1"))
    {
      persistenceCol = "ct.relpersistence";
    }
    else if (JdbcUtils.hasMinimumServerVersion(dbConnection, "8.4"))
    {
      persistenceCol = "case when ct.relistemp then 't' else null::char end as relpersistence";
    }
    else
    {
      persistenceCol = "null::char as relpersistence";
    }

    String replicaCol;
    String replicaIndexCol;
    if (JdbcUtils.hasMinimumServerVersion(dbConnection, "9.4"))
    {
      replicaCol = "ct.relreplident";
      replicaIndexCol = "" +
        "case \n" +
        "          when ct.relreplident = 'i' " +
        "               then (select ix.indexrelid::regclass::text from pg_index ix where ix.indrelid = ct.oid and ix.indisreplident) \n" +
        "          else null \n" +
        "       end as replica_index";
    }
    else
    {
      replicaCol = "null as relreplident";
      replicaIndexCol = "null as replica_index";
    }

    String spcnameCol;
    String defaultTsCol;
    String defaultTsQuery;
    String rlsEnableCol;
    String rlsForceCol;
    boolean showNonStandardTablespace = dbConnection.getDbSettings().getBoolProperty("show.nonstandard.tablespace", true);

    boolean is95 = JdbcUtils.hasMinimumServerVersion(dbConnection, "9.5");

    if (JdbcUtils.hasMinimumServerVersion(dbConnection, "8.0"))
    {
      spcnameCol = "spc.spcname";
      defaultTsCol = "ts.default_tablespace";
      defaultTsQuery =
      "  cross join (\n" +
      "    select ts.spcname as default_tablespace\n" +
      "    from pg_catalog.pg_database d\n" +
      "      join pg_catalog.pg_tablespace ts on ts.oid = d.dattablespace\n" +
      "    where d.datname = current_database()\n" +
      "  ) ts \n ";
    }
    else
    {
      spcnameCol = "null as spcname";
      defaultTsCol = "null as default_tablespace";
      defaultTsQuery = "";
      showNonStandardTablespace = false;
    }

    if (is95)
    {
      rlsEnableCol = "ct.relrowsecurity";
      rlsForceCol = "ct.relforcerowsecurity";
    }
    else
    {
      rlsEnableCol = "false as relrowsecurity";
      rlsForceCol = "false as relforcerowsecurity";
    }
    PreparedStatement pstmt = null;
    ResultSet rs = null;

    String sql =
      "-- SQL Workbench/J \n" +
      "select " + persistenceCol + ", \n" +
      "       ct.relkind, \n" +
      "       array_to_string(ct.reloptions, ', ') as options, \n" +
      "       " + spcnameCol + ", \n" +
      "       own.rolname as owner, \n" +
      "       " + defaultTsCol + ", \n" +
      "       " + rlsEnableCol + ", \n" +
      "       " + rlsForceCol + ", \n " +
      "       " + replicaCol + ", \n" +
      "       " + replicaIndexCol + " \n" +
      "from pg_catalog.pg_class ct \n" +
      "  join pg_catalog.pg_namespace cns on ct.relnamespace = cns.oid \n " +
      "  join pg_catalog.pg_roles own on ct.relowner = own.oid \n " +
      "  left join pg_catalog.pg_tablespace spc on spc.oid = ct.reltablespace \n" + defaultTsQuery +
      " where cns.nspname = ? \n" +
      "   and ct.relname = ?";

    boolean isPartitioned = false;

    final CallerInfo ci = new CallerInfo(){};
    Savepoint sp = null;
    try
    {
      sp = dbConnection.setSavepoint();
      pstmt = this.dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, tbl.getRawSchema());
      pstmt.setString(2, tbl.getRawTableName());

      LogMgr.logMetadataSql(ci, "table options", sql, tbl.getSchema(), tbl.getTableName());

      rs = pstmt.executeQuery();

      if (rs.next())
      {
        String persistence = rs.getString("relpersistence");
        String replica = rs.getString("relreplident");
        String type = rs.getString("relkind");
        String settings = rs.getString("options");
        String tableSpace = rs.getString("spcname");
        String owner = rs.getString("owner");
        String defaultTablespace = rs.getString("default_tablespace");
        boolean rlsEnabled = rs.getBoolean("relrowsecurity");
        boolean forceRls = rs.getBoolean("relforcerowsecurity");

        if (showNonStandardTablespace && !"pg_default".equals(defaultTablespace) && StringUtil.isEmpty(tableSpace))
        {
          tableSpace = defaultTablespace;
        }

        tbl.setOwner(owner);
        tbl.setTablespace(tableSpace);

        if (StringUtil.isNotEmpty(persistence))
        {
          switch (persistence.charAt(0))
          {
            case 'u':
              option.setTypeModifier("UNLOGGED");
              break;
            case 't':
              option.setTypeModifier("TEMPORARY");
              break;
          }
        }

        if (StringUtil.isNotEmpty(replica))
        {
          String tname = tbl.getTableExpression(dbConnection);
          String alter = "ALTER TABLE " + tname + " REPLICA IDENTITY ";
          switch (replica.charAt(0))
          {
            case 'n':
              option.appendAdditionalSql(alter + "NOTHING;");
              option.addConfigSetting(REPLICA_IDENT_OPTION, "nothing");
              break;
            case 'f':
              option.appendAdditionalSql(alter + "FULL;");
              option.addConfigSetting(REPLICA_IDENT_OPTION, "full");
              break;
            case 'i':
              String index = rs.getString("replica_index");
              option.appendAdditionalSql(alter + "USING INDEX " + SqlUtil.quoteObjectname(index) + ";");
              option.addConfigSetting(REPLICA_IDENT_OPTION, "index");
              option.addConfigSetting(REPLICA_INDEX, index);
              break;
          }
        }

        if (forceRls)
        {
          option.addConfigSetting(FORCE_RLS_OPTION, "true");
        }

        if (rlsEnabled)
        {
          option.addConfigSetting(RLS_ENABLED_OPTION, "true");
        }

        if ("f".equalsIgnoreCase(type))
        {
          option.setTypeModifier("FOREIGN");
        }

        isPartitioned = "p".equals(type);

        if (!isPartitioned && inherit != null)
        {
          if (tableSql.length() > 0) tableSql.append('\n');
          tableSql.append(inherit);
        }

        if (StringUtil.isNotEmpty(settings))
        {
          setConfigSettings(settings, option);
          if (tableSql.length() > 0) tableSql.append('\n');
          tableSql.append("WITH (");
          tableSql.append(settings);
          tableSql.append(")");
        }

        if (StringUtil.isNotBlank(tableSpace))
        {
          if (tableSql.length() > 0) tableSql.append('\n');
          tableSql.append("TABLESPACE ");
          tableSql.append(tableSpace);
        }

        if (JdbcUtils.hasMinimumServerVersion(dbConnection, "9.5"))
        {
          PostgresPolicyReader reader = new PostgresPolicyReader();
          String policies = reader.getTablePolicies(dbConnection, tbl);
          option.appendAdditionalSql(policies);
        }
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(ci, e, "table options", sql, tbl.getSchema(), tbl.getTableName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    option.setTableOption(tableSql.toString());

    if (isPartitioned)
    {
      handlePartitions(tbl);
    }
  }

  protected void setConfigSettings(String options, ObjectSourceOptions tblOption)
  {
    List<String> l = StringUtil.stringToList(options, ",", true, true, false, true);
    for (String s : l)
    {
      String[] opt = s.split("=");
      if (opt.length == 2)
      {
        tblOption.addConfigSetting(opt[0], opt[1]);
      }
    }
  }

  protected void handlePartitions(TableIdentifier table)
  {
    PostgresPartitionReader reader = new PostgresPartitionReader(table, dbConnection);
    reader.readPartitionInformation();
    ObjectSourceOptions option = table.getSourceOptions();
    String def = reader.getPartitionDefinition();
    option.appendTableOptionSQL(def);
    option.addConfigSetting(PostgresPartitionReader.OPTION_KEY_STRATEGY, reader.getStrategy().toLowerCase());
    option.addConfigSetting(PostgresPartitionReader.OPTION_KEY_EXPRESSION, reader.getPartitionExpression());

    String createPartitions = reader.getCreatePartitions();
    if (createPartitions != null)
    {
      option.appendAdditionalSql(createPartitions);
    }
  }

  protected StringBuilder readInherits(TableIdentifier table)
  {
    if (table == null) return null;

    StringBuilder result = null;
    PostgresInheritanceReader reader = new PostgresInheritanceReader();

    List<TableIdentifier> parents = reader.getParents(dbConnection, table);
    if (CollectionUtil.isEmpty(parents)) return null;

    result = new StringBuilder(parents.size() * 30);
    result.append("INHERITS (");

    for (int i = 0; i < parents.size(); i++)
    {
      TableIdentifier tbl = parents.get(i);
      table.getSourceOptions().addConfigSetting("inherits", tbl.getTableName());
      result.append(tbl.getTableName());
      if (i > 0) result.append(',');
    }
    result.append(')');

    return result;
  }

  public void readForeignTableOptions(TableIdentifier table)
  {
    ObjectSourceOptions option = table.getSourceOptions();

    String sql
      = "select ft.ftoptions, fs.srvname \n" +
      "from pg_foreign_table ft \n" +
      "  join pg_class tbl on tbl.oid = ft.ftrelid  \n" +
      "  join pg_namespace ns on tbl.relnamespace = ns.oid  \n" +
      "  join pg_foreign_server fs on ft.ftserver = fs.oid \n " +
      " WHERE tbl.relname = ? \n" +
      "   and ns.nspname = ? ";

    PreparedStatement stmt = null;
    ResultSet rs = null;
    StringBuilder result = new StringBuilder(100);
    Savepoint sp = null;
    final CallerInfo ci = new CallerInfo(){};
    try
    {
      sp = dbConnection.setSavepoint();
      stmt = dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, table.getRawTableName());
      stmt.setString(2, table.getRawSchema());

      LogMgr.logMetadataSql(ci, "foreign table options", sql, table.getTableName(), table.getSchema());

      rs = stmt.executeQuery();
      if (rs.next())
      {
        String[] options = JdbcUtils.getArray(rs, "ftoptions", String[].class);
        String serverName = rs.getString(2);
        result.append("SERVER ");
        result.append(serverName);
        if (options != null && options.length > 0)
        {
          result.append("\nOPTIONS (");
          for (int i = 0; i < options.length; i++)
          {
            if (i > 0)
            {
              result.append(", ");
            }
            String[] optValues = options[i].split("=");
            if (optValues.length == 2)
            {
              result.append(optValues[0] + " '" + optValues[1] + "'");
            }
          }
          result.append(')');
        }
        option.setTableOption(result.toString());
      }
    }
    catch (SQLException ex)
    {
      dbConnection.rollback(sp);
      sp = null;
      LogMgr.logMetadataError(ci, ex, "foreign table options", table.getTableName(), table.getSchema());
    }
    finally
    {
      dbConnection.releaseSavepoint(sp);
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  @Override
  public StringBuilder getFkSource(TableIdentifier table)
  {
    if (usePgFunctionForFKSource())
    {
      return readFKSource(table, null, false);
    }
    return getFkSource(table, getForeignKeys(table), getCreateInlineFKConstraints());
  }

  public StringBuilder readFKSource(TableIdentifier table, List<DependencyNode> fkList, boolean forInlineUse)
  {
    String sql =
      "select pg_catalog.quote_ident(con.conname) as conname,  \n" +
      "       pg_catalog.pg_get_constraintdef(con.oid) as src \n" +
      "from pg_catalog.pg_class tbl \n" +
      "  join pg_catalog.pg_constraint con on tbl.oid = con.conrelid   \n" +
      "  join pg_catalog.pg_namespace nsp on tbl.relnamespace = nsp.oid \n" +
      "where con.contype = 'f' \n" +
      " and tbl.relname = ? \n" +
      " and nsp.nspname = ?";

    if (CollectionUtil.isNonEmpty(fkList))
    {
      sql += " \n and con.conname in (" +
             fkList.stream().
               map(n -> "'" + n.getFkName() + "'").
               collect(Collectors.joining(",")) + ")";
    }

    PreparedStatement stmt = null;
    ResultSet rs = null;
    StringBuilder result = new StringBuilder(100);
    Savepoint sp = null;
    final CallerInfo ci = new CallerInfo(){};
    try
    {
      sp = dbConnection.setSavepoint();
      stmt = dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, table.getRawTableName());
      stmt.setString(2, table.getRawSchema());

      LogMgr.logMetadataSql(ci, "foreign key source", sql, table.getTableName(), table.getSchema());
      String tableName = table.getTableExpression(dbConnection);
      rs = stmt.executeQuery();
      boolean first = true;
      while (rs.next())
      {
        String name = rs.getString(1);
        String source = rs.getString(2);
        if (forInlineUse)
        {
          if (first)
          {
            first = false;
          }
          else
          {
            result.append(",\n");
          }
          result.append(COL_INDENT);
          result.append("CONSTRAINT ");
          result.append(name);
          result.append("\n     ");
          result.append(source);
        }
        else
        {
          result.append("ALTER TABLE ");
          result.append(tableName);
          result.append("\n  ADD CONSTRAINT ");
          result.append(name);
          result.append("\n  ");
          result.append(source);
          result.append(";\n");
        }
      }
    }
    catch (SQLException ex)
    {
      dbConnection.rollback(sp);
      sp = null;
      LogMgr.logMetadataError(ci, ex, "foreign key source", table.getTableName(), table.getSchema());
    }
    finally
    {
      dbConnection.releaseSavepoint(sp);
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public StringBuilder getFkSource(TableIdentifier table, List<DependencyNode> fkList, boolean forInlineUse)
  {
    if (CollectionUtil.isEmpty(fkList)) return StringUtil.emptyBuilder();

    StringBuilder fkSource;
    if (usePgFunctionForFKSource())
    {
      fkSource = readFKSource(table, fkList, forInlineUse);
    }
    else
    {
      fkSource = super.getFkSource(table, fkList, forInlineUse);
    }

    if (!forInlineUse)
    {
      appendFKComments(table, fkSource, fkList);
    }
    return fkSource;
  }

  private boolean usePgFunctionForFKSource()
  {
    return !dbConnection.getDbSettings().getBoolProperty("fk.source.generate", false);
  }

  @Override
  protected void appendFKComments(TableIdentifier table, StringBuilder fkSource, List<DependencyNode> fkList)
  {
    if (CollectionUtil.isEmpty(fkList)) return;
    String tblname = table.getTableExpression(dbConnection);
    for (DependencyNode node : fkList)
    {
      String conname = SqlUtil.quoteObjectname(node.getFkName());
      String comment = SqlUtil.escapeQuotes(node.getComment());
      if (StringUtil.isNotEmpty(comment))
      {
        String ddl = "\nCOMMENT ON CONSTRAINT " + conname + " ON " + tblname + " IS '" + comment + "';";
        fkSource.append(ddl);
      }
    }
  }

  @Override
  public String getAdditionalTableInfo(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList)
  {
    String schema = table.getSchemaToUse(this.dbConnection);
    CharSequence enums = getEnumInformation(columns, schema);
    CharSequence domains = getDomainInformation(columns, schema);
    CharSequence sequences = getColumnSequenceInformation(table, columns);
    CharSequence stats = null;
    if (JdbcUtils.hasMinimumServerVersion(dbConnection, "10.0"))
    {
      stats = readExtendeStats(table);
    }
    CharSequence children = null;
    ObjectSourceOptions sourceOptions = table.getSourceOptions();
    if (sourceOptions.getConfigSettings().get(PostgresPartitionReader.OPTION_KEY_STRATEGY) == null)
    {
      children = getChildTables(table);
    }
    StringBuilder storage = getColumnStorage(table, columns);
    String owner = getOwnerSql(table);

    if (StringUtil.allEmpty(enums, domains, sequences, children, owner, storage, stats)) return null;

    StringBuilder result = new StringBuilder(200);

    if (storage != null) result.append(storage);
    if (enums != null) result.append(enums);
    if (domains != null) result.append(domains);
    if (sequences != null) result.append(sequences);
    if (stats != null) result.append(stats);
    if (children != null) result.append(children);
    if (owner != null) result.append(owner);

    return result.toString();
  }

  private StringBuilder getColumnStorage(TableIdentifier table, List<ColumnIdentifier> columns)
  {
    StringBuilder result = null;
    String tname = table.getTableExpression(dbConnection);

    for (ColumnIdentifier col : columns)
    {
      int storage = col.getPgStorage();
      String option = PostgresColumnEnhancer.getStorageOption(storage);
      if (option != null && !isDefaultStorage(col.getDataType(), storage))
      {
        if (result == null)
        {
          result = new StringBuilder(50);
          result.append('\n');
        }
        result.append("ALTER TABLE ");
        result.append(tname);
        result.append(" ALTER ");
        result.append(dbConnection.getMetadata().quoteObjectname(col.getColumnName()));
        result.append(" SET STORAGE ");
        result.append(option);
        result.append(";\n");
      }
    }
    return result;
  }

  private boolean isDefaultStorage(int columnType, int storage)
  {
    if (columnType == Types.NUMERIC && storage == PostgresColumnEnhancer.STORAGE_MAIN) return true;
    return storage == PostgresColumnEnhancer.STORAGE_EXTENDED;
  }

  protected String getOwnerSql(TableIdentifier table)
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

      return "\nALTER TABLE " + table.getFullyQualifiedName(dbConnection) + " OWNER TO " + SqlUtil.quoteObjectname(owner) + ";";
    }
    catch (Exception ex)
    {
      return null;
    }
  }

  protected CharSequence getColumnSequenceInformation(TableIdentifier table, List<ColumnIdentifier> columns)
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
        // if the default value is shown as nextval, the sequence name is already visible
        if (defaultValue != null && defaultValue.toLowerCase().contains("nextval")) continue;

        String colname = StringUtil.trimQuotes(col.getColumnName());
        sql = "select pg_get_serial_sequence('" + tblname + "', '" + colname + "')";
        LogMgr.logMetadataSql(new CallerInfo(){}, "sequence information", sql);
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
      LogMgr.logMetadataError(new CallerInfo(){}, e, "sequence information", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    if (b.length() == 0) return null;
    return b;
  }

  protected CharSequence getEnumInformation(List<ColumnIdentifier> columns, String schema)
  {
    PostgresEnumReader reader = new PostgresEnumReader();
    Map<String, EnumIdentifier> enums = reader.getEnumInfo(dbConnection, schema, null);
    if (CollectionUtil.isEmpty(enums)) return null;

    StringBuilder result = null;

    for (ColumnIdentifier col : columns)
    {
      String dbType = col.getDbmsType();
      EnumIdentifier enumDef = enums.get(dbType);
      if (enumDef != null)
      {
        if (result == null) result = new StringBuilder(50);
        result.append("\n-- enum '");
        result.append(dbType);
        result.append("': ");
        result.append(StringUtil.listToString(enumDef.getValues(), ",", true, '\''));
      }
    }

    return result;
  }

  public CharSequence getDomainInformation(List<ColumnIdentifier> columns, String schema)
  {
    PostgresDomainReader reader = new PostgresDomainReader();
    Map<String, DomainIdentifier> domains = reader.getDomainInfo(dbConnection, schema);
    if (domains == null || domains.isEmpty()) return null;
    StringBuilder result = null;

    for (ColumnIdentifier col : columns)
    {
      String dbType = col.getDbmsType();
      DomainIdentifier domain = domains.get(dbType);
      if (domain != null)
      {
        if (result == null) result = new StringBuilder(50);
        result.append("\n-- domain '");
        result.append(dbType);
        result.append("': ");
        result.append(domain.getSummary());
      }
    }

    return result;
  }

  protected CharSequence getChildTables(TableIdentifier table)
  {
    if (table == null) return null;

    PostgresInheritanceReader reader = new PostgresInheritanceReader();

    List<InheritanceEntry> tables = reader.getChildren(dbConnection, table);
    if (CollectionUtil.isEmpty(tables)) return null;

    StringBuilder result = new StringBuilder(tables.size() * 50);
    boolean is84 = JdbcUtils.hasMinimumServerVersion(dbConnection, "8.4");

    if (is84)
    {
      result.append("\n/* Inheritance tree:\n\n");
      result.append(table.getSchema());
      result.append('.');
      result.append(table.getTableName());
    }
    else
    {
      result.append("\n-- Child tables:");
    }

    for (int i = 0; i < tables.size(); i++)
    {
      String tableName = tables.get(i).getTable().getTableName();
      String schemaName = tables.get(i).getTable().getSchema();
      int level = tables.get(i).getLevel();
      if (is84)
      {
        result.append('\n');
        result.append(StringUtil.padRight(" ", level * 2));
      }
      else
      {
        result.append("\n--  ");
      }
      result.append(schemaName);
      result.append('.');
      result.append(tableName);
    }

    if (is84)
    {
      result.append("\n*/");
    }
    return result;
  }

  private CharSequence readExtendeStats(TableIdentifier table)
  {
    if (table == null) return null;

    boolean is14 = JdbcUtils.hasMinimumServerVersion(dbConnection, "14");

    String sql;

    if (is14)
    {
      sql =
        "SELECT st.stxname as statistic_name, \n" +
        "       st.stxnamespace::pg_catalog.regnamespace AS statistic_schema, \n" +
        "       pg_catalog.pg_get_statisticsobjdef_columns(st.oid) AS columns,\n" +
        "       replace(array_to_string(stxkind, ''),'e','') as stxkind\n" +
        "FROM pg_catalog.pg_statistic_ext st\n" +
        "WHERE st.stxnamespace = to_regnamespace(?) \n" +
        "  AND st.stxrelid = to_regclass(?) \n" +
        "ORDER BY st.stxname";
    }
    else
    {
      sql =
      "select st.stxname as statistic_name, \n" +
      "       sn.nspname as statistic_schema, \n" +
      "       string_agg(col.attname, ',') as columns, \n" +
      "       array_to_string(stxkind, '') as stxkind \n"+
      "from pg_statistic_ext st \n" +
      "  join pg_namespace sn on sn.oid = st.stxnamespace \n" +
      "  join pg_class t on t.oid = st.stxrelid\n" +
      "  join pg_namespace nsp on nsp.oid = t.relnamespace\n" +
      "  join pg_attribute col on t.oid = col.attrelid and col.attnum = any(stxkeys)\n" +
      "where nsp.nspname = ? \n"+
      "  and t.relname = ? \n" +
      "group by st.stxname, sn.nspname, st.stxkind \n" +
      "order by st.stxname";
    }
    ResultSet rs = null;
    PreparedStatement pstmt = null;

    Savepoint sp = null;
    StringBuilder result = null;

    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logMetadataSql(ci, "extended column statistics", sql, table.getSchema(), table.getTableName());

    ObjectSourceOptions option = table.getSourceOptions();
    try
    {
      sp = dbConnection.setSavepoint();
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getRawSchema());
      pstmt.setString(2, table.getRawTableName());
      rs = pstmt.executeQuery();
      String tname = table.getTableExpression(dbConnection);
      while (rs.next())
      {
        String name = rs.getString(1);
        String schema = rs.getString(2);
        String cols = rs.getString(3);
        String types = rs.getString(4);

        TableIdentifier tbl = new TableIdentifier(schema, name);

        if (result == null)
        {
          result = new StringBuilder(100);
        }
        result.append("\nCREATE STATISTICS ");
        result.append(tbl.getTableExpression(dbConnection));
        if (StringUtil.isNotBlank(types))
        {
          result.append(" (");
          for (int i=0; i < types.length(); i++)
          {
            if (i > 0) result.append(',');
            switch (types.charAt(i))
            {
              case 'd':
                result.append("ndistinct");
                break;
              case 'f':
                result.append("dependencies");
                break;
              case 'm':
                result.append("mcv");
                break;
            }
          }
          result.append(")");
        }
        result.append(" ON ");
        result.append(cols);
        result.append(" FROM ");
        result.append(tname);
        result.append(';');
        option.addConfigSetting("column_statistics", result.toString());
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(ci, e, "extended column statistics", sql, table.getSchema(), table.getTableName());
      result = null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }

    return result;
  }
}
