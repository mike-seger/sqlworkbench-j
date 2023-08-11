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
package workbench.util.download;

import java.beans.XMLDecoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.JProgressBar;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;

import workbench.util.CollectionUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A class to download JDBC drivers (or any artefact actually) from Maven central.
 *
 * @author Thomas Kellerer
 */
public class MavenDownloader
{
  private static final String BASE_SEARCH_URL = "https://search.maven.org/solrsearch/select?";
  private String lastHttpMsg = null;
  private int lastHttpCode = -1;
  private int contentLength = -1;
  private final List<MavenArtefact> knownArtefacts;
  private HttpURLConnection connection;
  private boolean cancelled = false;
  private JProgressBar progressBar;

  public MavenDownloader()
  {
    this.knownArtefacts = retrieveKnownArtefacts();
  }

  public String getLastHttpMsg()
  {
    return lastHttpMsg;
  }

  public int getLastHttpCode()
  {
    return lastHttpCode;
  }

  public int getContentLength()
  {
    return contentLength;
  }

  public void setProgressBar(JProgressBar bar)
  {
    this.progressBar = bar;
  }

  private int getMaxResults(String groupId)
  {
    int defaultMax = Settings.getInstance().getIntProperty("workbench.maven.download.max", 15);
    return Settings.getInstance().getIntProperty("workbench.maven.download.max." + groupId, defaultMax);
  }

  public List<MavenArtefact> getAvailableVersions(String groupId, String artefactId)
  {
    return getAvailableVersions(groupId, artefactId, getMaxResults(groupId));
  }

  public List<MavenArtefact> getAvailableVersions(String groupId, String artefactId, int maxRows)
  {
    String url = buildSearchUrl(groupId, artefactId, maxRows);
    String searchResult = retrieveSearchResult(url);
    MavenResultParser parser = new MavenResultParser(searchResult);
    return parser.getResult();
  }

  private String buildSearchUrl(String groupId, String artefactId, int maxRows)
  {
    return BASE_SEARCH_URL + "q=g:" + groupId + "+AND+a:" + artefactId + "&wt=xml&core=gav&rows=" + maxRows;
  }

  public boolean isCancelled()
  {
    return this.cancelled;
  }

  private String retrieveSearchResult(String searchUrl)
  {
    LogMgr.logInfo(new CallerInfo(){}, "Searching for drivers on Maven Central using: " + searchUrl);
    try
    {
      if (progressBar != null)
      {
        progressBar.setIndeterminate(true);
      }
      Duration timeout = Duration.ofMillis(Settings.getInstance().getIntProperty("workbench.maven.search.timeout", 5000));
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder().
        uri(URI.create(searchUrl)).
        timeout(timeout).
        build();

      HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
      lastHttpCode = response.statusCode();
      lastHttpMsg = "";
      Object body = response.body();
      if (body != null)
      {
        String result = body.toString();
        if (LogMgr.isTraceEnabled())
        {
          LogMgr.logTrace(new CallerInfo(){}, "Search result for URL=" + searchUrl + ":\n" + result);
        }
        return result;
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not search Maven using URL=" + searchUrl, ex);
      lastHttpMsg = ex.getMessage();
    }
    finally
    {
      if (progressBar != null)
      {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setMaximum(0);
      }
    }
    return null;
  }

  public void cancelDownload()
  {
    this.cancelled = true;
    if (this.connection == null) return;
    try
    {
      this.connection.disconnect();
      LogMgr.logDebug(new CallerInfo(){}, "Download cancelled");
    }
    catch (Throwable th)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Error when closing connection");
    }
  }

  private void initProgressBar()
  {
    if (progressBar == null) return;
    if (contentLength > 0)
    {
      progressBar.setIndeterminate(false);
      progressBar.setMaximum(contentLength);
    }
    else
    {
      progressBar.setIndeterminate(true);
      progressBar.setValue(1);
    }
  }

  public void updateProgressBar(int length)
  {
    if (progressBar == null) return;
    if (contentLength < 0) return;
    if (length < 0) return;
    progressBar.setValue(length);
  }

