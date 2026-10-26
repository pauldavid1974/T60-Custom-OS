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
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Always-on local BLE Foreground Service for Paul's T60 smartwatch.
 * Connects directly to 60:1A:B7:00:AE:67, uses Write-With-Response on 33F1
 * and Write-Without-Response on 34F1, decodes all 33F2/34F2 telemetry, and
 * auto-reconnects when in range. Zero cloud calls.
 */
@SuppressLint("MissingPermission")
public class T60BleService extends Service {

    public static final String CHANNEL_ID = "t60_ble_channel";
    public static final String ALERT_CHANNEL_ID = "t60_find_phone_alert";
    private static final int NOTIF_ID = 1001;

    private static T60BleService activeInstance;

    private final IBinder binder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic char33F1;
    private BluetoothGattCharacteristic char34F1;
    private boolean isConnected = false;

    private static class WriteOp {
        final UUID charUuid;
        final byte[] payload;
        WriteOp(UUID u, byte[] p) { this.charUuid = u; this.payload = p; }
    }

    private final Queue<WriteOp> writeQueue = new LinkedList<>();
    private boolean writeBusy = false;

    public class LocalBinder extends Binder {
        public T60BleService getService() {
            return T60BleService.this;
        }
    }

    public static T60BleService getInstance() {
        return activeInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        createChannels();
        startForeground(NOTIF_ID, buildStatusNotification("T60 Ready • Connecting..."));
        connectWatch();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // autoConnect=true keeps an existing GATT client retrying; opening a second one
        // duplicates every notification (each wrist press was handled twice).
        if (gatt == null) {
            connectWatch();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        activeInstance = null;
        disconnectGatt();
        super.onDestroy();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "T60 Watch Connection", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps your T60 watch connected in your pocket");
            nm.createNotificationChannel(ch);

            NotificationChannel alertCh = new NotificationChannel(
                    ALERT_CHANNEL_ID, "T60 Find My Phone Alert", NotificationManager.IMPORTANCE_HIGH);
            alertCh.setDescription("Rings phone when Find Phone is tapped on T60 watch");
            nm.createNotificationChannel(alertCh);
        }
    }

