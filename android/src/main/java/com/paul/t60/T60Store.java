package com.paul.t60;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONArray;

/** Settings (SharedPreferences) plus live watch state. Nothing here leaves the phone. */
public final class T60Store {

    private static T60Store instance;
    private final SharedPreferences prefs;
    private final List<String> packetLog = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener { void onStateChanged(); }

    // Live state
    private boolean connected = false;
    private String connectionState = "Disconnected";
    private int liveHeartRate = 0;
    private boolean hrTesting = false, spo2Testing = false;
    private int liveSpo2 = 0;
    private boolean syncing = false;
    private boolean findPhoneRinging = false;

    private T60Store(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("t60_local_store", Context.MODE_PRIVATE);
    }

    public static synchronized T60Store get(Context ctx) {
        if (instance == null) instance = new T60Store(ctx);
        return instance;
    }

    public synchronized void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public synchronized void removeListener(Listener l) { listeners.remove(l); }

    public synchronized void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            try { l.onStateChanged(); } catch (Exception ignored) {}
        }
    }

    public void logEvent(String msg) {
        android.util.Log.i("T60", msg);
        synchronized (this) {
            packetLog.add(0, new SimpleDateFormat("h:mm:ss", Locale.US).format(new Date()) + "  " + msg);
            if (packetLog.size() > 200) packetLog.remove(packetLog.size() - 1);
        }
    }

    public synchronized List<String> getPacketLog() { return new ArrayList<>(packetLog); }

    // Generic settings
    int i(String k, int def) { return prefs.getInt(k, def); }
    boolean b(String k, boolean def) { return prefs.getBoolean(k, def); }
    String s(String k, String def) { return prefs.getString(k, def); }
    void set(String k, int v) { prefs.edit().putInt(k, v).apply(); notifyChanged(); }
    void set(String k, boolean v) { prefs.edit().putBoolean(k, v).apply(); notifyChanged(); }
    void set(String k, String v) { prefs.edit().putString(k, v).apply(); notifyChanged(); }

    public String getWatchMac() { return s("watch_mac", T60Protocol.DEFAULT_MAC); }
    public String getWatchName() { return s("watch_name", "T60(ID-AE67)"); }

    // Units and clock
    public boolean isImperial() { return b("imperial", true); }
    public boolean isTwelveHour() { return b("twelve_hour", true); }
    public boolean isFahrenheit() { return b("fahrenheit", true); }

    // Profile (metric inside, shown in the user's units)
    int heightCm() { return i("height_cm", 178); }
    int weightKg() { return i("weight_kg", 79); }
    int birthYear() { return i("birth_year", 1985); }
    boolean isMale() { return b("male", true); }
    int age() { return Math.max(10, Calendar.getInstance().get(Calendar.YEAR) - birthYear()); }
    /** Stride in cm; GloryFit's default is height * 0.418. */
    int strideCm() { return i("stride_cm", Math.round(heightCm() * 0.418f)); }
    int stepGoal() { return i("step_goal", 8000); }

    float metersForSteps(int steps) { return steps * strideCm() / 100f; }
    int kcalForSteps(int steps) { return Math.round(steps * weightKg() * 0.0005f); }

    String distance(float meters) {
        return isImperial() ? String.format(Locale.US, "%.1f mi", meters / 1609.34f)
                : String.format(Locale.US, "%.1f km", meters / 1000f);
    }

    // Alerts
    boolean isForwardCalls() { return b("fwd_calls", true); }
    boolean isForwardSms() { return b("fwd_sms", true); }
    boolean isForwardApps() { return b("fwd_apps", true); }

    public Set<String> getSeenApps() { return new TreeSet<>(prefs.getStringSet("seen_apps", new HashSet<>())); }

    public void markAppSeen(String pkg) {
        Set<String> seen = getSeenApps();
        if (seen.add(pkg)) prefs.edit().putStringSet("seen_apps", seen).apply();
    }

    public boolean isAppMuted(String pkg) { return prefs.getStringSet("muted_apps", new HashSet<>()).contains(pkg); }

    public void setAppMuted(String pkg, boolean muted) {
        Set<String> m = new HashSet<>(prefs.getStringSet("muted_apps", new HashSet<>()));
        if (muted) m.add(pkg); else m.remove(pkg);
        prefs.edit().putStringSet("muted_apps", m).apply();
        notifyChanged();
    }

    /** Quiet hours window check (minutes after midnight, may cross midnight). */
    boolean inQuietHours() {
        if (!b("quiet_on", false)) return false;
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int from = i("quiet_from", 22 * 60), to = i("quiet_to", 7 * 60);
        return from <= to ? now >= from && now < to : now >= from || now < to;
    }

    List<String> quickReplies() { return list("quick_replies", "On my way.", "Can't talk now, I'll call you back.", "Yes", "No", "Thanks!"); }
    void setQuickReplies(List<String> l) { setList("quick_replies", l); }

    /** Contacts on watch: each item "name\nnumber". */
    List<String> watchContacts() { return list("watch_contacts"); }
    void setWatchContacts(List<String> l) { setList("watch_contacts", l); }

    List<String> todayCards() { return list("today_cards", "Steps", "Heart rate", "Sleep", "Quick actions", "Blood oxygen"); }
    Set<String> hiddenCards() { return new HashSet<>(list("hidden_cards", "Blood oxygen")); }
    void setTodayCards(List<String> order, Set<String> hidden) {
        setList("today_cards", order);
        setList("hidden_cards", new ArrayList<>(hidden));
    }

    List<String> list(String key, String... defaults) {
        String raw = s(key, null);
        if (raw == null) return new ArrayList<>(Arrays.asList(defaults));
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int k = 0; k < a.length(); k++) out.add(a.getString(k));
        } catch (Exception ignored) {}
        return out;
    }

    void setList(String key, List<String> l) { set(key, new JSONArray(l).toString()); }

    // Live state
    public synchronized boolean isConnected() { return connected; }
    public synchronized String getConnectionState() { return connectionState; }

    public synchronized void setConnection(boolean isConnected, String state) {
        connected = isConnected;
        connectionState = state;
        if (isConnected) prefs.edit().putLong("last_seen", System.currentTimeMillis()).apply();
        notifyChanged();
    }

    long lastSeen() { return prefs.getLong("last_seen", 0); }

    public String getFirmwareVersion() { return s("firmware", "RH281LWJV007108"); }
    public void setFirmwareVersion(String v) { set("firmware", v); }

    public int getBatteryPercent() { return i("last_battery", -1); }
    public void setBatteryPercent(int pct) { set("last_battery", pct); }

    public synchronized int getLiveHeartRate() { return liveHeartRate; }
    public synchronized void setLiveHeartRate(int bpm) { liveHeartRate = bpm; prefs.edit().putLong("hr_time", System.currentTimeMillis()).apply(); notifyChanged(); }
    long hrTime() { return prefs.getLong("hr_time", 0); }
    public synchronized boolean isHrTesting() { return hrTesting; }
    public synchronized void setHrTesting(boolean t) { hrTesting = t; notifyChanged(); }
    public synchronized int getLiveSpo2() { return liveSpo2; }
    public synchronized void setLiveSpo2(int v) { liveSpo2 = v; notifyChanged(); }
    public synchronized boolean isSpo2Testing() { return spo2Testing; }
    public synchronized void setSpo2Testing(boolean t) { spo2Testing = t; notifyChanged(); }
    public synchronized boolean isSyncing() { return syncing; }
    public synchronized void setSyncing(boolean s) { syncing = s; notifyChanged(); }
    public synchronized boolean isFindPhoneRinging() { return findPhoneRinging; }
    public synchronized void setFindPhoneRinging(boolean r) { findPhoneRinging = r; notifyChanged(); }

    long lastSync() { return prefs.getLong("last_sync_ms", 0); }
    void markSynced() { prefs.edit().putLong("last_sync_ms", System.currentTimeMillis()).apply(); notifyChanged(); }

    String time(long ms) {
        return new SimpleDateFormat(isTwelveHour() ? "h:mm a" : "HH:mm", Locale.US).format(new Date(ms));
    }

    String clock(int minuteOfDay) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, (minuteOfDay / 60) % 24);
        c.set(Calendar.MINUTE, minuteOfDay % 60);
        return time(c.getTimeInMillis());
    }
}
