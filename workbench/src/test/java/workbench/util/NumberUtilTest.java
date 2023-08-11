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
package workbench.util;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class NumberUtilTest
{

  @Test
  public void testValuesAreEquals()
  {
    assertTrue(NumberUtil.valuesAreEqual(Integer.valueOf(42), Long.valueOf(42)));
    assertTrue(NumberUtil.valuesAreEqual(Short.valueOf((short)5), new BigDecimal(5)));
    assertTrue(NumberUtil.valuesAreEqual(Long.valueOf(5), Double.valueOf(5)));
    assertTrue(NumberUtil.valuesAreEqual(BigInteger.valueOf(42), new BigDecimal(42)));
    assertTrue(NumberUtil.valuesAreEqual(Integer.valueOf(42), BigInteger.valueOf(42)));
    assertFalse(NumberUtil.valuesAreEqual(BigInteger.valueOf(43), new BigDecimal(42)));
    assertFalse(NumberUtil.valuesAreEqual(Integer.valueOf(42), BigInteger.valueOf(2)));
  }

}
