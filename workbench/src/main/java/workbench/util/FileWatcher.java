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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * A class to monitor a file or a list of directories for changes.
 *
 * @author Thomas Kellerer
 * @see FileEvent
 * @see FileWatcherFactory
 */
public class FileWatcher
{
  private final List<PropertyChangeListener> listenerList = new ArrayList<>();
  private final File toWatch;
  private boolean isActive;
  private Thread worker;
  private final List<WatchKey> registration = new ArrayList<>();
  private final Map<File, FileEvent> events = new HashMap<>();
  private boolean suspended;

  public FileWatcher(File target)
  {
    this.toWatch = target;
  }

  public void start()
  {
    if (worker != null && isActive)
    {
      LogMgr.logWarning(new CallerInfo(){}, "start() called while watcher is active", new Exception("Backtrace"));
      stop();
    }
    worker = new WbThread(this::watch, "FileWatcher for " + toWatch.getName());
    isActive = true;
    suspended = false;
    worker.start();
  }

  public synchronized void removeChangeListener(PropertyChangeListener listener)
  {
    if (listener != null)
    {
      listenerList.remove(listener);
    }
  }

  public synchronized void addChangeListener(PropertyChangeListener listener)
  {
    if (listener != null && !listenerList.contains(listener))
    {
      listenerList.add(listener);
    }
  }

  public synchronized void setSuspended(boolean flag)
  {
    suspended = flag;
  }

  /**
   * Stops this FileWatcher, sets it to inactive and removes all change listeners.
   */
  public void stop()
  {
    LogMgr.logDebug(new CallerInfo(){}, "Stopping FileWatcher for: " + toWatch.getAbsolutePath());
    this.isActive = false;
    if (worker != null)
    {
      worker.interrupt();
      worker = null;
    }
    resetRegistration();
    listenerList.clear();
  }

  private synchronized void resetRegistration()
  {
    for (WatchKey key : registration)
    {
      try { key.cancel(); } catch (Exception ex) {}
    }
    registration.clear();
  }

  private void fireFileChanged(File f, String what)
  {
    if (isActive && !suspended)
    {
      PropertyChangeEvent evt = new PropertyChangeEvent(this, what, null, f);
      for (PropertyChangeListener l : listenerList)
      {
        l.propertyChange(evt);
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void watch()
  {
    LogMgr.logDebug(new CallerInfo(){}, "Starting file watcher for " + toWatch.getAbsolutePath());

    try (WatchService watcher = FileSystems.getDefault().newWatchService())
    {
      registerDirectories(watcher);

      while (isActive)
      {
        WatchKey key = watcher.take();
        if (!isActive) break;

        if (key == null)
        {
          Thread.yield();
          continue;
        }

        for (WatchEvent<?> event : key.pollEvents())
        {
          if (!isActive) break;

          WatchEvent.Kind<?> kind = event.kind();
          Path dir = (Path)key.watchable();
          WatchEvent<Path> ev = (WatchEvent<Path>)event;
          Path file = ev.context();
          File modified = new File(dir.toFile(), file.getFileName().toString());

          if (kind == OVERFLOW || event.count() > 1)
          {
            Thread.yield();
            continue;
          }

          if (kind == ENTRY_MODIFY && isWatched(modified) && isModifiedLater(modified))
          {
            fireFileChanged(modified, "fileModified");
          }
          else if (kind == ENTRY_DELETE && isWatched(modified))
          {
            fireFileChanged(modified, "fileDeleted");
          }

          boolean valid = key.reset();
          if (!valid)
          {
            break;
          }
          Thread.yield();
        }

      }
    }
    catch (InterruptedException e)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Watch thread stopped");
    }
    catch (Exception ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error when watching: " + toWatch.getAbsolutePath(), ex);
    }
    finally
    {
      worker = null;
      isActive = false;
      resetRegistration();
    }
  }

  private boolean isWatched(File modified)
  {
    if (modified == null) return false;

    if (toWatch.isDirectory())
    {
      return true;
    }
    return modified.equals(toWatch);
  }

  /*
   * The WatchService sends multiple events for the same file.
   *
   * To avoid sending duplicate PropertyChangeEvents we are checking
   * the most recent "lastModified" of the files being watched
   * with the lastModified time of the event's file.
   */
  private synchronized boolean isModifiedLater(File toCheck)
  {
    FileEvent lastEvent = events.get(toCheck.getAbsoluteFile());
    if (lastEvent == null)
    {
      FileEvent evt = new FileEvent(toCheck);
      evt.eventOccurred(toCheck);
      events.put(toCheck.getAbsoluteFile(), evt);
      return true;
    }
    boolean wasChanged = lastEvent.isRelevant(toCheck);
    lastEvent.eventOccurred(toCheck);
    return wasChanged;
  }

  private void registerDirectories(WatchService watcher)
  {
    events.clear();
    try
    {
      if (this.toWatch.isFile())
      {
        Path path = toWatch.getParentFile().toPath();
        WatchKey key = path.register(watcher, ENTRY_MODIFY);
        registration.add(key);
        FileEvent evt = new FileEvent(toWatch);
        events.put(toWatch.getAbsoluteFile(), evt);
      }
      else if (toWatch.isDirectory())
      {
        File[] dirs = toWatch.listFiles((File f) -> f != null && f.isDirectory());
        for (File dir : dirs)
        {
          Path path = dir.toPath();
          LogMgr.logDebug(new CallerInfo(){}, "Registered sub-directory " + path.toString());
          WatchKey key = path.register(watcher, ENTRY_MODIFY, ENTRY_DELETE);
          registration.add(key);
        }
      }
      LogMgr.logInfo(new CallerInfo(){}, "File watcher initialized for " + toWatch.getAbsolutePath());
    }
    catch (IOException io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not register source: " + toWatch.getAbsolutePath(), io);
    }
  }
}
