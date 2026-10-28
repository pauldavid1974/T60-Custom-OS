package com.paul.t60;

import android.Manifest;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Watch tab and its settings screens. */
final class WatchScreens {

    private final MainActivity a;
    private final Ui ui;
    private final T60Store store;

    WatchScreens(MainActivity a) { this.a = a; ui = a.ui; store = a.store; }

    View build(String name, LinearLayout page) {
        switch (name) {
            case "Watch": return watch(page);
            case "Profile": return profile(page);
            case "ScreenTime": return screenTime(page);
            case "TimeUnits": return timeUnits(page);
            case "WatchLanguage": return language(page);
            case "Alarm": return alarms(page);
            case "Weather": return weather(page);
            case "Calls": return calls(page);
            case "Contacts": return contacts(page);
            case "ContactPicker": return contactPicker(page);
            case "ClearData": return clearData(page);
            case "WomensHealth": return womensHealth(page);
            case "PhotoFace": return photoFace(page);
            case "PhotoPicker": return photoPicker(page);
        }
        return ui.note("Unknown screen " + name);
    }

    private String fw() {
        String v = store.getFirmwareVersion();
        int i = v.indexOf('V');
        return i >= 0 ? v.substring(i) : v;
    }

    private String board() {
        String v = store.getFirmwareVersion();
        int i = v.indexOf('V');
        return i > 0 ? v.substring(0, i) : v;
    }

    String heightText() {
        int cm = store.heightCm();
        if (!store.isImperial()) return cm + " cm";
        int in = Math.round(cm / 2.54f);
        return (in / 12) + " ft " + (in % 12) + " in";
    }

    String weightText() {
        return store.isImperial() ? Math.round(store.weightKg() * 2.20462f) + " lb" : store.weightKg() + " kg";
    }

    String alarmSummary() {
        for (int id = 1; id <= 3; id++) {
            if (store.b("alarm" + id + "_set", false) && store.b("alarm" + id + "_on", false)) {
                return store.clock(store.i("alarm" + id + "_h", 7) * 60 + store.i("alarm" + id + "_m", 0)) + " " + daysText(store.i("alarm" + id + "_days", 0x7F)).toLowerCase(Locale.US);
            }
        }
        return "Off";
    }

