package com.paul.t60;

import android.app.Notification;
import android.os.Bundle;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashMap;
import java.util.Map;

/**
 * Forwards app notifications to the watch. Texts and calls are handled by T60BleService from the
 * phone's own SMS and call events (they carry the sender's number for quick replies), so the
 * messaging and dialer apps are skipped here. Also gives the service access to media sessions.
 */
public class T60NotificationListener extends NotificationListenerService {

    /** GloryFit's getAppType: the watch shows that app's icon. Everything else is type 4. */
    private static final Map<String, Integer> TYPES = new HashMap<>();
    static {
        String[][] t = {{"com.tencent.mobileqq", "1"}, {"com.tencent.mm", "2"}, {"com.facebook.katana", "5"},
                {"com.twitter.android", "6"}, {"com.whatsapp", "7"}, {"com.skype.raider", "8"}, {"com.facebook.orca", "9"},
                {"com.google.android.talk", "10"}, {"jp.naver.line.android", "11"}, {"com.linkedin.android", "12"},
                {"com.instagram.android", "13"}, {"com.viber.voip", "14"}, {"com.kakao.talk", "15"},
                {"com.vkontakte.android", "16"}, {"com.snapchat.android", "17"}, {"com.google.android.gm", "19"},
                {"com.pinterest", "22"}, {"com.google.android.youtube", "23"}, {"org.telegram.messenger", "24"},
                {"com.truecaller", "25"}, {"com.microsoft.teams", "29"}, {"com.whatsapp.w4b", "52"}};
        for (String[] p : t) TYPES.put(p[0], Integer.parseInt(p[1]));
    }

    private String lastSig = "";
    private long lastTime;

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.isOngoing()) return;
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        String pkg = sbn.getPackageName();
        if (pkg.equals(getPackageName()) || pkg.equals("android") || pkg.equals("com.android.systemui")) return;
        if (pkg.equals(Telephony.Sms.getDefaultSmsPackage(this)) || pkg.contains("dialer") || pkg.contains("incallui")
                || pkg.contains("telecom") || Notification.CATEGORY_CALL.equals(n.category)) return;

        T60Store store = T60Store.get(this);
        store.markAppSeen(pkg);
        if (!store.isForwardApps() || store.isAppMuted(pkg) || store.inQuietHours()) return;
        T60BleService svc = T60BleService.getInstance();
        if (svc == null) return;

        Bundle ex = n.extras;
        CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE), text = ex.getCharSequence(Notification.EXTRA_TEXT);
        String t = title == null ? "" : title.toString().trim(), b = text == null ? "" : text.toString().trim();
        if (t.isEmpty() && b.isEmpty()) return;
        String sig = pkg + "|" + t + "|" + b;
        long now = System.currentTimeMillis();
        if (sig.equals(lastSig) && now - lastTime < 5000) return;
        lastSig = sig;
        lastTime = now;
        Integer type = TYPES.get(pkg);
        svc.pushText(type != null ? type : T60Protocol.MSG_OTHER, t.isEmpty() ? b : b.isEmpty() ? t : t + ": " + b, null, null);
    }
}
