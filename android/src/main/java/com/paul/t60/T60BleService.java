package com.paul.t60;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;

/**
 * Foreground service that keeps the T60 connected and is the only thing that talks to it.
 * Commands are sent one at a time: each waits for the watch's reply (same first byte) or a
 * timeout before the next goes out, the way GloryFit's command index queue does.
 */
@SuppressLint("MissingPermission")
public class T60BleService extends Service {

    static final String CHANNEL_ID = "t60_ble_channel", ALERT_CHANNEL_ID = "t60_find_phone_alert";
    private static final int NOTIF_ID = 1001, FIND_PHONE_NOTIF = 1002;

    private static T60BleService activeInstance;
    static T60BleService getInstance() { return activeInstance; }

    private final Handler h = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic char33F1, char34F1;
    private boolean ready = false;
    private int mtuPayload = 20;
    private T60Store store;
    private T60Db db;

    /** One write; waits for a packet starting with {@code expect} unless expect < 0. */
    private static final class Op {
        final boolean ble5;
        final byte[] data;
        final int expect;
        final long timeoutMs;
        final String label;
        Runnable onDone;
        int retries;
        Op(boolean ble5, byte[] data, int expect, long timeoutMs, String label) {
            this.ble5 = ble5; this.data = data; this.expect = expect; this.timeoutMs = timeoutMs; this.label = label;
        }
    }

    private final LinkedList<Op> queue = new LinkedList<>();
    private Op current;
    private boolean streaming; // current op expects several packets and ends on a marker
    private final Runnable timeout = () -> { if (current != null) { store.logEvent("No reply to " + current.label); finishCurrent(); } };

    // Android 14+ kills the app if a connected-device service starts before Bluetooth is allowed.
    private boolean granted(String perm) {
        return checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean bluetoothAllowed() {
        return Build.VERSION.SDK_INT < 31 || granted(android.Manifest.permission.BLUETOOTH_CONNECT);
    }

    // Re-run on every start, so a location permission granted later adds workout GPS.
    private void goForeground() {
        if (Build.VERSION.SDK_INT >= 29) {
            int type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            if (granted(android.Manifest.permission.ACCESS_FINE_LOCATION)) type |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            startForeground(NOTIF_ID, statusNotification(statusText), type);
        } else {
            startForeground(NOTIF_ID, statusNotification(statusText));
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        store = T60Store.get(this);
        if (!bluetoothAllowed()) {
            store.logEvent("Service not started: Bluetooth permission not granted yet");
            stopSelf();
            return;
        }
        activeInstance = this;
        db = T60Db.get(this);
        createChannels();
        goForeground();
        registerPhoneHooks();
        registerReceiver(btState, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        connectWatch();
        watchMedia();
        h.postDelayed(tick, 60_000);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!bluetoothAllowed()) { stopSelf(); return START_NOT_STICKY; }
        goForeground();
        registerPhoneHooks(); // permissions are asked per feature, so they may arrive after the service started
        if (gatt == null) connectWatch();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        activeInstance = null;
        h.removeCallbacksAndMessages(null);
        try { unregisterReceiver(smsReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(btState); } catch (Exception ignored) {}
        stopGps();
        disconnectGatt();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- notifications

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Watch connection", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Keeps your T60 connected");
        nm.createNotificationChannel(ch);
        NotificationChannel alert = new NotificationChannel(ALERT_CHANNEL_ID, "Find my phone", NotificationManager.IMPORTANCE_HIGH);
        alert.setSound(null, null);
        nm.createNotificationChannel(alert);
    }

    private Notification statusNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID).setContentTitle("T60 watch").setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentIntent(pi).setOngoing(true).build();
    }

    private String statusText = "Connecting…";

    private void status(String text) {
        statusText = text;
        getSystemService(NotificationManager.class).notify(NOTIF_ID, statusNotification(text));
    }

    // ---------------------------------------------------------------- connection

