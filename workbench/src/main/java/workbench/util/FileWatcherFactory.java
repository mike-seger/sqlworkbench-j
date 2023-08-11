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

import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * A class to manage FileWatcher instances per watched file.
 *
 * @author Thomas Kellerer
 * @see FileWatcher
 */
public class FileWatcherFactory
{
  private final Map<File, FileWatcher> watchers = new HashMap<>();

  private final Object lock = new Object();

  private static class InstanceHolder
  {
    protected static FileWatcherFactory instance = new FileWatcherFactory();
  }

  private FileWatcherFactory()
  {
  }

  public static FileWatcherFactory getInstance()
  {
    return InstanceHolder.instance;
  }

  public void stopAllWatchers()
  {
    synchronized (lock)
    {
      for (FileWatcher w : watchers.values())
      {
        w.stop();
      }
      watchers.clear();
    }
  }

  /**
   * Adds a change listener for the given file.
   *
   * If no FileWatcher was initialized for the given file, nothing happens.
   *
   * @see #registerWatcher(File, PropertyChangeListener)
   */
  public void addChangeListener(PropertyChangeListener listener, File toWatch)
  {
    if (toWatch == null || listener == null) return;

    synchronized (lock)
    {
      FileWatcher watcher = watchers.get(toWatch.getAbsoluteFile());
      if (watcher != null)
      {
        watcher.addChangeListener(listener);
      }
    }
  }

  /**
   * Removes a change listener for the given file.
   *
   * If no FileWatcher was initialized for the given file, nothing happens.
   *
   * @see #addChangeListener(PropertyChangeListener, File)
   * @see #registerWatcher(File, PropertyChangeListener)
   */
  public void removeChangeListener(File toWatch, PropertyChangeListener listener)
  {
    if (toWatch == null || listener == null) return;

    synchronized (lock)
    {
      FileWatcher watcher = watchers.get(toWatch.getAbsoluteFile());
      if (watcher != null)
      {
        watcher.removeChangeListener(listener);
      }
    }
  }

  /**
   * Creates and starts a FileWatcher for the given file and adds listener.
   *
   * @see #addChangeListener(PropertyChangeListener, File)
   */
  public FileWatcher registerWatcher(File toWatch, PropertyChangeListener listener)
  {
    if (toWatch == null) return null;

    synchronized (lock)
    {
      File f = toWatch.getAbsoluteFile();
      FileWatcher watcher = watchers.get(f);
      if (watcher == null)
      {
        watcher = new FileWatcher(f);
        watchers.put(f, watcher);
        watcher.start();
      }
      watcher.addChangeListener(listener);
      return watcher;
    }
  }

  public void suspendWatcher(File f)
  {
    setSuspended(f, true);
  }

  public void continueWatcher(File f)
  {
    setSuspended(f, false);
  }

  private void setSuspended(File f, boolean flag)
  {
    if (f == null) return;

    synchronized (lock)
    {
      FileWatcher watcher = watchers.get(f.getAbsoluteFile());
      if (watcher != null)
      {
        watcher.setSuspended(flag);
      }
    }
  }


}
