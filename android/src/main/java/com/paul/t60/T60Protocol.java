package com.paul.t60;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Packet builders for Paul's T60 (RH281LWJ, firmware V007108).
 * Every layout here is copied from GloryFit v4.0.12 (com.yc.pedometer.sdk.WriteCommandToBLE,
 * decompiled offline) and the branch chosen by this watch's FunctionList bits
 * (33F1 = 08 08 44 2A 01 24 39 43 75 6F FF FE D9 21 00 5F 78 4B E1 DC). See docs/PROTOCOL.md.
 * Writes go to 33F1 unless the builder name ends in Ble5 (34F1).
 */
public final class T60Protocol {

    public static final String DEFAULT_MAC = "60:1A:B7:00:AE:67";

    public static final UUID UUID_SERVICE_MAIN = UUID.fromString("000055ff-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHAR_WRITE   = UUID.fromString("000033f1-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHAR_NOTIFY  = UUID.fromString("000033f2-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_SERVICE_EXT      = UUID.fromString("000056ff-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHAR_EXT_WRITE   = UUID.fromString("000034f1-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHAR_EXT_NOTIFY  = UUID.fromString("000034f2-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CCCD             = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /** Text push message types (BluetoothLeService.MessageType / getAppType). */
    public static final int MSG_CALL = 0, MSG_SMS = 3, MSG_OTHER = 4;

    private T60Protocol() {}

    static byte b(int v) { return (byte) (v & 0xFF); }

    /** A3: set clock (syncBLETime). */
    public static byte[] syncTime(Calendar c) {
        int y = c.get(Calendar.YEAR);
        return new byte[]{(byte) 0xA3, b(y >> 8), b(y), b(c.get(Calendar.MONTH) + 1), b(c.get(Calendar.DAY_OF_MONTH)),
                b(c.get(Calendar.HOUR_OF_DAY)), b(c.get(Calendar.MINUTE)), b(c.get(Calendar.SECOND))};
    }

    /** A0: distance unit (1 km, 2 miles) and clock (1 = 24 h, 2 = 12 h) (sendUnitToBLE). */
    public static byte[] units(boolean imperial, boolean twelveHour) {
        return new byte[]{(byte) 0xA0, b(imperial ? 2 : 1), b(twelveHour ? 2 : 1)};
    }

    /**
     * A9: profile, screen-on seconds, step goal, raise-to-wake, heart-rate alert limits and
     * temperature unit (sendStepLenAndWeightToBLE). This watch sets L2 0x1000, so byte 2 is
     * height in cm (not stride); L3 0x40 is clear, so weight has no fraction byte.
     */
    public static byte[] profile(int heightCm, int weightKg, int screenSec, int stepGoal, boolean raise,
                                 int highHrLimit /* 0 = off */, int age, boolean male, boolean fahrenheit) {
        return new byte[]{(byte) 0xA9, 0, b(heightCm), 0, b(weightKg), b(screenSec),
                b(stepGoal >> 24), b(stepGoal >> 16), b(stepGoal >> 8), b(stepGoal),
                b(raise ? 1 : 0), b(highHrLimit > 0 ? highHrLimit : 0xFF), 1 /* HR sleep assist */,
                b(age), b(male ? 1 : 2), 0, 2 /* not Chinese */, b(fahrenheit ? 2 : 1), 0};
    }

    public static byte[] readVersion() { return new byte[]{(byte) 0xA1}; }
    public static byte[] readBattery() { return new byte[]{(byte) 0xA2}; }
    public static byte[] readDialConfig() { return new byte[]{0x26, 0x01}; }

    /** 3F 04: daily step target (sendTodayTargetCommand(4, 1)). */
    public static byte[] stepTarget(int goal) {
        return new byte[]{0x3F, 4, 1, b(goal >> 24), b(goal >> 16), b(goal >> 8), b(goal)};
    }

    /** AB ... 07 01: buzz the watch (findBand(2)). */
    public static byte[] findWatch() { return new byte[]{(byte) 0xAB, 0, 0, 0, 1, 2, 7, 1}; }

    /** AB 00..: stop buzzing (sendStopVibrationCommand). */
    public static byte[] stopVibration() { return new byte[]{(byte) 0xAB, 0, 0, 0, 0, 0, 0, 0}; }

    /** D1 0A: enable the watch's find-my-phone button (openFindPhone). */
    public static byte[] findPhoneEnable(boolean on) { return new byte[]{(byte) 0xD1, 0x0A, b(on ? 1 : 0)}; }

    /** D1 0A 01 xx: tell the watch whether the phone is still ringing (phoneRingStatus). */
    public static byte[] phoneRinging(boolean ringing) { return new byte[]{(byte) 0xD1, 0x0A, 1, b(ringing ? 1 : 0)}; }

    /** D1 0C: play state to watch, 1 = playing, 2 = paused (controlBandMusic). */
    public static byte[] musicState(boolean playing) { return new byte[]{(byte) 0xD1, 0x0C, b(playing ? 1 : 2)}; }

    /** D1 0D/0E: phone volume percent (controlBandMusicVolume). */
    public static byte[] musicVolume(boolean down, int pct) {
        return new byte[]{(byte) 0xD1, b(down ? 0x0E : 0x0D), b(Math.max(0, Math.min(100, pct)))};
    }

    /** 3A 01 / 3A 06 / 3A FA: play state and song title (sendSongInformationToBle), on 34F1. */
    public static List<byte[]> songInfoBle5(boolean playing, String title) {
        List<byte[]> out = new ArrayList<>();
        out.add(new byte[]{0x3A, 1, b(playing ? 1 : 2), -1, -1, -1, -1, -1, -1, -1, -1, -1, -1});
        if (title != null && !title.isEmpty()) {
            byte[] t = title.getBytes(StandardCharsets.UTF_8);
            int n = Math.min(52, t.length);
            byte[] p = new byte[n + 3];
            p[0] = 0x3A; p[1] = 6; p[2] = b(n);
            System.arraycopy(t, 0, p, 3, n);
            out.add(p);
        }
        out.add(new byte[]{0x3A, (byte) 0xFA});
        return out;
    }

    /** F7 01/02: all-day heart rate on/off (set24HourRate). */
    public static byte[] allDayHeartRate(boolean on) { return new byte[]{(byte) 0xF7, b(on ? 1 : 2)}; }

    /** D6 01: single (static) heart-rate measurement mode (sendKeyOpenDynamicOrStaticRate(1)). */
    public static byte[] staticHeartRateMode() { return new byte[]{(byte) 0xD6, 1}; }

    /** E5 11 / E5 00: start/stop a live heart-rate reading (sendRateTestCommand). */
    public static byte[] heartRateTest(boolean start) { return new byte[]{(byte) 0xE5, b(start ? 0x11 : 0)}; }

    /** 34 11 / 34 00: start/stop a blood-oxygen reading. */
    public static byte[] oxygenTest(boolean start) { return new byte[]{0x34, b(start ? 0x11 : 0)}; }

    /** 34 03: automatic blood-oxygen readings every intervalMin minutes. */
    public static byte[] oxygenAuto(boolean on, int intervalMin) {
        return new byte[]{0x34, 3, b(on ? 1 : 0), b(intervalMin >> 8), b(intervalMin)};
    }

    /** 34 04: automatic blood-oxygen time window (minutes after midnight). */
    public static byte[] oxygenPeriod(boolean on, int startMin, int endMin) {
        return new byte[]{0x34, 4, b(on ? 1 : 0), b(startMin / 60), b(startMin % 60), b(endMin / 60), b(endMin % 60)};
    }

    public static byte[] syncSteps() { return new byte[]{(byte) 0xB2, (byte) 0xFA}; }
    /** Band sleep algorithm (L4 0x40000 set): 31 01. */
    public static byte[] syncSleep() { return new byte[]{0x31, 0x01}; }
    public static byte[] syncHeartRate() { return new byte[]{(byte) 0xF7, (byte) 0xFA}; }
    public static byte[] syncOxygen() { return new byte[]{0x34, (byte) 0xFA}; }
    /** FD FA: workouts recorded on the watch (syncMultipleSportsModes). */
    public static byte[] syncWorkouts() { return new byte[]{(byte) 0xFD, (byte) 0xFA, 0, 0, 0, 0, 0, 0}; }

    /**
     * D3: move reminder, 12-byte form because L2 0x10 is set (sendSedentaryRemindCommand).
     * Times are hour/minute; the watch has no weekday field.
     */
    public static byte[] moveReminder(boolean on, int intervalMin, int fromH, int fromM, int toH, int toM) {
        return new byte[]{(byte) 0xD3, b(on ? 1 : 0), b(intervalMin), 2, 3, 1, 0, b(fromH), b(fromM), b(toH), b(toM), 0};
    }

    /**
     * D7: do not disturb (sendDisturbToBle). Flags: 8 end-call, 4 no message push, 2 no
     * vibration, 1 no raise-to-wake. Last byte turns the time window on.
     */
    public static byte[] quietHours(boolean on, int fromH, int fromM, int toH, int toM) {
        int flags = on ? (4 | 2) : 0;
        return new byte[]{(byte) 0xD7, b(flags), b(fromH), b(fromM), b(toH), b(toM), b(on ? 1 : 0)};
    }

    /**
     * AB: one alarm, 9-byte form because L6 0x100 is clear (sendToSetAlarmCommand).
     * dayMask bit 0 = Sunday .. bit 6 = Saturday. id 1..3.
     */
    public static byte[] alarm(int id, boolean on, int hour, int minute, int dayMask) {
        int cycle = dayMask == 0 ? 62 : dayMask;
        if (!on) return new byte[]{(byte) 0xAB, b(cycle), b(hour), b(minute), 0, 0, 0, 0, b(id)};
        return new byte[]{(byte) 0xAB, b(cycle), b(hour), b(minute), 2, 5, 2, 0, b(id)};
    }

    /** 29: menstrual cycle (setPhysiologicalPeriod). */
    public static byte[] cycle(boolean on, Calendar lastStart, int periodDays, int cycleDays) {
        int y = lastStart.get(Calendar.YEAR);
        return new byte[]{0x29, b(on ? 1 : 0), b(y >> 8), b(y), b(lastStart.get(Calendar.MONTH) + 1),
                b(lastStart.get(Calendar.DAY_OF_MONTH)), b(periodDays), b(cycleDays)};
    }

    /** AF AA: ask which languages the watch has and which one is on. */
    public static byte[] queryLanguage() { return new byte[]{(byte) 0xAF, (byte) 0xAA}; }
    /** AF AB n: switch the watch language (sendBandLanguageNumber). */
    public static byte[] setLanguage(int n) { return new byte[]{(byte) 0xAF, (byte) 0xAB, b(n)}; }

    /** 38 01 02 00 + 4 random bytes on 34F1: link the watch's call audio (sendBt3AgreementCommand). */
    public static byte[] callAudioLinkBle5(byte[] random4) {
        return new byte[]{0x38, 1, 2, 0, random4[0], random4[1], random4[2], random4[3]};
    }

    /** 38 02: watch call audio (classic Bluetooth) on/off, on 34F1 (openDeviceBt3). */
    public static byte[] callAudioBle5(boolean on) { return new byte[]{0x38, 2, b(on ? 1 : 0)}; }

    /** AD: erase all stored data on the watch (deleteDevicesAllData). Destructive. */
    public static byte[] clearWatchData() { return new byte[]{(byte) 0xAD}; }

    /** C1 04: stop the incoming-call screen (sendOffHookCommand). */
    public static byte[] callEnded() { return new byte[]{(byte) 0xC1, 4}; }

    /** AB ... 0A 02 01: buzz for an incoming call (sendIncallCommand(10)). */
    public static byte[] callBuzz() { return new byte[]{(byte) 0xAB, 0, 0, 0, 1, 10, 2, 1}; }

    /** AB ... 01 00 00: buzz for a message (sendQQWeChatVibrationCommand(1)). */
    public static byte[] messageBuzz() { return new byte[]{(byte) 0xAB, 0, 0, 0, 1, 1, 0, 0}; }

    /** Big-endian UTF-16 (GBUtils.string2unicode drops control characters). */
    static byte[] utf16(String s, int maxBytes) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int i = 0; i < s.length() && o.size() + 2 <= maxBytes; i++) {
            char c = s.charAt(i);
            if (c < 32 || (c >= 127 && c <= 159) || Character.isSurrogate(c)) continue;
            o.write(c >> 8);
            o.write(c);
        }
        return o.toByteArray();
    }

    /**
     * C5 sections for a text popup (sendTextToBle/sendTextSectionKey): payload is
     * [type][len][UTF-16BE, max 240 bytes] cut into 18-byte pieces "C5 idx data", then "C5 FD".
     * The watch acknowledges each piece; the caller waits for that before sending the next.
     */
    public static List<byte[]> textSections(int lead, String text, int type, int maxText) {
        byte[] body = utf16(text == null || text.isEmpty() ? "unknown" : text, maxText);
        byte[] data = new byte[body.length + 2];
        data[0] = b(type);
        data[1] = b(body.length);
        System.arraycopy(body, 0, data, 2, body.length);
        List<byte[]> out = new ArrayList<>();
        for (int off = 0, idx = 0; off < data.length; off += 18, idx++) {
            int n = Math.min(18, data.length - off);
            byte[] p = new byte[n + 2];
            p[0] = b(lead);
            p[1] = b(idx);
            System.arraycopy(data, off, p, 2, n);
            out.add(p);
        }
        out.add(new byte[]{b(lead), (byte) 0xFD});
        return out;
    }

    /** 45 FA: the sender's number, so a quick reply knows where to go (sendPhoneNumber), 34F1. */
    public static byte[] senderNumberBle5(String number) {
        byte[] n = number.getBytes(StandardCharsets.US_ASCII);
        int len = Math.min(n.length, 30);
        byte[] p = new byte[len + 3];
        p[0] = 0x45; p[1] = (byte) 0xFA; p[2] = b(len);
        System.arraycopy(n, 0, p, 3, len);
        return p;
    }

    /** 46 FA total idx len utf8: one quick reply (syncCustomizeSMSSection), 34F1. */
    public static byte[] quickReplyBle5(int total, int idx, String text) {
        byte[] t = text.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(90, t.length);
        byte[] p = new byte[n + 5];
        p[0] = 0x46; p[1] = (byte) 0xFA; p[2] = b(total); p[3] = b(idx); p[4] = b(n);
        System.arraycopy(t, 0, p, 5, n);
        return p;
    }

    public static byte[] quickRepliesDoneBle5() { return new byte[]{0x46, (byte) 0xFD, 0}; }
    public static byte[] quickRepliesClearBle5() { return new byte[]{0x46, (byte) 0xFB}; }

    /** 52 01 / 52 00: allow replying from the watch (sendEndCallReplySmsStatus). */
    public static byte[] repliesAllowed(boolean on) { return new byte[]{0x52, b(on ? 1 : 0)}; }

    /** 52 FE / 52 FF: tell the watch whether its reply was sent (sendSmsWhetherSuccessfully). */
    public static byte[] replyResult(boolean ok) { return new byte[]{0x52, (byte) (ok ? 0xFE : 0xFF)}; }

    /**
     * 37 FA n / 37 FB sections / 37 FC FD: contacts on watch (sendSectionContacts), 34F1.
     * Each record is 37 FB phoneLen nameLen phone(ASCII, max 15) name(UTF-16BE, max 10 chars);
     * L6 0x80000 is clear so records are counted as 39 bytes, (244 - 3) / 39 = 6 per section.
     */
    public static List<byte[]> contactsBle5(List<String[]> contacts, int mtuPayload) {
        List<byte[]> out = new ArrayList<>();
        out.add(new byte[]{0x37, (byte) 0xFA, b(contacts.size())});
        int per = Math.max(1, (mtuPayload - 3) / 39);
        for (int i = 0; i < contacts.size(); i += per) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            int n = Math.min(per, contacts.size() - i);
            o.write(0x37); o.write(0xFB); o.write(n);
            for (int k = i; k < i + n; k++) {
                String phone = contacts.get(k)[1].replace(" ", "");
                if (phone.length() > 15) phone = phone.substring(0, 15);
                String name = contacts.get(k)[0];
                if (name.length() > 10) name = name.substring(0, 10);
                byte[] pb = phone.getBytes(StandardCharsets.US_ASCII), nb = utf16(name, 20);
                o.write(0x37); o.write(0xFB); o.write(pb.length); o.write(nb.length);
                o.write(pb, 0, pb.length);
                o.write(nb, 0, nb.length);
            }
            out.add(o.toByteArray());
        }
        out.add(new byte[]{0x37, (byte) 0xFC, (byte) 0xFD});
        return out;
    }

