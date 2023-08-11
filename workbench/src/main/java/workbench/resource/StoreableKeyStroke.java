/*
 * StoreableKeyStroke.java
 *
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
package workbench.resource;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import workbench.util.StringUtil;

/**
 * A class which wraps keyStroke and can be serialized
 * using the XMLDecode/XMLEncoder classes
 *
 * @author Thomas Kellerer
 */
public class StoreableKeyStroke
{
  private int keyCode;
  private int modifier;

  private boolean keyCodeSet = false;
  private boolean modifierSet = false;

  public StoreableKeyStroke()
  {
  }

  public StoreableKeyStroke(int keyCode, int modifier)
  {
    this.keyCode = keyCode;
    this.modifier = modifier;
    this.modifierSet = true;
    this.keyCodeSet = true;
  }

  public StoreableKeyStroke(KeyStroke aKey)
  {
    this.keyCode = aKey.getKeyCode();
    this.modifier = aKey.getModifiers();
    this.modifierSet = true;
    this.keyCodeSet = true;
  }

  public KeyStroke getKeyStroke()
  {
    if (keyCodeSet || modifierSet)
    {
      return KeyStroke.getKeyStroke(this.keyCode, this.modifier);
    }
    return null;
  }

  public int getKeyCode()
  {
    KeyStroke theKey = getKeyStroke();
    if (theKey != null) return theKey.getKeyCode();
    return 0;
  }

  public void setKeyCode(int c)
  {
    this.keyCode = c;
    this.keyCodeSet = true;
  }

  public void setKeyModifier(int mod)
  {
    this.modifier = mod;
    this.modifierSet = true;
  }

  public int getKeyModifier()
  {
    KeyStroke theKey = getKeyStroke();
    if (theKey != null) return theKey.getModifiers();
    return 0;
  }

  public boolean equals(KeyStroke other)
  {
    KeyStroke thisKey = getKeyStroke();
    if (thisKey == null && other == null) return true;
    return thisKey.equals(other);
  }

  public boolean equals(StoreableKeyStroke other)
  {
    KeyStroke thisKey = getKeyStroke();
    KeyStroke otherKey = other.getKeyStroke();
    if (thisKey == null && otherKey == null) return true;
    if (thisKey == null && otherKey != null) return false;
    if (thisKey != null && otherKey == null) return false;
    return thisKey.equals(otherKey);
  }

  @Override
  public boolean equals(Object other)
  {
    if (other instanceof StoreableKeyStroke)
    {
      return equals((StoreableKeyStroke)other);
    }
    else if (other instanceof KeyStroke)
    {
      return equals((KeyStroke)other);
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    int hash = 7;
    hash = 17 * hash + this.keyCode;
    hash = 17 * hash + this.modifier;
    return hash;
  }

  @Override
  public String toString()
  {
    return displayString(getKeyStroke());
  }

  public static String displayString(KeyStroke key)
  {
    if (key == null) return "";

    int modifier = key.getModifiers();
    int code = key.getKeyCode();

    String text = KeyEvent.getKeyText(code);
    if (modifier != 0)
    {
      text = InputEvent.getModifiersExText(modifier) + "-" + text;
    }
    return text;
  }

  public String toPropertiesString()
  {
    return getKeyCode() + "," + getKeyModifier();
  }

  public static StoreableKeyStroke fromPropertiesString(String data)
  {
    if (StringUtil.isBlank(data)) return null;
    String[] items = data.split(",");
    if (items.length < 1) return null;

    int code = 0;
    int modifier = 0;
    if (items.length > 0)
    {
      code = StringUtil.getIntValue(items[0], Integer.MIN_VALUE);
    }
    if (items.length > 1)
    {
      modifier = StringUtil.getIntValue(items[1], 0);
    }

    if (code > 0)
    {
      return new StoreableKeyStroke(code, modifier);
    }
    return null;
  }

  public StoreableKeyStroke createCopy()
  {
    StoreableKeyStroke copy = new StoreableKeyStroke();
    copy.keyCode = this.keyCode;
    copy.modifier = this.modifier;
    copy.modifierSet = this.modifierSet;
    copy.keyCodeSet = this.keyCodeSet;
    return copy;
  }
}
