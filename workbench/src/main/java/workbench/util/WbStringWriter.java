/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
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
package workbench.util;

import java.io.IOException;
import java.io.Writer;

/**
 * A StringWriter that uses a StringBuilder rather than a StringBuffer.
 *
 * @author Thomas Kellerer
 */
public class WbStringWriter
  extends Writer
{
  private final StringBuilder buffer;

  public WbStringWriter()
  {
    this(100);
  }

  public WbStringWriter(int intialCapacity)
  {
    this.buffer = new StringBuilder(intialCapacity);
    this.lock = buffer;
  }

  @Override
  public void write(String str)
  {
    buffer.append(str);
  }

  @Override
  public void write(String str, int off, int len)
  {
    buffer.append(str, off, off + len);
  }

  @Override
  public void write(int c)
  {
    buffer.append((char)c);
  }

  @Override
  public void write(char[] cbuf, int off, int len)
    throws IOException
  {
    if (len == 0) return;
    buffer.append(cbuf, off, len);
  }

  @Override
  public WbStringWriter append(CharSequence sequence, int start, int end)
  {
    if (sequence == null) return append("null");
    return append(sequence.subSequence(start, end));
  }

  @Override
  public WbStringWriter append(CharSequence sequence)
  {
    buffer.append(sequence);
    return this;
  }

  @Override
  public WbStringWriter append(char ch)
  {
    buffer.append(ch);
    return this;
  }

  @Override
  public void flush()
    throws IOException
  {
  }

  @Override
  public void close()
    throws IOException
  {
  }

  @Override
  public String toString()
  {
    return buffer.toString();
  }

}
