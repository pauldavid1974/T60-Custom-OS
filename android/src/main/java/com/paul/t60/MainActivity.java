package com.paul.t60;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Clean Dark-Mode 4-Tab Android Companion App ("T60") for Paul's T60 Smartwatch.
 * Tabs:
 *   1. Status (Live Connection, Battery, Pure-Black 320x380 Watchface Preview & Switcher, Quick Actions)
 *   2. Notifications (Call/SMS/App Forwarding, Test Popup Sender, Wrist-Button Webhook)
 *   3. Health (Daily Steps, Sleep, Live Heart-Rate BPM Sensor & History, JSON Export)
 *   4. Settings (12-Hour AM/PM, Imperial °F/miles/lbs, Raise-to-Wake, Wrist Speakerphone, Alarms, Live BLE Log)
 */
public class MainActivity extends Activity implements T60Store.Listener {

    private static final int COLOR_BG       = 0xFF0B0E14;
    private static final int COLOR_CARD     = 0xFF151A24;
    private static final int COLOR_BORDER   = 0xFF252E3F;
    private static final int COLOR_ACCENT   = 0xFF38BDF8;
    private static final int COLOR_GREEN    = 0xFF22C55E;
    private static final int COLOR_TEXT     = 0xFFF8FAFC;
    private static final int COLOR_SUBTEXT  = 0xFF94A3B8;

    private int activeTab = 0;
    private FrameLayout contentContainer;
    private final Button[] tabButtons = new Button[4];

