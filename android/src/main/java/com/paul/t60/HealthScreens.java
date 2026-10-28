package com.paul.t60;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Health tab and its detail screens: heart rate, blood oxygen, one night's sleep, workouts. */
final class HealthScreens {

    static final int DEEP = 0xFF3B4BB8, LIGHT = 0xFF7C8CF5, AWAKE = 0xFFFFB38A, REM = 0xFFC9D0FF;
    static final int[] STAGE_COLORS = {DEEP, LIGHT, AWAKE, REM};
    static final String[] STAGE_NAMES = {"Deep", "Light", "Awake", "REM"};

    private final MainActivity a;
    private final Ui ui;
    private final T60Store store;
    private final T60Db db;

    HealthScreens(MainActivity a) { this.a = a; ui = a.ui; store = a.store; db = a.db; }

    View build(String name, LinearLayout page) {
        switch (name) {
            case "Health": return health(page);
            case "HeartRate": return heartRate(page);
            case "Oxygen": return oxygen(page);
            case "SleepNight": return sleepNight(page);
            case "Workouts": return workouts(page);
            case "LiveWorkout": return liveWorkout(page);
            case "Route": return route(page);
        }
        return null;
    }

    /** A saved phone workout's route on the map. */
    private View route(LinearLayout page) {
        LinearLayout b = a.detail(page, "Route");
        b.addView(ui.text(a.args.getString("routeTitle", ""), 14, Ui.SUB, false));
        List<double[]> pts = new ArrayList<>();
        try {
            org.json.JSONArray t = new org.json.JSONArray(a.args.getString("route", "[]"));
            for (int k = 0; k < t.length(); k++) pts.add(new double[]{t.getJSONArray(k).getDouble(0), t.getJSONArray(k).getDouble(1)});
        } catch (Exception ignored) {}
        ui.add(b, sized(trackView(pts), 420), 12);
        return null;
    }

    private static String dayLabel(String day, String pattern) {
        return new SimpleDateFormat(pattern, Locale.US).format(T60Db.parse(day).getTime());
    }