    static String daysText(int mask) {
        if (mask == 0x7F) return "Daily";
        if (mask == 0x3E) return "Weekdays";
        if (mask == 0x41) return "Weekends";
        String[] d = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 7; k++) if ((mask & (1 << k)) != 0) sb.append(sb.length() > 0 ? " " : "").append(d[k]);
        return sb.length() == 0 ? "Once" : sb.toString();
    }

    // ---------------------------------------------------------------- Watch tab

    private View watch(LinearLayout page) {
        LinearLayout b = a.tabHeader(page, null, "Watch");
        b.addView(ui.text("T60 · firmware " + fw(), 14, Ui.SUB, false));

        LinearLayout face = ui.card();
        face.addView(ui.cardHead("Watch face", "320 × 380", null));
        LinearLayout fr = ui.row();
        View preview = facePreview(photo(), store.i("face_pos", 0), store.i("face_color", 0xFFFFFF));
        fr.addView(preview, new LinearLayout.LayoutParams(ui.dp(120), ui.dp(142)));
        LinearLayout opts = ui.col();
        opts.setPadding(ui.dp(14), 0, 0, 0);
        opts.addView(ui.text(photo() != null ? "Photo face is ready" : "The watch keeps its own faces; switch them by holding the watch screen.", 13, Ui.SUB, false));
        ui.add(opts, ui.button("Photo face", false, v -> a.go("PhotoFace")), 10);
        fr.addView(opts, Ui.weight(1));
        ui.add(face, fr, 10);
        ui.add(b, face, 12);

        ui.add(b, ui.sectionLabel("You"), 12);
        ui.add(b, ui.group(
                ui.navRow("Your profile", null, heightText() + " · " + weightText(), v -> a.go("Profile")),
                ui.navRow("Step goal", null, a.fmt(store.stepGoal()), v -> a.go("StepGoal")),
                ui.navRow("Women's health", null, store.b("cycle_on", false) ? "On" : "Off", v -> a.go("WomensHealth"))), 6);

        ui.add(b, ui.sectionLabel("Watch settings"), 14);
        String units = (store.isTwelveHour() ? "12-hour" : "24-hour") + " · " + (store.isImperial() ? "miles" : "km") + " · " + (store.isFahrenheit() ? "°F" : "°C");
        ui.add(b, ui.group(
                ui.navRow("Screen and raise to wake", null, store.i("screen_sec", 5) + " sec", v -> a.go("ScreenTime")),
                ui.navRow("Time and units", null, units, v -> a.go("TimeUnits")),
                ui.navRow("Watch language", null, T60Protocol.languageName(store.i("language", 2)), v -> a.go("WatchLanguage")),
                ui.navRow("Alarms", null, alarmSummary(), v -> { a.args.remove("alarm"); a.go("Alarm"); }),
                ui.navRow("Weather on watch", null, store.b("weather_on", true) ? "weather.gov" : "Off", v -> a.go("Weather")),
                ui.navRow("Calls and contacts", null, store.b("bt3_on", false) ? "On" : "Off", v -> a.go("Calls")),
                ui.switchRow("Music controls + song name", null, store.b("music_on", true), on -> store.set("music_on", on)),
                ui.switchRow("Find my phone from the watch", null, store.b("find_phone_on", true), on -> {
                    store.set("find_phone_on", on);
                    a.withWatch(T60BleService::sendFindPhoneSetting);
                })), 6);

        ui.add(b, ui.sectionLabel("Phone app"), 14);
        int shown = 0;
        Set<String> hidden = store.hiddenCards();
        for (String c : store.todayCards()) if (!hidden.contains(c)) shown++;
        ui.add(b, ui.group(ui.navRow("Today cards", null, shown + " shown", v -> a.go("TodayCards"))), 6);

        ui.add(b, ui.sectionLabel("This watch"), 14);
        int bat = store.getBatteryPercent();
        ui.add(b, ui.group(
                ui.navRow("Battery", null, bat >= 0 ? bat + "%" : "--", null),
                ui.navRow("Board", null, board(), null),
                ui.navRow("Address", null, store.getWatchMac(), null)), 6);
        ui.add(b, ui.group(
                ui.navRow("Set up a watch again", null, null, v -> a.go("Setup")),
                ui.navRow("Test the find-my-phone alert", null, null, v -> a.go("FindPhone")),
                ui.navRow("Clear watch data", null, null, v -> a.go("ClearData"))), 12);
        return null;
    }

    Bitmap photo() {
        File f = new File(a.getFilesDir(), "face.png");
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    /** Black 320x380 face: photo (if any), time and date top or bottom. */
    View facePreview(Bitmap bg, int pos, int color) {
        return new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                float w = getWidth(), h = getHeight(), r = ui.dp(18);
                p.setColor(0xFF000000);
                c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
                if (bg != null) {
                    c.save();
                    android.graphics.Path clip = new android.graphics.Path();
                    clip.addRoundRect(new RectF(0, 0, w, h), r, r, android.graphics.Path.Direction.CW);
                    c.clipPath(clip);
                    c.drawBitmap(bg, null, new RectF(0, 0, w, h), p);
                    c.restore();
                }
                p.setColor(0xFF000000 | color);
                p.setTextAlign(Paint.Align.CENTER);
                p.setFakeBoldText(true);
                p.setTextSize(h * 0.2f);
                Date now = new Date();
                float y = pos == 1 ? h * 0.78f : h * 0.32f;
                c.drawText(new SimpleDateFormat(store.isTwelveHour() ? "h:mm" : "HH:mm", Locale.US).format(now), w / 2, y, p);
                p.setTextSize(h * 0.07f);
                p.setFakeBoldText(false);
                c.drawText(new SimpleDateFormat("EEE · MMM d", Locale.US).format(now).toUpperCase(Locale.US), w / 2, y + h * 0.1f, p);
            }
        };
    }

    // ---------------------------------------------------------------- Profile

    private View profile(LinearLayout page) {
        LinearLayout b = a.detail(page, "Your profile");
        b.addView(ui.note("The watch uses these to work out distance and calories. They stay on your phone and watch."));
        boolean imp = store.isImperial();
        int inches = Math.round(store.heightCm() / 2.54f);
        EditText ft = ui.input(String.valueOf(inches / 12), "ft", true), in = ui.input(String.valueOf(inches % 12), "in", true);
        EditText cm = ui.input(String.valueOf(store.heightCm()), "cm", true);
        EditText wt = ui.input(imp ? String.valueOf(Math.round(store.weightKg() * 2.20462f)) : String.valueOf(store.weightKg()), imp ? "lb" : "kg", true);
        EditText yr = ui.input(String.valueOf(store.birthYear()), "1985", true);
        int stride = store.strideCm();
        EditText st = ui.input(imp ? String.valueOf(Math.round(stride / 2.54f)) : String.valueOf(stride), imp ? "in" : "cm", true);
        ui.add(b, ui.sectionLabel("Height"), 12);
        if (imp) {
            LinearLayout r = ui.row();
            r.addView(ft, Ui.weight(1));
            r.addView(ui.text("  ft   ", 15, Ui.SUB, false));
            r.addView(in, Ui.weight(1));
            r.addView(ui.text("  in", 15, Ui.SUB, false));
            ui.add(b, r, 6);
        } else ui.add(b, labeled(cm, "cm"), 6);
        ui.add(b, ui.sectionLabel("Weight"), 12);
        ui.add(b, labeled(wt, imp ? "lb" : "kg"), 6);
        ui.add(b, ui.sectionLabel("Year born"), 12);
        ui.add(b, yr, 6);
        ui.add(b, ui.sectionLabel("Stride length"), 12);
        ui.add(b, labeled(st, imp ? "in" : "cm"), 6);
        ui.add(b, ui.sectionLabel("Sex (used only for calorie math)"), 12);
        int[] sex = {store.isMale() ? 0 : 1};
        ui.add(b, chooser(new String[]{"Male", "Female"}, sex), 6);
        ui.add(b, ui.button("Save", true, v -> {
            try {
                int h = imp ? Math.round((Integer.parseInt(ft.getText().toString()) * 12 + Integer.parseInt(in.getText().toString())) * 2.54f)
                        : Integer.parseInt(cm.getText().toString());
                int w = imp ? Math.round(Integer.parseInt(wt.getText().toString()) / 2.20462f) : Integer.parseInt(wt.getText().toString());
                int s = imp ? Math.round(Integer.parseInt(st.getText().toString()) * 2.54f) : Integer.parseInt(st.getText().toString());
                int y = Integer.parseInt(yr.getText().toString());
                if (h < 100 || h > 250 || w < 25 || w > 250 || y < 1900 || y > Calendar.getInstance().get(Calendar.YEAR) - 5) throw new Exception();
                store.set("height_cm", h);
                store.set("weight_kg", w);
                store.set("stride_cm", Math.max(30, Math.min(150, s)));
                store.set("birth_year", y);
                store.set("male", sex[0] == 0);
                a.withWatch(T60BleService::sendProfile);
                a.back();
            } catch (Exception e) {
                a.toast("Please check the numbers.");
            }
        }), 18);
        return null;
    }

    private View labeled(EditText e, String unit) {
        LinearLayout r = ui.row();
        r.addView(e, Ui.weight(1));
        r.addView(ui.text("  " + unit, 15, Ui.SUB, false));
        return r;
    }

    // ---------------------------------------------------------------- Screen, time and units, language

    private View screenTime(LinearLayout page) {
        LinearLayout b = a.detail(page, "Screen");
        boolean[] raise = {store.b("raise_wake", true)};
        ui.add(b, ui.group(ui.switchRow("Raise wrist to wake", "Screen turns on when you look", raise[0], on -> raise[0] = on)), 0);
        int[] secs = {5, 10, 15, 20};
        int cur = store.i("screen_sec", 5);
        int[] sel = {cur <= 5 ? 0 : cur <= 10 ? 1 : cur <= 15 ? 2 : 3};
        ui.add(b, ui.sectionLabel("Screen stays on for"), 14);
        ui.add(b, chooser(new String[]{"5 sec", "10 sec", "15 sec", "20 sec"}, sel), 6);
        ui.add(b, ui.note("Longer uses more battery."), 8);
        ui.add(b, ui.button("Save to watch", true, v -> {
            store.set("raise_wake", raise[0]);
            store.set("screen_sec", secs[sel[0]]);
            a.withWatch(T60BleService::sendScreen);
            a.back();
        }), 16);
        return null;
    }

    /** Chips that redraw themselves; returns holder. */
    private LinearLayout chooser(String[] labels, int[] sel) {
        LinearLayout holder = ui.col();
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            holder.removeAllViews();
            holder.addView(ui.chips(labels, sel[0], k -> { sel[0] = k; draw[0].run(); }));
        };
        draw[0].run();
        return holder;
    }

    private View timeUnits(LinearLayout page) {
        LinearLayout b = a.detail(page, "Time and units");
        int[] clock = {store.isTwelveHour() ? 0 : 1}, dist = {store.isImperial() ? 0 : 1}, temp = {store.isFahrenheit() ? 0 : 1};
        b.addView(ui.sectionLabel("Clock"));
        ui.add(b, chooser(new String[]{"12-hour (3:45 PM)", "24-hour (15:45)"}, clock), 6);
        ui.add(b, ui.sectionLabel("Distance and weight"), 14);
        ui.add(b, chooser(new String[]{"Miles, pounds", "Km, kilograms"}, dist), 6);
        ui.add(b, ui.sectionLabel("Temperature"), 14);
        ui.add(b, chooser(new String[]{"°F", "°C"}, temp), 6);
        ui.add(b, ui.note("The watch's clock is set from your phone every time they connect."), 12);
        ui.add(b, ui.button("Save to watch", true, v -> {
            store.set("twelve_hour", clock[0] == 0);
            store.set("imperial", dist[0] == 0);
            store.set("fahrenheit", temp[0] == 0);
            a.withWatch(s -> { s.sendUnits(); s.syncTimeNow(); });
            a.back();
        }), 16);
        return null;
    }

    private View language(LinearLayout page) {
        LinearLayout b = a.detail(page, "Watch language");
        b.addView(ui.note("The language of the watch's own menus. The list shows the languages your watch reports it has."));
        List<String> have = store.list("languages");
        if (have.isEmpty()) {
            ui.add(b, ui.note("Connect the watch to read its language list."), 12);
            return null;
        }
        int cur = store.i("language", 2);
        int[] sel = {cur};
        List<View> rows = new ArrayList<>();
        List<TextView> marks = new ArrayList<>();
        for (String n : have) {
            int num = Integer.parseInt(n);
            LinearLayout r = ui.navRow(T60Protocol.languageName(num), null, null, null);
            TextView mark = ui.text(num == cur ? "✓" : "", 18, Ui.ACCENT, true);
            r.addView(mark);
            marks.add(mark);
            r.setOnClickListener(v -> {
                sel[0] = num;
                for (TextView m : marks) m.setText("");
                mark.setText("✓");
            });
            rows.add(r);
        }
        ui.add(b, ui.group(rows.toArray(new View[0])), 12);
        ui.add(b, ui.button("Save to watch", true, v -> {
            a.withWatch(s -> s.sendLanguage(sel[0]));
            store.set("language", sel[0]);
            a.back();
        }), 16);
        return null;
    }

    // ---------------------------------------------------------------- Alarms

    private View alarms(LinearLayout page) {
        LinearLayout b = a.detail(page, "Alarms");
        List<View> rows = new ArrayList<>();
        int free = 0;
        for (int id = 1; id <= 3; id++) {
            String k = "alarm" + id;
            if (!store.b(k + "_set", false)) { if (free == 0) free = id; continue; }
            int fid = id;
            LinearLayout r = ui.switchRow(store.clock(store.i(k + "_h", 7) * 60 + store.i(k + "_m", 0)), daysText(store.i(k + "_days", 0x7F)),
                    store.b(k + "_on", false), on -> { store.set(k + "_on", on); a.withWatch(T60BleService::sendAlarms); });
            ((LinearLayout) r.getChildAt(0)).setOnClickListener(v -> { a.args.putInt("alarm", fid); a.render(); });
            TextView del = ui.text("✕", 16, Ui.DIM, false);
            del.setPadding(ui.dp(12), ui.dp(8), ui.dp(4), ui.dp(8));
            del.setOnClickListener(v -> {
                store.set(k + "_set", false);
                store.set(k + "_on", false);
                a.withWatch(T60BleService::sendAlarms);
                a.render();
            });
            r.addView(del);
            rows.add(r);
        }
        if (!rows.isEmpty()) b.addView(ui.group(rows.toArray(new View[0])));
        int editing = a.args.getInt("alarm", 0);
        if (editing == 0) editing = free;
        if (editing == 0) {
            ui.add(b, ui.note("All 3 alarms are in use. Tap one to change it, or ✕ to remove it."), 12);
            return null;
        }
        String k = "alarm" + editing;
        int fid = editing;
        boolean existing = store.b(k + "_set", false);
        LinearLayout c = ui.card();
        c.addView(ui.text(existing ? "Change alarm" : "New alarm", 16, Ui.TEXT, true));
        int h24 = existing ? store.i(k + "_h", 7) : 6, m = existing ? store.i(k + "_m", 0) : 30;
        boolean twelve = store.isTwelveHour();
        EditText hh = ui.input(String.valueOf(twelve ? (h24 % 12 == 0 ? 12 : h24 % 12) : h24), "Hour", true);
        EditText mm = ui.input(String.format(Locale.US, "%02d", m), "Minute", true);
        hh.setGravity(Gravity.CENTER);
        mm.setGravity(Gravity.CENTER);
        LinearLayout tr = ui.row();
        tr.addView(hh, new LinearLayout.LayoutParams(ui.dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        tr.addView(ui.text(" : ", 22, Ui.TEXT, true));
        tr.addView(mm, new LinearLayout.LayoutParams(ui.dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        int[] pm = {h24 >= 12 ? 1 : 0};
        if (twelve) {
            LinearLayout ap = chooser(new String[]{"AM", "PM"}, pm);
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.leftMargin = ui.dp(12);
            tr.addView(ap, lp);
        }
        ui.add(c, tr, 12);
        int[] days = {existing ? store.i(k + "_days", 0x7F) : 0x3E};
        ui.add(c, ui.dayPicker(days), 14);
        ui.add(c, ui.button("Save to watch", true, v -> {
            try {
                int H = Integer.parseInt(hh.getText().toString().trim()), M = Integer.parseInt(mm.getText().toString().trim());
                if (twelve) { if (H < 1 || H > 12) throw new Exception(); H = H % 12 + (pm[0] == 1 ? 12 : 0); }
                if (H < 0 || H > 23 || M < 0 || M > 59) throw new Exception();
                store.set(k + "_h", H);
                store.set(k + "_m", M);
                store.set(k + "_days", days[0] == 0 ? 0x7F : days[0]);
                store.set(k + "_set", true);
                store.set(k + "_on", true);
                a.withWatch(T60BleService::sendAlarms);
                a.args.remove("alarm");
                a.render();
                a.toast("Alarm saved");
            } catch (Exception e) {
                a.toast("Please check the time.");
            }
        }), 16);
        ui.add(b, c, 12);
        ui.add(b, ui.note("The watch holds up to 3 alarms."), 10);
        return null;
    }

    // ---------------------------------------------------------------- Weather

    private View weather(LinearLayout page) {
        LinearLayout b = a.detail(page, "Weather");
        ui.add(b, ui.group(
                ui.switchRow("Show weather on watch", null, store.b("weather_on", true), on -> store.set("weather_on", on)),
                ui.navRow("Location", null, "Phone GPS", null),
                ui.navRow("Update", null, "Every hour", null)), 0);
        JSONObject w;
        try { w = new JSONObject(store.s("weather_json", "{}")); } catch (Exception e) { w = new JSONObject(); }
        LinearLayout c = ui.card();
        if (w.has("town")) {
            LinearLayout top = ui.row();
            top.addView(ui.text(w.optString("town"), 16, Ui.TEXT, true), Ui.weight(1));
            top.addView(ui.text(Weather.updatedText(store), 13, Ui.SUB, false));
            c.addView(top);
            int nowF = w.optInt("now");
            ui.add(c, ui.mono(store.isFahrenheit() ? nowF + "°" : Weather.toC(nowF) + "°", 48), 6);
            c.addView(ui.text(w.optString("text"), 15, Ui.BODY, false));
            JSONArray days = w.optJSONArray("days");
            if (days != null) {
                for (int k = 0; k < days.length(); k++) {
                    JSONObject d = days.optJSONObject(k);
                    int hi = d.optInt("hi"), lo = d.optInt("lo");
                    if (!store.isFahrenheit()) { hi = Weather.toC(hi); lo = Weather.toC(lo); }
                    LinearLayout r = ui.row();
                    r.setPadding(0, ui.dp(8), 0, ui.dp(8));
                    r.addView(ui.text(d.optString("day"), 14, Ui.TEXT, true), new LinearLayout.LayoutParams(ui.dp(52), ViewGroup.LayoutParams.WRAP_CONTENT));
                    TextView wx = ui.text(d.optString("text"), 14, Ui.SUB, false);
                    wx.setSingleLine(true);
                    wx.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    r.addView(wx, Ui.weight(1));
                    r.addView(ui.text(hi + "°  ", 14, Ui.TEXT, true));
                    r.addView(ui.text(lo + "°", 14, Ui.DIM, false));
                    ui.add(c, r, 0);
                }
            }
        } else {
            c.addView(ui.text("No forecast yet", 16, Ui.TEXT, true));
        }
        ui.add(b, c, 12);
        ui.add(b, ui.button("Update now", false, v -> {
            a.toast("Getting the forecast…");
            Weather.update(a, err -> { if (err != null) a.toast(err); a.render(); });
        }), 12);
        ui.add(b, ui.note("Forecast from the National Weather Service (weather.gov). Only your location, rounded to about a mile, is sent. No account, nothing else."), 12);
        return null;
    }

    // ---------------------------------------------------------------- Calls and contacts

    private boolean mediaAudioOnWatch() {
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            return ad != null && ad.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothA2dp.STATE_CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    private View calls(LinearLayout page) {
        LinearLayout b = a.detail(page, "Calls through the watch");
        b.addView(ui.note("Answer and talk on your wrist. The watch uses its own speaker and microphone."));
        boolean on = store.b("bt3_on", false), paired = store.b("bt3_paired", false);
        LinearLayout c = ui.card();
        c.addView(ui.text(on && paired ? "Ready for calls" : on ? "Calls on, not paired" : "Calls through the watch are off", 18, on && paired ? Ui.GREEN : Ui.TEXT, true));
        String name = store.s("bt3_name", "");
        ui.add(c, ui.text(paired ? "Paired with this phone as " + (name.isEmpty() ? store.getWatchName() : name) : "Pair the watch's call audio in Bluetooth settings", 14, Ui.SUB, false), 4);
        ui.add(b, c, 12);
        ui.add(b, ui.group(
                ui.switchRow("Use watch for calls", "Calls go to your wrist", on, v -> {
                    store.set("bt3_on", v);
                    a.withWatch(s -> s.setCallAudio(v));
                }),
                ui.switchRow("Play phone audio on watch", "Music and videos from the watch speaker", mediaAudioOnWatch(), v -> {
                    a.toast("Turn \"Media audio\" " + (v ? "on" : "off") + " for " + store.getWatchName() + " here.");
                    a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                }),
                ui.navRow("Contacts on watch", null, store.watchContacts().size() + " contacts", v -> a.go("Contacts"))), 12);
        ui.add(b, ui.button("Pair again", false, v -> {
            a.withWatch(T60BleService::linkCallAudio);
            a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        }), 14);
        return null;
    }

    private View contacts(LinearLayout page) {
        LinearLayout b = a.detail(page, "Contacts on watch");
        b.addView(ui.note("Pick the people you want to call straight from your wrist."));
        List<String> list = store.watchContacts();
        ui.add(b, ui.sectionLabel("On your watch · " + list.size() + " contacts"), 12);
        List<View> rows = new ArrayList<>();
        for (int k = 0; k < list.size(); k++) {
            String[] p = list.get(k).split("\n", 2);
            int idx = k;
            LinearLayout r = ui.navRow(p[0], p.length > 1 ? p[1] : "", null, null);
            TextView del = ui.text("✕", 16, Ui.DIM, false);
            del.setPadding(ui.dp(12), ui.dp(8), ui.dp(4), ui.dp(8));
            del.setOnClickListener(v -> { list.remove(idx); store.setWatchContacts(list); a.render(); });
            r.addView(del);
            rows.add(r);
        }
        if (!rows.isEmpty()) ui.add(b, ui.group(rows.toArray(new View[0])), 6);
        TextView add = ui.text("+ Add from phone contacts", 15, Ui.ACCENT, true);
        add.setPadding(ui.dp(4), ui.dp(14), 0, ui.dp(14));
        add.setOnClickListener(v -> {
            if (!a.has(Manifest.permission.READ_CONTACTS)) { a.ask(Manifest.permission.READ_CONTACTS); return; }
            a.go("ContactPicker");
        });
        b.addView(add);
        ui.add(b, ui.button("Send to watch", true, v -> {
            List<String[]> out = new ArrayList<>();
            for (String s : store.watchContacts()) {
                String[] p = s.split("\n", 2);
                if (p.length == 2) out.add(new String[]{p[0], p[1]});
            }
            if (a.withWatch(s -> s.sendContacts(out))) a.toast("Sending " + out.size() + " contacts to the watch");
        }), 8);
        ui.add(b, ui.note("Only the names and numbers you pick are copied to the watch."), 10);
        return null;
    }

    private View contactPicker(LinearLayout page) {
        LinearLayout b = a.detail(page, "Add contacts");
        EditText search = ui.input("", "Search your phone's contacts", false);
        b.addView(search);
        LinearLayout list = ui.col();
        ui.add(b, list, 10);
        List<String[]> all = new ArrayList<>();
        try (Cursor c = a.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            Set<String> seen = new HashSet<>();
            while (c != null && c.moveToNext()) {
                String n = c.getString(0), num = c.getString(1);
                if (n == null || num == null || !seen.add(n + num.replaceAll("[^0-9+]", ""))) continue;
                all.add(new String[]{n, num});
            }
        } catch (Exception e) {
            b.addView(ui.note("Needs permission to read your contacts."));
        }
        Set<String> picked = new HashSet<>(store.watchContacts());
        Runnable draw = () -> {
            list.removeAllViews();
            String q = search.getText().toString().toLowerCase(Locale.US);
            List<View> rows = new ArrayList<>();
            for (String[] ct : all) {
                if (!q.isEmpty() && !ct[0].toLowerCase(Locale.US).contains(q) && !ct[1].contains(q)) continue;
                if (rows.size() >= 200) break;
                String key = ct[0] + "\n" + ct[1];
                LinearLayout r = ui.navRow(ct[0], ct[1], null, null);
                CheckBox cb = new CheckBox(a);
                cb.setChecked(picked.contains(key));
                cb.setOnCheckedChangeListener((x, on) -> { if (on) picked.add(key); else picked.remove(key); });
                r.addView(cb);
                r.setOnClickListener(v -> cb.toggle());
                rows.add(r);
            }
            if (!rows.isEmpty()) list.addView(ui.group(rows.toArray(new View[0])));
        };
        draw.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int bf, int c) {}
            @Override public void afterTextChanged(Editable e) { draw.run(); }
        });
        ui.add(b, ui.button("Add selected", true, v -> {
            List<String> out = new ArrayList<>(picked);
            if (out.size() > 50) { a.toast("The watch holds up to 50 contacts."); return; }
            store.setWatchContacts(out);
            a.back();
        }), 12);
        ui.add(b, ui.note("Only the ones you tick are copied."), 8);
        return null;
    }

    // ---------------------------------------------------------------- Clear data

    private View clearData(LinearLayout page) {
        LinearLayout b = a.detail(page, "Clear watch data");
        b.addView(ui.text("Clear the watch's history?", 22, Ui.TEXT, true));
        ui.add(b, ui.text("Deleted from the watch: stored steps, sleep, heart rate, blood oxygen and workouts.", 15, Ui.BODY, false), 12);
        ui.add(b, ui.text("Kept: everything already synced to this phone, your settings and watch faces.", 15, Ui.BODY, false), 8);
        ui.add(b, ui.note("The app syncs first, so nothing new is lost."), 10);
        ui.add(b, ui.dangerButton("Sync, then clear watch", v -> a.confirm("Clear watch data?", "This erases the history stored on the watch after syncing it to this phone. It can't be undone.", "Clear", () ->
                a.withWatch(s -> s.clearWatchData(() -> { a.toast("Watch history cleared"); a.render(); })))), 20);
        ui.add(b, ui.button("Cancel", false, v -> a.back()), 10);
        return null;
    }

    // ---------------------------------------------------------------- Women's health

    private View womensHealth(LinearLayout page) {
        LinearLayout b = a.detail(page, "Women's health");
        boolean on = store.b("cycle_on", false);
        ui.add(b, ui.group(ui.switchRow("Track cycle on watch", null, on, v -> {
            store.set("cycle_on", v);
            a.withWatch(T60BleService::sendCycle);
            a.render();
        })), 0);
        String start = store.s("cycle_start", "");
        int len = store.i("cycle_len", 28), per = store.i("cycle_period", 5);
        LinearLayout c = ui.card();
        if (!start.isEmpty()) {
            long day = (T60Db.parse(T60Db.today()).getTimeInMillis() - T60Db.parse(start).getTimeInMillis()) / 86400000L;
            int d = (int) (day % len) + 1;
            c.addView(ui.mono("Day " + d, 40));
            c.addView(ui.text("of a " + len + "-day cycle", 14, Ui.SUB, false));
            ui.add(c, ui.text("■ Period   ■ Fertile window", 13, 0xFFFF8A8E, false), 8);
            ui.add(c, ui.text("Next period expected in " + MainActivity.daysUntilNextPeriod(store) + " days", 15, Ui.BODY, false), 6);
        } else {
            c.addView(ui.text("Log the first day of your last period to start.", 15, Ui.BODY, false));
        }
        ui.add(b, c, 12);
        ui.add(b, ui.button("Log period start today", true, v -> {
            store.set("cycle_start", T60Db.today());
            a.withWatch(T60BleService::sendCycle);
            a.render();
        }), 10);
        int[] cl = {len}, pl = {per};
        LinearLayout cycleRow = ui.col();
        cycleRow.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));
        cycleRow.addView(ui.text("Cycle length", 15, Ui.TEXT, true));
        cycleRow.addView(ui.stepper(cl, 1, 20, 45, " days", v -> { store.set("cycle_len", v); a.withWatch(T60BleService::sendCycle); }, 24));
        LinearLayout perRow = ui.col();
        perRow.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));
        perRow.addView(ui.text("Period length", 15, Ui.TEXT, true));
        perRow.addView(ui.stepper(pl, 1, 2, 10, " days", v -> { store.set("cycle_period", v); a.withWatch(T60BleService::sendCycle); }, 24));
        ui.add(b, ui.group(cycleRow, perRow), 12);
        int rd = store.i("cycle_remind_days", 2);
        ui.add(b, ui.group(ui.switchRow("Reminder on watch", rd + " days before", store.b("cycle_remind", true), v -> store.set("cycle_remind", v))), 12);
        ui.add(b, ui.chips(new String[]{"1 day", "2 days", "3 days"}, Math.max(0, Math.min(2, rd - 1)), k -> { store.set("cycle_remind_days", k + 1); a.render(); }), 8);
        ui.add(b, ui.note("Stays on this phone and the watch. Turn this off and it disappears from the watch."), 12);
        return null;
    }

    // ---------------------------------------------------------------- Photo face

    private View photoFace(LinearLayout page) {
        LinearLayout b = a.detail(page, "Photo watch face");
        int pos = store.i("face_pos", 0), color = store.i("face_color", 0xFFFFFF);
        LinearLayout pv = ui.row();
        pv.setGravity(Gravity.CENTER);
        pv.addView(facePreview(photo(), pos, color), new LinearLayout.LayoutParams(ui.dp(200), ui.dp(237)));
        b.addView(pv);
        ui.add(b, ui.button(photo() == null ? "Choose a photo" : "Choose a different photo", false, v -> a.go("PhotoPicker")), 14);
        ui.add(b, ui.sectionLabel("Where the time sits"), 14);
        ui.add(b, ui.chips(new String[]{"Top", "Bottom"}, pos, k -> { store.set("face_pos", k); a.render(); }), 6);
        ui.add(b, ui.sectionLabel("Text color"), 14);
        int[] colors = {0xFFFFFF, 0x5AB0FF, 0xFFB38A, 0x7BE8A8, 0x000000};
        LinearLayout cr = ui.row();
        for (int col : colors) {
            View dot = new View(a);
            dot.setBackground(Ui.round(0xFF000000 | col, col == color ? Ui.ACCENT : Ui.TILE_BORDER, ui.dp(22), ui.dp(col == color ? 3 : 1)));
            dot.setOnClickListener(v -> { store.set("face_color", col); a.render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44));
            lp.rightMargin = ui.dp(12);
            cr.addView(dot, lp);
        }
        ui.add(b, cr, 6);
        ui.add(b, ui.button("Send to watch", true, v -> {
            Bitmap p = photo();
            if (p == null) { a.toast("Choose a photo first."); return; }
            a.withWatch(s -> s.sendPhotoFace(p, pos, color, msg -> a.runOnUiThread(() -> a.toast(msg))));
        }), 18);
        return null;
    }

    private View photoPicker(LinearLayout page) {
        LinearLayout b = a.detail(page, "Choose a photo");
        b.addView(ui.note("Your phone's photo picker opens here. The app only sees the photo you choose."));
        ui.add(b, ui.note("Next you can crop it to the watch's 320 × 380 screen."), 8);
        ui.add(b, ui.button("Open photos", true, v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
            a.startActivityForResult(i, 42);
        }), 16);
        return null;
    }
}