    /** One day of weather for the watch. Temperatures in whole degrees Celsius. */
    public static final class Day {
        public int code, max, min;
        public Day(int code, int max, int min) { this.code = code; this.max = max; this.min = min; }
    }

    static int signed(int t) { return t < 0 ? (Math.abs(t) | 0x80) : t; }

    /**
     * CB 01/02/03: 7-day weather (syncWeatherToBLEForXiaoYang), on 34F1 because L6 0x800 is set.
     * days[0] is today; current is today's temperature now; humidity percent; uv index.
     */
    public static List<byte[]> weatherBle5(Day[] d, int current, int humidity, int uv) {
        List<byte[]> out = new ArrayList<>();
        byte[] p1 = new byte[21];
        p1[0] = (byte) 0xCB; p1[1] = 1; p1[2] = b(d[0].code); p1[3] = 0;
        p1[4] = b(signed(current)); p1[5] = b(signed(d[0].max)); p1[6] = b(signed(d[0].min));
        p1[19] = b(humidity); p1[20] = b(uv);
        out.add(p1);
        byte[] p2 = new byte[18];
        p2[0] = (byte) 0xCB; p2[1] = 2;
        for (int i = 0; i < 4; i++) {
            p2[2 + i * 4] = b(d[1 + i].code);
            p2[4 + i * 4] = b(signed(d[1 + i].max));
            p2[5 + i * 4] = b(signed(d[1 + i].min));
        }
        out.add(p2);
        out.add(new byte[]{(byte) 0xCB, 3, b(d[5].code), 0, b(signed(d[5].max)), b(signed(d[5].min)),
                b(d[6].code), 0, b(signed(d[6].max)), b(signed(d[6].min))});
        return out;
    }

