package com.paul.t60;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Pure, zero-cloud protocol encoder & decoder for Paul's T60 smartwatch (RH281LWJV007108).
 * Decoded from GloryFit v4.0.12 classes6.dex and verified against live watch 60:1A:B7:00:AE:67.
 */
public final class T60Protocol {

    public static final String DEFAULT_MAC = "60:1A:B7:00:AE:67";

    public static final UUID UUID_SERVICE_MAIN = UUID.fromString("000055ff-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHAR_WRITE   = UUID.fromString("000033f1-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHAR_NOTIFY  = UUID.fromString("000033f2-0000-1000-8000-00805f9b34fb");

    public static final UUID UUID_SERVICE_EXT      = UUID.fromString("000056ff-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHAR_EXT_WRITE   = UUID.fromString("000034f1-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHAR_EXT_NOTIFY  = UUID.fromString("000034f2-0000-1000-8000-00805f9b34fb");

    public static final UUID UUID_SERVICE_BATTERY  = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CHAR_BATTERY     = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CCCD             = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private T60Protocol() {}

    /** 0xA3: Sync current local time and date to watch. */
    public static byte[] buildSyncTime(Calendar cal) {
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        return new byte[] {
            (byte) 0xA3,
            (byte) ((year >> 8) & 0xFF),
            (byte) (year & 0xFF),
            (byte) (month & 0xFF),
            (byte) (day & 0xFF),
            (byte) (hour & 0xFF),
            (byte) (minute & 0xFF),
            (byte) (second & 0xFF)
        };
    }

    /**
     * 0xA0: Set unit system and clock format (sendUnitToBLE).
     * Byte 1: 0x01 = Metric (km/kg), 0x02 = Imperial (miles/lbs)
     * Byte 2: 0x01 = 24-hour, 0x02 = 12-hour (AM/PM)
     */
    public static byte[] buildUnitAndTimeFormat(boolean imperial, boolean twelveHour) {
        return new byte[] {
            (byte) 0xA0,
            (byte) (imperial ? 0x02 : 0x01),
            (byte) (twelveHour ? 0x02 : 0x01)
        };
    }

    /**
     * 0xA9: Set user profile, step target, English language (0x02), and Fahrenheit (0x02) / Celsius (0x01).
     */
    public static byte[] buildProfileAndTempUnit(int stepGoal, boolean fahrenheit, boolean raiseToWake) {
        byte[] pkt = new byte[19];
        pkt[0] = (byte) 0xA9;
        pkt[1] = 0x00;
        pkt[2] = 75; // stride length cm
        pkt[3] = 0;
        pkt[4] = 80; // weight kg
        pkt[5] = (byte) (raiseToWake ? 5 : 0); // screen-on seconds
        pkt[6] = (byte) ((stepGoal >> 24) & 0xFF);
        pkt[7] = (byte) ((stepGoal >> 16) & 0xFF);
        pkt[8] = (byte) ((stepGoal >> 8) & 0xFF);
        pkt[9] = (byte) (stepGoal & 0xFF);
        pkt[10] = (byte) (raiseToWake ? 0x01 : 0x00);
        pkt[11] = (byte) 0xFF;
        pkt[12] = 0x01; // HR sleep assist
        pkt[13] = 35;   // age
        pkt[14] = 0x01; // male
        pkt[15] = 0x00;
        pkt[16] = 0x02; // English language
        pkt[17] = (byte) (fahrenheit ? 0x02 : 0x01); // 0x02 = Fahrenheit, 0x01 = Celsius
        pkt[18] = 0x00;
        return pkt;
    }

    /** 0xA1: Query watch internal firmware version (returns RH281LWJV007108). */
    public static byte[] buildReadVersion() {
        return new byte[] { (byte) 0xA1 };
    }

    /** 0xA2: Query watch battery level (returns A2 <pct>). */
    public static byte[] buildReadBattery() {
        return new byte[] { (byte) 0xA2 };
    }

    /** 0x26 0x01: Query display geometry and dial slot configuration (returns 320x380, 1MB). */
    public static byte[] buildReadDialConfig() {
        return new byte[] { 0x26, 0x01 };
    }

    /** 0x26 0x09: Query all watchface slot info. */
    public static byte[] buildQueryWatchFaces() {
        return new byte[] { 0x26, 0x09 };
    }

    /** 0x26 0x0D: Switch active watchface index (0..N). */
    public static byte[] buildSwitchWatchFace(int index, int dialId) {
        return new byte[] {
            0x26, 0x0D,
            (byte) (index & 0xFF),
            (byte) ((dialId >> 24) & 0xFF),
            (byte) ((dialId >> 16) & 0xFF),
            (byte) ((dialId >> 8) & 0xFF),
            (byte) (dialId & 0xFF)
        };
    }

    /** 0x26 0x0A: Query custom watchface background parameters (ImageWatchFaceRk). */
    public static byte[] buildQueryCustomBgParams() {
        return new byte[] { 0x26, 0x0A };
    }

    /**
     * 0x26 0x0B: Configure custom watchface background (styleIndex, positionIndex: 1=top, 2=bottom,
     * pure black background with Day of Week, Date, Time, and Battery overlay).
     */
    public static byte[] buildCustomBgConfig(int styleIndex, int positionIndex, int previewSize, int bgSize, int crc32) {
        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.put((byte) 0x26);
        buf.put((byte) 0x0B);
        buf.put((byte) (styleIndex & 0xFF));
        buf.put((byte) 0x00);
        buf.putInt(positionIndex);
        buf.putInt(previewSize);
        buf.putInt(bgSize);
        buf.putInt(crc32);
        return buf.array();
    }

    /** 0xAB: Vibrate / ring watch (Find My Watch). */
    public static byte[] buildFindWatch(boolean start) {
        return new byte[] {
            (byte) 0xAB, 0x00, 0x00, 0x00,
            (byte) 0xAB, (byte) (start ? 0x01 : 0x00),
            0x07, (byte) 0xAB
        };
    }

    /** 0xD1 0x0A: Enable Find-Phone button on watch screen. */
    public static byte[] buildEnableFindPhone(boolean enable) {
        return new byte[] { (byte) 0xD1, 0x0A, (byte) (enable ? 0x01 : 0x00) };
    }

    /** 0xD1 0x0C: Report phone music playback state to watch (1 = Playing, 2 = Paused). */
    public static byte[] buildMusicPlayState(boolean playing) {
        return new byte[] { (byte) 0xD1, 0x0C, (byte) (playing ? 0x01 : 0x02) };
    }

    /** 0xD1 0x0D / 0x0E: Report current phone media volume (0..100%) to watch. */
    public static byte[] buildMusicVolumeState(boolean isVolumeDownAck, int volumePercent) {
        int clamped = Math.max(0, Math.min(100, volumePercent));
        return new byte[] {
            (byte) 0xD1,
            (byte) (isVolumeDownAck ? 0x0E : 0x0D),
            (byte) (clamped & 0xFF)
        };
    }

    /** 0xFC: Enable/disable Raise-to-Wake (Turn Wrist to Light Screen). */
    public static byte[] buildRaiseToWake(boolean enable) {
        return new byte[] { (byte) 0xFC, (byte) (enable ? 0x01 : 0x00) };
    }

    /** 0xE5: Start (0x11) or stop (0x00) live heart-rate sensor reading. */
    public static byte[] buildHeartRateTest(boolean start) {
        return new byte[] { (byte) 0xE5, (byte) (start ? 0x11 : 0x00) };
    }

    /** 0xAA: Query current step/sleep status. */
    public static byte[] buildQueryStepStatus() {
        return new byte[] { (byte) 0xAA };
    }

    /** 0xB2 0xFA: Sync all step history from watch. */
    public static byte[] buildSyncSteps() {
        return new byte[] { (byte) 0xB2, (byte) 0xFA };
    }

    /** 0x31 0x01: Sync all sleep history from watch (band sleep algorithm). */
    public static byte[] buildSyncSleep() {
        return new byte[] { 0x31, 0x01 };
    }

    /** 0x38 0x02 (written to 34F1): Enable/disable Classic BT 3.0 wrist speakerphone. */
    public static byte[] buildBt3AudioSwitch(boolean enable) {
        return new byte[] { 0x38, 0x02, (byte) (enable ? 0x01 : 0x00) };
    }

    /**
     * Set wrist alarm (sendToSetAlarmCommand, 10-byte format on 33F1).
     * alarmId: 1..3, enabled: true/false, hour: 0..23, minute: 0..59, cycleMask: 0x7F for daily.
     */
    public static byte[] buildAlarmCommand(int alarmId, boolean enabled, int hour, int minute, int cycleMask) {
        return new byte[] {
            (byte) 0xAB,
            (byte) (cycleMask & 0xFF),
            (byte) (hour & 0xFF),
            (byte) (minute & 0xFF),
            0x02,
            0x05, // shake count
            0x02,
            0x00,
            (byte) (alarmId & 0xFF),
            (byte) (enabled ? 0x01 : 0x00)
        };
    }

    /**
     * Build notification packets (0xAB header + 0xC5 UTF-8 chunks + 0xC5 0xFD end marker).
     * msgType: 1 = SMS/Text, 2 = WeChat/Chat, 3 = QQ/App, 6 = WhatsApp/Messenger, 7 = Email, 14 = Other App
     */
    public static List<byte[]> buildNotificationPackets(String title, String body, int msgType) {
        List<byte[]> packets = new ArrayList<>();
        packets.add(new byte[] {
            (byte) 0xAB, 0x00, 0x00, 0x00, 0x01, (byte) (msgType & 0xFF), 0x00, 0x00
        });
        String combined = (title != null && !title.isEmpty())
                ? (title + ": " + (body != null ? body : ""))
                : (body != null ? body : "");
        byte[] raw = combined.getBytes(StandardCharsets.UTF_8);
        int limit = Math.min(raw.length, 180);
        int chunkIndex = 0;
        for (int offset = 0; offset < limit; offset += 18) {
            int len = Math.min(18, limit - offset);
            byte[] chunk = new byte[2 + len];
            chunk[0] = (byte) 0xC5;
            chunk[1] = (byte) (chunkIndex & 0xFF);
            System.arraycopy(raw, offset, chunk, 2, len);
            packets.add(chunk);
            chunkIndex++;
        }
        packets.add(new byte[] { (byte) 0xC5, (byte) 0xFD });
        return packets;
    }

    public static String toHex(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }
}
