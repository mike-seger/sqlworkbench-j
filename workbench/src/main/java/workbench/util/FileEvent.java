/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
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

import java.io.File;
/**
 *
 * @author Thomas Kellerer
 */
public class FileEvent
{
  private long lastEventTime;
  private long lastFileModifiedTime;

  public FileEvent(File object)
  {
    this.lastFileModifiedTime = object.lastModified();
  }

  public long getLastEventTime()
  {
    return lastEventTime;
  }

  public boolean isRelevant(File toCheck)
  {
    return isInThePast() && isFileChanged(toCheck);
  }

  public boolean isFileChanged(File toCheck)
  {
    return toCheck.lastModified() > lastFileModifiedTime;
  }

  public boolean isInThePast()
  {
    return now() >= lastEventTime + 100;
  }

  public long now()
  {
    return System.currentTimeMillis();
  }

  public void eventOccurred(File toCheck)
  {
    setLastFileModifiedTime(toCheck.lastModified());
    setLastEventTime(now());
  }

  public void setLastEventTime(long lastEventTime)
  {
    this.lastEventTime = lastEventTime;
  }

  public long getLastFileModifiedTime()
  {
    return lastFileModifiedTime;
  }

  public void setLastFileModifiedTime(long lastFileModifiedTime)
  {
    this.lastFileModifiedTime = lastFileModifiedTime;
  }

}
