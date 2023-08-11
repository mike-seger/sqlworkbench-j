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


import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class PGProcNameTest
{

  @Test
  public void testParse()
  {
    String procname = "my_func(integer, varchar)";
    PGProcName proc = new PGProcName(procname);
    assertEquals("my_func", proc.getName());
    assertEquals(2, proc.getArguments().size());
    assertEquals("integer", proc.getArguments().get(0).argType);
    assertEquals("varchar", proc.getArguments().get(1).argType);

    ArgInfo info2 = new ArgInfo("one;two;three", "integer;integer;character varying", "i;i;i");
    PGProcName proc2 = new PGProcName("func_2", info2);
    assertEquals("func_2", proc2.getName());
    assertEquals(3, proc2.getArguments().size());
    assertEquals("integer", proc2.getArguments().get(0).argType);
    assertEquals("integer", proc2.getArguments().get(1).argType);
    assertEquals("varchar", proc2.getArguments().get(2).argType);
  }

}
