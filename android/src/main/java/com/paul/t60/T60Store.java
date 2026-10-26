package com.paul.t60;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Locale;

/**
 * Local, zero-cloud storage for T60 settings, health history (steps, sleep, HR),
 * and BLE telemetry log. Supports one-tap JSON/CSV export to phone storage.
 */
public final class T60Store {

    private static final String PREFS = "t60_local_store";
    private static T60Store instance;
    private final SharedPreferences prefs;
    private final Context appContext;
    private final List<String> packetLog = new ArrayList<>();

    public interface Listener {
        void onStateChanged();
    }

    private final List<Listener> listeners = new ArrayList<>();

    private String connectionState = "Disconnected";
    private String firmwareVersion = "RH281LWJV007108";
    private String screenSpec = "320 × 380 px • 1 MB Dial";
    private int batteryPercent = -1;
    private int liveHeartRate = 0;
    private boolean hrTesting = false;
    private int todaySteps = 0;
    private int todaySleepMinutes = 0;
    private String lastSyncTime = "Never";

    private T60Store(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.batteryPercent = prefs.getInt("last_battery", 56);
        this.todaySteps = prefs.getInt("today_steps", 0);
        this.todaySleepMinutes = prefs.getInt("today_sleep_min", 450);
        this.liveHeartRate = prefs.getInt("last_hr", 72);
        this.lastSyncTime = prefs.getString("last_sync", "Ready");
    }

    public static synchronized T60Store get(Context ctx) {
        if (instance == null) {
            instance = new T60Store(ctx);
        }
        return instance;
    }

