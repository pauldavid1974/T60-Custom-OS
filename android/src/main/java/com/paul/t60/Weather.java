package com.paul.t60;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 7-day forecast from the US National Weather Service (api.weather.gov). The only thing sent is
 * the phone's location rounded to 2 decimal places (about half a mile to a mile). US only.
 */
final class Weather {

    interface Done { void done(String error); }

    private Weather() {}

    static int code(String text) {
        String t = text.toLowerCase(Locale.US);
        if (t.contains("thunder")) return T60Protocol.WX_STORM;
        if (t.contains("snow") || t.contains("flurr") || t.contains("blizzard")) return T60Protocol.WX_SNOW;
        if (t.contains("sleet") || t.contains("freezing") || t.contains("ice")) return T60Protocol.WX_SLEET;
        if (t.contains("shower")) return T60Protocol.WX_SHOWERS;
        if (t.contains("drizzle") || t.contains("light rain")) return T60Protocol.WX_LIGHT_RAIN;
        if (t.contains("rain")) return T60Protocol.WX_RAIN;
        if (t.contains("fog") || t.contains("haze") || t.contains("smoke")) return T60Protocol.WX_FOG;
        if (t.contains("dust") || t.contains("sand")) return T60Protocol.WX_DUST;
        if (t.contains("wind") || t.contains("breezy") || t.contains("blustery")) return T60Protocol.WX_WIND;
        if (t.contains("partly") || t.contains("mostly sunny") || t.contains("mostly clear")) return T60Protocol.WX_PARTLY;
        if (t.contains("cloud") || t.contains("overcast")) return T60Protocol.WX_CLOUDY;
        return T60Protocol.WX_SUNNY;
    }

    static int toC(int f) { return Math.round((f - 32) * 5 / 9f); }

    @SuppressLint("MissingPermission")
    static Location lastLocation(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        if (lm == null) return null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {}
        }
        return best;
    }

    /** Fetches, stores the forecast in settings and pushes it to the watch. Runs off the main thread. */
    @SuppressLint("MissingPermission")
    static void update(Context ctx, Done done) {
        Context c = ctx.getApplicationContext();
        Location loc = lastLocation(c);
        if (loc == null) {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            try {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, l -> fetchAsync(c, l, done), Looper.getMainLooper());
            } catch (Exception e) {
                done.done("Location is off or not allowed");
            }
            return;
        }
        fetchAsync(c, loc, done);
    }

    private static void fetchAsync(Context c, Location loc, Done done) {
        new Thread(() -> {
            String err = null;
            try {
                fetch(c, Math.round(loc.getLatitude() * 100) / 100.0, Math.round(loc.getLongitude() * 100) / 100.0);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
                T60Store.get(c).logEvent("Weather error: " + err);
            }
            String fe = err;
            new android.os.Handler(Looper.getMainLooper()).post(() -> done.done(fe));
        }).start();
    }

    static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(15000);
        h.setRequestProperty("User-Agent", "T60-watch-app (personal, no tracking)");
        h.setRequestProperty("Accept", "application/geo+json");
        int code = h.getResponseCode();
        if (code == 404) throw new Exception("weather.gov has no forecast here (US only)");
        if (code != 200) throw new Exception("weather.gov answered " + code);
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
            return o.toString("UTF-8");
        }
    }

    static void fetch(Context c, double lat, double lon) throws Exception {
        JSONObject pts = new JSONObject(get("https://api.weather.gov/points/" + lat + "," + lon)).getJSONObject("properties");
        JSONObject rel = pts.getJSONObject("relativeLocation").getJSONObject("properties");
        String town = rel.optString("city") + ", " + rel.optString("state");
        JSONArray periods = new JSONObject(get(pts.getString("forecast"))).getJSONObject("properties").getJSONArray("periods");
        JSONArray hourly = new JSONObject(get(pts.getString("forecastHourly"))).getJSONObject("properties").getJSONArray("periods");
        JSONObject now = hourly.getJSONObject(0);
        int nowF = now.getInt("temperature");
        int hum = now.optJSONObject("relativeHumidity") != null ? now.getJSONObject("relativeHumidity").optInt("value", 0) : 0;

        // Group 12-hour periods by date: daytime high, night low, daytime text.
        Map<String, int[]> temps = new LinkedHashMap<>();
        Map<String, String> text = new LinkedHashMap<>();
        for (int k = 0; k < periods.length(); k++) {
            JSONObject p = periods.getJSONObject(k);
            String day = p.getString("startTime").substring(0, 10);
            int t = p.getInt("temperature");
            int[] hl = temps.get(day);
            if (hl == null) { hl = new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}; temps.put(day, hl); }
            if (p.getBoolean("isDaytime")) { hl[0] = t; text.put(day, p.getString("shortForecast")); }
            else { hl[1] = t; if (!text.containsKey(day)) text.put(day, p.getString("shortForecast")); }
        }
        JSONArray days = new JSONArray();
        List<T60Protocol.Day> watch = new ArrayList<>();
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US), out = new SimpleDateFormat("EEE", Locale.US);
        for (String day : temps.keySet()) {
            int[] hl = temps.get(day);
            int hi = hl[0] == Integer.MIN_VALUE ? Math.max(nowF, hl[1]) : hl[0];
            int lo = hl[1] == Integer.MAX_VALUE ? hi - 10 : hl[1];
            String t = text.get(day);
            JSONObject d = new JSONObject();
            d.put("day", out.format(in.parse(day))); d.put("text", t); d.put("hi", hi); d.put("lo", lo);
            days.put(d);
            watch.add(new T60Protocol.Day(code(t), toC(hi), toC(lo)));
        }
        while (watch.size() < 7) watch.add(watch.get(watch.size() - 1));
        JSONObject save = new JSONObject();
        save.put("town", town); save.put("now", nowF); save.put("text", now.optString("shortForecast"));
        save.put("updated", System.currentTimeMillis()); save.put("days", days);
        T60Store store = T60Store.get(c);
        store.set("weather_json", save.toString());
        store.logEvent("Weather updated for " + town + ": " + nowF + "F " + now.optString("shortForecast"));
        T60BleService svc = T60BleService.getInstance();
        if (svc != null && store.b("weather_on", true)) {
            svc.sendWeather(watch.subList(0, 7).toArray(new T60Protocol.Day[0]), toC(nowF), hum);
        }
    }

    static String updatedText(T60Store s) {
        try {
            return "Updated " + s.time(new JSONObject(s.s("weather_json", "{}")).getLong("updated"));
        } catch (Exception e) {
            return "Not updated yet";
        }
    }

    static long lastUpdate(T60Store s) {
        try { return new JSONObject(s.s("weather_json", "{}")).getLong("updated"); } catch (Exception e) { return 0; }
    }
}