    /**
     * FD 11/00: start or stop a workout on the watch from the phone (setMultipleSportsModes).
     * code is GloryFit's sport index + 1 (1 outdoor run, 2 cycling, 9 walking).
     */
    public static byte[] workoutStart(int code) { return new byte[]{(byte) 0xFD, 0x11, b(code), 1}; }
    public static byte[] workoutStop(int code) { return new byte[]{(byte) 0xFD, 0, b(code), 1}; }

    /**
     * FD 22/33/44: pause, continue, end a phone GPS workout with its totals (setMultipleSportsModes2).
     */
    public static byte[] workoutState(int state, int code, int seconds, int kcal, float km, int paceSecPerKm) {
        int kmInt = (int) km, kmFrac = Math.round((km - kmInt) * 100) % 100;
        int pm = paceSecPerKm / 60, ps = paceSecPerKm % 60;
        if (paceSecPerKm > 5999 || paceSecPerKm <= 0) { pm = 0; ps = 0; }
        return new byte[]{(byte) 0xFD, b(state), b(code), 1, b(seconds / 3600), b((seconds % 3600) / 60), b(seconds % 60),
                b(kcal >> 8), b(kcal), b(kmInt), b(kmFrac), b(pm), b(ps)};
    }

    /** FD 01 01: phone has a GPS fix (sendAppLocationStatus), 34F1. */
    public static byte[] gpsReadyBle5() { return new byte[]{(byte) 0xFD, 1, 1}; }

