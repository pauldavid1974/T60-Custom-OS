package com.paul.t60;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Health history kept on the phone only. Dates are "yyyyMMdd" strings, times are minutes after midnight. */
final class T60Db extends SQLiteOpenHelper {

    private static T60Db instance;

    static synchronized T60Db get(Context c) {
        if (instance == null) instance = new T60Db(c.getApplicationContext());
        return instance;
    }

    private T60Db(Context c) { super(c, "t60_health.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE steps(day TEXT, hour INTEGER, steps INTEGER, PRIMARY KEY(day, hour))");
        db.execSQL("CREATE TABLE hr(day TEXT, minute INTEGER, bpm INTEGER, PRIMARY KEY(day, minute))");
        db.execSQL("CREATE TABLE spo2(day TEXT, minute INTEGER, pct INTEGER, PRIMARY KEY(day, minute))");
        // night = the date the watch files the night under; state 0 deep, 1 light, 2 awake, 3 REM
        db.execSQL("CREATE TABLE sleep(night TEXT, start INTEGER, minutes INTEGER, state INTEGER, PRIMARY KEY(night, start))");
        db.execSQL("CREATE TABLE workout(start INTEGER PRIMARY KEY, sport INTEGER, seconds INTEGER, steps INTEGER,"
                + " meters INTEGER, kcal INTEGER, avg_hr INTEGER, max_hr INTEGER, source TEXT, track TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}

    static String day(Calendar c) { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(c.getTime()); }
    static String today() { return day(Calendar.getInstance()); }

    static String dayOffset(int deltaDays) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, deltaDays);
        return day(c);
    }

    static Calendar parse(String day) {
        Calendar c = Calendar.getInstance();
        try { c.setTime(new SimpleDateFormat("yyyyMMdd", Locale.US).parse(day)); } catch (Exception ignored) {}
        return c;
    }

