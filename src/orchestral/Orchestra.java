package orchestral;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.google.common.base.Throwables;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.Player;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

public class Orchestra {

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(Orchestra.class);

  private static final Executor executor = Executors.newCachedThreadPool();

  private final JsonParser parser = new JsonParser();
  private final File downloadsFolder;
  // private final boolean mute


  private final Map<String, InputStream> preloadCache = new LinkedHashMap<String, InputStream>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, InputStream> eldest) {
      if (size() > 5) {
        IOUtils.closeQuietly(eldest.getValue());
        return true;
      }
      return false;
    };
  };

  public Orchestra() {
    this(new File(OS.getLocalAppFolder("Orchestral")));
  }

  public Orchestra(File downloadsFolder) {
    this.downloadsFolder = downloadsFolder;

    // initialize the audio device factory
    try {
      FactoryRegistry.systemRegistry().createAudioDevice();
    } catch (JavaLayerException e) {
      throw Throwables.propagate(e);
    }
  }

  public void load(final String id) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          preloadCache.put(id, getStream(id));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });

  }

  public void play(final String id) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          Player player = new Player(getStream(id));
          player.play();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  private InputStream getStream(String id) {
    InputStream is = preloadCache.remove(id);
    if (is != null) {
      return is;
    }

    try {
      File f = new File(downloadsFolder, id + ".mp3");

      if (f.exists()) {
        logger.debug("Cache hit: " + id);
        return new BufferedInputStream(new FileInputStream(f));
      }

      logger.debug("Cache miss: " + id);

      URL url =
          new URL("http://www.youtube-mp3.org/api/itemInfo/?video_id=" + id + "&ac=www&r="
              + System.currentTimeMillis());

      String s = IOUtils.toString(url);
      s = s.substring(6, s.length() - 1);
      JsonObject json = parser.parse(s).getAsJsonObject();
      String hash = json.get("h").getAsString();

      url =
          new URL("http://www.youtube-mp3.org/get?video_id=" + id + "&h=" + hash + "&r="
              + System.currentTimeMillis());

      return new MusicStream(url.openStream(), f);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  public static void main(String[] args) throws Exception {
    BasicConfigurator.configure();
    Orchestra orchestra = new Orchestra();
    orchestra.play("QK8mJJJvaes");
  }

}