    private View sized(View v, int heightDp) {
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(heightDp)));
        return v;
    }

    // ---------------------------------------------------------------- Health tab

    private View health(LinearLayout page) {
        LinearLayout r = ui.row();
        r.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(12));
        r.addView(ui.text("Health", 28, Ui.TEXT, true), Ui.weight(1));
        TextView w = ui.text("Workouts ›", 14, Ui.ACCENT, true);
        w.setPadding(ui.dp(8), ui.dp(10), 0, ui.dp(10));
        w.setOnClickListener(v -> a.go("Workouts"));
        r.addView(w);
        page.addView(r, Ui.fill());
        LinearLayout b = a.body(page);

        int mode = store.i("health_range", 1);
        b.addView(ui.chips(new String[]{"Day", "Week", "Month"}, mode, k -> { store.set("health_range", k); a.render(); }));

        // Steps chart
        LinearLayout steps = ui.card();
        float[] vals;
        String[] labels;
        int total = 0, days;
        if (mode == 0) {
            int[] hh = db.hourlySteps(T60Db.today());
            vals = new float[24];
            labels = new String[24];
            for (int k = 0; k < 24; k++) { vals[k] = hh[k]; total += hh[k]; labels[k] = k % 6 == 0 ? (k == 0 ? "12a" : k == 12 ? "12p" : (k % 12) + (k < 12 ? "a" : "p")) : null; }
            days = 1;
        } else {
            days = mode == 1 ? 7 : 30;
            vals = new float[days];
            labels = new String[days];
            for (int k = 0; k < days; k++) {
                String d = T60Db.dayOffset(k - days + 1);
                vals[k] = db.stepsOn(d);
                total += vals[k];
                labels[k] = mode == 1 ? dayLabel(d, "EE").substring(0, 2) : (k % 5 == 4 ? dayLabel(d, "d") : null);
            }
        }
        steps.addView(ui.cardHead("Steps", null, null));
        ((LinearLayout) steps.getChildAt(0)).addView(ui.text(mode == 0 ? a.fmt(total) + " today" : "Avg " + a.fmt(total / days) + " / day", 13, Ui.SUB, false));
        ui.add(steps, sized(ui.barChart(vals, labels, Ui.ACCENT, mode == 0 ? -1 : vals.length - 1), 140), 12);
        ui.add(b, steps, 12);

        // Heart rate: daily low-high ranges for the last 7 days
        LinearLayout hr = ui.card();
        hr.addView(ui.cardHead("Heart rate ›", null, null));
        int[] wk = db.range("hr", "bpm", T60Db.dayOffset(-6), T60Db.today());
        int rest = db.restingHr(T60Db.today());
        ((LinearLayout) hr.getChildAt(0)).addView(ui.text(wk == null ? "No readings yet" : "Resting " + (rest > 0 ? rest : wk[0]) + " · High " + wk[2], 13, Ui.SUB, false));
        float[][] ranges = new float[7][];
        String[] dl = new String[7];
        for (int k = 0; k < 7; k++) {
            String d = T60Db.dayOffset(k - 6);
            int[] rr = db.range("hr", "bpm", d, d);
            ranges[k] = rr == null ? null : new float[]{rr[0], rr[2]};
            dl[k] = dayLabel(d, "EE").substring(0, 2);
        }
        ui.add(hr, sized(rangeChart(ranges, dl, Ui.HEART), 110), 12);
        hr.setOnClickListener(v -> a.go("HeartRate"));
        ui.add(b, hr, 12);

        // Blood oxygen
        LinearLayout ox = ui.card();
        ox.addView(ui.cardHead("Blood oxygen ›", null, null));
        int[] or = db.range("spo2", "pct", T60Db.dayOffset(-6), T60Db.today());
        ((LinearLayout) ox.getChildAt(0)).addView(ui.text(or == null ? "No readings yet" : "Range this week " + or[0] + "–" + or[2] + "%", 13, Ui.SUB, false));
        int[] last = db.latest("spo2", "pct");
        ui.add(ox, ui.mono(last == null ? "--" : last[2] + "%", 34), 6);
        ox.setOnClickListener(v -> a.go("Oxygen"));
        ui.add(b, ox, 12);

        // Last night
        String night = a.lastNight();
        List<int[]> segs = night == null ? new ArrayList<>() : db.night(night);
        int[] t = T60Db.stageTotals(segs);
        LinearLayout sl = ui.card();
        sl.addView(ui.cardHead("Last night ›", null, null));
        ((LinearLayout) sl.getChildAt(0)).addView(ui.text(segs.isEmpty() ? "Not synced yet" : MainActivity.hm(T60Db.asleep(t)), 13, Ui.SUB, false));
        if (!segs.isEmpty()) {
            ui.add(sl, sized(stageBar(t), 14), 12);
            LinearLayout leg = ui.row();
            int[] order = {0, 1, 3, 2};
            for (int k : order) leg.addView(ui.text("■ " + STAGE_NAMES[k] + " " + MainActivity.hm(t[k]) + "   ", 12, STAGE_COLORS[k], false));
            ui.add(sl, leg, 8);
        }
        sl.setOnClickListener(v -> { a.args.putString("night", night); a.go("SleepNight"); });
        ui.add(b, sl, 12);
        return null;
    }

    /** Vertical low-high bars (null = no data). */
    View rangeChart(float[][] r, String[] labels, int color) {
        return new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                float lo = 300, hi = 0;
                for (float[] x : r) if (x != null) { lo = Math.min(lo, x[0]); hi = Math.max(hi, x[1]); }
                if (hi <= 0) { lo = 50; hi = 120; }
                lo -= 5; hi += 5;
                float w = getWidth(), labelH = ui.dp(18), h = getHeight() - labelH, slot = w / r.length, bw = ui.dp(10);
                p.setTextSize(ui.dp(11));
                p.setTextAlign(Paint.Align.CENTER);
                for (int k = 0; k < r.length; k++) {
                    float cx = slot * k + slot / 2;
                    p.setColor(Ui.BORDER);
                    c.drawRoundRect(new RectF(cx - bw / 2, 0, cx + bw / 2, h), bw / 2, bw / 2, p);
                    if (r[k] != null) {
                        float y1 = h - (r[k][1] - lo) / (hi - lo) * h, y2 = h - (r[k][0] - lo) / (hi - lo) * h;
                        p.setColor(color);
                        c.drawRoundRect(new RectF(cx - bw / 2, y1, cx + bw / 2, Math.max(y2, y1 + bw)), bw / 2, bw / 2, p);
                    }
                    p.setColor(Ui.DIM);
                    float half = p.measureText(labels[k]) / 2;
                    c.drawText(labels[k], Math.max(half, Math.min(w - half, cx)), getHeight() - ui.dp(3), p);
                }
            }
        };
    }

    /** One horizontal bar split into deep / light / REM / awake. */
    View stageBar(int[] t) {
        return new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                int sum = t[0] + t[1] + t[2] + t[3];
                if (sum == 0) return;
                float x = 0, w = getWidth(), h = getHeight();
                int[] order = {0, 1, 3, 2};
                for (int k : order) {
                    float seg = w * t[k] / sum;
                    p.setColor(STAGE_COLORS[k]);
                    c.drawRect(x, 0, x + seg, h, p);
                    x += seg;
                }
            }
        };
    }

    // ---------------------------------------------------------------- Heart rate

    private View heartRate(LinearLayout page) {
        LinearLayout b = a.detail(page, "Heart rate");
        LinearLayout c = ui.card();
        int live = store.getLiveHeartRate();
        int[] last = db.latest("hr", "bpm");
        int bpm = live > 0 ? live : last != null ? last[2] : 0;
        LinearLayout big = ui.row();
        big.setGravity(Gravity.BOTTOM);
        TextView n = ui.mono(bpm > 0 ? String.valueOf(bpm) : "--", 56);
        n.setTextColor(Ui.HEART);
        big.addView(n);
        big.addView(ui.text("  bpm · " + (store.isHrTesting() ? "measuring…" : a.ago(a.lastHrMs(last))), 14, Ui.SUB, false));
        c.addView(big);
        List<int[]> today = db.hrOn(T60Db.today());
        float[][] hours = new float[24][];
        int sum = 0, max = 0;
        for (int[] p : today) {
            int hh = Math.min(23, p[0] / 60);
            if (hours[hh] == null) hours[hh] = new float[]{p[1], p[1]};
            hours[hh][0] = Math.min(hours[hh][0], p[1]);
            hours[hh][1] = Math.max(hours[hh][1], p[1]);
            sum += p[1];
            max = Math.max(max, p[1]);
        }
        String[] lab = new String[24];
        lab[0] = "12 AM"; lab[6] = "6 AM"; lab[12] = "12 PM"; lab[18] = "6 PM";
        for (int k = 0; k < 24; k++) if (lab[k] == null) lab[k] = "";
        ui.add(c, sized(rangeChart(hours, lab, Ui.HEART), 130), 12);
        LinearLayout stats = ui.row();
        int rest = db.restingHr(T60Db.today());
        String[][] s = {{"Resting", rest > 0 ? "" + rest : "--"}, {"Average", today.isEmpty() ? "--" : "" + sum / today.size()}, {"Highest", max > 0 ? "" + max : "--"}};
        for (String[] x : s) {
            LinearLayout col = ui.col();
            col.addView(ui.text(x[0], 12, Ui.SUB, false));
            col.addView(ui.mono(x[1], 22));
            stats.addView(col, Ui.weight(1));
        }
        ui.add(c, stats, 14);
        b.addView(c);
        ui.add(b, ui.button(store.isHrTesting() ? "Stop measuring" : "Measure now", true,
                v -> a.withWatch(sv -> sv.measureHeartRate(!store.isHrTesting()))), 12);
        int[] lim = {store.i("hr_limit", 120)};
        LinearLayout limRow = ui.row();
        limRow.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));
        limRow.addView(ui.text("Alert limit", 15, Ui.TEXT, true), Ui.weight(1));
        LinearLayout st = ui.stepper(lim, 5, 90, 200, " bpm", v -> { store.set("hr_limit", v); a.withWatch(T60BleService::sendProfile); }, 20);
        limRow.addView(st, new LinearLayout.LayoutParams(ui.dp(220), ViewGroup.LayoutParams.WRAP_CONTENT));
        ui.add(b, ui.group(
                ui.switchRow("Measure all day", "The watch checks on its own", store.b("hr_all_day", true),
                        on -> { store.set("hr_all_day", on); a.withWatch(T60BleService::sendHeartRateSettings); }),
                ui.switchRow("High heart-rate alert", "Buzz if it stays above the limit", store.b("hr_alert", false),
                        on -> { store.set("hr_alert", on); a.withWatch(T60BleService::sendProfile); }),
                limRow), 12);
        return null;
    }

    // ---------------------------------------------------------------- Blood oxygen

    private View oxygen(LinearLayout page) {
        LinearLayout b = a.detail(page, "Blood oxygen");
        LinearLayout c = ui.card();
        int live = store.getLiveSpo2();
        int[] last = db.latest("spo2", "pct");
        TextView v = ui.mono(store.isSpo2Testing() ? (live > 0 ? live + "%" : "…") : last != null ? last[2] + "%" : "--", 56);
        v.setTextColor(Ui.ACCENT);
        c.addView(v);
        String when = store.isSpo2Testing() ? "Measuring — keep your arm still" : last == null ? "No reading yet"
                : (last[0] == 0 ? "Today " : last[0] == 1 ? "Yesterday " : last[0] + " days ago ") + store.clock(last[1]);
        c.addView(ui.text(when, 14, Ui.SUB, false));
        ui.add(c, ui.note("95% and above is the normal range"), 10);
        b.addView(c);
        ui.add(b, ui.button(store.isSpo2Testing() ? "Stop measuring" : "Measure now", true,
                x -> a.withWatch(sv -> sv.measureOxygen(!store.isSpo2Testing()))), 12);
        ui.add(b, ui.sectionLabel("This week"), 16);
        List<View> rows = new ArrayList<>();
        for (int k = 0; k < 7; k++) {
            String d = T60Db.dayOffset(-k);
            int[] r = db.range("spo2", "pct", d, d);
            LinearLayout row = ui.navRow(dayLabel(d, "EEE"), null, r == null ? "—" : r[0] + "–" + r[2] + "%", null);
            rows.add(row);
        }
        ui.add(b, ui.group(rows.toArray(new View[0])), 6);
        return null;
    }

    // ---------------------------------------------------------------- One night's sleep

    private View sleepNight(LinearLayout page) {
        LinearLayout b = a.detail(page, "Sleep");
        List<String> nights = db.nights();
        String night = a.args.getString("night");
        if (night == null || !nights.contains(night)) night = nights.isEmpty() ? null : nights.get(0);
        if (night == null) {
            b.addView(ui.note("No sleep synced yet. Wear the watch overnight and the app will fetch it in the morning."));
            return null;
        }
        int idx = nights.indexOf(night);
        LinearLayout nav = ui.row();
        TextView prev = ui.text("‹", 28, idx < nights.size() - 1 ? Ui.ACCENT : Ui.OFF, false);
        TextView next = ui.text("›", 28, idx > 0 ? Ui.ACCENT : Ui.OFF, false);
        prev.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        next.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        TextView title = ui.text(dayLabel(night, "EEEE, MMM d"), 16, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        String fn = night;
        prev.setOnClickListener(v -> { if (idx < nights.size() - 1) { a.args.putString("night", nights.get(idx + 1)); a.render(); } });
        next.setOnClickListener(v -> { if (idx > 0) { a.args.putString("night", nights.get(idx - 1)); a.render(); } });
        nav.addView(prev);
        nav.addView(title, Ui.weight(1));
        nav.addView(next);
        b.addView(nav);
        List<int[]> segs = db.night(fn);
        int[] t = T60Db.stageTotals(segs);
        LinearLayout c = ui.card();
        c.addView(ui.mono(MainActivity.hm(T60Db.asleep(t)), 40));
        int start = segs.get(0)[0], end = segs.get(segs.size() - 1)[0] + segs.get(segs.size() - 1)[1];
        c.addView(ui.text(store.clock(start) + " – " + store.clock(end % 1440), 14, Ui.SUB, false));
        ui.add(c, sized(hypnogram(segs), 150), 14);
        ui.add(b, c, 10);
        int sum = Math.max(1, t[0] + t[1] + t[2] + t[3]);
        List<View> rows = new ArrayList<>();
        for (int k : new int[]{0, 1, 3, 2}) {
            LinearLayout r = ui.navRow("■  " + STAGE_NAMES[k], null, MainActivity.hm(t[k]) + "    " + Math.round(t[k] * 100f / sum) + "%", null);
            ((TextView) ((LinearLayout) r.getChildAt(0)).getChildAt(0)).setTextColor(STAGE_COLORS[k]);
            rows.add(r);
        }
        ui.add(b, ui.group(rows.toArray(new View[0])), 12);
        return null;
    }

    /** Stage rows (Awake, REM, Light, Deep) over time. */
    View hypnogram(List<int[]> segs) {
        return new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                if (segs.isEmpty()) return;
                int start = segs.get(0)[0];
                int end = start;
                for (int[] s : segs) end = Math.max(end, norm(s[0], start) + s[1]);
                float span = Math.max(1, end - start), w = getWidth(), labelH = ui.dp(18), rowH = (getHeight() - labelH) / 4f;
                int[] rowOf = {3, 2, 0, 1}; // state -> row (0 top = awake)
                for (int[] s : segs) {
                    float x1 = (norm(s[0], start) - start) / span * w, x2 = x1 + s[1] / span * w;
                    int row = rowOf[Math.max(0, Math.min(3, s[2]))];
                    p.setColor(STAGE_COLORS[Math.max(0, Math.min(3, s[2]))]);
                    c.drawRoundRect(new RectF(x1, row * rowH + 2, Math.max(x2, x1 + 2), (row + 1) * rowH - 2), 4, 4, p);
                }
                p.setColor(Ui.DIM);
                p.setTextSize(ui.dp(11));
                for (int hr = 0; hr < 4; hr++) {
                    int m = start + (int) (span * hr / 3);
                    p.setTextAlign(hr == 0 ? Paint.Align.LEFT : hr == 3 ? Paint.Align.RIGHT : Paint.Align.CENTER);
                    c.drawText(store.clock(m % 1440), w * hr / 3, getHeight() - ui.dp(3), p);
                }
            }
            int norm(int m, int start) { return m < start ? m + 1440 : m; }
        };
    }

    // ---------------------------------------------------------------- Workouts

    static final int[][] PHONE_SPORTS = {{9, 0}, {1, 1}, {2, 2}}; // code, icon index
    static final String[] PHONE_SPORT_NAMES = {"Walk", "Run", "Bike"};

    private View workouts(LinearLayout page) {
        LinearLayout b = a.detail(page, "Workouts");
        T60BleService s = a.svc();
        if (s != null && s.workoutRunning) {
            ui.add(b, ui.button("Return to workout in progress", true, v -> a.go("LiveWorkout")), 0);
        }
        b.addView(ui.sectionLabel("Start a workout"));
        LinearLayout r = ui.row();
        String[] icons = {"🚶", "🏃", "🚲"};
        for (int k = 0; k < 3; k++) {
            int code = PHONE_SPORTS[k][0];
            LinearLayout t = (LinearLayout) a.action(icons[k], PHONE_SPORT_NAMES[k], v -> {
                if (s == null) { a.ask(MainActivity.BLUETOOTH); return; } // the service starts once Bluetooth is allowed
                if (!a.has(android.Manifest.permission.ACCESS_FINE_LOCATION)) { a.ask(MainActivity.LOCATION); return; }
                s.startWorkout(code);
                a.go("LiveWorkout");
            });
            TextView gps = ui.text("with GPS", 11, Ui.SUB, false);
            gps.setGravity(Gravity.CENTER);
            t.addView(gps, Ui.fill());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(92), 1);
            if (k > 0) lp.leftMargin = ui.dp(10);
            r.addView(t, lp);
        }
        ui.add(b, r, 6);
        ui.add(b, ui.note("Works without the watch too; heart rate needs the watch. Indoor workouts started on the watch show up here as well."), 10);
        ui.add(b, ui.sectionLabel("Recent"), 16);
        List<Object[]> list = db.workouts(30);
        if (list.isEmpty()) {
            ui.add(b, ui.note("No workouts yet."), 6);
            return null;
        }
        List<View> rows = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("EEE, MMM d, h:mm a", Locale.US);
        for (Object[] w : list) {
            int sport = (Integer) w[1], secs = (Integer) w[2], steps = (Integer) w[3], meters = (Integer) w[4], avg = (Integer) w[6];
            String name = "phone".equals(w[8]) ? PHONE_SPORT_NAMES[sport == 9 ? 0 : sport == 1 ? 1 : 2] : T60Protocol.sportName(sport);
            String when = f.format(new Date((Long) w[0])) + ("watch".equals(w[8]) ? " · from watch" : "");
            String main = meters > 0 ? store.distance(meters) : steps > 0 ? a.fmt(steps) + " steps" : (secs / 60) + " min";
            String sub = (secs / 60) + " min" + (avg > 0 ? " · avg " + avg + " bpm" : "");
            String track = (String) w[9];
            boolean hasRoute = track != null && track.length() > 2;
            rows.add(ui.navRow(name, when + "\n" + sub, main, !hasRoute ? null : v -> {
                a.args.putString("route", track);
                a.args.putString("routeTitle", name + " · " + when);
                a.go("Route");
            }));
        }
        ui.add(b, ui.group(rows.toArray(new View[0])), 6);
        return null;
    }

    private View liveWorkout(LinearLayout page) {
        T60BleService s = a.svc();
        LinearLayout b = a.detail(page, "Workout in progress");
        if (s == null || !s.workoutRunning) {
            b.addView(ui.note("No workout is running."));
            ui.add(b, ui.button("Back to workouts", false, v -> a.back()), 12);
            return null;
        }
        String name = PHONE_SPORT_NAMES[s.workoutCode == 9 ? 0 : s.workoutCode == 1 ? 1 : 2];
        LinearLayout top = ui.row();
        top.addView(ui.text(name, 18, Ui.TEXT, true), Ui.weight(1));
        top.addView(ui.text(s.gpsGood() ? "● GPS good" : "● Finding GPS…", 13, s.gpsGood() ? Ui.GREEN : 0xFFFFB38A, true));
        b.addView(top);
        List<double[]> pts = new ArrayList<>(s.track);
        android.location.Location here = s.lastReading;
        if (here != null && here.getAccuracy() < 40) pts.add(new double[]{here.getLatitude(), here.getLongitude()});
        View map = trackView(pts);
        ui.add(b, sized(map, 180), 10);
        int secs = s.workoutSeconds();
        TextView time = ui.mono(String.format(Locale.US, "%d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60), 52);
        time.setGravity(Gravity.CENTER);
        ui.add(b, time, 12);
        TextView tl = ui.text(s.workoutPaused ? "Paused" : "Time", 13, Ui.SUB, false);
        tl.setGravity(Gravity.CENTER);
        b.addView(tl);
        boolean mi = store.isImperial();
        float dist = mi ? s.workoutMeters / 1609.34f : s.workoutMeters / 1000f;
        int pace = s.paceSecPerKm();
        if (mi) pace = Math.round(pace * 1.60934f);
        int bpm = s.liveWorkoutHr > 0 ? s.liveWorkoutHr : store.getLiveHeartRate();
        LinearLayout stats = ui.row();
        String[][] x = {{String.format(Locale.US, "%.2f", dist), mi ? "miles" : "km"},
                {pace > 0 ? String.format(Locale.US, "%d:%02d", pace / 60, pace % 60) : "--:--", mi ? "min / mile" : "min / km"},
                {bpm > 0 ? "" + bpm : "--", "bpm"}};
        for (String[] st : x) {
            LinearLayout col = ui.col();
            col.setGravity(Gravity.CENTER);
            col.addView(ui.mono(st[0], 26));
            col.addView(ui.text(st[1], 12, Ui.SUB, false));
            stats.addView(col, Ui.weight(1));
        }
        ui.add(b, stats, 16);
        ui.add(b, ui.button(s.workoutPaused ? "Resume" : "Pause", false, v -> s.pauseWorkout(!s.workoutPaused)), 20);
        ui.add(b, ui.button("End and save", true, v -> a.confirm("End workout?", "Save this " + name.toLowerCase(Locale.US) + " and stop it on the watch?", "End and save", () -> {
            s.endWorkout(false);
            a.replace("Workouts");
        })), 10);
        return null;
    }

    /** GPS track drawn over OpenStreetMap squares, zoomed as close as the whole route allows. */
    View trackView(List<double[]> pts) {
        return new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            { tilePaint.setColorFilter(new android.graphics.LightingColorFilter(0x9A9A9A, 0)); } // dim to suit the dark app
            final Path clip = new Path();
            @Override protected void onDraw(Canvas c) {
                float W = getWidth(), H = getHeight(), pad = ui.dp(20);
                clip.reset();
                clip.addRoundRect(new RectF(0, 0, W, H), ui.dp(20), ui.dp(20), Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                c.drawColor(Ui.CARD);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.DIM);
                p.setTextSize(ui.dp(12));
                if (pts.isEmpty()) { c.drawText("Map: waiting for GPS…", ui.dp(16), ui.dp(26), p); c.restore(); return; }
                double minLa = 90, maxLa = -90, minLo = 180, maxLo = -180;
                for (double[] q : pts) { minLa = Math.min(minLa, q[0]); maxLa = Math.max(maxLa, q[0]); minLo = Math.min(minLo, q[1]); maxLo = Math.max(maxLo, q[1]); }
                // Squares drawn at 128dp: 256px tiles stay sharp and their labels stay readable on a phone screen.
                float T = ui.dp(128);
                int z = MapTiles.MAX_ZOOM;
                while (z > 2 && ((MapTiles.x(maxLo, z) - MapTiles.x(minLo, z)) * T > W - 2 * pad
                        || (MapTiles.y(minLa, z) - MapTiles.y(maxLa, z)) * T > H - 2 * pad)) z--;
                double ox = (MapTiles.x(minLo, z) + MapTiles.x(maxLo, z)) / 2 * T - W / 2;
                double oy = (MapTiles.y(maxLa, z) + MapTiles.y(minLa, z)) / 2 * T - H / 2;
                int n = 1 << z;
                for (int tx = (int) Math.floor(ox / T); tx * T < ox + W; tx++) {
                    for (int ty = (int) Math.floor(oy / T); ty * T < oy + H; ty++) {
                        if (ty < 0 || ty >= n) continue;
                        android.graphics.Bitmap t = MapTiles.get(a, z, Math.floorMod(tx, n), ty, this::invalidate);
                        float l = (float) (tx * T - ox), top = (float) (ty * T - oy);
                        if (t != null) c.drawBitmap(t, null, new RectF(l, top, l + T, top + T), tilePaint);
                    }
                }
                Path path = new Path();
                for (int k = 0; k < pts.size(); k++) {
                    float x = (float) (MapTiles.x(pts.get(k)[1], z) * T - ox), y = (float) (MapTiles.y(pts.get(k)[0], z) * T - oy);
                    if (k == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeWidth(ui.dp(7));
                p.setColor(Ui.BG);
                c.drawPath(path, p);
                p.setStrokeWidth(ui.dp(4));
                p.setColor(Ui.ACCENT);
                c.drawPath(path, p);
                // Dot where you are now (the newest point).
                double[] now = pts.get(pts.size() - 1);
                float nx = (float) (MapTiles.x(now[1], z) * T - ox), ny = (float) (MapTiles.y(now[0], z) * T - oy);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.TEXT);
                c.drawCircle(nx, ny, ui.dp(8), p);
                p.setColor(Ui.ACCENT);
                c.drawCircle(nx, ny, ui.dp(5.5f), p);
                // OSM's licence requires this credit on the map.
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(ui.dp(10));
                String credit = "© OpenStreetMap contributors";
                float cw = p.measureText(credit);
                p.setColor(0xB0000000);
                c.drawRect(W - cw - ui.dp(12), H - ui.dp(18), W, H, p);
                p.setColor(Ui.TEXT);
                c.drawText(credit, W - cw - ui.dp(6), H - ui.dp(5), p);
                c.restore();
            }
        };
    }
}