    private Notification buildStatusNotification(String statusText) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("T60 Watch")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateForegroundStatus(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildStatusNotification(text));
        }
    }

    public void connectWatch() {
        T60Store store = T60Store.get(this);
        String mac = store.getWatchMac();
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
        if (adapter == null || !adapter.isEnabled()) {
            store.setConnectionState("Bluetooth Off");
            store.logEvent("Bluetooth adapter is disabled");
            return;
        }
        try {
            disconnectGatt();
            store.setConnectionState("Connecting to " + mac + "...");
            store.logEvent("Connecting to T60 (" + mac + ")...");
            BluetoothDevice device = adapter.getRemoteDevice(mac);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                gatt = device.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                gatt = device.connectGatt(this, true, gattCallback);
            }
        } catch (Exception e) {
            store.setConnectionState("Connection Error");
            store.logEvent("Connect error: " + e.getMessage());
        }
    }

    private void disconnectGatt() {
        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) {}
            try { gatt.close(); } catch (Exception ignored) {}
            gatt = null;
        }
        isConnected = false;
        char33F1 = null;
        char34F1 = null;
        writeQueue.clear();
        writeBusy = false;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            T60Store store = T60Store.get(T60BleService.this);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                store.setConnectionState("Connected • Discovering...");
                store.logEvent("BLE connected! Requesting MTU & services...");
                updateForegroundStatus("T60 Connected • Syncing...");
                // Android runs one GATT op at a time and silently drops overlaps, so chain:
                // MTU -> discover -> 33F2 CCCD -> 34F2 CCCD -> startup sync.
                if (!g.requestMtu(247)) g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                char33F1 = null;
                char34F1 = null;
                writeQueue.clear();
                writeBusy = false;
                store.setConnectionState("Disconnected (Auto-Retry)");
                store.logEvent("Watch disconnected (status=" + status + ")");
                updateForegroundStatus("T60 Disconnected • Waiting for watch...");
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            T60Store.get(T60BleService.this).logEvent("MTU negotiated: " + mtu + " bytes");
            g.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            T60Store store = T60Store.get(T60BleService.this);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                store.logEvent("Service discovery failed: " + status);
                return;
            }
            BluetoothGattService svc55 = g.getService(T60Protocol.UUID_SERVICE_MAIN);
            BluetoothGattService svc56 = g.getService(T60Protocol.UUID_SERVICE_EXT);
            if (svc55 != null) char33F1 = svc55.getCharacteristic(T60Protocol.UUID_CHAR_WRITE);
            if (svc56 != null) char34F1 = svc56.getCharacteristic(T60Protocol.UUID_CHAR_EXT_WRITE);
            store.setConnectionState("Connected • Active");
            store.logEvent("55FF/33F1 & 56FF/34F1 ready! Enabling replies...");
            if (!enableNotification(g, svc55, T60Protocol.UUID_CHAR_NOTIFY)
                    && !enableNotification(g, svc56, T60Protocol.UUID_CHAR_EXT_NOTIFY)) {
                performStartupSync();
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            T60Store.get(T60BleService.this).logEvent("Replies enabled on "
                    + d.getCharacteristic().getUuid().toString().substring(4, 8).toUpperCase() + " (status=" + status + ")");
            if (d.getCharacteristic().getUuid().equals(T60Protocol.UUID_CHAR_NOTIFY)
                    && enableNotification(g, g.getService(T60Protocol.UUID_SERVICE_EXT), T60Protocol.UUID_CHAR_EXT_NOTIFY)) {
                return; // 34F2 next; sync runs after its callback
            }
            performStartupSync();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            writeBusy = false;
            processNextWrite();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            byte[] data = c.getValue();
            handleIncomingPacket(c.getUuid(), data);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            handleIncomingPacket(c.getUuid(), value);
        }
    };

    /** Returns true if a CCCD write was started (onDescriptorWrite will follow). */
    private boolean enableNotification(BluetoothGatt g, BluetoothGattService svc, UUID charUuid) {
        BluetoothGattCharacteristic ch = svc != null ? svc.getCharacteristic(charUuid) : null;
        if (g == null || ch == null) return false;
        g.setCharacteristicNotification(ch, true);
        BluetoothGattDescriptor cccd = ch.getDescriptor(T60Protocol.UUID_CCCD);
        if (cccd == null) return false;
        cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return g.writeDescriptor(cccd);
    }

    public void performStartupSync() {
        T60Store store = T60Store.get(this);
        // 1. Sync current local time
        enqueueWrite33F1(T60Protocol.buildSyncTime(Calendar.getInstance()));
        // 2. Sync 12-hour AM/PM + Imperial (miles/lbs)
        enqueueWrite33F1(T60Protocol.buildUnitAndTimeFormat(store.isImperial(), store.isTwelveHour()));
        // 3. Sync English + Fahrenheit + Raise-to-Wake
        enqueueWrite33F1(T60Protocol.buildProfileAndTempUnit(8000, store.isImperial(), store.isRaiseToWake()));
        // 4. Enable Find-Phone button on watch
        enqueueWrite33F1(T60Protocol.buildEnableFindPhone(true));
        // 5. Query Battery, Version, Dial Config, and Steps
        enqueueWrite33F1(T60Protocol.buildReadBattery());
        enqueueWrite33F1(T60Protocol.buildReadVersion());
        enqueueWrite33F1(T60Protocol.buildReadDialConfig());
        enqueueWrite33F1(T60Protocol.buildSyncSteps());
        enqueueWrite33F1(T60Protocol.buildSyncSleep());
        store.updateLastSyncTime();
    }

    public synchronized void enqueueWrite33F1(byte[] payload) {
        writeQueue.add(new WriteOp(T60Protocol.UUID_CHAR_WRITE, payload));
        processNextWrite();
    }

    public synchronized void enqueueWrite34F1(byte[] payload) {
        writeQueue.add(new WriteOp(T60Protocol.UUID_CHAR_EXT_WRITE, payload));
        processNextWrite();
    }

    private synchronized void processNextWrite() {
        if (writeBusy || gatt == null || !isConnected) return;
        WriteOp op = writeQueue.poll();
        if (op == null) return;

        BluetoothGattCharacteristic target = op.charUuid.equals(T60Protocol.UUID_CHAR_EXT_WRITE) ? char34F1 : char33F1;
        if (target == null) return;

        writeBusy = true;
        boolean is33F1 = op.charUuid.equals(T60Protocol.UUID_CHAR_WRITE);
        target.setWriteType(is33F1
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        target.setValue(op.payload);
        T60Store.get(this).logEvent("TX -> " + (is33F1 ? "33F1: " : "34F1: ") + T60Protocol.toHex(op.payload));
        boolean ok = gatt.writeCharacteristic(target);
        if (!ok || !is33F1) {
            mainHandler.postDelayed(() -> {
                writeBusy = false;
                processNextWrite();
            }, 90);
        } else {
            // Safety fallback timeout in case callback is dropped
            mainHandler.postDelayed(() -> {
                if (writeBusy) {
                    writeBusy = false;
                    processNextWrite();
                }
            }, 600);
        }
    }

    private void handleIncomingPacket(UUID uuid, byte[] data) {
        if (data == null || data.length == 0) return;
        T60Store store = T60Store.get(this);
        int op = data[0] & 0xFF;
        String hex = T60Protocol.toHex(data);

        if (op == 0xA1 && data.length > 1) {
            String ver = new String(data, 1, data.length - 1, StandardCharsets.US_ASCII).trim();
            store.setFirmwareVersion(ver);
            store.logEvent("RX FW Version: " + ver);
        } else if (op == 0xA2 && data.length >= 2) {
            int pct = data.length == 2 ? (data[1] & 0xFF) : (data[2] & 0xFF);
            store.setBatteryPercent(pct);
            updateForegroundStatus("T60 Connected • Battery " + pct + "%");
            store.logEvent("RX Battery: " + pct + "%");
        } else if (op == 0x26 && data.length >= 17 && (data[1] & 0xFF) == 0x01) {
            int w = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
            int h = ((data[8] & 0xFF) << 8) | (data[9] & 0xFF);
            int maxSz = ((data[11] & 0xFF) << 24) | ((data[12] & 0xFF) << 16)
                      | ((data[13] & 0xFF) << 8) | (data[14] & 0xFF);
            store.setScreenSpec(w + " × " + h + " px • " + (maxSz / 1024) + " KB Dial");
            store.logEvent("RX Dial Config: " + w + "x" + h + "px");
        } else if (op == 0xE5 && data.length >= 3) {
            int bpm = data[data.length - 1] & 0xFF;
            if (bpm > 30 && bpm < 240) {
                store.setLiveHeartRate(bpm);
                store.logEvent("RX Live Heart Rate: " + bpm + " BPM");
            } else {
                store.logEvent("RX HR State: " + hex);
            }
        } else if ((op == 0xB1 || op == 0xB2) && data.length >= 18) {
            int runSteps = ((data[11] & 0xFF) << 8) | (data[12] & 0xFF);
            int walkSteps = ((data[16] & 0xFF) << 8) | (data[17] & 0xFF);
            int total = runSteps + walkSteps;
            if (total > store.getTodaySteps()) {
                store.setTodaySteps(total);
            }
            store.logEvent("RX Steps Packet: " + total + " steps");
        } else if (op == 0xD1 && data.length >= 2) {
            int sub = data[1] & 0xFF;
            handleWristD1Command(sub, data, hex);
        } else {
            store.logEvent("RX [" + uuid.toString().substring(4, 8).toUpperCase() + "]: " + hex);
        }
    }

    private void handleWristD1Command(int sub, byte[] data, String hex) {
        T60Store store = T60Store.get(this);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (sub == 0x07) {
            // Play / Pause toggle (0xD1 0x07)
            dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            boolean active = am != null && am.isMusicActive();
            enqueueWrite33F1(T60Protocol.buildMusicPlayState(!active));
            syncCurrentVolumeToWatch(am, false);
            store.logEvent("RX Wrist Media: Play/Pause (0xD1 0x07)");
            fireWebhookIfConfigured("media_play_pause");
        } else if (sub == 0x08) {
            // Next Track (0xD1 0x08)
            dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_NEXT);
            enqueueWrite33F1(T60Protocol.buildMusicPlayState(true));
            store.logEvent("RX Wrist Media: Next Track (0xD1 0x08)");
            fireWebhookIfConfigured("media_next");
        } else if (sub == 0x09) {
            // Previous Track (0xD1 0x09)
            dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            enqueueWrite33F1(T60Protocol.buildMusicPlayState(true));
            store.logEvent("RX Wrist Media: Previous Track (0xD1 0x09)");
            fireWebhookIfConfigured("media_prev");
        } else if (sub == 0x0C) {
            // Watch opened Music screen -> sync play state & volume %
            boolean active = am != null && am.isMusicActive();
            enqueueWrite33F1(T60Protocol.buildMusicPlayState(active));
            syncCurrentVolumeToWatch(am, false);
            store.logEvent("RX Watch Music Screen Opened (0xD1 0x0C)");
        } else if (sub == 0x0D) {
            // Volume UP (0xD1 0x0D)
            if (am != null) {
                am.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI);
                syncCurrentVolumeToWatch(am, false);
            }
            store.logEvent("RX Wrist Media: Volume UP (0xD1 0x0D)");
            fireWebhookIfConfigured("volume_up");
        } else if (sub == 0x0E) {
            // Volume DOWN (0xD1 0x0E)
            if (am != null) {
                am.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI);
                syncCurrentVolumeToWatch(am, true);
            }
            store.logEvent("RX Wrist Media: Volume DOWN (0xD1 0x0E)");
            fireWebhookIfConfigured("volume_down");
        } else if (sub == 0x12) {
            // Mute toggle (0xD1 0x12)
            dispatchMediaKey(am, KeyEvent.KEYCODE_VOLUME_MUTE);
            syncCurrentVolumeToWatch(am, false);
            store.logEvent("RX Wrist Media: Mute Toggle (0xD1 0x12)");
        } else if (sub == 0x0A) {
            store.logEvent("RX WRIST TRIGGER: Find My Phone (0xD1 0x0A)!");
            triggerFindMyPhoneAlert();
            fireWebhookIfConfigured("find_phone");
        } else {
            store.logEvent("RX Wrist Button (0xD1): " + hex);
            fireWebhookIfConfigured("wrist_button_" + sub);
        }
    }

    private void dispatchMediaKey(AudioManager am, int keyCode) {
        if (am == null) return;
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
        am.dispatchMediaKeyEvent(down);
        am.dispatchMediaKeyEvent(up);
    }

    private void syncCurrentVolumeToWatch(AudioManager am, boolean isVolDownAck) {
        if (am == null) return;
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int pct = max > 0 ? Math.round((cur * 100f) / max) : 50;
        enqueueWrite33F1(T60Protocol.buildMusicVolumeState(isVolDownAck, pct));
    }

    private void triggerFindMyPhoneAlert() {
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500, 200, 700}, -1));
                } else {
                    vib.vibrate(1500);
                }
            }
            Uri ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (ringUri == null) {
                ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), ringUri);
            if (r != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    r.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build());
                }
                r.play();
                mainHandler.postDelayed(r::stop, 4500);
            }
        } catch (Exception ignored) {}
    }

    private void fireWebhookIfConfigured(String eventName) {
        String urlStr = T60Store.get(this).getWebhookUrl();
        if (urlStr == null || urlStr.isEmpty()) return;
        new Thread(() -> {
            try {
                URL u = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(4000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String body = "{\"event\":\"" + eventName + "\",\"watch\":\"T60\"}";
                conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                int code = conn.getResponseCode();
                T60Store.get(T60BleService.this).logEvent("Webhook fired (" + eventName + ") -> HTTP " + code);
            } catch (Exception e) {
                T60Store.get(T60BleService.this).logEvent("Webhook error: " + e.getMessage());
            }
        }).start();
    }

    public void pushWristNotification(String title, String text, int msgType) {
        List<byte[]> pkts = T60Protocol.buildNotificationPackets(title, text, msgType);
        for (byte[] p : pkts) {
            enqueueWrite33F1(p);
        }
    }
}
