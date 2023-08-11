/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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
package workbench.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.WbConnection;
import workbench.db.oracle.OracleUtils;

import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;
import workbench.sql.parser.ParserType;

/**
 *
 * @author Thomas Kellerer
 */
public class DdlObjectInfo
{
  private String objectType;
  private final List<String> objectNames = new ArrayList<>();

  public DdlObjectInfo(CharSequence sql)
  {
    parseSQL(sql, ParserType.Standard);
  }

  public DdlObjectInfo(CharSequence sql, ParserType type)
  {
    parseSQL(sql, type);
  }

  public DdlObjectInfo(CharSequence sql, WbConnection conn)
  {
    parseSQL(sql, conn);
  }

  @Override
  public String toString()
  {
    return "Type: " + objectType + ", name: " + getObjectName();
  }

  public void setObjectType(String newType)
  {
    this.objectType = newType;
  }

  public String getDisplayType()
  {
    return StringUtil.capitalize(objectType);
  }

  public boolean isValid()
  {
    return objectType != null;
  }

  public String getObjectType()
  {
    return objectType;
  }

  public List<String> getObjectNames()
  {
    return Collections.unmodifiableList(objectNames);
  }

  public String getObjectName()
  {
    if (objectNames.isEmpty()) return null;
    if (objectNames.size() == 1) return objectNames.get(0);
    return objectNames.stream().collect(Collectors.joining(", "));
  }

  private void parseSQL(CharSequence sql, WbConnection conn)
  {
    ParserType type = ParserType.getTypeFromConnection(conn);
    parseSQL(sql, type);
  }

  private void parseSQL(CharSequence sql, ParserType type)
  {
    SQLLexer lexer = SQLLexerFactory.createLexer(type, sql);
    SQLToken t = lexer.getNextToken(false, false);
    objectNames.clear();

    if (t == null) return;
    String verb = t.getContents();
    Set<String> verbs = CollectionUtil.caseInsensitiveSet("DROP", "RECREATE", "ALTER", "ANALYZE");

    if (!verb.startsWith("CREATE") && !verbs.contains(verb)) return;

    try
    {
      boolean typeFound = false;
      SQLToken token = lexer.getNextToken(false, false);
      while (token != null)
      {
        String c = token.getContents();
        if (SqlUtil.getKnownTypes().contains(c))
        {
          typeFound = true;
          this.objectType = c.toUpperCase();
          break;
        }
        token = lexer.getNextToken(false, false);
      }

      if (!typeFound) return;

      // if a type was found we assume the next keyword is the name
      if (!SqlUtil.getTypesWithoutNames().contains(this.objectType))
      {
        SQLToken name = lexer.getNextToken(false, false);
        if (name == null) return;
        String content = name.getContents();

        // For PostgreSQL
        if (type == ParserType.Postgres && content.equalsIgnoreCase("CONCURRENTLY"))
        {
          name = lexer.getNextToken(false, false);
          if (name == null) return;
          content = name.getContents();
        }

        if (content.equals("IF NOT EXISTS") || content.equals("IF EXISTS") || content.equals(OracleUtils.KEYWORD_EDITIONABLE))
        {
          name = lexer.getNextToken(false, false);
          if (name == null) return;
        }

        if (type == ParserType.Postgres && "DROP".equalsIgnoreCase(verb))
        {
          parsePgDropNames(lexer, name);
          return;
        }

        SQLToken next = lexer.getNextToken(false, false);
        if (next != null && next.getContents().equals("."))
        {
          next = lexer.getNextToken(false, false);
          if (next != null) name = next;
        }

        if ("CREATE".equalsIgnoreCase(verb) &&
            this.objectType.equalsIgnoreCase("index") &&
            name.getContents().equalsIgnoreCase("ON"))
        {
          // this is for unnamed CREATE INDEX in Postgres to avoid the message
          // Index "ON" created.
          this.objectNames.clear();
        }
        else
        {
          if (next != null && name.getContents().endsWith("."))
          {
            this.objectNames.add(SqlUtil.removeObjectQuotes(name.getContents()) + SqlUtil.removeObjectQuotes(next.getContents()));
          }
          else
          {
            this.objectNames.add(SqlUtil.removeObjectQuotes(name.getContents()));
          }
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error finding object info", e);
      this.objectNames.clear();
      this.objectType = null;
    }
  }

  private void parsePgDropNames(SQLLexer lexer, SQLToken current)
  {
    String currentName = "";
    Set<String> end = CollectionUtil.caseInsensitiveSet("CASCADE", "RESTRICT", "WITH", ";");
    while (current != null)
    {
      if (current != null && end.contains(current.getText()) )
      {
        break;
      }

      if (",".equals(current.getText()))
      {
        this.objectNames.add(currentName);
        currentName = "";
      }
      else
      {
        currentName += current.getText();
      }
      current = lexer.getNextToken(false, false);
    }
    if (!currentName.isBlank())
    {
      this.objectNames.add(currentName);
    }
  }
}
