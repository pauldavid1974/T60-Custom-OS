package com.paul.t60;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The whole phone app: one activity, screens drawn in code to match design/*.dc.html.
 * Tabs: Today, Health, Alerts, Watch. Detail screens have a back arrow and no tab bar.
 */
public class MainActivity extends Activity implements T60Store.Listener {

    static final String[] TABS = {"Today", "Health", "Alerts", "Watch"};
    static final String[] TAB_ICONS = {"⌂", "∿", "🔔", "⌚"};

    Ui ui;
    T60Store store;
    T60Db db;
    private FrameLayout content;
    private LinearLayout nav;
    private final List<String> stack = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private ScrollView currentScroll;
    private boolean refreshPending;
    final Bundle args = new Bundle();

    private HealthScreens health;
    private AlertScreens alerts;
    private WatchScreens watch;

    /** Screens redrawn when watch data changes (no text fields on them). */
    private static final Set<String> LIVE = new HashSet<>(Arrays.asList("Today", "Health", "HeartRate", "Oxygen",
            "SleepNight", "Watch", "Connection", "Workouts", "LiveWorkout", "FindPhone", "Calls", "Weather", "Setup",
            "Alerts", "WatchLanguage"));

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.NAV);
        ui = new Ui(this);
        store = T60Store.get(this);
        db = T60Db.get(this);
        health = new HealthScreens(this);
        alerts = new AlertScreens(this);
        watch = new WatchScreens(this);
        LinearLayout root = ui.col();
        root.setBackgroundColor(Ui.BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        nav = ui.row();
        nav.setBackgroundColor(Ui.NAV);
        nav.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(10));
        root.addView(nav, Ui.fill());
        setContentView(root);
        requestPermissionsIfNeeded();
        startService(new Intent(this, T60BleService.class));
        stack.add(store.b("setup_done", false) ? "Today" : "Setup");
        handleIntent(getIntent());
        render();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        render();
    }

    private void handleIntent(Intent i) {
        String s = i != null ? i.getStringExtra("screen") : null;
        if (s == null) return;
        if ("FindPhone".equals(s)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        if (Arrays.asList(TABS).contains(s)) { stack.clear(); stack.add(s); }
        else if (!top().equals(s)) stack.add(s);
    }

    @Override protected void onResume() {
        super.onResume();
        store.addListener(this);
        render();
    }

    @Override protected void onPause() {
        store.removeListener(this);
        super.onPause();
    }

    @Override public void onStateChanged() {
        if (refreshPending) return;
        refreshPending = true;
        h.postDelayed(() -> {
            refreshPending = false;
            if (LIVE.contains(top())) render();
        }, 400);
    }

    @Override public void onBackPressed() {
        if (stack.size() > 1) back();
        else super.onBackPressed();
    }

    void requestPermissionsIfNeeded() {
        String[] want = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS};
        List<String> ask = new ArrayList<>();
        for (String p : want) if (!has(p)) ask.add(p);
        if (!ask.isEmpty()) requestPermissions(ask.toArray(new String[0]), 101);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        startService(new Intent(this, T60BleService.class));
        render();
    }

    /** Photo picked for the watch face: centre-crop to the watch's 320 x 380 screen and keep it. */
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != 42 || result != RESULT_OK || data == null || data.getData() == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeStream(in);
            float scale = Math.max(320f / src.getWidth(), 380f / src.getHeight());
            int w = Math.round(320 / scale), hh = Math.round(380 / scale);
            android.graphics.Bitmap crop = android.graphics.Bitmap.createBitmap(src, (src.getWidth() - w) / 2, (src.getHeight() - hh) / 2, w, hh);
            android.graphics.Bitmap out = android.graphics.Bitmap.createScaledBitmap(crop, 320, 380, true);
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(getFilesDir(), "face.png"))) {
                out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
            }
            if (top().equals("PhotoPicker")) stack.remove(stack.size() - 1);
            render();
        } catch (Exception e) {
            toast("Couldn't open that photo: " + e.getMessage());
        }
    }

    boolean has(String perm) {
        // These permissions only exist on newer Android; older phones grant the access at install.
        if ((perm.equals(Manifest.permission.BLUETOOTH_CONNECT) || perm.equals(Manifest.permission.BLUETOOTH_SCAN)) && android.os.Build.VERSION.SDK_INT < 31) return true;
        if (perm.equals(Manifest.permission.POST_NOTIFICATIONS) && android.os.Build.VERSION.SDK_INT < 33) return true;
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    boolean hasNotificationAccess() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    // ---------------------------------------------------------------- navigation

    String top() { return stack.get(stack.size() - 1); }

    void go(String screen) {
        stack.add(screen);
        render();
    }

    void back() {
        if (stack.size() > 1) stack.remove(stack.size() - 1);
        render();
    }

    void tab(String name) {
        stack.clear();
        stack.add(name);
        render();
    }

    void replace(String screen) {
        stack.set(stack.size() - 1, screen);
        render();
    }

    T60BleService svc() { return T60BleService.getInstance(); }

    /** Runs r with the service if the watch is connected, otherwise says so. */
    boolean withWatch(java.util.function.Consumer<T60BleService> r) {
        T60BleService s = svc();
        if (s == null || !s.isReady()) {
            toast("The watch isn't connected right now. It will get this when it reconnects.");
            return false;
        }
        r.accept(s);
        return true;
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    void render() {
        String name = top();
        int scrollY = currentScroll != null && name.equals(currentScroll.getTag()) ? currentScroll.getScrollY() : 0;
        content.removeAllViews();
        LinearLayout page = ui.col();
        boolean isTab = Arrays.asList(TABS).contains(name);
        View body;
        try {
            body = build(name, page);
        } catch (Exception e) {
            store.logEvent("Screen error " + name + ": " + e);
            body = ui.note("Something went wrong drawing this screen: " + e.getMessage());
        }
        if (body != null) page.addView(body, Ui.fill());
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setTag(name);
        sv.addView(page);
        content.addView(sv);
        currentScroll = sv;
        if (scrollY > 0) sv.post(() -> sv.scrollTo(0, scrollY));
        nav.setVisibility(isTab ? View.VISIBLE : View.GONE);
        drawNav(name);
    }

    private void drawNav(String active) {
        nav.removeAllViews();
        for (int k = 0; k < TABS.length; k++) {
            String t = TABS[k];
            LinearLayout b = ui.col();
            b.setGravity(Gravity.CENTER);
            b.setMinimumHeight(ui.dp(52));
            int color = t.equals(active) ? Ui.ACCENT : Ui.DIM;
            TextView icon = ui.text(TAB_ICONS[k], 18, color, false);
            icon.setGravity(Gravity.CENTER);
            TextView label = ui.text(t, 12, color, t.equals(active));
            label.setGravity(Gravity.CENTER);
            b.addView(icon);
            b.addView(label);
            b.setOnClickListener(v -> tab(t));
            nav.addView(b, Ui.weight(1));
        }
    }

    /** Builds a screen into page (header already handled per screen). Returns an extra view or null. */
    private View build(String name, LinearLayout page) {
        switch (name) {
            case "Today": return today(page);
            case "Connection": return connection(page);
            case "Setup": return setup(page);
            case "FindWatch": return findWatch(page);
            case "FindPhone": return findPhone(page);
            case "Export": return export(page);
            case "StepGoal": return stepGoal(page);
            case "TodayCards": return todayCards(page);
            case "Health": case "HeartRate": case "Oxygen": case "SleepNight": case "Workouts": case "LiveWorkout":
                return health.build(name, page);
            case "Alerts": case "MoveReminder": case "QuietHours": case "QuickReplies": case "EditReply":
                return alerts.build(name, page);
            default:
                return watch.build(name, page);
        }
    }

    // ---------------------------------------------------------------- shared pieces

    LinearLayout body(LinearLayout page) {
        LinearLayout b = ui.col();
        b.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(28));
        page.addView(b, Ui.fill());
        return b;
    }

    /** Tab screen header: small date line, big title, connection pill on the right. */
    LinearLayout tabHeader(LinearLayout page, String small, String title) {
        LinearLayout r = ui.row();
        r.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(12));
        LinearLayout t = ui.col();
        if (small != null) t.addView(ui.text(small, 13, Ui.SUB, true));
        t.addView(ui.text(title, 28, Ui.TEXT, true));
        r.addView(t, Ui.weight(1));
        r.addView(connectionPill());
        page.addView(r, Ui.fill());
        return body(page);
    }

    View connectionPill() {
        boolean on = store.isConnected();
        int bat = store.getBatteryPercent();
        LinearLayout p = ui.row();
        p.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        p.setMinimumHeight(ui.dp(40));
        p.setBackground(Ui.round(on ? 0xFF0F2A1A : 0xFF2A1E14, on ? 0xFF1F5A36 : 0xFF5A3A22, ui.dp(999), ui.dp(1)));
        TextView dot = ui.text("●", 10, on ? Ui.GREEN : Ui.HEART, false);
        p.addView(dot);
        String label = on ? "Connected" + (bat >= 0 ? " · " + bat + "%" : "") : store.isSyncing() ? "Syncing" : "Not connected";
        if (on && store.isSyncing()) label = "Syncing…";
        TextView t = ui.text("  " + label, 13, on ? 0xFF7BE8A8 : 0xFFFFB38A, true);
        p.addView(t);
        p.setOnClickListener(v -> go("Connection"));
        return p;
    }

    /** Detail screen header with back arrow; returns the padded body. */
    LinearLayout detail(LinearLayout page, String title) {
        page.addView(ui.detailHeader(title, this::back), Ui.fill());
        return body(page);
    }

    void pickTime(int minuteOfDay, Ui.OnInt done) {
        TimePickerDialog d = new TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog,
                (v, hh, mm) -> done.set(hh * 60 + mm), minuteOfDay / 60, minuteOfDay % 60, !store.isTwelveHour());
        d.show();
    }

    LinearLayout timeRow(String label, String key, int def) {
        LinearLayout r = ui.navRow(label, null, store.clock(store.i(key, def)), null);
        r.setOnClickListener(v -> pickTime(store.i(key, def), m -> { store.set(key, m); render(); }));
        return r;
    }

    void confirm(String title, String msg, String yes, Runnable r) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title).setMessage(msg)
                .setPositiveButton(yes, (d, w) -> r.run()).setNegativeButton("Cancel", null).show();
    }

    String fmt(int n) { return String.format(Locale.US, "%,d", n); }

    static String hm(int minutes) {
        return minutes >= 60 ? (minutes / 60) + "h " + String.format(Locale.US, "%02dm", minutes % 60) : minutes + "m";
    }

    String ago(long ms) {
        if (ms <= 0) return "never";
        long m = (System.currentTimeMillis() - ms) / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        if (m < 24 * 60) return (m / 60) + " h ago";
        return new SimpleDateFormat("MMM d", Locale.US).format(new Date(ms));
    }

    /** Time of the newest heart-rate reading: live one, else the newest stored sample. */
    long lastHrMs(int[] latest) {
        long live = store.hrTime();
        if (latest == null) return live;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -latest[0]);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.add(Calendar.MINUTE, latest[1]);
        return Math.max(live, c.getTimeInMillis());
    }

    /** Most recent night with data (last night if synced). */
    String lastNight() {
        List<String> n = db.nights();
        return n.isEmpty() ? null : n.get(0);
    }

    static int daysUntilNextPeriod(T60Store s) {
        String start = s.s("cycle_start", "");
        if (start.isEmpty()) return -1;
        long dayMs = 86400000L;
        long since = (T60Db.parse(T60Db.today()).getTimeInMillis() - T60Db.parse(start).getTimeInMillis()) / dayMs;
        int len = s.i("cycle_len", 28);
        return (int) (len - (since % len));
    }

    // ---------------------------------------------------------------- Today

    private View today(LinearLayout page) {
        String date = new SimpleDateFormat("EEEE, MMM d", Locale.US).format(new Date());
        LinearLayout b = tabHeader(page, date, "Today");
        Set<String> hidden = store.hiddenCards();
        boolean first = true;
        for (String card : store.todayCards()) {
            if (hidden.contains(card)) continue;
            View v = todayCard(card);
            if (v == null) continue;
            ui.add(b, v, first ? 0 : 12);
            first = false;
        }
        LinearLayout foot = ui.row();
        foot.addView(ui.text((store.lastSync() > 0 ? "Last synced " + store.time(store.lastSync()) : "Not synced yet")
                + " · nothing leaves this phone", 12, Ui.DIM, false), Ui.weight(1));
        TextView edit = ui.text("Edit cards", 13, Ui.ACCENT, true);
        edit.setPadding(ui.dp(8), ui.dp(12), 0, ui.dp(12));
        edit.setOnClickListener(v -> go("TodayCards"));
        foot.addView(edit);
        ui.add(b, foot, 8);
        return null;
    }

    private View todayCard(String card) {
        switch (card) {
            case "Steps": {
                int steps = db.stepsOn(T60Db.today()), goal = store.stepGoal();
                LinearLayout c = ui.card();
                c.addView(ui.cardHead("Steps", "Goal " + fmt(goal) + " ›", v -> go("StepGoal")));
                LinearLayout r = ui.row();
                r.setGravity(Gravity.BOTTOM);
                r.addView(ui.mono(fmt(steps), 48));
                r.addView(ui.text("  " + Math.round(steps * 100f / Math.max(1, goal)) + "%", 15, Ui.SUB, false));
                ui.add(c, r, 8);
                View bar = ui.bar(steps / (float) Math.max(1, goal), Ui.ACCENT);
                bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(10)));
                ui.add(c, bar, 12);
                ui.add(c, ui.text(store.distance(store.metersForSteps(steps)) + "     " + store.kcalForSteps(steps) + " kcal     "
                        + db.activeMinutes(T60Db.today()) + " active min", 14, Ui.BODY, false), 14);
                c.setOnClickListener(v -> tab("Health"));
                return c;
            }
            case "Heart rate": case "Sleep": {
                if (card.equals("Sleep")) return null; // drawn together with heart rate
                LinearLayout grid = ui.row();
                grid.setGravity(Gravity.TOP);
                LinearLayout hr = ui.card(), sl = ui.card();
                hr.addView(ui.text("♥  Heart rate", 14, Ui.HEART, true));
                int bpm = store.getLiveHeartRate();
                int[] last = db.latest("hr", "bpm");
                if (bpm <= 0 && last != null) bpm = last[2];
                LinearLayout hv = ui.row();
                hv.setGravity(Gravity.BOTTOM);
                hv.addView(ui.mono(bpm > 0 ? String.valueOf(bpm) : "--", 34));
                hv.addView(ui.text(" bpm", 13, Ui.SUB, false));
                ui.add(hr, hv, 6);
                int rest = db.restingHr(T60Db.today());
                ui.add(hr, ui.text((rest > 0 ? "Resting " + rest + " · " : "") + ago(lastHrMs(last)), 12, Ui.SUB, false), 4);
                hr.setOnClickListener(v -> go("HeartRate"));
                sl.addView(ui.text("☾  Sleep", 14, Ui.PURPLE, true));
                String night = lastNight();
                List<int[]> segs = night == null ? new ArrayList<>() : db.night(night);
                int asleep = T60Db.asleep(T60Db.stageTotals(segs));
                ui.add(sl, ui.mono(segs.isEmpty() ? "--" : (asleep / 60) + ":" + String.format(Locale.US, "%02d", asleep % 60), 34), 6);
                ui.add(sl, ui.text(segs.isEmpty() ? "No sleep synced yet" : store.clock(segs.get(0)[0]) + " – "
                        + store.clock(segs.get(segs.size() - 1)[0] + segs.get(segs.size() - 1)[1]), 12, Ui.SUB, false), 4);
                sl.setOnClickListener(v -> { args.putString("night", night); go("SleepNight"); });
                LinearLayout.LayoutParams l1 = Ui.weight(1);
                l1.rightMargin = ui.dp(6);
                LinearLayout.LayoutParams l2 = Ui.weight(1);
                l2.leftMargin = ui.dp(6);
                grid.addView(hr, l1);
                grid.addView(sl, l2);
                return grid;
            }
            case "Blood oxygen": {
                LinearLayout c = ui.card();
                c.addView(ui.cardHead("Blood oxygen", "›", v -> go("Oxygen")));
                int[] last = db.latest("spo2", "pct");
                ui.add(c, ui.mono(last == null ? "--" : last[2] + "%", 34), 6);
                c.setOnClickListener(v -> go("Oxygen"));
                return c;
            }
            case "Quick actions": {
                LinearLayout c = ui.card();
                c.addView(ui.text("Quick actions", 14, Ui.SUB, true));
                LinearLayout r = ui.row();
                r.addView(action("⌚", "Find watch", v -> { if (withWatch(s -> s.findWatch(true))) go("FindWatch"); }), tileLp(0));
                r.addView(action("⟳", store.isSyncing() ? "Syncing…" : "Sync now", v -> withWatch(T60BleService::syncHistory)), tileLp(10));
                r.addView(action("⇩", "Export", v -> go("Export")), tileLp(10));
                ui.add(c, r, 12);
                return c;
            }
        }
        return null;
    }

    private LinearLayout.LayoutParams tileLp(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(76), 1);
        lp.leftMargin = ui.dp(left);
        return lp;
    }

    View action(String icon, String label, View.OnClickListener l) {
        LinearLayout t = ui.col();
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.round(Ui.TILE, Ui.TILE_BORDER, ui.dp(16), ui.dp(1)));
        TextView i = ui.text(icon, 20, Ui.ACCENT, true);
        i.setGravity(Gravity.CENTER);
        TextView s = ui.text(label, 13, Ui.TEXT, true);
        s.setGravity(Gravity.CENTER);
        t.addView(i);
        t.addView(s);
        t.setOnClickListener(l);
        return t;
    }

    // ---------------------------------------------------------------- Connection, Setup

    private View connection(LinearLayout page) {
        LinearLayout b = detail(page, "Connection");
        boolean on = store.isConnected();
        LinearLayout c = ui.card();
        c.addView(ui.text(on ? "Watch connected" : "Watch not connected", 22, on ? Ui.GREEN : Ui.TEXT, true));
        int bat = store.getBatteryPercent();
        ui.add(c, ui.text(on ? store.getWatchName() + (bat >= 0 ? " · battery " + bat + "%" : "")
                : "Last seen " + (store.lastSeen() > 0 ? store.time(store.lastSeen()) : "never") + (bat >= 0 ? " · battery was " + bat + "%" : ""), 14, Ui.SUB, false), 6);
        b.addView(c);
        if (!on) {
            LinearLayout tips = ui.card();
            tips.addView(ui.text("Things to try", 14, Ui.SUB, true));
            for (String t : new String[]{"Bring the watch near the phone", "Make sure Bluetooth is on", "Check the watch has battery left"}) {
                ui.add(tips, ui.text("•  " + t, 15, Ui.TEXT, false), 10);
            }
            ui.add(b, tips, 12);
            ui.add(b, ui.note("The app keeps trying on its own. Your data is safe on the watch until it reconnects."), 12);
        }
        ui.add(b, ui.button("Try again now", true, v -> {
            T60BleService s = svc();
            if (s != null) s.connectWatch(); else startService(new Intent(this, T60BleService.class));
            toast("Connecting…");
        }), 16);
        ui.add(b, ui.button("Set up the watch again", false, v -> go("Setup")), 10);
        ui.add(b, ui.sectionLabel("Bluetooth log"), 20);
        StringBuilder sb = new StringBuilder();
        List<String> log = store.getPacketLog();
        for (int k = 0; k < Math.min(40, log.size()); k++) sb.append(log.get(k)).append('\n');
        TextView lv = ui.text(sb.length() == 0 ? "Nothing yet" : sb.toString(), 11, Ui.DIM, false);
        lv.setTypeface(android.graphics.Typeface.MONOSPACE);
        lv.setTextIsSelectable(true);
        ui.add(b, lv, 6);
        return null;
    }

    private View setup(LinearLayout page) {
        LinearLayout b = body(page);
        b.setPadding(ui.dp(20), ui.dp(36), ui.dp(20), ui.dp(28));
        b.addView(ui.text("T60", 15, Ui.ACCENT, true));
        ui.add(b, ui.text("Let's connect your watch", 28, Ui.TEXT, true), 6);
        ui.add(b, ui.text("No account. Your data stays on this phone.", 15, Ui.SUB, false), 6);
        boolean bt = has(Manifest.permission.BLUETOOTH_CONNECT);
        boolean notif = hasNotificationAccess();
        boolean loc = has(Manifest.permission.ACCESS_FINE_LOCATION);
        ui.add(b, setupStep(1, "Allow Bluetooth", null, bt, v -> requestPermissionsIfNeeded()), 24);
        LinearLayout pick = setupStep(2, "Pick your watch", "Keep it close to the phone", store.isConnected(), null);
        ui.add(b, pick, 10);
        LinearLayout found = ui.group(ui.navRow(store.getWatchName(), store.isConnected() ? "Connected" : store.getWatchMac(),
                store.isConnected() ? "✓" : null, null));
        ui.add(b, found, 8);
        ui.add(b, setupStep(3, "Allow notification access", "So alerts reach your wrist", notif,
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))), 10);
        ui.add(b, setupStep(4, "Allow location", "For weather and workout maps", loc, v -> requestPermissionsIfNeeded()), 10);
        ui.add(b, ui.button("Connect", true, v -> {
            T60BleService s = svc();
            if (s != null && !s.isReady()) s.connectWatch();
            store.set("setup_done", true);
            tab("Today");
        }), 24);
        return null;
    }

    private LinearLayout setupStep(int n, String title, String sub, boolean done, View.OnClickListener l) {
        LinearLayout r = ui.row();
        r.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        r.setBackground(Ui.round(Ui.CARD, Ui.BORDER, ui.dp(16), ui.dp(1)));
        TextView badge = ui.text(done ? "✓" : String.valueOf(n), 14, done ? 0xFF06121F : Ui.TEXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.round(done ? Ui.GREEN : Ui.TILE, Ui.TILE_BORDER, ui.dp(16), done ? 0 : ui.dp(1)));
        r.addView(badge, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        LinearLayout t = ui.col();
        t.setPadding(ui.dp(12), 0, 0, 0);
        t.addView(ui.text(title, 15, Ui.TEXT, true));
        if (sub != null) t.addView(ui.text(sub, 13, Ui.SUB, false));
        r.addView(t, Ui.weight(1));
        if (done) r.addView(ui.text("Done", 13, Ui.GREEN, true));
        else if (l != null) r.addView(ui.text("Allow ›", 13, Ui.ACCENT, true));
        if (l != null && !done) r.setOnClickListener(l);
        return r;
    }

    // ---------------------------------------------------------------- Find watch / phone

    private View findWatch(LinearLayout page) {
        LinearLayout b = detail(page, "Finding your watch");
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView big = ui.text("⌚", 72, Ui.ACCENT, false);
        big.setGravity(Gravity.CENTER);
        ui.add(b, big, 40);
        TextView t = ui.text("Your watch is buzzing", 24, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        ui.add(b, t, 20);
        TextView s = ui.text("Listen and feel for it nearby. It stops on its own after 30 seconds.", 15, Ui.SUB, false);
        s.setGravity(Gravity.CENTER);
        ui.add(b, s, 8);
        ui.add(b, ui.button("Found it, stop buzzing", true, v -> {
            T60BleService sv = svc();
            if (sv != null) sv.findWatch(false);
            back();
        }), 40);
        return null;
    }

    private View findPhone(LinearLayout page) {
        page.setBackgroundColor(0xFF1A0F0A);
        LinearLayout b = body(page);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(ui.dp(24), ui.dp(80), ui.dp(24), ui.dp(40));
        TextView big = ui.text("📱", 72, Ui.HEART, false);
        big.setGravity(Gravity.CENTER);
        b.addView(big);
        TextView t = ui.text("Here I am!", 34, 0xFFFFD9C7, true);
        t.setGravity(Gravity.CENTER);
        ui.add(b, t, 24);
        TextView s = ui.text(store.isFindPhoneRinging() ? "Your watch is looking for this phone." : "This is what you'll see when your watch looks for this phone.", 16, 0xFFFFB38A, false);
        s.setGravity(Gravity.CENTER);
        ui.add(b, s, 8);
        TextView done = ui.button("I found it", true, v -> {
            T60BleService sv = svc();
            if (sv != null) sv.stopFindPhone(true);
            if (stack.size() > 1) back(); else tab("Today");
        });
        done.setBackground(Ui.round(Ui.HEART, 0, ui.dp(14), 0));
        ui.add(b, done, 60);
        return null;
    }

    // ---------------------------------------------------------------- Export

    private View export(LinearLayout page) {
        LinearLayout b = detail(page, "Export your data");
        int[] range = {store.i("export_range", 0)}, fmt = {store.i("export_fmt", 0)};
        b.addView(ui.sectionLabel("Save a file"));
        ui.add(b, ui.chips(new String[]{"Last week", "Last month", "Everything"}, range[0], k -> { store.set("export_range", k); render(); }), 8);
        ui.add(b, ui.chips(new String[]{"Spreadsheet (CSV)", "Full detail (JSON)"}, fmt[0], k -> { store.set("export_fmt", k); render(); }), 10);
        ui.add(b, ui.button("Save to Downloads", true, v -> saveExport(range[0], fmt[0])), 14);
        LinearLayout hc = ui.card();
        hc.addView(ui.text("Share with Health Connect", 16, Ui.TEXT, true));
        ui.add(hc, ui.text("Android's health app, on this phone", 13, Ui.SUB, false), 2);
        ui.add(hc, ui.note("Shares steps, heart rate, sleep, blood oxygen and workouts. Other apps can read them only if you allow it in Health Connect."), 8);
        boolean hcInstalled = isInstalled("com.google.android.apps.healthdata");
        ui.add(hc, ui.note(hcInstalled
                ? "Health Connect is installed, but this app can only write to it on Android 14 or newer. This phone runs Android " + android.os.Build.VERSION.RELEASE + ", so use the file above for now."
                : "Health Connect isn't on this phone (Android " + android.os.Build.VERSION.RELEASE + "). Use the file above for now."), 8);
        ui.add(b, hc, 16);
        ui.add(b, ui.note("Everything stays on this phone unless you move the file yourself."), 14);
        return null;
    }

    private boolean isInstalled(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    private void saveExport(int range, int format) {
        String from = range == 0 ? T60Db.dayOffset(-7) : range == 1 ? T60Db.dayOffset(-31) : "";
        String csv = db.csv(from);
        String data;
        String ext = format == 0 ? "csv" : "json";
        if (format == 0) data = csv;
        else {
            org.json.JSONArray rows = new org.json.JSONArray();
            String[] lines = csv.split("\n");
            for (int k = 1; k < lines.length; k++) {
                String[] f = lines[k].split(",", -1);
                try {
                    rows.put(new org.json.JSONObject().put("type", f[0]).put("date", f[1]).put("time", f[2]).put("value", f[3]).put("extra", f.length > 4 ? f[4] : ""));
                } catch (Exception ignored) {}
            }
            data = rows.toString();
        }
        String name = "t60-health-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date()) + "." + ext;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, format == 0 ? "text/csv" : "application/json");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            try (OutputStream o = getContentResolver().openOutputStream(uri)) {
                o.write(data.getBytes(StandardCharsets.UTF_8));
            }
            toast("Saved " + name + " to Downloads");
        } catch (Exception e) {
            toast("Couldn't save: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- Step goal, Today cards

    private View stepGoal(LinearLayout page) {
        LinearLayout b = detail(page, "Step goal");
        int[] goal = {store.stepGoal()};
        LinearLayout c = ui.card();
        LinearLayout st = ui.stepper(goal, 500, 1000, 50000, "", null);
        c.addView(st);
        TextView sub = ui.text("steps a day", 14, Ui.SUB, false);
        sub.setGravity(Gravity.CENTER);
        ui.add(c, sub, 4);
        LinearLayout presets = ui.row();
        for (int p : new int[]{6000, 8000, 10000, 12000}) {
            TextView t = ui.text(fmt(p), 14, Ui.BODY, true);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(ui.dp(44));
            t.setBackground(Ui.round(Ui.TILE, Ui.TILE_BORDER, ui.dp(12), ui.dp(1)));
            t.setOnClickListener(v -> { goal[0] = p; ((TextView) ((LinearLayout) st).getChildAt(1)).setText(fmt(p)); });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.leftMargin = ui.dp(4);
            lp.rightMargin = ui.dp(4);
            presets.addView(t, lp);
        }
        ui.add(c, presets, 16);
        b.addView(c);
        int sum = 0;
        for (int k = 1; k <= 7; k++) sum += db.stepsOn(T60Db.dayOffset(-k));
        ui.add(b, ui.note("The watch celebrates when you reach it. Your last 7 days averaged " + fmt(sum / 7) + "."), 12);
        ui.add(b, ui.button("Save to watch", true, v -> {
            store.set("step_goal", goal[0]);
            withWatch(s -> { s.sendStepGoal(); s.sendProfile(); });
            back();
        }), 16);
        return null;
    }

    private View todayCards(LinearLayout page) {
        LinearLayout b = detail(page, "Today cards");
        b.addView(ui.note("Choose what shows on Today. Tap the arrow to move a card up."));
        List<String> order = store.todayCards();
        Set<String> hidden = store.hiddenCards();
        List<View> rows = new ArrayList<>();
        for (int k = 0; k < order.size(); k++) {
            String name = order.get(k);
            int idx = k;
            LinearLayout r = ui.navRow(name, null, null, null);
            TextView up = ui.text(k > 0 ? "↑" : " ", 20, Ui.ACCENT, true);
            up.setPadding(ui.dp(12), 0, ui.dp(12), 0);
            up.setOnClickListener(v -> {
                if (idx == 0) return;
                order.add(idx - 1, order.remove(idx));
                store.setTodayCards(order, hidden);
                render();
            });
            r.addView(up);
            android.widget.Switch sw = new android.widget.Switch(this);
            sw.setChecked(!hidden.contains(name));
            sw.setOnCheckedChangeListener((x, on) -> {
                if (on) hidden.remove(name); else hidden.add(name);
                store.setTodayCards(order, hidden);
            });
            r.addView(sw);
            rows.add(r);
        }
        ui.add(b, ui.group(rows.toArray(new View[0])), 12);
        ui.add(b, ui.button("Done", true, v -> back()), 16);
        return null;
    }
}
