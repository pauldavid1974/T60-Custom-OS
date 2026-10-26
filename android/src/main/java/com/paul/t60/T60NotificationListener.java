package com.paul.t60;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Locale;

/**
 * Forwards incoming phone calls, SMS, and user-enabled app notifications to Paul's T60 wrist screen.
 */
public class T60NotificationListener extends NotificationListenerService {

    private long lastPushTime = 0;
    private String lastSignature = "";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.isOngoing()) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName()) || pkg.equals("android") || pkg.equals("com.android.systemui")) {
            return;
        }

        T60Store store = T60Store.get(this);
        store.markAppSeen(pkg);
        if (store.isAppMuted(pkg)) return;
        T60BleService svc = T60BleService.getInstance();
        if (svc == null) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        Bundle ex = n.extras;
        CharSequence titleCs = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = ex.getCharSequence(Notification.EXTRA_TEXT);
        String title = titleCs != null ? titleCs.toString().trim() : "";
        String text = textCs != null ? textCs.toString().trim() : "";
        if (title.isEmpty() && text.isEmpty()) return;

        String sig = pkg + "|" + title + "|" + text;
        long now = System.currentTimeMillis();
        if (sig.equals(lastSignature) && (now - lastPushTime) < 3000) {
            return;
        }
        lastSignature = sig;
        lastPushTime = now;

        String pLow = pkg.toLowerCase(Locale.US);
        int msgType = 14; // Generic app notification icon
        if (pLow.contains("mms") || pLow.contains("messaging") || pLow.contains("sms")) {
            if (!store.isForwardSms()) return;
            msgType = 1; // SMS icon
        } else if (pLow.contains("dialer") || pLow.contains("telecom") || pLow.contains("incall")) {
            if (!store.isForwardCalls()) return;
            msgType = 1;
        } else {
            if (!store.isForwardApps()) return;
        }

        svc.pushWristNotification(title, text, msgType);
    }
}
