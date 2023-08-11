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

import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

/**
 * @author  Thomas Kellerer
 */
public class WbAESCipher
  implements WbCipher
{
  private static final byte[] SALT = new byte[] {42,10,33,17,8,52,-17,19,26,65,-42,17,-88};
  private static final byte[] IV = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

  private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
  private Cipher encrypter;
  private Cipher decrypter;

  public WbAESCipher(String password)
  {
    long start = System.currentTimeMillis();
    try
    {
      decrypter = Cipher.getInstance(ALGORITHM);
      encrypter = Cipher.getInstance(ALGORITHM);
      IvParameterSpec ivspec = new IvParameterSpec(IV);
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, 65536, 256);
      SecretKey tmp = factory.generateSecret(spec);
      SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
      decrypter.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
      encrypter.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "No encryption available!", e);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Initializing WbAESCipher took " + duration + "ms");
  }

  @Override
  public String decryptString(String toDecrypt)
  {
    if (StringUtil.isEmpty(toDecrypt)) return toDecrypt;

    try
    {
      byte[] decoded = Base64.getDecoder().decode(toDecrypt);
      byte[] decrypted = decrypter.doFinal(decoded);
      return new String(decrypted, "UTF-8");
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not decrypt", e);
      return toDecrypt;
    }
  }

  @Override
  public String encryptString(String toEncrypt)
  {
    if (StringUtil.isEmpty(toEncrypt)) return toEncrypt;

    try
    {
      byte[] encrypted = encrypter.doFinal(toEncrypt.getBytes("UTF-8"));
      return Base64.getEncoder().encodeToString(encrypted);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not encrypt", e);
      return toEncrypt;
    }
  }

}
