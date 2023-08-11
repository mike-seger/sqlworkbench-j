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
package workbench.db.ucanaccess;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.sql.Types;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.storage.DataConverter;

/**
 *
 * @author Thomas Kellerer
 */
public class UCanAccessDataConverter
  implements DataConverter
{
  private Method getValue;

  private static class LazyInstanceHolder
  {
    protected static final UCanAccessDataConverter INSTANCE = new UCanAccessDataConverter();
  }

  public static UCanAccessDataConverter getInstance()
  {
    return LazyInstanceHolder.INSTANCE;
  }

  private UCanAccessDataConverter()
  {
  }

  @Override
  public boolean convertsType(int jdbcType, String dbmsType)
  {
    return jdbcType == Types.OTHER;
  }

  @Override
  public Object convertValue(int jdbcType, String dbmsType, Object value)
  {
    if (jdbcType != Types.OTHER) return value;
    if (value == null) return null;

    try
    {
      if (value.getClass().isArray() && Array.getLength(value) > 0)
      {
        Object singleValue = Array.get(value, 0);
        if (singleValue == null) return value;

        if (singleValue.getClass().getName().equals("net.ucanaccess.complex.SingleValue"))
        {
          Method get = getValueMethod(singleValue);
          if (get != null)
          {
            Object realValue = get.invoke(singleValue);
            return realValue;
          }
        }
      }
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not read SingleValue", th);
    }
    return value;
  }

  @Override
  public Class getConvertedClass(int jdbcType, String dbmsType)
  {
    if (convertsType(jdbcType, dbmsType))
    {
      return Object.class;
    }
    return null;
  }

  private synchronized Method getValueMethod(Object value)
  {
    if (getValue == null)
    {
      try
      {
        getValue = value.getClass().getMethod("getValue");
      }
      catch (Throwable th)
      {
        // ignore
      }
    }
    return getValue;
  }

}