    // Dynamic UI views updated on state changes
    private TextView tvConnBadge;
    private TextView tvBatteryBig;
    private TextView tvFwInfo;
    private TextView tvWatchTime;
    private TextView tvWatchDate;
    private TextView tvWatchBattery;
    private TextView tvStepsVal;
    private TextView tvMilesVal;
    private TextView tvSleepVal;
    private TextView tvHrVal;
    private TextView tvPacketLog;
    private Button btnHrToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestBlePermissionsIfNeeded();
        startBleService();
        buildRootUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        T60Store.get(this).addListener(this);
        refreshUi();
    }

    @Override
    protected void onPause() {
        T60Store.get(this).removeListener(this);
        super.onPause();
    }

    private void requestBlePermissionsIfNeeded() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!perms.isEmpty()) {
            requestPermissions(perms.toArray(new String[0]), 101);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        startBleService();
        T60BleService svc = T60BleService.getInstance();
        if (svc != null) {
            svc.connectWatch();
        }
    }

    private void startBleService() {
        Intent i = new Intent(this, T60BleService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void buildRootUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        // Top Header Bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(12));
        header.setBackgroundColor(0xFF0F141D);

        TextView title = new TextView(this);
        title.setText("T60");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleLp);

        tvConnBadge = new TextView(this);
        tvConnBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvConnBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvConnBadge.setPadding(dp(12), dp(6), dp(12), dp(6));
        header.addView(tvConnBadge);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Main Scrollable Content Area
        contentContainer = new FrameLayout(this);
        root.addView(contentContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom 4-Tab Bar
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(0xFF0F141D);
        tabBar.setPadding(dp(8), dp(8), dp(8), dp(8));

        String[] tabNames = new String[] { "Status", "Alerts", "Health", "Settings" };
        for (int i = 0; i < 4; i++) {
            final int tabIdx = i;
            Button b = new Button(this);
            b.setText(tabNames[i]);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setOnClickListener(v -> selectTab(tabIdx));
            tabButtons[i] = b;
            tabBar.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }

        root.addView(tabBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        selectTab(0);
    }

    private void selectTab(int idx) {
        activeTab = idx;
        for (int i = 0; i < 4; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            if (i == activeTab) {
                bg.setColor(0xFF1E293B);
                bg.setStroke(dp(1), COLOR_ACCENT);
                tabButtons[i].setTextColor(COLOR_ACCENT);
                tabButtons[i].setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                bg.setColor(Color.TRANSPARENT);
                tabButtons[i].setTextColor(COLOR_SUBTEXT);
                tabButtons[i].setTypeface(Typeface.DEFAULT);
            }
            tabButtons[i].setBackground(bg);
        }

        contentContainer.removeAllViews();
        ScrollView sv = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(24));
        sv.addView(page);
        contentContainer.addView(sv);

        if (idx == 0) buildStatusTab(page);
        else if (idx == 1) buildAlertsTab(page);
        else if (idx == 2) buildHealthTab(page);
        else buildSettingsTab(page);

        refreshUi();
    }

    private void buildStatusTab(LinearLayout page) {
        // Card 1: Pure-Black 320x380 Watchface Preview + Battery & Connection
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);

        // Left: Pure Black 320x380 Watchface Mockup (scaled proportionally: 144dp x 171dp)
        LinearLayout watchScreen = new LinearLayout(this);
        watchScreen.setOrientation(LinearLayout.VERTICAL);
        watchScreen.setGravity(Gravity.CENTER);
        watchScreen.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable watchBezel = new GradientDrawable();
        watchBezel.setColor(0xFF000000); // Pure Black (#000000) per Paul's spec
        watchBezel.setCornerRadius(dp(24));
        watchBezel.setStroke(dp(2), 0xFF334155);
        watchScreen.setBackground(watchBezel);

        tvWatchBattery = new TextView(this);
        tvWatchBattery.setTextColor(COLOR_GREEN);
        tvWatchBattery.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvWatchBattery.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvWatchBattery.setGravity(Gravity.END);
        watchScreen.addView(tvWatchBattery, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvWatchTime = new TextView(this);
        tvWatchTime.setTextColor(0xFFFFFFFF);
        tvWatchTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvWatchTime.setSingleLine(true);
        tvWatchTime.setTypeface(Typeface.DEFAULT_BOLD);
        tvWatchTime.setGravity(Gravity.CENTER);
        tvWatchTime.setPadding(0, dp(14), 0, dp(8));
        watchScreen.addView(tvWatchTime);

        tvWatchDate = new TextView(this);
        tvWatchDate.setTextColor(COLOR_ACCENT);
        tvWatchDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvWatchDate.setSingleLine(true);
        tvWatchDate.setTypeface(Typeface.DEFAULT_BOLD);
        tvWatchDate.setGravity(Gravity.CENTER);
        watchScreen.addView(tvWatchDate);

        heroRow.addView(watchScreen, new LinearLayout.LayoutParams(dp(152), dp(180)));

        // Right: Live Watch Telemetry & Sync Button
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setPadding(dp(16), 0, 0, 0);

        tvBatteryBig = new TextView(this);
        tvBatteryBig.setTextColor(COLOR_TEXT);
        tvBatteryBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        tvBatteryBig.setTypeface(Typeface.DEFAULT_BOLD);
        rightCol.addView(tvBatteryBig);

        tvFwInfo = new TextView(this);
        tvFwInfo.setTextColor(COLOR_SUBTEXT);
        tvFwInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvFwInfo.setPadding(0, dp(4), 0, dp(12));
        rightCol.addView(tvFwInfo);

        Button btnSyncNow = makeActionButton("Sync Time & Status", COLOR_ACCENT, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) {
                svc.performStartupSync();
                Toast.makeText(this, "Syncing time, 12h/Imperial & battery...", Toast.LENGTH_SHORT).show();
            }
        });
        rightCol.addView(btnSyncNow);

        Button btnReconnect = makeActionButton("Reconnect Watch", 0xFF334155, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) {
                svc.connectWatch();
                Toast.makeText(this, "Reconnecting to T60...", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams recLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recLp.topMargin = dp(8);
        rightCol.addView(btnReconnect, recLp);

        heroRow.addView(rightCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(wrapCard("T60 Live Wrist Display (320 × 380)", heroRow));

        // Card 2: Quick Wrist Controls
        LinearLayout quickGrid = new LinearLayout(this);
        quickGrid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFind = makeActionButton("Find Watch (Vibrate)", 0xFF0284C7, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) svc.enqueueWrite33F1(T60Protocol.buildFindWatch(true));
        });
        Button btnStopFind = makeActionButton("Stop Vibrate", 0xFF334155, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) svc.enqueueWrite33F1(T60Protocol.buildFindWatch(false));
        });
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half1.rightMargin = dp(6);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(6);
        row1.addView(btnFind, half1);
        row1.addView(btnStopFind, half2);
        quickGrid.addView(row1);

        // Watchface Slot Switcher + Pure Black Custom Face Apply
        TextView wfLabel = makeSubLabel("Switch Active Watchface Slot (or Custom Black Slot):");
        wfLabel.setPadding(0, dp(12), 0, dp(6));
        quickGrid.addView(wfLabel);

        LinearLayout dialRow = new LinearLayout(this);
        dialRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int slot = 0; slot < 4; slot++) {
            final int sIdx = slot;
            Button bSlot = makeActionButton("Dial " + (slot + 1), 0xFF1E293B, v -> {
                T60BleService svc = T60BleService.getInstance();
                if (svc != null) {
                    svc.enqueueWrite33F1(T60Protocol.buildSwitchWatchFace(sIdx, 0));
                    Toast.makeText(this, "Switched to Watchface Slot " + (sIdx + 1), Toast.LENGTH_SHORT).show();
                }
            });
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (slot > 0) slp.leftMargin = dp(6);
            dialRow.addView(bSlot, slp);
        }
        quickGrid.addView(dialRow);

        Button btnBlackFace = makeActionButton("Apply Clean Black Layout (Day • Date • Time • Battery)", 0xFF16A34A, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) {
                // Sync 12h/Imperial + query/configure custom RK background slot with pure black
                svc.enqueueWrite33F1(T60Protocol.buildUnitAndTimeFormat(
                        T60Store.get(this).isImperial(), T60Store.get(this).isTwelveHour()));
                svc.enqueueWrite33F1(T60Protocol.buildQueryCustomBgParams());
                svc.enqueueWrite33F1(T60Protocol.buildCustomBgConfig(1, 1, 0, 0, 0));
                Toast.makeText(this, "Sent Clean Black Watchface Config (320x380)!", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams bfLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bfLp.topMargin = dp(10);
        quickGrid.addView(btnBlackFace, bfLp);

        page.addView(wrapCard("Quick Watch Controls & Watchface", quickGrid));
    }

    private void buildAlertsTab(LinearLayout page) {
        T60Store store = T60Store.get(this);
        LinearLayout alertBox = new LinearLayout(this);
        alertBox.setOrientation(LinearLayout.VERTICAL);

        Button btnGrant = makeActionButton("1. Enable Android Notification Access for T60", COLOR_ACCENT, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Open Settings -> Notification Access -> Enable T60", Toast.LENGTH_LONG).show();
            }
        });
        alertBox.addView(btnGrant);

        alertBox.addView(makeSwitchRow("Forward Incoming Phone Calls", store.isForwardCalls(), store::setForwardCalls));
        alertBox.addView(makeSwitchRow("Forward SMS / Text Messages", store.isForwardSms(), store::setForwardSms));
        alertBox.addView(makeSwitchRow("Forward Other App Notifications", store.isForwardApps(), store::setForwardApps));

        page.addView(wrapCard("Wrist Notification Forwarding", alertBox));

        // Test Popup Sender Card
        LinearLayout testBox = new LinearLayout(this);
        testBox.setOrientation(LinearLayout.VERTICAL);
        EditText etTestMsg = new EditText(this);
        etTestMsg.setHint("Type a test message to pop up on your wrist...");
        etTestMsg.setText("Hello Paul! T60 App Connected.");
        etTestMsg.setTextColor(COLOR_TEXT);
        etTestMsg.setHintTextColor(COLOR_SUBTEXT);
        testBox.addView(etTestMsg);

        Button btnSendPopup = makeActionButton("Send Instant Popup to Watch Screen", 0xFF0284C7, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) {
                String msg = etTestMsg.getText().toString().trim();
                if (!msg.isEmpty()) {
                    svc.pushWristNotification("T60", msg, 1);
                    Toast.makeText(this, "Sent popup to watch!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spLp.topMargin = dp(8);
        testBox.addView(btnSendPopup, spLp);

        page.addView(wrapCard("Test Wrist Popup (0xAB / 0xC5)", testBox));

        // Optional Webhook Action Card
        LinearLayout hookBox = new LinearLayout(this);
        hookBox.setOrientation(LinearLayout.VERTICAL);
        TextView hookHelp = makeSubLabel(
                "When you tap 'Find Phone' on your wrist, your phone rings & vibrates. " +
                "Optionally paste a Tailscale / Home-Server Webhook URL below to also trigger an automation:");
        hookBox.addView(hookHelp);

        EditText etHook = new EditText(this);
        etHook.setHint("https://gitserver.tail97bf76.ts.net/...");
        etHook.setText(store.getWebhookUrl());
        etHook.setTextColor(COLOR_TEXT);
        etHook.setHintTextColor(COLOR_SUBTEXT);
        etHook.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        hookBox.addView(etHook);

        Button btnSaveHook = makeActionButton("Save Wrist-Trigger Webhook", 0xFF334155, v -> {
            store.setWebhookUrl(etHook.getText().toString());
            Toast.makeText(this, "Saved webhook URL!", Toast.LENGTH_SHORT).show();
        });
        hookBox.addView(btnSaveHook);

        page.addView(wrapCard("Wrist Button -> Custom Home Server Action", hookBox));
    }

    private void buildHealthTab(LinearLayout page) {
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);

        tvStepsVal = makeBigMetricLine(metrics, "Today's Steps");
        tvMilesVal = makeBigMetricLine(metrics, "Estimated Distance");
        tvSleepVal = makeBigMetricLine(metrics, "Last Night Sleep");
        tvHrVal    = makeBigMetricLine(metrics, "Heart Rate Sensor");

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnRefreshHealth = makeActionButton("Sync Steps & Sleep", COLOR_ACCENT, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) {
                svc.enqueueWrite33F1(T60Protocol.buildSyncSteps());
                svc.enqueueWrite33F1(T60Protocol.buildSyncSleep());
                Toast.makeText(this, "Requesting step & sleep history...", Toast.LENGTH_SHORT).show();
            }
        });

        T60Store store = T60Store.get(this);
        btnHrToggle = makeActionButton(
                store.isHrTesting() ? "Stop Live HR" : "Measure Live HR",
                0xFFE11D48,
                v -> {
                    T60BleService svc = T60BleService.getInstance();
                    if (svc != null) {
                        boolean next = !store.isHrTesting();
                        store.setHrTesting(next);
                        svc.enqueueWrite33F1(T60Protocol.buildHeartRateTest(next));
                        refreshUi();
                    }
                });

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = dp(6);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = dp(6);
        btnRow.addView(btnRefreshHealth, lp1);
        btnRow.addView(btnHrToggle, lp2);
        metrics.addView(btnRow);

        Button btnExport = makeActionButton("Export Health Data (JSON File)", 0xFF334155, v -> {
            try {
                File out = store.exportHealthJson();
                Toast.makeText(this, "Exported to: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Export error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        exLp.topMargin = dp(10);
        metrics.addView(btnExport, exLp);

        page.addView(wrapCard("Activity, Sleep & Heart Rate (100% Local)", metrics));
    }

    private void buildSettingsTab(LinearLayout page) {
        T60Store store = T60Store.get(this);
        LinearLayout prefsBox = new LinearLayout(this);
        prefsBox.setOrientation(LinearLayout.VERTICAL);

        prefsBox.addView(makeSwitchRow("12-Hour Time Format (AM / PM)", store.isTwelveHour(), checked -> {
            store.setTwelveHour(checked);
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) {
                svc.enqueueWrite33F1(T60Protocol.buildUnitAndTimeFormat(store.isImperial(), checked));
            }
        }));

        prefsBox.addView(makeSwitchRow("Imperial Units (Miles / °F / lbs)", store.isImperial(), checked -> {
            store.setImperial(checked);
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) {
                svc.enqueueWrite33F1(T60Protocol.buildUnitAndTimeFormat(checked, store.isTwelveHour()));
                svc.enqueueWrite33F1(T60Protocol.buildProfileAndTempUnit(8000, checked, store.isRaiseToWake()));
            }
        }));

        prefsBox.addView(makeSwitchRow("Raise Wrist to Wake Screen", store.isRaiseToWake(), checked -> {
            store.setRaiseToWake(checked);
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) {
                svc.enqueueWrite33F1(T60Protocol.buildRaiseToWake(checked));
                svc.enqueueWrite33F1(T60Protocol.buildProfileAndTempUnit(8000, store.isImperial(), checked));
            }
        }));

        // Classic BT 3.0 Wrist Speakerphone controls
        LinearLayout bt3Row = new LinearLayout(this);
        bt3Row.setOrientation(LinearLayout.HORIZONTAL);
        bt3Row.setPadding(0, dp(10), 0, 0);
        Button btnBt3On = makeActionButton("Wrist Speaker ON", 0xFF0284C7, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) svc.enqueueWrite34F1(T60Protocol.buildBt3AudioSwitch(true));
        });
        Button btnBt3Off = makeActionButton("Wrist Speaker OFF", 0xFF334155, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) svc.enqueueWrite34F1(T60Protocol.buildBt3AudioSwitch(false));
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b1.rightMargin = dp(6);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b2.leftMargin = dp(6);
        bt3Row.addView(btnBt3On, b1);
        bt3Row.addView(btnBt3Off, b2);
        prefsBox.addView(bt3Row);

        // Quick 7:00 AM Wrist Alarm button
        Button btnAlarm = makeActionButton("Set 7:00 AM Daily Wrist Alarm", 0xFF1E293B, v -> {
            T60BleService svc = T60BleService.getInstance();
            if (svc != null) {
                svc.enqueueWrite33F1(T60Protocol.buildAlarmCommand(1, true, 7, 0, 0x7F));
                Toast.makeText(this, "7:00 AM Wrist Alarm synced!", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams alLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alLp.topMargin = dp(10);
        prefsBox.addView(btnAlarm, alLp);

        page.addView(wrapCard("Watch Preferences & Audio (RTL8763EWE)", prefsBox));

        // Live BLE Packet Log Card
        tvPacketLog = new TextView(this);
        tvPacketLog.setTextColor(0xFF93C5FD);
        tvPacketLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvPacketLog.setTypeface(Typeface.MONOSPACE);
        page.addView(wrapCard("Live Bluetooth Telemetry Log", tvPacketLog));
    }

    @Override
    public void onStateChanged() {
        runOnUiThread(this::refreshUi);
    }

    private void refreshUi() {
        T60Store store = T60Store.get(this);
        String conn = store.getConnectionState();
        int bat = store.getBatteryPercent();
        String batStr = bat >= 0 ? (bat + "%") : "--%";

        if (tvConnBadge != null) {
            tvConnBadge.setText(conn + " • " + batStr);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dp(16));
            boolean active = conn.startsWith("Connected");
            badgeBg.setColor(active ? 0xFF052E16 : 0xFF1E293B);
            badgeBg.setStroke(dp(1), active ? COLOR_GREEN : COLOR_BORDER);
            tvConnBadge.setTextColor(active ? COLOR_GREEN : COLOR_SUBTEXT);
            tvConnBadge.setBackground(badgeBg);
        }

        if (tvBatteryBig != null) {
            tvBatteryBig.setText("Battery: " + batStr);
        }
        if (tvFwInfo != null) {
            tvFwInfo.setText("Board ID: " + store.getFirmwareVersion() + "\n"
                    + store.getScreenSpec() + "\n"
                    + "MAC: " + store.getWatchMac() + "\n"
                    + "Last Sync: " + store.getLastSyncTime());
        }
        if (tvWatchBattery != null) {
            tvWatchBattery.setText("BAT " + batStr);
        }
        if (tvWatchTime != null) {
            String fmt = store.isTwelveHour() ? "h:mm a" : "HH:mm";
            tvWatchTime.setText(new SimpleDateFormat(fmt, Locale.US).format(new Date()));
        }
        if (tvWatchDate != null) {
            String dayDate = new SimpleDateFormat("EEE • MMM d", Locale.US).format(new Date()).toUpperCase(Locale.US);
            tvWatchDate.setText(dayDate);
        }

        if (tvStepsVal != null) {
            int steps = store.getTodaySteps();
            tvStepsVal.setText(String.format(Locale.US, "%,d steps", steps));
            double miles = steps * 0.00045;
            if (tvMilesVal != null) {
                tvMilesVal.setText(String.format(Locale.US, "%.2f miles  (%.0f kcal)", miles, steps * 0.04));
            }
        }
        if (tvSleepVal != null) {
            int mins = store.getTodaySleepMinutes();
            tvSleepVal.setText((mins / 60) + "h " + (mins % 60) + "m");
        }
        if (tvHrVal != null) {
            int bpm = store.getLiveHeartRate();
            tvHrVal.setText((bpm > 0 ? bpm + " BPM" : "-- BPM")
                    + (store.isHrTesting() ? " (Measuring...)" : ""));
        }
        if (btnHrToggle != null) {
            btnHrToggle.setText(store.isHrTesting() ? "Stop Live HR" : "Measure Live HR");
        }
        if (tvPacketLog != null) {
            List<String> logs = store.getPacketLog();
            if (logs.isEmpty()) {
                tvPacketLog.setText("Waiting for BLE events...");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(logs.size(), 25); i++) {
                    sb.append(logs.get(i)).append('\n');
                }
                tvPacketLog.setText(sb.toString().trim());
            }
        }
    }

    private View wrapCard(String heading, View inner) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(bg);

        TextView tvHead = new TextView(this);
        tvHead.setText(heading);
        tvHead.setTextColor(COLOR_ACCENT);
        tvHead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvHead.setTypeface(Typeface.DEFAULT_BOLD);
        tvHead.setPadding(0, 0, 0, dp(10));
        card.addView(tvHead);
        card.addView(inner);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private Button makeActionButton(String label, int bgColor, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(COLOR_TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setOnClickListener(onClick);
        return b;
    }

    private interface BoolCallback {
        void onChanged(boolean checked);
    }

    private View makeSwitchRow(String label, boolean initial, BoolCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(COLOR_TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> cb.onChanged(isChecked));
        row.addView(sw);
        return row;
    }

    private TextView makeBigMetricLine(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(10));

        TextView tvL = new TextView(this);
        tvL.setText(label);
        tvL.setTextColor(COLOR_SUBTEXT);
        tvL.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(tvL, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvV = new TextView(this);
        tvV.setTextColor(COLOR_TEXT);
        tvV.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvV.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tvV);

        parent.addView(row);
        return tvV;
    }

    private TextView makeSubLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(COLOR_SUBTEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        return tv;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
