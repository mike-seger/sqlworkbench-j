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
package workbench.db.importer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.MessageBuffer;
import workbench.util.StringUtil;

import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;

/**
 * A SpreadsheetReader for ODS documents using the SODS library.
 *
 * @author Thomas Kellerer
 */
public class SODSReader
  implements SpreadsheetReader
{
  private final Duration oneDay = Duration.of(1, ChronoUnit.DAYS);
  private String nullIndicator;
  private File inputFile;
  private SpreadSheet dataFile;
  private Sheet worksheet;
  private int worksheetIndex;
  private String worksheetName;
  private List<String> headerColumns;
  private MessageBuffer messages = new MessageBuffer();
  private boolean emptyStringIsNull;
  private boolean numbersAsString;

  public SODSReader(File odsFile, int sheetIndex, String name)
  {
    inputFile = odsFile;
    if (sheetIndex > -1 && StringUtil.isBlank(name))
    {
      worksheetIndex = sheetIndex;
    }
    else if (StringUtil.isNotBlank(name))
    {
      worksheetIndex = -1;
      worksheetName = name;
    }
    else
    {
      worksheetIndex = 0;
    }
  }

  @Override
  public void enableRecalcOnLoad(boolean flag)
  {
  }

  @Override
  public void setReturnNumbersAsString(boolean flag)
  {
    numbersAsString = flag;
  }

  @Override
  public void setReturnDatesAsString(boolean flag)
  {
    // we always use Strings anyway
  }

  @Override
  public MessageBuffer getMessages()
  {
    return messages;
  }

  @Override
  public synchronized List<String> getHeaderColumns()
  {
    if (headerColumns == null)
    {
      headerColumns = readHeader();
    }
    return headerColumns;
  }

  @Override
  public void setEmptyStringIsNull(boolean flag)
  {
    emptyStringIsNull = flag;
  }

  private List<String> readHeader()
  {
    List<String> result = new ArrayList<>();

    Range data = worksheet.getDataRange();
    int colCount = data.getNumColumns();

    for (int i=0; i < colCount; i++)
    {
      Range cell = data.getCell(0, i);
      Object title = cell.getValue();

      if (title != null)
      {
        result.add(title.toString());
      }
      else
      {
        result.add("Col" + Integer.toString(i));
      }
    }
    return result;
  }

  @Override
  public void setActiveWorksheet(int index)
  {
    worksheetIndex = index;
    worksheetName = null;
    headerColumns = null;
    initCurrentWorksheet();
  }

  @Override
  public void setActiveWorksheet(String name)
  {
    worksheetIndex = -1;
    worksheetName = name;
    headerColumns = null;
    initCurrentWorksheet();
  }

  private void initCurrentWorksheet()
  {
    if (dataFile == null) return;
    if (worksheetIndex > -1)
    {
      worksheet = dataFile.getSheet(worksheetIndex);
    }
    else if (worksheetName != null)
    {
      worksheet = dataFile.getSheet(worksheetName);
    }
    else
    {
      worksheet = dataFile.getSheet(0);
    }
  }

  @Override
  public List<Object> getRowValues(int row)
  {
    int colCount = worksheet.getMaxColumns();

    List<Object> result = new ArrayList<>(colCount);
    int nullCount = 0;

    for (int col=0; col < colCount; col++)
    {
      Range cell = worksheet.getRange(row, col);
      if (cell.isPartOfMerge())
      {
        LogMgr.logDebug(new CallerInfo(){}, worksheet.getName() +
          ": column:" + cell.getColumn() + ", row:" + cell.getRow() + " is merged. Ignoring row!");
        result.clear();
        break;
      }

      Object value = cell.getValue();

      if (value == null)
      {
        nullCount ++;
      }
      else if (isTimeValue(value))
      {
        Duration duration = (Duration)value;
        value = LocalTime.of(0, 0, 0).
          plus(duration.getSeconds(), ChronoUnit.SECONDS).
          plus(duration.getNano(), ChronoUnit.NANOS);
      }
      else if (isNullString(value.toString()))
      {
        value = null;
      }
      else if (value instanceof Number && numbersAsString)
      {
        value = value.toString();
      }
      result.add(value);
    }

    if (nullCount == result.size())
    {
      result.clear();
    }

    return result;
  }

  private boolean isTimeValue(Object value)
  {
    if (value instanceof Duration)
    {
      Duration duration = (Duration)value;

      if (duration.getSeconds() <= oneDay.getSeconds())
      {
        return true;
      }
    }
    return false;
  }

  private boolean isNullString(String value)
  {
    if (value == null) return true;
    if (emptyStringIsNull && StringUtil.isEmpty(value)) return true;
    return StringUtil.equalString(value, nullIndicator);
  }

  @Override
  public void setNullString(String nullString)
  {
    nullIndicator = nullString;
  }

  @Override
  public int getRowCount()
  {
    if (worksheet == null) return 0;
    return worksheet.getDataRange().getNumRows();
  }

  @Override
  public void done()
  {
    dataFile = null;
    worksheet = null;
  }

  @Override
  public void load()
    throws IOException
  {
    long start = System.currentTimeMillis();
    try (InputStream in = new FileInputStream(inputFile);)
    {
      dataFile = new SpreadSheet(in);
      initCurrentWorksheet();
      long duration = System.currentTimeMillis() - start;
      LogMgr.logDebug(new CallerInfo(){}, "File " + inputFile.getAbsolutePath() + " loaded in " + duration + "ms, rows: " + getRowCount());
    }
    catch (Exception ex)
    {
      throw new IOException("Could not load file " + inputFile.getAbsolutePath(), ex);
    }
  }

  @Override
  public List<String> getSheets()
  {
    List<String> result = new ArrayList<>();
    if (dataFile == null)
    {
      try
      {
        load();
      }
      catch (IOException io)
      {
        LogMgr.logError(new CallerInfo(){}, io.getMessage(), io);
        return result;
      }
    }

    List<Sheet> sheets = dataFile.getSheets();
    for (Sheet sheet : sheets)
    {
      result.add(sheet.getName());
    }
    return result;
  }

}