    /** FD 02: GPS point for the watch (sendAppLocationData), 34F1. */
    public static byte[] gpsPointBle5(double lat, double lon, float speedMs, int pointM, int totalM) {
        int la = (int) (lat * 1e6), lo = (int) (lon * 1e6), sp = (int) (speedMs * 100);
        return new byte[]{(byte) 0xFD, 2, b(la >> 24), b(la >> 16), b(la >> 8), b(la),
                b(lo >> 24), b(lo >> 16), b(lo >> 8), b(lo), b(sp >> 8), b(sp),
                b(pointM >> 24), b(pointM >> 16), b(pointM >> 8), b(pointM),
                b(totalM >> 24), b(totalM >> 16), b(totalM >> 8), b(totalM)};
    }

    /** GloryFit sportManagementType names; index = sport code - 1. */
    public static final String[] SPORTS = {"Outdoor run", "Outdoor cycle", "Jump rope", "Swimming", "Badminton",
            "Table tennis", "Tennis", "Mountain hike", "Walking", "Basketball", "Football", "Baseball", "Volleyball",
            "Cricket", "Rugby", "Hockey", "Dancing", "Spinning", "Yoga", "Sit-ups", "Indoor run", "Gymnastics",
            "Rowing", "Jumping jacks", "Free training", "Indoor walk", "Indoor run", "Strength training",
            "Step training", "Horse riding", "Elliptical", "Tai chi", "Shuttlecock", "Boxing", "Outdoor walk",
            "Trail running", "Skiing", "Ice hockey", "Taekwondo", "VO2max test", "Rowing machine", "Air walker",
            "Hiking", "Athletics", "Waist training", "Karate", "Cooldown", "Cross training", "Pilates", "CrossFit",
            "Functional training", "Physical training", "Archery", "Flexibility", "Mixed cardio", "Latin dance",
            "Street dance", "Kickboxing", "Barre", "Australian football", "Martial arts", "Stairs", "Handball",
            "Bowling", "Racquetball", "Curling", "Hunting", "Snowboarding", "Play", "American football",
            "Hand cycling", "Fishing", "Disc sports", "Rugby", "Golf", "Folk dance", "Downhill skiing", "Snow sports",
            "Mind and body", "Core training", "Skating", "Fitness gaming", "Aerobics", "Group training", "Kendo",
            "Lacrosse", "Rolling", "Wrestling", "Fencing", "Softball", "Single bar", "Parallel bars", "Roller skating",
            "Hula hoop", "Darts", "Pickleball", "HIIT", "Shooting", "Judo", "Trampoline", "Skateboarding",
            "Hoverboard", "Blading", "Parkour", "Diving", "Surfing", "Snorkeling", "Pull-ups", "Push-ups", "Plank",
            "Rock climbing", "High jump", "Bungee jumping", "Long jump", "Marathon"};

