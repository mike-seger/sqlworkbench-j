package workbench.util;

import java.io.*;

/**
 * Generic unicode textreader, which will use BOM mark to identify the encoding to be used.
 *
 * Original pseudocode   : Thomas Weidenfeller
 * Implementation tweaked: Aki Nieminen
 *
 * https://www.unicode.org/unicode/faq/utf_bom.html
 * BOMs:
 * 00 00 FE FF    = UTF-32, big-endian
 * FF FE 00 00    = UTF-32, little-endian
 * FE FF          = UTF-16, big-endian
 * FF FE          = UTF-16, little-endian
 * EF BB BF       = UTF-8
 *
 * Win2k Notepad:
 * Unicode format = UTF-16LE
 **/
public class UnicodeReader
  extends Reader
{
  private PushbackInputStream internalIn;
  private InputStreamReader internalIn2 = null;
  private String defaultEnc;
  private boolean hasBOM = true;

  private static final int BOM_SIZE = 4;

  public UnicodeReader(InputStream in, String encoding)
    throws IOException
  {
    super();
    this.internalIn = new PushbackInputStream(in, BOM_SIZE);
    this.defaultEnc = encoding;
    this.init();
  }

  public String getDefaultEncoding()
  {
    return defaultEnc;
  }

  public String getEncoding()
  {
    if (internalIn2 == null) return null;
    return internalIn2.getEncoding();
  }

  public boolean hasBOM()
  {
    return hasBOM;
  }

  /**
   * Read-ahead four bytes and check for BOM marks. Extra bytes are
   * unread back to the stream, only BOM bytes are skipped.
   */
  protected final void init() throws IOException
  {
    if (internalIn2 != null) return;

    String encoding;
    byte[] bom = new byte[BOM_SIZE];
    int n, unread;
    n = internalIn.read(bom, 0, bom.length);

    if (  (bom[0] == (byte)0xEF) && (bom[1] == (byte)0xBB) && (bom[2] == (byte)0xBF) )
    {
      encoding = "UTF-8";
      unread = n - 3;
    }
    else if ( (bom[0] == (byte)0xFE) && (bom[1] == (byte)0xFF) )
    {
      encoding = "UTF-16BE";
      unread = n - 2;
    }
    else if ( (bom[0] == (byte)0xFF) && (bom[1] == (byte)0xFE) )
    {
      encoding = "UTF-16LE";
      unread = n - 2;
    }
    else if ( (bom[0] == (byte)0x00) && (bom[1] == (byte)0x00) &&
      (bom[2] == (byte)0xFE) && (bom[3] == (byte)0xFF))
    {
      encoding = "UTF-32BE";
      unread = n - 4;
    }
    else if ( (bom[0] == (byte)0xFF) && (bom[1] == (byte)0xFE) &&
      (bom[2] == (byte)0x00) && (bom[3] == (byte)0x00))
    {
      encoding = "UTF-32LE";
      unread = n - 4;
    }
    else
    {
      // Unicode BOM mark not found, unread all bytes
      encoding = defaultEnc;
      unread = n;
      hasBOM = false;
    }
    if (unread > 0) internalIn.unread(bom, (n - unread), unread);
    else if (unread < -1) internalIn.unread(bom, 0, 0);

    // Use given encoding
    if (encoding == null)
    {
      internalIn2 = new InputStreamReader(internalIn);
    }
    else
    {
      internalIn2 = new InputStreamReader(internalIn, encoding);
    }
  }

  @Override
  public void close() throws IOException
  {
    if (internalIn2 != null) internalIn2.close();
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException
  {
    return internalIn2.read(cbuf, off, len);
  }
}