    public synchronized void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }

    public synchronized void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            try {
                l.onStateChanged();
            } catch (Exception ignored) {}
        }
    }

    public synchronized void logEvent(String msg) {
        String ts = new SimpleDateFormat("h:mm:ss a", Locale.US).format(new Date());
        packetLog.add(0, ts + "  " + msg);
        if (packetLog.size() > 80) {
            packetLog.remove(packetLog.size() - 1);
        }
        notifyChanged();
    }

    public synchronized List<String> getPacketLog() {
        return new ArrayList<>(packetLog);
    }

    public String getWatchMac() {
        return prefs.getString("watch_mac", T60Protocol.DEFAULT_MAC);
    }

    public void setWatchMac(String mac) {
        prefs.edit().putString("watch_mac", mac.trim().toUpperCase(Locale.US)).apply();
        notifyChanged();
    }

    public boolean isImperial() {
        return prefs.getBoolean("imperial", true);
    }

    public void setImperial(boolean v) {
        prefs.edit().putBoolean("imperial", v).apply();
        notifyChanged();
    }

    public boolean isTwelveHour() {
        return prefs.getBoolean("twelve_hour", true);
    }

    public void setTwelveHour(boolean v) {
        prefs.edit().putBoolean("twelve_hour", v).apply();
        notifyChanged();
    }

    public boolean isRaiseToWake() {
        return prefs.getBoolean("raise_to_wake", true);
    }

    public void setRaiseToWake(boolean v) {
        prefs.edit().putBoolean("raise_to_wake", v).apply();
        notifyChanged();
    }

    public boolean isForwardCalls() {
        return prefs.getBoolean("fwd_calls", true);
    }

    public void setForwardCalls(boolean v) {
        prefs.edit().putBoolean("fwd_calls", v).apply();
    }

    public boolean isForwardSms() {
        return prefs.getBoolean("fwd_sms", true);
    }

    public void setForwardSms(boolean v) {
        prefs.edit().putBoolean("fwd_sms", v).apply();
    }

    public boolean isForwardApps() {
        return prefs.getBoolean("fwd_apps", true);
    }

    public void setForwardApps(boolean v) {
        prefs.edit().putBoolean("fwd_apps", v).apply();
    }

    /** Apps that have posted a notification since install (for the per-app filter list). */
    public Set<String> getSeenApps() {
        return new TreeSet<>(prefs.getStringSet("seen_apps", new HashSet<>()));
    }

    public void markAppSeen(String pkg) {
        Set<String> seen = getSeenApps();
        if (seen.add(pkg)) {
            prefs.edit().putStringSet("seen_apps", seen).apply();
        }
    }

    public boolean isAppMuted(String pkg) {
        return prefs.getStringSet("muted_apps", new HashSet<>()).contains(pkg);
    }

    public void setAppMuted(String pkg, boolean muted) {
        Set<String> m = new HashSet<>(prefs.getStringSet("muted_apps", new HashSet<>()));
        if (muted) m.add(pkg); else m.remove(pkg);
        prefs.edit().putStringSet("muted_apps", m).apply();
    }

    public String getWebhookUrl() {
        return prefs.getString("webhook_url", "");
    }

    public void setWebhookUrl(String url) {
        prefs.edit().putString("webhook_url", url != null ? url.trim() : "").apply();
    }

    public synchronized String getConnectionState() { return connectionState; }
    public synchronized void setConnectionState(String s) {
        this.connectionState = s;
        notifyChanged();
    }

    public synchronized String getFirmwareVersion() { return firmwareVersion; }
    public synchronized void setFirmwareVersion(String v) {
        this.firmwareVersion = v;
        notifyChanged();
    }

    public synchronized String getScreenSpec() { return screenSpec; }
    public synchronized void setScreenSpec(String s) {
        this.screenSpec = s;
        notifyChanged();
    }

    public synchronized int getBatteryPercent() { return batteryPercent; }
    public synchronized void setBatteryPercent(int pct) {
        this.batteryPercent = pct;
        prefs.edit().putInt("last_battery", pct).apply();
        notifyChanged();
    }

    public synchronized int getLiveHeartRate() { return liveHeartRate; }
    public synchronized void setLiveHeartRate(int bpm) {
        this.liveHeartRate = bpm;
        prefs.edit().putInt("last_hr", bpm).apply();
        recordHrSample(bpm);
        notifyChanged();
    }

    public synchronized boolean isHrTesting() { return hrTesting; }
    public synchronized void setHrTesting(boolean testing) {
        this.hrTesting = testing;
        notifyChanged();
    }

    public synchronized int getTodaySteps() { return todaySteps; }
    public synchronized void setTodaySteps(int steps) {
        this.todaySteps = steps;
        prefs.edit().putInt("today_steps", steps).apply();
        notifyChanged();
    }

    public synchronized int getTodaySleepMinutes() { return todaySleepMinutes; }
    public synchronized void setTodaySleepMinutes(int mins) {
        this.todaySleepMinutes = mins;
        prefs.edit().putInt("today_sleep_min", mins).apply();
        notifyChanged();
    }

    public synchronized String getLastSyncTime() { return lastSyncTime; }
    public synchronized void updateLastSyncTime() {
        this.lastSyncTime = new SimpleDateFormat("MMM d, h:mm a", Locale.US).format(new Date());
        prefs.edit().putString("last_sync", this.lastSyncTime).apply();
        notifyChanged();
    }

    private void recordHrSample(int bpm) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("hr_history", "[]"));
            JSONObject item = new JSONObject();
            item.put("ts", System.currentTimeMillis());
            item.put("bpm", bpm);
            arr.put(item);
            while (arr.length() > 50) {
                arr.remove(0);
            }
            prefs.edit().putString("hr_history", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public File exportHealthJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("watch_name", "T60(ID-AE67)");
        root.put("watch_mac", getWatchMac());
        root.put("firmware", getFirmwareVersion());
        root.put("screen", getScreenSpec());
        root.put("battery_pct", getBatteryPercent());
        root.put("today_steps", getTodaySteps());
        root.put("today_sleep_minutes", getTodaySleepMinutes());
        root.put("last_heart_rate_bpm", getLiveHeartRate());
        root.put("exported_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
        root.put("hr_samples", new JSONArray(prefs.getString("hr_history", "[]")));

        File outFile = new File(appContext.getExternalFilesDir(null), "t60_health_export.json");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return outFile;
    }
}