    public static String sportName(int code) {
        return code >= 1 && code <= SPORTS.length ? SPORTS[code - 1] : "Workout";
    }

    /** Watch language numbers (BandLanguageSettingActivity); bit (n-1) of the AF AA display masks. */
    public static final String[][] LANGUAGES = {{"1", "简体中文"}, {"2", "English"}, {"3", "한국어"}, {"4", "日本語"},
            {"5", "Deutsch"}, {"6", "Español"}, {"7", "Français"}, {"8", "Italiano"}, {"9", "Português"},
            {"10", "العربية"}, {"12", "हिन्दी"}, {"13", "Polski"}, {"14", "Русский"}, {"15", "Nederlands"},
            {"16", "Türkçe"}, {"17", "বাংলা"}, {"18", "اردو"}, {"19", "Indonesia"}, {"20", "ਪੰਜਾਬੀ"}, {"21", "ไทย"},
            {"22", "Čeština"}, {"23", "繁體中文"}, {"24", "עברית"}, {"25", "Slovenčina"}, {"26", "Magyar"},
            {"27", "Română"}, {"28", "Беларуская"}, {"29", "Shqip"}, {"31", "Amharic"}, {"32", "Azərbaycan"},
            {"33", "Gaeilge"}, {"34", "Occitan"}, {"35", "Oriya"}, {"36", "Oromo"}, {"37", "Euskara"},
            {"38", "Български"}, {"39", "Bosanski"}, {"40", "فارسی"}, {"41", "Pilipino"}, {"42", "ខ្មែរ"},
            {"43", "ગુજરાતી"}, {"44", "Қазақ"}, {"45", "Kreyòl ayisyen"}, {"46", "Hausa"}, {"48", "Català"},
            {"49", "ಕನ್ನಡ"}, {"50", "Xhosa"}, {"51", "Hrvatski"}, {"52", "Runa simi"}, {"53", "Kurdî"},
            {"54", "Latina"}, {"55", "Lëtzebuergesch"}, {"56", "Kinyarwanda"}, {"57", "Dansk"}, {"59", "Rumantsch"},
            {"60", "Malagasy"}, {"61", "मराठी"}, {"63", "മലയാളം"}, {"64", "Bahasa Melayu"}, {"65", "Македонски"},
            {"66", "मैथिली"}, {"67", "Gaelg"}, {"68", "မြန်မာ"}, {"69", "Napulitano"}, {"70", "नेपाली"},
            {"71", "Norsk"}, {"72", "Pashto"}, {"73", "Temmotofa"}, {"74", "Svenska"}, {"75", "Srpski"},
            {"76", "සිංහල"}, {"77", "Slovenščina"}, {"78", "Kiswahili"}, {"79", "Sinugboanon"}, {"80", "Soomaali"},
            {"81", "Gagana Samoa"}, {"82", "Tagalog"}, {"83", "Тоҷикӣ"}, {"84", "తెలుగు"}, {"85", "தமிழ்"},
            {"86", "Tigrinya"}, {"87", "Türkmen"}, {"88", "Українська"}, {"89", "Ўзбек"}, {"90", "Ελληνικά"},
            {"91", "سنڌي"}, {"93", "Syriac"}, {"94", "Igbo"}, {"95", "Ilokano"}, {"96", "Bahasa Indonesia"},
            {"97", "Yorùbá"}, {"98", "Suomi"}, {"99", "Tiếng Việt"}, {"100", "isiZulu"}, {"101", "Монгол"},
            {"109", "Latviešu"}, {"110", "Lietuvių"}, {"111", "Eesti"}};

    public static String languageName(int n) {
        for (String[] l : LANGUAGES) if (Integer.parseInt(l[0]) == n) return l[1];
        return "Language " + n;
    }

    /** Weather icon codes the watch draws (weatherConditionCode). */
    public static final int WX_SUNNY = 1, WX_PARTLY = 2, WX_CLOUDY = 3, WX_SHOWERS = 4, WX_STORM = 5, WX_SLEET = 6,
            WX_LIGHT_RAIN = 7, WX_RAIN = 8, WX_SNOW = 9, WX_DUST = 10, WX_FOG = 11, WX_WIND = 12;

    public static String toHex(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }
}
