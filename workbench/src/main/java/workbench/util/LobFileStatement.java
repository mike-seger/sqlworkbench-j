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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.Blob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.BlobAccessType;
import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

/**
 * A class to analyze the {$blobfile= } and {$clobfile= } parameters in a SQL statement.
 *
 * This class supports INSERT and UPDATE statements.
 * To retrieve a blob from the database {@link workbench.sql.wbcommands.WbSelectBlob} has to be used.
 *
 * @author Thomas Kellerer
 */
public class LobFileStatement
  implements AutoCloseable
{
  public static final String MARKER = "\\{\\$[cb]lobfile=[^\\}]*\\}";
  private String sqlToUse;
  private final List<LobFileParameter> parameters = new ArrayList<>();

  public LobFileStatement(String sql)
    throws FileNotFoundException
  {
    this(sql, null);
  }

  public LobFileStatement(String sql, final String dir)
    throws FileNotFoundException
  {
    LobFileParameterParser p = new LobFileParameterParser(sql);
    this.parameters.addAll(p.getParameters());

    if (parameters.isEmpty() && (sql.contains("{$clob") || sql.contains("{$blob")))
    {
      String msg = ResourceMgr.getString("ErrUpdateBlobSyntax");
      throw new IllegalArgumentException(msg);
    }
    if (this.parameters.isEmpty()) return;

    for (LobFileParameter parameter : this.parameters)
    {
      if (parameter.getFilename() == null)
      {
        String msg = ResourceMgr.getString("ErrUpdateBlobNoFileParameter");
        throw new FileNotFoundException(msg);
      }
      File f = new File(parameter.getFilename());

      if (!f.isAbsolute() && dir != null)
      {
        f = new File(dir, parameter.getFilename());
        parameter.setFilename(f.getAbsolutePath());
      }

      if (f.isDirectory() || !f.exists())
      {
        String msg = ResourceMgr.getFormattedString("ErrFileNotFound", parameter.getFilename());
        throw new FileNotFoundException(msg);
      }
    }
    this.sqlToUse = sql.replaceAll(MARKER, " ? ");
  }

  public String getPreparedSql()
  {
    return sqlToUse;
  }

  public List<LobFileParameter> getParameters()
  {
    return Collections.unmodifiableList(parameters);
  }

  public int getParameterCount()
  {
    return parameters.size();
  }

  public boolean containsParameter()
  {
    return (parameters.size() > 0);
  }

  public PreparedStatement prepareStatement(WbConnection conn)
    throws SQLException, IOException
  {
    if (this.parameters.isEmpty()) return null;

    boolean supportsMeta = conn.getDbSettings().supportsParameterMetaData();

    PreparedStatement pstmt = conn.getSqlConnection().prepareStatement(sqlToUse);
    ParameterMetaData meta = null;
    try
    {
      if (supportsMeta)
      {
        meta = pstmt.getParameterMetaData();
      }
    }
    catch (SQLException sql)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Cannot obtain parameter meta data", sql);
    }

    final int buffSize = 64*1024;
    for (int i = 0; i < parameters.size(); i++)
    {
      LobFileParameter param = parameters.get(i);
      File f = new File(param.getFilename());
      int length = (int)f.length();
      if (param.isBinary())
      {
        InputStream in = new BufferedInputStream(new FileInputStream(f), buffSize);
        BlobAccessType method = conn.getDbSettings().getBlobReadMethod();
        switch (method)
        {
          case byteArray:
            byte[] data = FileUtil.readBytes(in);
            pstmt.setBytes(i + 1, data);
            break;
          case jdbcBlob:
            Blob blob = conn.getSqlConnection().createBlob();
            byte[] content = FileUtil.readBytes(in);
            blob.setBytes(1, content);
            pstmt.setBlob(i+1, blob);
            break;
          default:
            param.setDataStream(in);
            pstmt.setBinaryStream(i + 1, in, length);
        }
      }
      else
      {
        // createBufferedReader can handle null for the encoding
        Reader in = EncodingUtil.createBufferedReader(f, param.getEncoding());

        // The value of the length parameter is actually wrong if
        // a multi-byte encoding is used. So far only Derby seems to choke
        // on this, so we need to calculate the file length in characters
        // which is probably very slow. So this is not turned on by default.
        if (conn.getDbSettings().needsExactClobLength())
        {
          length = (int) FileUtil.getCharacterLength(f, param.getEncoding());
        }

        if (meta != null && SqlUtil.isXMLType(meta.getParameterType(i+1)))
        {
          // createXML closes the stream, so there is no need to keep the reference in the LobFileParameter instance
          SQLXML xml = JdbcUtils.createXML(in, conn);
          pstmt.setSQLXML(i+1, xml);
        }
        else
        {
          param.setDataStream(in);
          pstmt.setCharacterStream(i+1, in, length);
        }
      }
    }
    return pstmt;
  }

  @Override
  public void close()
    throws Exception
  {
    done();
  }

  public void done()
  {
    for (LobFileParameter parameter : parameters)
    {
      if (parameter != null)
      {
        parameter.close();
      }
    }
  }

}
