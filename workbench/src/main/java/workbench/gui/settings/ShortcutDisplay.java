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
package workbench.gui.settings;

import workbench.resource.ShortcutDefinition;
import workbench.resource.StoreableKeyStroke;

/**
 *
 * @author Thomas Kellerer
 */
class ShortcutDisplay
{
  private final DisplayType displayType;
  private final ShortcutDefinition shortcut;

  ShortcutDisplay(ShortcutDefinition key, DisplayType type)
  {
    this.shortcut = key;
    this.displayType = type;
  }

  @Override
  public String toString()
  {
    StoreableKeyStroke key = null;
    switch (this.displayType)
    {
      case DEFAULT:
        key = this.shortcut.getDefaultKey();
        break;
      case PRIMARY:
        key = this.shortcut.getActiveKey();
        break;
      case ALTERNATE:
        key = this.shortcut.getAlternateKey();
        break;
    }
    if (key == null) return "";
    return key.toString();
  }

  public static enum DisplayType
  {
    DEFAULT,
    PRIMARY,
    ALTERNATE
  }
}
