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
package workbench.ssh;

import java.io.Serializable;
import java.util.Objects;

import workbench.resource.Settings;

import workbench.db.PasswordTrimType;

import workbench.util.GlobalPasswordManager;
import workbench.util.StringUtil;

import static workbench.db.ConnectionProfile.*;

/**
 *
 * @author Thomas Kellerer
 */
public class SshHostConfig
  implements Serializable
{
  private String sshHost;
  private String password;
  private String username;
  private String privateKeyFile;
  private String configName;
  private String temporaryPassword;

  private int sshPort = PortForwarder.DEFAULT_SSH_PORT;
  private boolean tryAgent;

  public SshHostConfig()
  {
  }

  public SshHostConfig(String configName)
  {
    this.configName = StringUtil.trimToNull(configName);
  }

  public boolean isGlobalConfig()
  {
    return this.configName != null;
  }

  public int getSshPort()
  {
    return sshPort;
  }

  public void setSshPort(int port)
  {
    if (port > 0 && port != sshPort)
    {
      sshPort = port;
    }
  }

  public boolean getTryAgent()
  {
    return tryAgent;
  }

  public void setTryAgent(boolean flag)
  {
    tryAgent = flag;
  }

  public void setTemporaryPassword(String temporaryPassword)
  {
    this.temporaryPassword = temporaryPassword;
  }

  public void clearTemporaryPassword()
  {
    this.temporaryPassword = null;
  }

  public boolean hasTemporaryPassword()
  {
    return this.temporaryPassword != null;
  }

  public String getHostname()
  {
    return sshHost;
  }

  public void setHostname(String sshHost)
  {
    this.sshHost = sshHost;
  }

  public void setEncryptedPassword(String pwd)
  {
    this.password = pwd;
  }

  public String getDecryptedPassword()
  {
    if (temporaryPassword != null) return temporaryPassword;
    return decryptPassword(password);
  }

  private String decryptPassword(String pwd)
  {
    if (pwd == null) return null;

    if (pwd.startsWith(MASTER_CRYPT_PREFIX))
    {
      return GlobalPasswordManager.getInstance().decrypt(pwd.substring(MASTER_CRYPT_PREFIX.length()));
    }
    return pwd;
  }

  public String getPassword()
  {
    return password;
  }

  public void setPassword(String pwd)
  {
    if (pwd != null)
    {
      PasswordTrimType trimType = Settings.getInstance().getPassworTrimType();

      if (trimType == PasswordTrimType.always)
      {
        pwd = pwd.trim();
      }
      else if (trimType == PasswordTrimType.blankOnly && StringUtil.isBlank(pwd))
      {
        pwd = "";
      }
    }

    if (StringUtil.isNotEmpty(pwd))
    {
      // check encryption settings when reading the profiles...
      if (Settings.getInstance().getUseMasterPassword())
      {
        if (!isEncrypted(pwd))
        {
          pwd = MASTER_CRYPT_PREFIX + GlobalPasswordManager.getInstance().encrypt(pwd);
        }
      }
      else
      {
        // no encryption should be used, but password is encrypted, decrypt it now.
        if (this.isEncrypted(pwd))
        {
          pwd = decryptPassword(pwd);
        }
      }
    }
    this.password = pwd;
  }

  private boolean isEncrypted(String aPwd)
  {
    if (aPwd == null) return false;
    return aPwd.startsWith(MASTER_CRYPT_PREFIX);
  }

  public String getUsername()
  {
    return username;
  }

  public void setUsername(String username)
  {
    this.username = username;
  }

  public String getPrivateKeyFile()
  {
    return privateKeyFile;
  }

  public void setPrivateKeyFile(String privateKeyFile)
  {
    this.privateKeyFile = StringUtil.trimToNull(privateKeyFile);
  }

  public String getConfigName()
  {
    return configName;
  }

  public void setConfigName(String configName)
  {
    this.configName = StringUtil.trimToNull(configName);
  }

  public boolean isValid()
  {
    return this.sshHost != null && this.username != null;
  }

  public SshHostConfig createCopy()
  {
    SshHostConfig copy = new SshHostConfig();
    copy.sshHost = this.sshHost;
    copy.password = this.password;
    copy.temporaryPassword = this.temporaryPassword;
    copy.privateKeyFile = this.privateKeyFile;
    copy.sshPort = this.sshPort;
    copy.tryAgent = this.tryAgent;
    copy.username = this.username;
    copy.configName = this.configName;
    return copy;
  }

  @Override
  public int hashCode()
  {
    int hash = 3;
    hash = 37 * hash + Objects.hashCode(this.sshHost);
    hash = 37 * hash + Objects.hashCode(this.username);
    hash = 37 * hash + Objects.hashCode(this.privateKeyFile);
    hash = 37 * hash + Objects.hashCode(this.configName);
    hash = 37 * hash + this.sshPort;
    hash = 37 * hash + Boolean.hashCode(tryAgent);
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final SshHostConfig other = (SshHostConfig)obj;

    if (this.sshPort != other.sshPort) return false;
    if (this.tryAgent != other.tryAgent) return false;
    if (!Objects.equals(this.sshHost, other.sshHost)) return false;
    if (!Objects.equals(this.username, other.username)) return false;
    if (!Objects.equals(this.privateKeyFile, other.privateKeyFile)) return false;
    if (!Objects.equals(this.configName, other.configName)) return false;

    return true;
  }

  public String getInfoString()
  {
    if (sshPort == 0 || sshPort == PortForwarder.DEFAULT_SSH_PORT)
    {
      return sshHost;
    }
    return String.format("%s:%s", sshHost, sshPort);
  }

  @Override
  public String toString()
  {
    return configName;
  }

}
