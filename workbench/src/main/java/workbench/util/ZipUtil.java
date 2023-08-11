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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

/**
 * @author Thomas Kellerer
 */
public class ZipUtil
{

  /**
   * Test if the given File is a ZIP Archive.
   *
   * <p>This is the same as using <code>getArchiveType(f) == ZipType.ZIP</code>.</p>
   * <p>This does not return true for GZIP archives!</p>
   *
   * @param f the File to test
   * @return true if the file is a ZIP Archive, false otherwise
   * @see #getArchiveType(java.io.File)
   */
  public static boolean isZipFile(File f)
  {
    return getArchiveType(f) == ZipType.ZIP;
  }

  public static ZipType getArchiveType(File f)
  {
    // The JVM crashes (sometimes) if I pass my "fake" ClipboardFile object
    // to the ZipFile constructor, so this is checked beforehand
    if (f instanceof ClipboardFile) return ZipType.None;

    if (!f.exists()) return ZipType.None;

    ZipType result = ZipType.None;

    InputStream in = null;
    try
    {
      in = new FileInputStream(f);
      byte[] buffer = new byte[4];
      int bytes = in.read(buffer);
      if (bytes == 4)
      {
        if (buffer[0] == 'P' && buffer[1] == 'K' && buffer[2] == 3 && buffer[3] == 4)
        {
          // PKZIP format
          result = ZipType.ZIP;
        }
        else if (buffer[0] == (byte)0x1f && buffer[1] == (byte)0x8b)
        {
          // GZIP format
          result = ZipType.GZIP;
        }
      }
    }
    catch (Throwable e)
    {
      result = ZipType.None;
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
    return result;
  }

  /**
   * Get the directory listing of a zip archive.
   *
   * Sub-Directories are not scanned.
   *
   * @param archive
   * @return a list of filenames contained in the archive
   */
  public static List<String> getFiles(File archive)
    throws IOException
  {
    ZipFile zip = new ZipFile(archive);
    List<String> result = new ArrayList<>(zip.size());

    try
    {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements())
      {
        ZipEntry entry = entries.nextElement();
        result.add(entry.getName());
      }
    }
    finally
    {
      zip.close();
    }
    return result;
  }

  public static void closeQuitely(ZipFile file)
  {
    if (file == null) return;
    try
    {
      file.close();
    }
    catch (Throwable th)
    {
      // ignore
    }
  }

  public static boolean isValid(File file, Map<String, Long> crcValues)
  {
    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logDebug(ci, "Testing archive: " + WbFile.getPathForLogging(file));

    try (ZipFile zipfile = new ZipFile(file);
         ZipInputStream zis = new ZipInputStream(new FileInputStream(file)))
    {
      int entries = 0;
      ZipEntry ze = zis.getNextEntry();
      while (ze != null)
      {
        zipfile.getInputStream(ze);
        ze.getCompressedSize();
        String name = ze.getName();
        long crc = ze.getCrc();
        Long expectedCrc = crcValues.get(name);
        if (expectedCrc != null && crc != expectedCrc.longValue())
        {
          LogMgr.logError(ci,
            "The CRC value for entry " + name + " in " + WbFile.getPathForLogging(file) +  " is: " + crc + " but " + expectedCrc + " was expected!", null);
        }
        else
        {
          entries ++;
        }
        ze = zis.getNextEntry();
      }
      return entries > 0;
    }
    catch (Exception e)
    {
      LogMgr.logError(ci, "An error occurred while testing archive: " + WbFile.getPathForLogging(file), e);
      return false;
    }
  }

  /**
   * Creates a ZIP archive with all files in the given directory.
   *
   * Sub-Directories are not included.
   *
   * @param sourceDir   the directory to zip
   * @param targetZip   the target ZIP file.
   * @throws IOException
   */
  public static void zipDirectory(File sourceDir, File targetZip)
    throws IOException
  {
    if (sourceDir == null || !sourceDir.exists()) return;

    try (FileOutputStream fout = new FileOutputStream(targetZip);
         ZipOutputStream zout = new ZipOutputStream(fout);)
    {
      zout.setLevel(Settings.getInstance().getIntProperty("workbench.workspace.compression", 5));
      zout.setComment("SQL Workbench/J Workspace backup");
      File[] files = sourceDir.listFiles();
      for (File f : files)
      {
        ZipEntry entry = new ZipEntry(f.getName());
        zout.putNextEntry(entry);
        copyToZip(f, zout);
      }
    }
    catch (Exception e)
    {
      if (e instanceof IOException)
      {
        throw (IOException)e;
      }
      throw new IOException("Could not create ZIP file", e);
    }
  }

  public static void unzipToDirectory(File zip, File targetDirectory)
    throws IOException
  {

    try (ZipFile archive = new ZipFile(zip);)
    {
      Enumeration<? extends ZipEntry> entries = archive.entries();
      while (entries.hasMoreElements())
      {
        ZipEntry entry = entries.nextElement();
        InputStream in = archive.getInputStream(entry);
        File target = new File(targetDirectory, entry.getName());
        FileOutputStream out = new FileOutputStream(target);
        FileUtil.copy(in, out);
      }
    }
  }

  private static void copyToZip(File source, ZipOutputStream zout)
    throws IOException
  {
    try (FileInputStream in = new FileInputStream(source);)
    {
      FileUtil.copy(in, zout, false);
    }
    finally
    {
      zout.closeEntry();
    }
  }

}