    /** Reconnects the moment the phone's Bluetooth comes back on, instead of waiting for the 15-minute tick. */
    private final BroadcastReceiver btState = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int s = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            if (s == BluetoothAdapter.STATE_ON) connectWatch();
            else if (s == BluetoothAdapter.STATE_OFF) { disconnectGatt(); store.setConnection(false, "Bluetooth is off"); status("Phone's Bluetooth is off"); }
        }
    };

    void connectWatch() {
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
        if (adapter == null || !adapter.isEnabled()) {
            store.setConnection(false, "Bluetooth is off");
            status("Phone's Bluetooth is off");
            return;
        }
        try {
            disconnectGatt();
            store.setConnection(false, "Connecting");
            BluetoothDevice device = adapter.getRemoteDevice(store.getWatchMac());
            gatt = device.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (Exception e) {
            store.setConnection(false, "Connection error");
            store.logEvent("Connect error: " + e);
        }
    }

    private void disconnectGatt() {
        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) {}
            try { gatt.close(); } catch (Exception ignored) {}
            gatt = null;
        }
        resetLink();
    }

    private void resetLink() {
        ready = false;
        char33F1 = null;
        char34F1 = null;
        queue.clear();
        current = null;
        streaming = false;
        h.removeCallbacks(timeout);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int st, int newState) {
            h.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    store.setConnection(false, "Connecting");
                    store.logEvent("Bluetooth link up");
                    if (!g.requestMtu(247)) g.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    resetLink();
                    store.setSyncing(false);
                    store.setConnection(false, "Not connected");
                    store.logEvent("Watch disconnected (status " + st + ")");
                    status("Watch not connected");
                }
            });
        }

        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int st) {
            h.post(() -> { mtuPayload = Math.max(20, mtu - 3); g.discoverServices(); });
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int st) {
            h.post(() -> {
                BluetoothGattService s55 = g.getService(T60Protocol.UUID_SERVICE_MAIN), s56 = g.getService(T60Protocol.UUID_SERVICE_EXT);
                if (st != BluetoothGatt.GATT_SUCCESS || s55 == null) {
                    store.logEvent("Service discovery failed: " + st);
                    return;
                }
                char33F1 = s55.getCharacteristic(T60Protocol.UUID_CHAR_WRITE);
                if (s56 != null) char34F1 = s56.getCharacteristic(T60Protocol.UUID_CHAR_EXT_WRITE);
                // Android runs one GATT operation at a time: 33F2 CCCD, then 34F2 CCCD, then startup.
                if (!enableNotify(g, s55, T60Protocol.UUID_CHAR_NOTIFY) && !enableNotify(g, s56, T60Protocol.UUID_CHAR_EXT_NOTIFY)) {
                    onLinkReady();
                }
            });
        }

        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int st) {
            h.post(() -> {
                if (d.getCharacteristic().getUuid().equals(T60Protocol.UUID_CHAR_NOTIFY)
                        && enableNotify(g, g.getService(T60Protocol.UUID_SERVICE_EXT), T60Protocol.UUID_CHAR_EXT_NOTIFY)) return;
                onLinkReady();
            });
        }

        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int st) {
            h.post(() -> {
                writeInFlight = false;
                if (current != null && current.expect < 0) finishCurrent();
            });
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            byte[] v = c.getValue();
            if (v == null || v.length == 0) return;
            byte[] copy = v.clone();
            h.post(() -> onPacket(copy));
        }
    };

    private boolean writeInFlight;

    private boolean enableNotify(BluetoothGatt g, BluetoothGattService svc, UUID uuid) {
        BluetoothGattCharacteristic ch = svc != null ? svc.getCharacteristic(uuid) : null;
        if (ch == null) return false;
        g.setCharacteristicNotification(ch, true);
        BluetoothGattDescriptor d = ch.getDescriptor(T60Protocol.UUID_CCCD);
        if (d == null) return false;
        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return g.writeDescriptor(d);
    }

    private void onLinkReady() {
        if (ready) return;
        ready = true;
        store.setConnection(true, "Connected");
        status("Connected");
        startupSync();
    }

    // ---------------------------------------------------------------- queue

    private Op op(byte[] data, int expect, long timeoutMs, String label) {
        Op o = new Op(false, data, expect, timeoutMs, label);
        queue.add(o);
        pump();
        return o;
    }

    private Op op5(byte[] data, int expect, long timeoutMs, String label) {
        Op o = new Op(true, data, expect, timeoutMs, label);
        queue.add(o);
        pump();
        return o;
    }

    /** Runs r after everything queued so far has finished. */
    private void then(Runnable r) {
        Op o = new Op(false, null, -1, 0, "step");
        o.onDone = r;
        queue.add(o);
        pump();
    }

    private void pump() {
        if (current != null || !ready || gatt == null) return;
        Op o = queue.poll();
        if (o == null) return;
        current = o;
        streaming = false;
        if (o.data == null) { finishCurrent(); return; }
        BluetoothGattCharacteristic c = o.ble5 ? char34F1 : char33F1;
        if (c == null) { finishCurrent(); return; }
        c.setWriteType(o.ble5 ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        c.setValue(o.data);
        if (!gatt.writeCharacteristic(c)) {
            // Android's GATT is still busy with the previous operation; try again shortly.
            current = null;
            if (++o.retries > 40) { store.logEvent("Gave up writing " + o.label); h.post(this::pump); return; }
            queue.addFirst(o);
            h.postDelayed(this::pump, 50);
            return;
        }
        writeInFlight = true;
        store.logEvent("TX " + (o.ble5 ? "34F1 " : "33F1 ") + T60Protocol.toHex(o.data));
        h.removeCallbacks(timeout);
        h.postDelayed(timeout, o.expect < 0 ? 800 : o.timeoutMs);
    }

    private void finishCurrent() {
        h.removeCallbacks(timeout);
        Op done = current;
        current = null;
        streaming = false;
        if (done != null && done.onDone != null) {
            try { done.onDone.run(); } catch (Exception e) { store.logEvent("Step error: " + e); }
        }
        h.post(this::pump);
    }

    /** Called for every packet that belongs to the current op; returns true when the op is complete. */
    private void replyArrived(int lead, boolean complete) {
        if (current == null || current.expect != lead) return;
        if (complete) finishCurrent();
        else { streaming = true; h.removeCallbacks(timeout); h.postDelayed(timeout, current.timeoutMs); }
    }

    // ---------------------------------------------------------------- startup and settings

    private void startupSync() {
        store.setSyncing(true);
        op(T60Protocol.syncTime(Calendar.getInstance()), 0xA3, 1500, "time");
        op(T60Protocol.readVersion(), 0xA1, 1500, "version");
        linkCallAudio();
        sendProfile();
        op(T60Protocol.readBattery(), 0xA2, 1500, "battery");
        op(T60Protocol.readDialConfig(), 0x26, 1500, "screen info");
        sendUnits();
        op(T60Protocol.findPhoneEnable(store.b("find_phone_on", true)), 0xD1, 1000, "find phone");
        op(T60Protocol.allDayHeartRate(store.b("hr_all_day", true)), 0xF7, 1000, "all-day heart rate");
        sendStepGoal();
        sendMoveReminder();
        sendQuietHours();
        sendAlarms();
        sendCycle();
        op(T60Protocol.queryLanguage(), 0xAF, 1500, "language list");
        sendQuickReplies();
        syncHistory();
        then(() -> {
            if (store.b("weather_on", true) && System.currentTimeMillis() - Weather.lastUpdate(store) > 30 * 60_000L) {
                Weather.update(this, e -> {});
            }
        });
    }

    void sendProfile() {
        op(T60Protocol.profile(store.heightCm(), store.weightKg(), store.i("screen_sec", 5), store.stepGoal(),
                store.b("raise_wake", true), store.b("hr_alert", false) ? store.i("hr_limit", 120) : 0,
                store.age(), store.isMale(), store.isFahrenheit()), 0xA9, 1500, "profile");
    }

    void sendUnits() {
        op(T60Protocol.units(store.isImperial(), store.isTwelveHour()), 0xA0, 1500, "units");
        sendProfile(); // temperature unit lives in A9
    }

    void sendStepGoal() {
        op(T60Protocol.stepTarget(store.stepGoal()), 0x3F, 1000, "step goal");
    }

    void sendMoveReminder() {
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday
        boolean today = (store.i("move_days", 0x3E) & (1 << dow)) != 0;
        int from = store.i("move_from", 9 * 60), to = store.i("move_to", 17 * 60);
        op(T60Protocol.moveReminder(store.b("move_on", false) && today, store.i("move_every", 60),
                from / 60, from % 60, to / 60, to % 60), 0xD3, 1000, "move reminder");
    }

    void sendQuietHours() {
        int from = store.i("quiet_from", 22 * 60), to = store.i("quiet_to", 7 * 60);
        op(T60Protocol.quietHours(store.b("quiet_on", false), from / 60, from % 60, to / 60, to % 60), 0xD7, 1000, "quiet hours");
    }

    void sendAlarms() {
        for (int id = 1; id <= 3; id++) {
            op(T60Protocol.alarm(id, store.b("alarm" + id + "_on", false), store.i("alarm" + id + "_h", 7),
                    store.i("alarm" + id + "_m", 0), store.i("alarm" + id + "_days", 0x7F)), 0xAB, 600, "alarm " + id);
        }
    }

    void sendCycle() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(store.s("cycle_start", "").isEmpty() ? System.currentTimeMillis() : T60Db.parse(store.s("cycle_start", "")).getTimeInMillis());
        op(T60Protocol.cycle(store.b("cycle_on", false), c, store.i("cycle_period", 5), store.i("cycle_len", 28)), 0x29, 1000, "women's health");
    }

    void sendQuickReplies() {
        List<String> r = store.quickReplies();
        if (r.isEmpty()) op5(T60Protocol.quickRepliesClearBle5(), 0x46, 1500, "clear replies");
        for (int k = 0; k < r.size(); k++) op5(T60Protocol.quickReplyBle5(r.size(), k, r.get(k)), 0x46, 1500, "reply " + (k + 1));
        if (!r.isEmpty()) op5(T60Protocol.quickRepliesDoneBle5(), 0x46, 1500, "replies done");
        op(T60Protocol.repliesAllowed(store.b("replies_on", true)), 0x52, 1000, "replies allowed");
    }

    void sendLanguage(int n) { op(T60Protocol.setLanguage(n), 0xAF, 2000, "set language"); op(T60Protocol.queryLanguage(), 0xAF, 1500, "language list"); }
    void sendScreen() { sendProfile(); }
    void sendHeartRateSettings() { op(T60Protocol.allDayHeartRate(store.b("hr_all_day", true)), 0xF7, 1000, "all-day heart rate"); sendProfile(); }
    void sendFindPhoneSetting() { op(T60Protocol.findPhoneEnable(store.b("find_phone_on", true)), 0xD1, 1000, "find phone"); }

    void linkCallAudio() {
        String saved = store.s("bt3_random", "");
        byte[] rnd = new byte[4];
        if (saved.length() == 8) {
            for (int k = 0; k < 4; k++) rnd[k] = (byte) Integer.parseInt(saved.substring(k * 2, k * 2 + 2), 16);
        } else {
            new SecureRandom().nextBytes(rnd);
            store.set("bt3_random", String.format("%02X%02X%02X%02X", rnd[0], rnd[1], rnd[2], rnd[3]));
        }
        op5(T60Protocol.callAudioLinkBle5(rnd), 0x38, 1500, "call audio link");
    }

    void setCallAudio(boolean on) { op5(T60Protocol.callAudioBle5(on), 0x38, 1500, "call audio"); linkCallAudio(); }

    void syncTimeNow() { op(T60Protocol.syncTime(Calendar.getInstance()), 0xA3, 1500, "time"); }

    // ---------------------------------------------------------------- history sync

    private final List<int[]> sleepSegs = new ArrayList<>();
    private String sleepNight;
    private ByteArrayBuilder sleepRaw = new ByteArrayBuilder();
    private int workoutsLeft;
    private boolean workoutSyncing;
    private int[] wo; // sport, startMs-ish fields
    private long woStart;

    private static final class ByteArrayBuilder {
        final java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        void add(byte[] b, int from) { o.write(b, from, b.length - from); }
        byte[] take() { byte[] r = o.toByteArray(); o.reset(); return r; }
    }

    void syncHistory() {
        store.setSyncing(true);
        op(T60Protocol.syncSteps(), 0xB2, 4000, "step history");
        op(T60Protocol.syncSleep(), 0x31, 4000, "sleep history");
        op(T60Protocol.syncHeartRate(), 0xF7, 4000, "heart-rate history");
        op(T60Protocol.syncOxygen(), 0x34, 4000, "blood-oxygen history");
        op(T60Protocol.syncWorkouts(), 0xFD, 4000, "workout history");
        op(T60Protocol.readBattery(), 0xA2, 1500, "battery");
        then(() -> { store.setSyncing(false); store.markSynced(); status("Connected · synced " + store.time(System.currentTimeMillis())); });
    }

    // ---------------------------------------------------------------- incoming packets

    private static int u(byte[] p, int i) { return i < p.length ? p[i] & 0xFF : 0; }
    private static int u16(byte[] p, int i) { return (u(p, i) << 8) | u(p, i + 1); }
    private static String date(byte[] p, int i) { return String.format("%04d%02d%02d", u16(p, i), u(p, i + 2), u(p, i + 3)); }

    private int syncCount;

    private void onPacket(byte[] p) {
        int lead = u(p, 0), sub = u(p, 1);
        if (lead != 0xB1 && lead != 0xB2 && lead != 0x32 && lead != 0xF7) store.logEvent("RX " + T60Protocol.toHex(p));
        switch (lead) {
            case 0xA1: {
                String v = new String(p, 1, p.length - 1, StandardCharsets.US_ASCII).trim();
                if (sub == 0x01) v = new String(p, 2, p.length - 2, StandardCharsets.US_ASCII).trim();
                if (!v.isEmpty()) store.setFirmwareVersion(v);
                replyArrived(lead, true);
                break;
            }
            case 0xA2:
                store.setBatteryPercent(Math.min(100, sub));
                status("Connected · battery " + Math.min(100, sub) + "%");
                replyArrived(lead, true);
                break;
            case 0x26:
                if (sub == 0x01 && p.length >= 17) {
                    store.set("screen_w", u16(p, 6));
                    store.set("screen_h", u16(p, 8));
                    store.set("dial_max", (u16(p, 11) << 16) | u16(p, 13));
                    store.set("dial_compat", u(p, 15));
                }
                replyArrived(lead, true);
                onFacePacket(p);
                break;
            case 0xB1: // live steps for the current hour
                if (p.length >= 8 && u16(p, 1) > 2000) { db.putSteps(date(p, 1), u(p, 5), u16(p, 6)); store.notifyChanged(); }
                break;
            case 0xB2:
                if (sub == 0xFD) { store.logEvent("Step history: " + syncCount + " hours"); syncCount = 0; replyArrived(lead, true); }
                else {
                    if (p.length >= 8 && u16(p, 1) > 2000) { db.putSteps(date(p, 1), u(p, 5), u16(p, 6)); syncCount++; }
                    replyArrived(lead, false);
                }
                break;
            case 0x31:
                onSleepHeader(p);
                break;
            case 0x32:
                sleepRaw.add(p, 1);
                if (current != null && current.expect == 0x31) { h.removeCallbacks(timeout); h.postDelayed(timeout, current.timeoutMs); }
                break;
            case 0xF7:
                onHeartRateHistory(p);
                break;
            case 0xE5:
                onLiveHeartRate(p);
                break;
            case 0x34:
                onOxygen(p);
                break;
            case 0xFD:
                onWorkout(p);
                break;
            case 0xD1:
                onWristButton(p);
                break;
            case 0xAF:
                if (sub == 0xAA && p.length >= 20) onLanguages(p);
                replyArrived(lead, true);
                break;
            case 0x38:
                if (sub == 0x01 && p.length >= 31) onCallAudioStatus(p);
                replyArrived(lead, true);
                break;
            case 0x52:
                if (p.length > 2 && u(p, 2) != 0xFD && p.length > sub + 3) onQuickReply(p);
                else replyArrived(lead, true);
                break;
            case 0xCB:
                replyArrived(lead, true);
                break;
            default:
                replyArrived(lead, true);
        }
    }

    private void onSleepHeader(byte[] p) {
        int sub = u(p, 1);
        if (sub == 0x01) {
            saveSleepNight();
            if (p.length >= 7) sleepNight = date(p, 2);
            replyArrived(0x31, false);
        } else if (sub == 0x02) {
            saveSleepNight();
            replyArrived(0x31, true);
        } else {
            replyArrived(0x31, true);
        }
    }

    /** Records are 6 bytes: hour, minute, state (1 deep, 2 light, 3 awake, 4 REM), ?, minutes (BE). */
    private void saveSleepNight() {
        byte[] raw = sleepRaw.take();
        if (sleepNight == null) return;
        sleepSegs.clear();
        for (int k = 0; k + 6 <= raw.length; k += 6) {
            int hr = raw[k] & 0xFF, mn = raw[k + 1] & 0xFF;
            if (hr > 23 || mn > 59) continue;
            sleepSegs.add(new int[]{hr * 60 + mn, ((raw[k + 4] & 0xFF) << 8) | (raw[k + 5] & 0xFF), Math.max(0, (raw[k + 2] & 0xFF) - 1)});
        }
        if (!sleepSegs.isEmpty()) {
            db.replaceNight(sleepNight, sleepSegs);
            store.logEvent("Sleep " + sleepNight + ": " + sleepSegs.size() + " parts");
        }
        sleepNight = null;
    }

    private void onHeartRateHistory(byte[] p) {
        int sub = u(p, 1);
        if (sub == 0xFD) { store.logEvent("Heart-rate history: " + syncCount + " hours"); syncCount = 0; replyArrived(0xF7, true); store.notifyChanged(); return; }
        if (sub == 0x03 && p.length >= 9) { // live all-day reading
            int bpm = u(p, 8);
            if (bpm > 40 && bpm < 200) {
                String day = date(p, 2);
                int hour = u(p, 6);
                if (hour == 0) { day = shiftDay(day, -1); hour = 24; }
                db.putHr(day, hour * 60 + u(p, 7), bpm);
                store.setLiveHeartRate(bpm);
            }
            return;
        }
        if (p.length >= 18 && u16(p, 1) > 2000) {
            String day = date(p, 1);
            int hour = u(p, 5);
            if (hour == 0) { day = shiftDay(day, -1); hour = 24; }
            syncCount++;
            for (int k = 6; k < p.length; k++) {
                int minute = hour * 60 - (17 - k) * 10, bpm = u(p, k);
                if (bpm > 40 && bpm < 200 && minute >= 0 && minute <= 1440) db.putHr(day, Math.min(minute, 1439), bpm);
            }
            replyArrived(0xF7, false);
            return;
        }
        replyArrived(0xF7, true);
    }

    private static String shiftDay(String day, int delta) {
        Calendar c = T60Db.parse(day);
        c.add(Calendar.DAY_OF_MONTH, delta);
        return T60Db.day(c);
    }

    private void onLiveHeartRate(byte[] p) {
        if (p.length == 2) {
            store.setHrTesting(u(p, 1) == 0x11);
            replyArrived(0xE5, true);
            return;
        }
        int bpm = u(p, 3);
        boolean finished = u(p, 1) == 0;
        if (u(p, 2) == 0 && bpm > 40 && bpm < 200) {
            store.setLiveHeartRate(bpm);
            Calendar c = Calendar.getInstance();
            db.putHr(T60Db.today(), c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), bpm);
        }
        if (finished) store.setHrTesting(false);
    }

    private void onOxygen(byte[] p) {
        int sub = u(p, 1);
        if (sub == 0xFA) {
            if (u(p, 2) == 0xFD || p.length < 8) { replyArrived(0x34, true); store.notifyChanged(); return; }
            String day = date(p, 2);
            int hour = u(p, 6);
            if (hour == 0) { day = shiftDay(day, -1); hour = 24; }
            for (int k = 0; k < p.length - 8; k++) {
                int v = u(p, k + 8), minute = hour * 60 - (11 - k) * 10;
                if (v != 0xFF && v >= 70 && v <= 100 && minute >= 0) db.putSpo2(day, Math.min(minute, 1439), v);
            }
            replyArrived(0x34, false);
            return;
        }
        if ((sub == 0x11 || sub == 0x00) && p.length >= 4) {
            int v = u(p, 2) == 0 ? u(p, 3) : 0;
            if (v >= 70 && v <= 100) {
                store.setLiveSpo2(v);
                if (sub == 0x00) {
                    Calendar c = Calendar.getInstance();
                    db.putSpo2(T60Db.today(), c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), v);
                }
            }
            if (sub == 0x00) store.setSpo2Testing(false);
            return;
        }
        if (sub == 0x11) store.setSpo2Testing(true);
        if (sub == 0x00 || sub == 0xFD) store.setSpo2Testing(false);
        replyArrived(0x34, true);
    }

    private void onWorkout(byte[] p) {
        int sub = u(p, 1);
        if (p.length == 10 && sub == 0xFA) {
            workoutsLeft = u16(p, 8);
            workoutSyncing = workoutsLeft > 0;
            replyArrived(0xFD, workoutsLeft == 0);
            return;
        }
        if ((p.length == 2 || p.length == 3) && sub == 0xFD) {
            saveWatchWorkout();
            workoutsLeft--;
            if (workoutsLeft <= 0) { workoutSyncing = false; replyArrived(0xFD, true); store.notifyChanged(); }
            else replyArrived(0xFD, false);
            return;
        }
        if ((p.length == 2 || p.length == 3) && sub == 0x01) {
            if (u(p, 2) == 0x55) { gpsForWatch = false; if (!workoutRunning) stopGps(); }
            replyArrived(0xFD, true);
            return;
        }
        if ((p.length == 2 || p.length == 3) && sub == 0x02) { replyArrived(0xFD, true); return; }
        if (workoutSyncing && p.length > 3) {
            int serial = u16(p, 1);
            if (serial == 0 && p.length >= 20) {
                wo = new int[10];
                wo[0] = u(p, 3);
                Calendar c = Calendar.getInstance();
                c.set(u16(p, 6), u(p, 8) - 1, u(p, 9), u(p, 10), u(p, 11), u(p, 12));
                c.set(Calendar.MILLISECOND, 0);
                woStart = c.getTimeInMillis();
                wo[1] = (u(p, 17) << 16) | u16(p, 18);
            } else if (serial == 1 && wo != null && p.length >= 15) {
                wo[2] = (u(p, 3) << 16) | u16(p, 4);
                wo[3] = u16(p, 8);
                wo[4] = Math.round((u(p, 10) + u(p, 11) / 100f) * 1000);
                wo[5] = u(p, 12);
                wo[6] = u(p, 13);
            }
            replyArrived(0xFD, false);
            return;
        }
        if (p.length == 14 && !workoutSyncing) { // live numbers while a workout runs on the watch
            int bpm = u(p, 2);
            if (bpm > 40 && bpm < 200) { store.setLiveHeartRate(bpm); liveWorkoutHr = bpm; }
            return;
        }
        if (p.length == 4 || p.length == 13) {
            if (sub == 0x01 && u(p, 2) == 0xAA) { // watch asks the phone for GPS
                gpsForWatch = true;
                startGps();
                return;
            }
            if (sub == 0x00 && workoutRunning && !endingWorkout) { store.logEvent("Workout ended on the watch"); endWorkout(true); }
            if (sub == 0x22 && workoutRunning) pauseLocal(true);
            if (sub == 0x33 && workoutRunning) pauseLocal(false);
            replyArrived(0xFD, true);
            return;
        }
        replyArrived(0xFD, true);
    }

    private void saveWatchWorkout() {
        if (wo == null) return;
        for (Object[] w : db.workouts(50)) {
            if (Math.abs((Long) w[0] - woStart) < 3 * 60_000L) { wo = null; return; } // already saved from the phone
        }
        db.putWorkout(woStart, wo[0], wo[1], wo[2], wo[4], wo[3], wo[5], wo[6], "watch", "");
        store.logEvent("Saved watch workout: " + T60Protocol.sportName(wo[0]));
        wo = null;
    }

    private void onLanguages(byte[] p) {
        store.set("language", u(p, 2));
        List<String> have = new ArrayList<>();
        // display1 = bytes 17..19 (languages 1-24), display2 = 14..16 (25-48), ... display5 = 5..7
        for (int group = 0; group < 5; group++) {
            int off = 17 - group * 3, mask = (u(p, off) << 16) | u16(p, off + 1);
            for (int bit = 0; bit < 24; bit++) if ((mask & (1 << bit)) != 0) have.add(String.valueOf(group * 24 + bit + 1));
        }
        if (!have.isEmpty()) store.setList("languages", have);
    }

    private void onCallAudioStatus(byte[] p) {
        int n = 0;
        while (n < 20 && p[2 + n] != 0) n++;
        store.set("bt3_name", new String(p, 2, n, StandardCharsets.US_ASCII));
        store.set("bt3_mac", String.format("%02X:%02X:%02X:%02X:%02X:%02X", u(p, 22), u(p, 23), u(p, 24), u(p, 25), u(p, 26), u(p, 27)));
        store.set("bt3_on", u(p, 28) == 1);
        store.set("bt3_paired", u(p, 29) == 1);
        store.set("bt3_connected", u(p, 30) == 1);
    }

    // ---------------------------------------------------------------- wrist buttons, music, find phone

    private void onWristButton(byte[] p) {
        int sub = u(p, 1);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        switch (sub) {
            case 0x07: mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); h.postDelayed(this::sendMusicInfo, 700); break;
            case 0x08: mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT); h.postDelayed(this::sendMusicInfo, 900); break;
            case 0x09: mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); h.postDelayed(this::sendMusicInfo, 900); break;
            // 0x0C is only the watch confirming a play-state update (GloryFit config status 95).
            case 0x0D:
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                op(T60Protocol.musicVolume(false, volumePct(am)), -1, 0, "volume");
                break;
            case 0x0E:
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                op(T60Protocol.musicVolume(true, volumePct(am)), -1, 0, "volume");
                break;
            case 0x12:
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
                break;
            case 0x0A:
                if (p.length == 2 && store.b("find_phone_on", true)) startFindPhone();
                else if (p.length == 4 && u(p, 3) == 0) stopFindPhone(false);
                break;
        }
        replyArrived(0xD1, true);
    }

    private static int volumePct(AudioManager am) {
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max > 0 ? Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max) : 50;
    }

    private void mediaKey(int code) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        long now = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
    }

    /** Play state, song title and volume to the watch's music screen. */
    void sendMusicInfo() {
        if (!store.b("music_on", true)) return;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        boolean playing = am.isMusicActive();
        String title = "";
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = msm.getActiveSessions(new ComponentName(this, T60NotificationListener.class));
            if (!list.isEmpty()) {
                MediaController mc = list.get(0);
                PlaybackState ps = mc.getPlaybackState();
                if (ps != null) playing = ps.getState() == PlaybackState.STATE_PLAYING;
                MediaMetadata md = mc.getMetadata();
                if (md != null) {
                    String t = md.getString(MediaMetadata.METADATA_KEY_TITLE), a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    title = (t == null ? "" : t) + (a == null || a.isEmpty() ? "" : " - " + a);
                }
            }
        } catch (Exception e) {
            store.logEvent("Song title needs notification access");
        }
        op(T60Protocol.musicState(playing), -1, 0, "play state");
        for (byte[] b : T60Protocol.songInfoBle5(playing, title)) op5(b, b[1] == (byte) 0xFA ? 0x3A : -1, 1000, "song");
        op(T60Protocol.musicVolume(false, volumePct(am)), -1, 0, "volume");
    }

    private MediaController watched;
    private final Runnable sendMusicSoon = this::sendMusicInfo;
    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { musicChanged(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { musicChanged(); }
    };

    private void musicChanged() {
        if (!ready) return;
        h.removeCallbacks(sendMusicSoon);
        h.postDelayed(sendMusicSoon, 800);
    }

    /** Follows the phone's active media player so the watch shows the current song (needs notification access). */
    void watchMedia() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, T60NotificationListener.class);
            msm.addOnActiveSessionsChangedListener(this::followSession, me, h);
            followSession(msm.getActiveSessions(me));
        } catch (Exception e) {
            // No notification access yet; retried on the 15-minute tick.
        }
    }

    private void followSession(List<MediaController> list) {
        if (watched != null) watched.unregisterCallback(mediaCallback);
        watched = list == null || list.isEmpty() ? null : list.get(0);
        if (watched != null) { watched.registerCallback(mediaCallback, h); musicChanged(); }
    }

    private Ringtone ringtone;

    void startFindPhone() {
        store.setFindPhoneRinging(true);
        try {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (ringtone != null) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                ringtone.setLooping(true);
                ringtone.play();
            }
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 600, 400}, 0));
        } catch (Exception ignored) {}
        Intent i = new Intent(this, MainActivity.class).putExtra("screen", "FindPhone").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, ALERT_CHANNEL_ID).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Here I am!").setContentText("Your watch is looking for this phone. Tap to stop.")
                .setContentIntent(pi).setFullScreenIntent(pi, true).setCategory(Notification.CATEGORY_ALARM).setOngoing(true).build();
        getSystemService(NotificationManager.class).notify(FIND_PHONE_NOTIF, n);
        try { startActivity(i); } catch (Exception ignored) {}
        h.postDelayed(autoStopRing, 60_000);
    }

    private final Runnable autoStopRing = () -> stopFindPhone(true);

    void stopFindPhone(boolean tellWatch) {
        h.removeCallbacks(autoStopRing);
        if (ringtone != null) { ringtone.stop(); ringtone = null; }
        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).cancel();
        getSystemService(NotificationManager.class).cancel(FIND_PHONE_NOTIF);
        if (store.isFindPhoneRinging() && tellWatch) op(T60Protocol.phoneRinging(false), 0xD1, 800, "stop ringing");
        store.setFindPhoneRinging(false);
    }

    void findWatch(boolean start) {
        op(start ? T60Protocol.findWatch() : T60Protocol.stopVibration(), -1, 0, start ? "find watch" : "stop buzzing");
    }

    // ---------------------------------------------------------------- live readings

    /** GloryFit (RateDetailsActivity): D6 01 static mode, wait 1.5 s on Realtek, then E5 11. */
    void measureHeartRate(boolean start) {
        store.setHrTesting(start);
        if (!start) { op(T60Protocol.heartRateTest(false), 0xE5, 1500, "stop heart-rate reading"); return; }
        op(T60Protocol.staticHeartRateMode(), 0xD6, 1500, "heart-rate mode");
        then(() -> h.postDelayed(() -> op(T60Protocol.heartRateTest(true), 0xE5, 3000, "heart-rate reading"), 1500));
    }

    void measureOxygen(boolean start) {
        store.setSpo2Testing(start);
        op(T60Protocol.oxygenTest(start), 0x34, 1500, "blood-oxygen reading");
    }

    // ---------------------------------------------------------------- text alerts

    /** Text popup on the watch: text sections, then the buzz (BluetoothLeService C5FD handling). */
    void pushText(int type, String text, String senderName, String number) {
        if (!ready) return;
        for (byte[] s : T60Protocol.textSections(0xC5, text, type, 240)) op(s, 0xC5, 2500, "text");
        if (type == T60Protocol.MSG_SMS && senderName != null) {
            // L7 0x800: the sender's name follows in C6 sections, capped at 30 bytes.
            for (byte[] s : T60Protocol.textSections(0xC6, senderName, type, 30)) op(s, 0xC6, 2500, "sender");
        }
        op(type == T60Protocol.MSG_CALL ? T60Protocol.callBuzz() : T60Protocol.messageBuzz(), -1, 0, "buzz");
        if (number != null && !number.isEmpty() && store.b("replies_on", true)) {
            op5(T60Protocol.senderNumberBle5(number), 0x45, 1000, "sender number");
        }
    }

    void callEnded() { if (ready) op(T60Protocol.callEnded(), 0xC1, 800, "call ended"); }

    private void onQuickReply(byte[] p) {
        int nLen = u(p, 1);
        String number = new String(p, 2, nLen, StandardCharsets.US_ASCII);
        int mLen = u(p, 2 + nLen);
        String msg = new String(p, 3 + nLen, Math.min(mLen, p.length - 3 - nLen), StandardCharsets.UTF_8);
        boolean ok = false;
        if (store.b("replies_on", true)) {
            try {
                SmsManager sms = SmsManager.getDefault();
                sms.sendMultipartTextMessage(number, null, sms.divideMessage(msg), null, null);
                ok = true;
                store.logEvent("Sent quick reply to " + number);
            } catch (Exception e) {
                store.logEvent("Quick reply failed: " + e.getMessage());
            }
        }
        op(T60Protocol.replyResult(ok), 0x52, 1000, "reply result");
    }

    // ---------------------------------------------------------------- calls and texts

    private String ringingNumber;

    private final PhoneStateListener phoneListener = new PhoneStateListener() {
        @Override public void onCallStateChanged(int state, String number) {
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                if (!store.isForwardCalls() || store.inQuietHours()) return;
                ringingNumber = number == null ? "" : number;
                String name = contactName(ringingNumber);
                pushText(T60Protocol.MSG_CALL, name != null ? name : (ringingNumber.isEmpty() ? "Incoming call" : ringingNumber), null, ringingNumber);
            } else if (ringingNumber != null) {
                ringingNumber = null;
                callEnded();
            }
        }
    };

    private final BroadcastReceiver smsReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (!store.isForwardSms() || store.inQuietHours()) return;
            SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (msgs == null || msgs.length == 0) return;
            StringBuilder body = new StringBuilder();
            for (SmsMessage m : msgs) body.append(m.getMessageBody());
            String number = msgs[0].getOriginatingAddress();
            String name = contactName(number);
            pushText(T60Protocol.MSG_SMS, body.toString(), name != null ? name : number, number);
        }
    };

    private boolean callHookOn, smsHookOn;

    private void registerPhoneHooks() {
        if (!callHookOn) try {
            ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
            callHookOn = true;
        } catch (Exception e) { store.logEvent("Call alerts need phone permission"); }
        if (!smsHookOn) try {
            registerReceiver(smsReceiver, new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION));
            smsHookOn = true;
        } catch (Exception e) { store.logEvent("Text alerts need SMS permission"); }
    }

    String contactName(String number) {
        if (number == null || number.isEmpty()) return null;
        try (android.database.Cursor c = getContentResolver().query(
                android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number)),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return null;
    }

    // ---------------------------------------------------------------- contacts, weather, clear

    void sendContacts(List<String[]> contacts) {
        for (byte[] b : T60Protocol.contactsBle5(contacts, mtuPayload)) op5(b, 0x37, 2500, "contacts");
    }

    void sendWeather(T60Protocol.Day[] days, int nowC, int humidity) {
        h.post(() -> {
            for (byte[] b : T60Protocol.weatherBle5(days, nowC, humidity, 0)) op5(b, 0xCB, 2000, "weather");
        });
    }

    /** Syncs everything first, then erases the watch's stored history. */
    void clearWatchData(Runnable done) {
        syncHistory();
        then(() -> op(T60Protocol.clearWatchData(), 0xAD, 3000, "clear watch data"));
        then(done);
    }

    boolean isReady() { return ready; }

    interface Msg { void say(String s); }

    // ---------------------------------------------------------------- photo watch face upload

    private byte[] face;
    private int faceIndex, faceChunk, faceBlock;
    private Msg faceMsg;
    private final Runnable faceTimeout = () -> faceDone("The watch stopped answering during the upload. Try again.");

    /**
     * Online-dial upload (WriteCommandToBLE.prepareSendOnlineDialData / sendSectionOnlineDialData):
     * 26 02 prepare, then "27 idxHi idxLo data" pieces on 34F1; the watch answers 26 03 04 after
     * each 4 KB block it has written; finally 26 03 00, answered 26 03 00 (ok), 01 (bad checksum),
     * 02 (too big) or 03 idx (resend from idx).
     */
    void sendPhotoFace(android.graphics.Bitmap bmp, int position, int color, Msg msg) {
        if (face != null) { msg.say("A watch face is already being sent."); return; }
        new Thread(() -> {
            byte[] data;
            try {
                data = T60Face.build(bmp, T60Face.preview(bmp, position, color), position, color, store.i("dial_compat", 2));
            } catch (Exception e) {
                msg.say("Couldn't build the face: " + e.getMessage());
                return;
            }
            h.post(() -> {
                if (data.length > store.i("dial_max", 1048576)) { msg.say("That face is too big for the watch."); return; }
                face = data;
                faceMsg = msg;
                faceIndex = 0;
                faceBlock = 0;
                faceChunk = mtuPayload - 3;
                store.set("face_progress", 0);
                store.logEvent("Photo face: " + data.length + " bytes in " + ((data.length + faceChunk - 1) / faceChunk) + " pieces");
                msg.say("Sending the photo face to the watch. Keep them close; this takes about a minute.");
                op(new byte[]{0x26, 0x02}, 0x26, 3000, "prepare face");
                h.postDelayed(faceTimeout, 10_000);
            });
        }).start();
    }

    private void onFacePacket(byte[] p) {
        if (face == null || p.length < 2) return;
        int sub = u(p, 1);
        if (sub == 0x02 && faceIndex == 0) { faceNext(); return; }
        if (sub != 0x03 || p.length < 3) return;
        switch (u(p, 2)) {
            case 0x04: faceNext(); break; // block written
            case 0x00: store.set("face_on_watch", true); faceDone("Done! The photo face is on your watch."); break;
            case 0x01: faceDone("The watch rejected the face (checksum). Nothing was changed."); break;
            case 0x02: faceDone("The watch says the face is too big."); break;
            case 0x03:
                if (p.length >= 5) { faceIndex = Math.max(0, u16(p, 3) - 1); faceNext(); }
                break;
        }
    }

    /** Sends pieces until a 4 KB block boundary (then waits for 26 03 04) or the end. */
    private void faceNext() {
        h.removeCallbacks(faceTimeout);
        int total = (face.length + faceChunk - 1) / faceChunk;
        while (faceIndex < total) {
            int off = faceIndex * faceChunk, n = Math.min(faceChunk, face.length - off);
            byte[] pkt = new byte[n + 3];
            pkt[0] = 0x27; pkt[1] = (byte) (faceIndex >> 8); pkt[2] = (byte) faceIndex;
            System.arraycopy(face, off, pkt, 3, n);
            queue.add(new Op(true, pkt, -1, 0, "face piece"));
            faceIndex++;
            int block = Math.min(faceIndex * faceChunk, face.length) / 4096;
            if (faceIndex % 20 == 0) store.set("face_progress", faceIndex * 100 / total);
            if (block != faceBlock && faceIndex < total) {
                faceBlock = block;
                h.postDelayed(faceTimeout, 15_000);
                pump();
                return; // the watch answers 26 03 04 when the block is written
            }
        }
        pump();
        then(() -> h.postDelayed(() -> { op(new byte[]{0x26, 0x03, 0x00}, 0x26, 5000, "finish face"); h.postDelayed(faceTimeout, 15_000); }, 500));
    }

    private void faceDone(String text) {
        h.removeCallbacks(faceTimeout);
        store.logEvent("Photo face: " + text);
        Msg m = faceMsg;
        face = null;
        faceMsg = null;
        store.set("face_progress", -1);
        if (m != null) m.say(text);
    }

    // ---------------------------------------------------------------- workouts with phone GPS

    boolean workoutRunning, workoutPaused, endingWorkout, gpsForWatch;
    int workoutCode, liveWorkoutHr;
    long workoutStartMs, pausedMs, pauseStartedMs;
    float workoutMeters;
    Location lastFix;     // last point added to the route (only after moving 5 m)
    Location lastReading; // latest reading of any kind, so standing still still counts as "GPS good"
    final List<double[]> track = new ArrayList<>();
    private final List<Integer> workoutHrs = new ArrayList<>();
    private boolean gpsOn;

    void startWorkout(int code) {
        workoutCode = code;
        workoutRunning = true;
        workoutPaused = false;
        endingWorkout = false;
        workoutStartMs = System.currentTimeMillis();
        pausedMs = 0;
        workoutMeters = 0;
        lastFix = null;
        lastReading = null;
        track.clear();
        workoutHrs.clear();
        liveWorkoutHr = 0;
        if (ready) op(T60Protocol.workoutStart(code), 0xFD, 2000, "start workout"); // the phone records it either way
        startGps();
        h.post(workoutTick);
        store.notifyChanged();
    }

    int workoutSeconds() {
        long now = workoutPaused ? pauseStartedMs : System.currentTimeMillis();
        return (int) Math.max(0, (now - workoutStartMs - pausedMs) / 1000);
    }

    int workoutKcal() { return Math.round(store.weightKg() * workoutMeters / 1000f * (workoutCode == 2 ? 0.3f : 0.9f)); }

    int paceSecPerKm() { return workoutMeters < 20 ? 0 : Math.round(workoutSeconds() / (workoutMeters / 1000f)); }

    private void pauseLocal(boolean pause) {
        if (pause == workoutPaused) return;
        workoutPaused = pause;
        if (pause) pauseStartedMs = System.currentTimeMillis();
        else pausedMs += System.currentTimeMillis() - pauseStartedMs;
        store.notifyChanged();
    }

    void pauseWorkout(boolean pause) {
        pauseLocal(pause);
        if (ready) op(T60Protocol.workoutState(pause ? 0x22 : 0x33, workoutCode, workoutSeconds(), workoutKcal(), workoutMeters / 1000f, paceSecPerKm()), 0xFD, 1500, pause ? "pause" : "resume");
    }

    void endWorkout(boolean fromWatch) {
        if (!workoutRunning) return;
        endingWorkout = true;
        if (workoutPaused) pauseLocal(false);
        int secs = workoutSeconds();
        if (!fromWatch && ready) {
            op(T60Protocol.workoutState(0x44, workoutCode, secs, workoutKcal(), workoutMeters / 1000f, paceSecPerKm()), 0xFD, 1500, "end workout");
            op(T60Protocol.workoutStop(workoutCode), 0xFD, 1500, "stop workout");
        }
        int avg = 0, max = 0;
        for (int b : workoutHrs) { avg += b; max = Math.max(max, b); }
        if (!workoutHrs.isEmpty()) avg /= workoutHrs.size();
        JSONArray t = new JSONArray();
        try { for (double[] pt : track) t.put(new JSONArray().put(pt[0]).put(pt[1])); } catch (Exception ignored) {}
        if (secs >= 30) db.putWorkout(workoutStartMs, workoutCode, secs, 0, Math.round(workoutMeters), workoutKcal(), avg, max, "phone", t.toString());
        workoutRunning = false;
        h.removeCallbacks(workoutTick);
        if (!gpsForWatch) stopGps();
        store.notifyChanged();
    }

    private final Runnable workoutTick = new Runnable() {
        @Override public void run() {
            if (!workoutRunning) return;
            int bpm = liveWorkoutHr > 0 ? liveWorkoutHr : store.getLiveHeartRate();
            if (bpm > 40 && !workoutPaused) workoutHrs.add(bpm);
            store.notifyChanged();
            h.postDelayed(this, 1000);
        }
    };

    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            int point = 0;
            lastReading = l;
            if (workoutRunning && !workoutPaused && l.getAccuracy() < 40
                    // Count a move only when it is bigger than the reading's own error, so standing still adds no distance.
                    && (lastFix == null || lastFix.distanceTo(l) >= Math.max(5, l.getAccuracy() + lastFix.getAccuracy()))) {
                if (lastFix != null) {
                    point = Math.round(lastFix.distanceTo(l));
                    if (point < 200) workoutMeters += point;
                }
                lastFix = l;
                track.add(new double[]{l.getLatitude(), l.getLongitude()});
            }
            if (gpsForWatch) {
                if (!gpsAnnounced) { gpsAnnounced = true; op5(T60Protocol.gpsReadyBle5(), 0xFD, 1000, "GPS ready"); }
                if (SystemClock.elapsedRealtime() - lastGpsSend > 2900) {
                    lastGpsSend = SystemClock.elapsedRealtime();
                    op5(T60Protocol.gpsPointBle5(l.getLatitude(), l.getLongitude(), l.getSpeed(), point, Math.round(workoutMeters)), -1, 0, "GPS point");
                }
            }
            store.notifyChanged();
        }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };
    private boolean gpsAnnounced;
    private long lastGpsSend;

    private void startGps() {
        if (gpsOn) return;
        try {
            ((LocationManager) getSystemService(Context.LOCATION_SERVICE))
                    .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener, Looper.getMainLooper());
            gpsOn = true;
            gpsAnnounced = false;
        } catch (Exception e) {
            store.logEvent("GPS unavailable: " + e.getMessage());
        }
    }

    private void stopGps() {
        if (!gpsOn) return;
        ((LocationManager) getSystemService(Context.LOCATION_SERVICE)).removeUpdates(gpsListener);
        gpsOn = false;
    }

    boolean gpsGood() { return lastReading != null && System.currentTimeMillis() - lastReading.getTime() < 10_000 && lastReading.getAccuracy() < 25; }

    // ---------------------------------------------------------------- every 15 minutes

    private int lastDay = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            h.postDelayed(this, 15 * 60_000L);
            if (watched == null) watchMedia();
            if (!ready) { if (gatt == null) connectWatch(); return; }
            Calendar c = Calendar.getInstance();
            int day = c.get(Calendar.DAY_OF_YEAR);
            if (day != lastDay) {
                if (lastDay != -1) { syncTimeNow(); sendMoveReminder(); cycleReminder(); }
                lastDay = day;
            }
            if (store.b("weather_on", true) && System.currentTimeMillis() - Weather.lastUpdate(store) > 60 * 60_000L) {
                Weather.update(T60BleService.this, e -> {});
            }
            if (System.currentTimeMillis() - store.lastSync() > 60 * 60_000L && !workoutRunning) syncHistory();
        }
    };

    private void cycleReminder() {
        if (!store.b("cycle_on", false) || !store.b("cycle_remind", true) || store.s("cycle_start", "").isEmpty()) return;
        int days = MainActivity.daysUntilNextPeriod(store);
        if (days == store.i("cycle_remind_days", 2)) pushText(T60Protocol.MSG_OTHER, "Period expected in " + days + " days", null, null);
    }
}
