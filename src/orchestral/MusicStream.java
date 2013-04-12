package orchestral;

import java.io.*;
import java.util.concurrent.*;

import org.apache.commons.io.*;
import org.apache.log4j.Logger;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Uninterruptibles;

public class MusicStream extends InputStream {

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(MusicStream.class);

  private static final Executor executor = Executors.newCachedThreadPool();

  private ByteArrayOutputStreamX content;
  private volatile int downloadedBytes = 0;
  private volatile int readBytes = 0;
  private boolean downloading = true;

  public MusicStream(final InputStream is, final File saveLocation) {
    this.content = new ByteArrayOutputStreamX();

    executor.execute(new Runnable() {
      @Override
      public void run() {
        int read = 0;

        byte[] buf = new byte[16 * 1024];

        try {
          while ((read = is.read(buf)) != -1) {
            content.write(buf, 0, read);
            downloadedBytes += read;
          }
          logger.debug("Done downloading.");
          downloading = false;
          save(saveLocation);
        } catch (Exception e) {
          e.printStackTrace();
          content = null;
        } finally {
          IOUtils.closeQuietly(is);
        }
      }
    });
  }

  private void save(File saveLocation) {
    try {
      FileUtils.writeByteArrayToFile(saveLocation, content.toByteArray());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public int available() throws IOException {
    return downloadedBytes - readBytes;
  }

  @Override
  public int read() throws IOException {
    throw new RuntimeException("Not implemented.");
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (!downloading && readBytes == content.size()) {
      return -1;
    }

    int tr = 0;

    while ((tr = Math.min(downloadedBytes - readBytes, len)) == 0) {
      Uninterruptibles.sleepUninterruptibly(10, TimeUnit.MILLISECONDS);
    }

    byte[] buf = content.getBuffer();

    System.arraycopy(buf, readBytes, b, off, tr);

    readBytes += tr;

    return tr;
  }

  private final class ByteArrayOutputStreamX extends ByteArrayOutputStream {
    public ByteArrayOutputStreamX() {
      super(1024 * 1024);
    }

    public byte[] getBuffer() {
      return buf;
    }
  }
}
