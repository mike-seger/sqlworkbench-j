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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import workbench.db.ColumnIdentifier;
import workbench.db.ProcedureDefinition;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PGProcName
  implements Comparable<PGProcName>
{
  private List<PGArg> arguments;
  private String procName;
  private String procSchema;

  public PGProcName(ProcedureDefinition def)
  {
    procName = SqlUtil.removeObjectQuotes(def.getProcedureName());
    procSchema = SqlUtil.removeObjectQuotes(def.getSchema());
    List<ColumnIdentifier> parameters = def.getParameters(null);
    if (CollectionUtil.isNonEmpty(parameters))
    {
      arguments = new ArrayList<>(parameters.size());
      for (ColumnIdentifier col : parameters)
      {
        String mode = col.getArgumentMode();
        PGArg arg = new PGArg(col.getDbmsType(), mode);
        arguments.add(arg);
      }
    }
    else
    {
      initFromDisplayName(def.getDisplayName());
    }
  }

  /**
   * Initialize a PGProcName from a "full" name that includes the
   * procedure's name and all parameter types in brackets.
   * <br/>
   * e.g. my_func(int4, varchar, date)
   *
   * @param fullname
   * @param typeMap
   */
  public PGProcName(String fullname)
  {
    initFromDisplayName(fullname);
  }

  private void initFromDisplayName(String displayName)
  {
    int pos = displayName.indexOf('(');
    if (pos > -1)
    {
      procName = displayName.substring(0, pos);
      String args = displayName.substring(pos + 1, displayName.indexOf(')'));
      List<String> elements = StringUtil.stringToList(args, ",", false, true);
      arguments = new ArrayList<>();
      for (String s : elements)
      {
        PGArg arg = new PGArg(s, "in");
        arguments.add(arg);
      }
    }
    else
    {
      procName = displayName;
      arguments = Collections.emptyList();
    }
  }

  public PGProcName(String name, ArgInfo info)
  {
    procName = name;
    arguments = new ArrayList<>();
    for (int i=0; i < info.getNumArgs(); i++)
    {
      String argType = info.getArgType(i);
      String mode = info.getArgMode(i);
      if ("t".equals(mode)) continue;

      PGArg parg = new PGArg(argType, mode);
      arguments.add(parg);
    }
  }

  public String getInputOIDsAsVector()
  {
    String oids = arguments.stream().
      filter(arg -> (arg.argMode == PGArg.ArgMode.in || arg.argMode == PGArg.ArgMode.inout) && StringUtil.isNotBlank(arg.argType) ).
      map(arg ->  "'"+arg.argType+"'::regtype::oid::text").
      collect(Collectors.joining("||' '||"));
    if (StringUtil.isBlank(oids)) return null;
    return "cast(" + oids + " as oidvector)";
  }

  /**
   * For unit tests.
   */
  List<PGArg> getArguments()
  {
    return arguments;
  }

  @Override
  public int compareTo(PGProcName o)
  {
    return getFormattedName().compareTo(o.getFormattedName());
  }

  public String getName()
  {
    return procName;
  }

  public String getName(WbConnection conn)
  {
    return SqlUtil.buildExpression(conn, null, procSchema, procName) + getSignature();
  }

  /**
   * Returns the signature of this procedure name <b>without</b> the procedure name.
   * If this is a function without parameters, <tt>()</tt> will be returned.
   */
  public String getSignature()
  {
    if (arguments == null || arguments.isEmpty()) return "()";
    StringBuilder b = new StringBuilder(procName.length() + arguments.size() * 10);
    b.append('(');

    String args = arguments.stream().
      filter(arg -> (arg.argMode == PGArg.ArgMode.in || arg.argMode == PGArg.ArgMode.inout) ).
      map(arg ->  arg.argType).
      collect(Collectors.joining(", "));

    b.append(args);
    b.append(')');
    return b.toString();
  }

  public String getFormattedName()
  {
    if (arguments == null || arguments.isEmpty()) return procName +"()";
    StringBuilder b = new StringBuilder(procName.length() + arguments.size() * 10);
    b.append(procName);
    b.append('(');

    String args = arguments.stream().
      map(arg ->  arg.argType).
      collect(Collectors.joining(", "));

    b.append(args);
    b.append(')');
    return b.toString();
  }

  @Override
  public String toString()
  {
    return getFormattedName();
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj instanceof PGProcName)
    {
      final PGProcName other = (PGProcName) obj;
      String myName = getFormattedName();
      String otherName = other.getFormattedName();
      return myName.equals(otherName);
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    int hash = 5;
    hash = 79 * hash + (this.arguments != null ? this.arguments.hashCode() : 0);
    hash = 79 * hash + (this.procName != null ? this.procName.hashCode() : 0);
    return hash;
  }

}
