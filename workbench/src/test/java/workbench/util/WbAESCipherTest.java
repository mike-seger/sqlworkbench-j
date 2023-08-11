/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class WbAESCipherTest
{

  @Test
  public void testCipher()
  {
    String input = "A string with special characters §°öäüß";
    String pwd = "SuperSecretPassword";
    WbCipher cipher = new WbAESCipher(pwd);

    String encrypted = cipher.encryptString(input);
    String decrypted = cipher.decryptString(encrypted);
    assertEquals(input, decrypted);

    input = "Some12354Password";
    cipher = new WbAESCipher(input);

    encrypted = cipher.encryptString(input);
    decrypted = cipher.decryptString(encrypted);
    assertEquals(input, decrypted);

    cipher = new WbAESCipher("WrongPassword");
    decrypted = cipher.decryptString(encrypted);
    assertNotSame(decrypted, input);

    cipher = new WbAESCipher(input);
    assertEquals(encrypted, cipher.encryptString(input));
  }

  public static void main(String[] args)
  {
    WbCipher cipher = new WbAESCipher("secret");
    System.out.println(cipher.encryptString("secret"));
  }
}
