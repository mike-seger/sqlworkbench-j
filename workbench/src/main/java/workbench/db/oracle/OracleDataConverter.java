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
package workbench.db.oracle;

import java.lang.reflect.Method;
import java.sql.Types;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.storage.DataConverter;

import workbench.util.CollectionUtil;
import workbench.util.NumberStringCache;
import workbench.util.SqlUtil;

/**
 * A class to convert Oracle's RAW datatype to something readable.
 * <br/>
 * This is only used if enabled.
 *
 * @see workbench.resource.Settings#getConvertOracleTypes()
 *
 * @author Thomas Kellerer
 */
public class OracleDataConverter
  implements DataConverter
{
  private Method stringValueMethod;
  private Set<String> convertToHexJdbcTypes = CollectionUtil.caseInsensitiveSet();
  private Set<String> convertToHexDBMSTypes = CollectionUtil.caseInsensitiveSet();

  private static class LazyInstanceHolder
  {
    protected static final OracleDataConverter INSTANCE = new OracleDataConverter();
  }

  public static OracleDataConverter getInstance()
  {
    return LazyInstanceHolder.INSTANCE;
  }

  private OracleDataConverter()
  {
    List<String> jdbcTypes = Settings.getInstance().getListProperty("workbench.db.oracle.convert_to_hex.jdbc_types", true);
    if (jdbcTypes != null)
    {
      convertToHexJdbcTypes.addAll(jdbcTypes);
    }
    List<String> dbmsTypes = Settings.getInstance().getListProperty("workbench.db.oracle.convert_to_hex.dbms_types", true);
    if (dbmsTypes != null)
    {
      convertToHexDBMSTypes.addAll(dbmsTypes);
    }
  }

  @Override
  public Class getConvertedClass(int jdbcType, String dbmsType)
  {
    if (convertsType(jdbcType, dbmsType)) return String.class;
    return null;
  }

  /**
   * Two Oracle datatypes are supported
   * <ul>
   * <li>RAW (jdbcType == Types.VARBINARY && dbmsType == "RAW")</li>
   * <li>ROWID (jdbcType = Types.ROWID)</li>
   * </ul>
   *
   * @param jdbcType the jdbcType as returned by the driver
   * @param dbmsType the name of the datatype for this value
   */
  @Override
  public boolean convertsType(int jdbcType, String dbmsType)
  {
    return ((isBinary(jdbcType) && dbmsType.startsWith("RAW")) ||
             jdbcType == Types.ROWID || isCustomConversion(jdbcType, dbmsType));

  }

  private boolean isBinary(int jdbcType)
  {
    return jdbcType == Types.VARBINARY || jdbcType == Types.BINARY;
  }
  
  private boolean isCustomConversion(int jdbcType, String dbmsType)
  {
    if (!convertToHexJdbcTypes.isEmpty() && convertToHexJdbcTypes.contains(SqlUtil.getTypeName(jdbcType)))
    {
      return true;
    }

    if (convertToHexDBMSTypes.isEmpty()) return false;
    int pos = dbmsType.indexOf('(');
    if (pos > -1)
    {
      dbmsType = dbmsType.substring(0, pos);
    }
    return convertToHexDBMSTypes.contains(dbmsType);
  }

  /**
   * If the type of the originalValue is RAW, then
   * the value is converted into a corresponding hex display, e.g. <br/>
   * <tt>0x000000000001dc91</tt>
   *
   * If the type of the originalValue is ROWID, Oracles stringValue() method
   * from the class oracle.sql.ROWID is used to convert the input value
   *
   * @param jdbcType the jdbcType as returned by the driver
   * @param dbmsType the name of the datatype for this value
   * @param inputValue the value to be converted (or not)
   *
   * @return the originalValue or a converted value if approriate
   * @see #convertsType(int, java.lang.String)
   */
  @Override
  public Object convertValue(int jdbcType, String dbmsType, Object inputValue)
  {
    if (inputValue == null) return null;
    if (!convertsType(jdbcType, dbmsType)) return inputValue;

    if (jdbcType == Types.ROWID)
    {
      return convertRowId(inputValue);
    }
    return convertRaw(inputValue);
  }

  private Object convertRaw(Object originalValue)
  {
    Object newValue;
    try
    {
      byte[] b = (byte[])originalValue;
      StringBuilder buffer = new StringBuilder(b.length * 2);
      for (byte v : b)
      {
        int c = (v < 0 ? 256 + v : v);
        buffer.append(NumberStringCache.getHexString(c).toUpperCase());
      }
      newValue = buffer.toString();
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error converting value " + originalValue, th);
      newValue = originalValue;
    }
    return newValue;
  }

  private Object convertRowId(Object value)
  {
    Method valueMethod = stringValueMethod(value);
    if (valueMethod == null) return value.toString();

    try
    {
      Object result = valueMethod.invoke(value);
      return result;
    }
    catch (Throwable th)
    {
      return value.toString();
    }
  }

  private synchronized Method stringValueMethod(Object value)
  {
    if (stringValueMethod == null)
    {
      try
      {
        stringValueMethod = value.getClass().getMethod("stringValue");
      }
      catch (Throwable th)
      {
        // ignore
      }
    }
    return stringValueMethod;
  }

}
