/*
 * BlobFormatterFactory.java
 *
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
package workbench.storage;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DBID;
import workbench.db.DbMetadata;
import workbench.db.DbSettings;

import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class BlobFormatterFactory
{

  public static BlobLiteralFormatter createAnsiFormatter()
  {
    // ANSI Syntax is X'FFF0'...
    DefaultBlobFormatter f = new DefaultBlobFormatter();
    f.setPrefix("X'");
    f.setSuffix("'");
    return f;
  }

  public static BlobLiteralFormatter createInstance(BlobLiteralType type)
  {
    if (type == BlobLiteralType.pgDecode || type == BlobLiteralType.pgEscape || type == BlobLiteralType.pgHex)
    {
      return new PostgresBlobFormatter(type);
    }
    DefaultBlobFormatter f = new DefaultBlobFormatter();
    f.setLiteralType(type);
    return f;
  }

  public static BlobLiteralFormatter createInstance(DbMetadata meta)
  {
    if (meta == null)
    {
      LogMgr.logError(new CallerInfo(){}, "No DbMetadata available", new Exception("Backtrace"));
      return createAnsiFormatter();
    }

    DbSettings s = meta.getDbSettings();
    if (s == null)
    {
      LogMgr.logError(new CallerInfo(){}, "No DbSettings available", new Exception("Backtrace"));
      return createAnsiFormatter();
    }

    // Check for a user-defined formatter definition
    // for the current DBMS
    String prefix = s.getBlobLiteralPrefix();
    String suffix = s.getBlobLiteralSuffix();

    if (StringUtil.isNotBlank(prefix) && StringUtil.isNotBlank(suffix))
    {
      DefaultBlobFormatter f = new DefaultBlobFormatter();
      String type = s.getBlobLiteralType();

      BlobLiteralType literalType = null;
      try
      {
        literalType = BlobLiteralType.valueOf(type);
      }
      catch (Throwable e)
      {
        literalType = BlobLiteralType.hex;
      }

      BlobLiteralType.valueOf(type);
      f.setUseUpperCase(s.getBlobLiteralUpperCase());
      f.setLiteralType(literalType);
      f.setPrefix(prefix);
      f.setSuffix(suffix);
      return f;
    }

    // No user-defined formatter definition found, use the built-in settings
    switch (DBID.fromID(meta.getDbId()))
    {
      case Postgres:
        return new PostgresBlobFormatter();
      case Oracle:
        // This will fail with BLOBs > 4KB
        // But there is no way of specifying longer literals anyway
        DefaultBlobFormatter ora = new DefaultBlobFormatter();
        ora.setUseUpperCase(true);
        ora.setPrefix("'");
        ora.setSuffix("'");
        return ora;
      case SQL_Server:
        DefaultBlobFormatter ms = new DefaultBlobFormatter();
        ms.setPrefix("0x");
        return ms;
    }
    // Use the ANSI format for all others (e.g.: DB2, H2, HSQLDB
    return createAnsiFormatter();
  }

}