  public long download(MavenArtefact artefact, File targetDir)
  {
    if (artefact == null) return -1;
    if (!artefact.isComplete()) return -1;

    this.cancelled = false;
    String downloadUrl = artefact.buildDownloadUrl();
    String fileName = artefact.buildFilename();
    if (!targetDir.exists())
    {
      LogMgr.logInfo(new CallerInfo(){}, "Target directory " + targetDir.getAbsolutePath() + " does not exist. Creating");
      targetDir.mkdirs();
    }
    WbFile target = new WbFile(targetDir, fileName);

    long bytes = -1;
    try
    {
      WbSwingUtilities.invoke(this::initProgressBar);
      long start = System.currentTimeMillis();

      connection = (HttpURLConnection)new URL(downloadUrl).openConnection();
      lastHttpCode = connection.getResponseCode();
      lastHttpMsg = connection.getResponseMessage();
      contentLength = connection.getContentLength();
      WbSwingUtilities.invoke(this::initProgressBar);

      LogMgr.logDebug(new CallerInfo(){},
        "URL: " + downloadUrl +
        ", HTTP status: " + lastHttpCode +
        ", message: " + lastHttpMsg +
        ", contentLength: " + contentLength);

      int filesize = 0;
      try (InputStream in = connection.getInputStream();
           OutputStream out = new BufferedOutputStream(new FileOutputStream(target));)
      {
        byte[] buffer = new byte[8192];
        int bytesRead = in.read(buffer);
        while (bytesRead != -1)
        {
          filesize += bytesRead;
          out.write(buffer, 0, bytesRead);
          bytesRead = in.read(buffer);
          if (cancelled) break;
          final int len = filesize;
          WbSwingUtilities.invokeLater(() -> {updateProgressBar(len);});
        }
      }
      long duration = System.currentTimeMillis() - start;

      if (!cancelled) bytes = filesize;
      LogMgr.logInfo(new CallerInfo(){},
        "Downloaded \"" + downloadUrl + "\" to \"" + target.getAbsolutePath() + "\", size=" + bytes + "bytes, duration="+ duration + "ms");
    }
    catch (Exception ex)
    {
      if (!cancelled)
      {
        LogMgr.logError(new CallerInfo(){}, "Error saving JAR file to " + target.getFullPath(), ex);
      }
      bytes = -1;
      
      // Make sure we report an error when writing the file as well
      if (lastHttpCode == HttpURLConnection.HTTP_OK)
      {
        lastHttpMsg = ex.getLocalizedMessage();
        lastHttpCode = 500;
      }
    }
    finally
    {
      if (connection != null) connection.disconnect();
    }
    return bytes;
  }

  public List<MavenArtefact> getKnownArtefacts()
  {
    return Collections.unmodifiableList(knownArtefacts);
  }

  public MavenArtefact searchByClassName(String className)
  {
    return searchByClassName(knownArtefacts, className);
  }

  public MavenArtefact searchByClassName(List<MavenArtefact> artefacts, String className)
  {
    if (StringUtil.isBlank(className)) return null;

    return artefacts.
             stream().
             filter(a -> className.equals(a.getDriverClassName())).
             findFirst().orElse(null);
  }

  private List<MavenArtefact> retrieveKnownArtefacts()
  {
    List<MavenArtefact> result = new ArrayList<>();
    try (InputStream in = this.getClass().getResourceAsStream("/workbench/db/MavenDrivers.xml");)
    {
      result.addAll(loadXmlFile(in));
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load built-in Maven definitions", th);
    }
    List<MavenArtefact> external = loadExternalFile();
    mergeArtefacts(result, external);
    return result;
  }

  public void mergeArtefacts(List<MavenArtefact> builtIn, List<MavenArtefact> external)
  {
    if (CollectionUtil.isEmpty(external)) return;

    Iterator<MavenArtefact> itr = builtIn.iterator();
    final CallerInfo ci = new CallerInfo(){};
    while (itr.hasNext())
    {
      MavenArtefact a = itr.next();
      MavenArtefact custom = searchByClassName(external, a.getDriverClassName());
      if (custom != null)
      {
        itr.remove();
        LogMgr.logDebug(ci,
          "Replaced built-in Maven artefact for driver class: " + a.getDriverClassName() +
          " and artefact: " + a.buildQualifier() +
          " with artefact: " + custom.buildQualifier());
      }
    }
    builtIn.addAll(external);
  }

  private List<MavenArtefact> loadXmlFile(InputStream in)
    throws IOException
  {
    List<MavenArtefact> result = new ArrayList<>();
    try
    {
      XMLDecoder d = new XMLDecoder(in);
      Object o = d.readObject();
      if (o instanceof List)
      {
        result.addAll((List)o);
      }
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
    return result;
  }

  private List<MavenArtefact> loadExternalFile()
  {
    File configDir = Settings.getInstance().getConfigDir();
    File xml = new File(configDir, "MavenDrivers.xml");
    return loadExternalFile(xml);
  }

  public List<MavenArtefact> loadExternalFile(File xmlFile)
  {
    if (xmlFile == null || !xmlFile.exists()) return Collections.emptyList();

    try (FileInputStream in = new FileInputStream(xmlFile);)
    {
      return loadXmlFile(in);
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load external Maven definitions: " + xmlFile.getAbsolutePath(), th);
    }
    return Collections.emptyList();
  }
}