    private void put(String table, ContentValues v) {
        getWritableDatabase().insertWithOnConflict(table, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void putSteps(String day, int hour, int steps) {
        ContentValues v = new ContentValues();
        v.put("day", day); v.put("hour", hour); v.put("steps", steps);
        put("steps", v);
    }

    void putHr(String day, int minute, int bpm) {
        ContentValues v = new ContentValues();
        v.put("day", day); v.put("minute", minute); v.put("bpm", bpm);
        put("hr", v);
    }

    void putSpo2(String day, int minute, int pct) {
        ContentValues v = new ContentValues();
        v.put("day", day); v.put("minute", minute); v.put("pct", pct);
        put("spo2", v);
    }

    void replaceNight(String night, List<int[]> segments /* start, minutes, state */) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("sleep", "night=?", new String[]{night});
            for (int[] s : segments) {
                ContentValues v = new ContentValues();
                v.put("night", night); v.put("start", s[0]); v.put("minutes", s[1]); v.put("state", s[2]);
                db.insertWithOnConflict("sleep", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void putWorkout(long startMs, int sport, int seconds, int steps, int meters, int kcal, int avgHr, int maxHr,
                    String source, String track) {
        ContentValues v = new ContentValues();
        v.put("start", startMs); v.put("sport", sport); v.put("seconds", seconds); v.put("steps", steps);
        v.put("meters", meters); v.put("kcal", kcal); v.put("avg_hr", avgHr); v.put("max_hr", maxHr);
        v.put("source", source); v.put("track", track);
        put("workout", v);
    }

    private int intQuery(String sql, String... args) {
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getInt(0) : 0;
        }
    }

    int stepsOn(String day) { return intQuery("SELECT SUM(steps) FROM steps WHERE day=?", day); }

    /** 24 values, steps per hour. */
    int[] hourlySteps(String day) {
        int[] h = new int[24];
        try (Cursor c = getReadableDatabase().rawQuery("SELECT hour, steps FROM steps WHERE day=?", new String[]{day})) {
            while (c.moveToNext()) if (c.getInt(0) >= 0 && c.getInt(0) < 24) h[c.getInt(0)] = c.getInt(1);
        }
        return h;
    }

    /** Minutes in which more than 60 steps were taken, approximated by hours over 1000 steps. */
    int activeMinutes(String day) {
        int[] h = hourlySteps(day);
        int m = 0;
        for (int s : h) m += Math.min(60, s / 100);
        return m;
    }

    /** [minute, bpm] pairs for a day, oldest first. */
    List<int[]> hrOn(String day) { return pairs("SELECT minute, bpm FROM hr WHERE day=? ORDER BY minute", day); }

    List<int[]> spo2On(String day) { return pairs("SELECT minute, pct FROM spo2 WHERE day=? ORDER BY minute", day); }

    private List<int[]> pairs(String sql, String day) {
        List<int[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{day})) {
            while (c.moveToNext()) out.add(new int[]{c.getInt(0), c.getInt(1)});
        }
        return out;
    }

    /** Latest reading: {dayIndexFromNow (0 today), minute, value} or null. */
    int[] latest(String table, String col) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT day, minute, " + col + " FROM " + table
                + " ORDER BY day DESC, minute DESC LIMIT 1", null)) {
            if (!c.moveToFirst()) return null;
            long diff = (parse(today()).getTimeInMillis() - parse(c.getString(0)).getTimeInMillis()) / 86400000L;
            return new int[]{(int) diff, c.getInt(1), c.getInt(2)};
        }
    }

    /** {min, avg, max} over the given days, or null. */
    int[] range(String table, String col, String fromDay, String toDay) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MIN(" + col + "), AVG(" + col + "), MAX(" + col + ") FROM "
                + table + " WHERE day>=? AND day<=?", new String[]{fromDay, toDay})) {
            if (!c.moveToFirst() || c.isNull(0)) return null;
            return new int[]{c.getInt(0), Math.round(c.getFloat(1)), c.getInt(2)};
        }
    }

    /** Lowest 10th-percentile-ish heart rate of the day as a resting estimate. */
    int restingHr(String day) {
        List<int[]> l = hrOn(day);
        if (l.isEmpty()) return 0;
        List<Integer> v = new ArrayList<>();
        for (int[] p : l) v.add(p[1]);
        java.util.Collections.sort(v);
        return v.get(v.size() / 10);
    }

    /** Sleep segments for a night: {start, minutes, state}. */
    List<int[]> night(String night) {
        List<int[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT start, minutes, state FROM sleep WHERE night=? ORDER BY start",
                new String[]{night})) {
            while (c.moveToNext()) out.add(new int[]{c.getInt(0), c.getInt(1), c.getInt(2)});
        }
        // Order across midnight: evening starts (>= 12:00) come first.
        out.sort((a, b2) -> Integer.compare(a[0] < 720 ? a[0] + 1440 : a[0], b2[0] < 720 ? b2[0] + 1440 : b2[0]));
        return out;
    }

    /** Nights that have data, newest first. */
    List<String> nights() {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT night FROM sleep ORDER BY night DESC", null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    /** {deep, light, awake, rem} minutes. */
    static int[] stageTotals(List<int[]> segs) {
        int[] t = new int[4];
        for (int[] s : segs) if (s[2] >= 0 && s[2] < 4) t[s[2]] += s[1];
        return t;
    }

    static int asleep(int[] t) { return t[0] + t[1] + t[3]; }

    /** Workouts newest first: start, sport, seconds, steps, meters, kcal, avgHr, maxHr; plus source/track. */
    List<Object[]> workouts(int limit) {
        List<Object[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT start, sport, seconds, steps, meters, kcal, avg_hr, max_hr,"
                + " source, track FROM workout ORDER BY start DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) out.add(new Object[]{c.getLong(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4),
                    c.getInt(5), c.getInt(6), c.getInt(7), c.getString(8), c.getString(9)});
        }
        return out;
    }

    /** CSV of everything from fromDay on ("" = all). */
    String csv(String fromDay) {
        StringBuilder sb = new StringBuilder("type,date,time,value,extra\n");
        SQLiteDatabase db = getReadableDatabase();
        String[] a = {fromDay};
        try (Cursor c = db.rawQuery("SELECT day, hour, steps FROM steps WHERE day>=? ORDER BY day, hour", a)) {
            while (c.moveToNext()) sb.append("steps,").append(c.getString(0)).append(',').append(String.format(Locale.US, "%02d:00", c.getInt(1))).append(',').append(c.getInt(2)).append(",\n");
        }
        try (Cursor c = db.rawQuery("SELECT day, minute, bpm FROM hr WHERE day>=? ORDER BY day, minute", a)) {
            while (c.moveToNext()) sb.append("heart_rate,").append(c.getString(0)).append(',').append(hm(c.getInt(1))).append(',').append(c.getInt(2)).append(",\n");
        }
        try (Cursor c = db.rawQuery("SELECT day, minute, pct FROM spo2 WHERE day>=? ORDER BY day, minute", a)) {
            while (c.moveToNext()) sb.append("blood_oxygen,").append(c.getString(0)).append(',').append(hm(c.getInt(1))).append(',').append(c.getInt(2)).append(",\n");
        }
        String[] st = {"deep", "light", "awake", "rem"};
        try (Cursor c = db.rawQuery("SELECT night, start, minutes, state FROM sleep WHERE night>=? ORDER BY night, start", a)) {
            while (c.moveToNext()) sb.append("sleep,").append(c.getString(0)).append(',').append(hm(c.getInt(1))).append(',').append(c.getInt(2)).append(',').append(st[Math.max(0, Math.min(3, c.getInt(3)))]).append('\n');
        }
        long fromMs = fromDay.isEmpty() ? 0 : parse(fromDay).getTimeInMillis();
        try (Cursor c = db.rawQuery("SELECT start, sport, seconds, meters, kcal, avg_hr FROM workout WHERE start>=" + fromMs + " ORDER BY start", null)) {
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd,HH:mm", Locale.US);
            while (c.moveToNext()) sb.append("workout,").append(f.format(new Date(c.getLong(0)))).append(',').append(c.getInt(2)).append(',')
                    .append(T60Protocol.sportName(c.getInt(1))).append(" ").append(c.getInt(3)).append("m ").append(c.getInt(4)).append("kcal avg").append(c.getInt(5)).append('\n');
        }
        return sb.toString();
    }

    static String hm(int minute) { return String.format(Locale.US, "%02d:%02d", (minute / 60) % 24, minute % 60); }
}
