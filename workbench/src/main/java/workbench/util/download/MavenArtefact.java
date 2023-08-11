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

import java.io.Serializable;
import java.util.Objects;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class MavenArtefact
  implements Serializable
{
  private static final String BASE_URL = "https://search.maven.org/remotecontent?filepath=";
  private String groupId;
  private String artefactId;
  private String version;
  private String productName;
  private String driverClassName;

  public MavenArtefact()
  {
  }

  public MavenArtefact(String name)
  {
    this.productName = name;
  }

  public MavenArtefact(String groupId, String artefact, String version)
  {
    this.groupId = groupId;
    this.artefactId = artefact;
    this.version = version;
  }

  public boolean isComplete()
  {
    return StringUtil.isNotBlank(groupId) &&
           StringUtil.isNotBlank(artefactId) &&
           StringUtil.isNotBlank(version);
  }

  public String getDriverClassName()
  {
    return driverClassName;
  }

  public void setDriverClassName(String driverClassName)
  {
    this.driverClassName = driverClassName;
  }

  public String getProductName()
  {
    return productName;
  }

  public void setProductName(String productName)
  {
    this.productName = productName;
  }

  public void setGroupId(String groupId)
  {
    this.groupId = groupId;
  }

  public void setArtefactId(String artefactId)
  {
    this.artefactId = artefactId;
  }

  public void setVersion(String version)
  {
    this.version = version;
  }

  public String getGroupId()
  {
    return groupId;
  }

  public String getArtefactId()
  {
    return artefactId;
  }

  public String getVersion()
  {
    return version;
  }

  public String buildQualifier()
  {
    return groupId + ":" + artefactId;
  }

  public String buildFilename()
  {
    return artefactId + "-" + version + ".jar";
  }

  public String buildDownloadUrl()
  {
    return BASE_URL +
      groupId.replace('.', '/') + "/" + artefactId + "/" + version + "/" + buildFilename();

  }

  @Override
  public String toString()
  {
    return groupId + ":" + artefactId + (version == null ? "" : ":" + version);
  }

  @Override
  public int hashCode()
  {
    int hash = 3;
    hash = 89 * hash + Objects.hashCode(this.groupId);
    hash = 89 * hash + Objects.hashCode(this.artefactId);
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final MavenArtefact other = (MavenArtefact)obj;
    if (!Objects.equals(this.groupId, other.groupId)) return false;
    return Objects.equals(this.artefactId, other.artefactId);
  }

}
