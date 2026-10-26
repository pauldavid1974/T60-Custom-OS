package com.paul.t60;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OpenStreetMap map squares ("tiles") for the workout map. No account or key. OSM's tile policy asks for an
 * identifying User-Agent, at most 2 connections, local caching, and the "© OpenStreetMap contributors" credit
 * shown on the map. Only the squares around the route are fetched; the route itself never leaves the phone.
 */
final class MapTiles {
    static final int MAX_ZOOM = 17;
    private static final long KEEP_MS = 30L * 24 * 3600 * 1000, RETRY_MS = 60_000;

    // ponytail: unbounded memory cache, fine for one workout's few dozen squares; add LRU if maps get bigger
    private static final Map<String, Bitmap> mem = new HashMap<>();
    private static final Map<String, Long> busy = new HashMap<>(); // loading, or failed at this time
    private static final ExecutorService pool = Executors.newFixedThreadPool(2);

    /** Square in tile units at zoom z (Web Mercator, as every web map uses). */
    static double x(double lon, int z) { return (lon + 180) / 360 * (1 << z); }

    static double y(double lat, int z) {
        double s = Math.sin(Math.toRadians(lat));
        return (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * (1 << z);
    }

    /** The square if it is ready; otherwise starts fetching it and runs done on the main thread when it lands. */
    static Bitmap get(Context c, int z, int x, int y, Runnable done) {
        String key = z + "_" + x + "_" + y;
        synchronized (mem) {
            Bitmap b = mem.get(key);
            if (b != null) return b;
            Long t = busy.get(key);
            if (t != null && (t == 0 || System.currentTimeMillis() - t < RETRY_MS)) return null;
            busy.put(key, 0L);
        }
        File f = new File(c.getApplicationContext().getCacheDir(), "tiles/" + key + ".png");
        pool.execute(() -> {
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > KEEP_MS) {
                try { download(z, x, y, f); } catch (Exception ignored) {} // an old copy still beats nothing
            }
            Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
            synchronized (mem) {
                if (b != null) { mem.put(key, b); busy.remove(key); }
                else busy.put(key, System.currentTimeMillis());
            }
            if (b != null) new Handler(Looper.getMainLooper()).post(done);
        });
        return null;
    }

    private static void download(int z, int x, int y, File f) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL("https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png").openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(15000);
        h.setRequestProperty("User-Agent", "T60-watch-app/2.0 (personal use; github.com/pauldavid1974/gloryfit-replacement-android)");
        if (h.getResponseCode() != 200) throw new Exception("tile server answered " + h.getResponseCode());
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".part");
        try (InputStream in = h.getInputStream(); OutputStream o = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
    }
}
