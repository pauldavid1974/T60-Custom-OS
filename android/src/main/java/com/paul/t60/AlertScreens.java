package com.paul.t60;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Alerts tab: what buzzes the wrist, quiet hours, move reminder, quick replies. */
final class AlertScreens {

    private final MainActivity a;
    private final Ui ui;
    private final T60Store store;

    AlertScreens(MainActivity a) { this.a = a; ui = a.ui; store = a.store; }

    View build(String name, LinearLayout page) {
        switch (name) {
            case "Alerts": return alerts(page);
            case "MoveReminder": return moveReminder(page);
            case "QuietHours": return quietHours(page);
            case "QuickReplies": return quickReplies(page);
            case "EditReply": return editReply(page);
        }
        return null;
    }

    private View alerts(LinearLayout page) {
        LinearLayout b = a.tabHeader(page, null, "Alerts");
        b.addView(ui.text("What buzzes your wrist", 15, Ui.SUB, false));
        if (!a.hasNotificationAccess()) {
            ui.add(b, ui.button("Allow notification access so app alerts reach the watch", true,
                    v -> a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))), 12);
        }
        boolean callPerm = a.has(Manifest.permission.READ_PHONE_STATE) && a.has(Manifest.permission.READ_CALL_LOG);
        boolean smsPerm = a.has(Manifest.permission.RECEIVE_SMS);
        ui.add(b, ui.group(
                ui.switchRow("Phone calls", callPerm ? null : "Needs phone permission", store.isForwardCalls(), on -> store.set("fwd_calls", on)),
                ui.switchRow("Text messages", smsPerm ? null : "Needs SMS permission", store.isForwardSms(), on -> store.set("fwd_sms", on))), 12);
        if (!callPerm || !smsPerm) ui.add(b, ui.button("Allow phone and SMS permissions", false, v -> a.requestPermissionsIfNeeded()), 8);

        Set<String> seen = store.getSeenApps();
        int onCount = 0;
        for (String p : seen) if (!store.isAppMuted(p)) onCount++;
        List<View> rows = new ArrayList<>();
        LinearLayout head = ui.switchRow("Apps", seen.isEmpty() ? "Apps appear after their first notification" : onCount + " of " + seen.size() + " on",
                store.isForwardApps(), on -> store.set("fwd_apps", on));
        rows.add(head);
        PackageManager pm = a.getPackageManager();
        for (String pkg : seen) {
            String label = pkg;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
            LinearLayout r = ui.switchRow(label, null, !store.isAppMuted(pkg), on -> store.setAppMuted(pkg, !on));
            TextView badge = ui.text(label.substring(0, 1).toUpperCase(), 14, Ui.TEXT, true);
            badge.setGravity(android.view.Gravity.CENTER);
            badge.setBackground(Ui.round(Ui.TILE, Ui.TILE_BORDER, ui.dp(10), ui.dp(1)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32));
            lp.rightMargin = ui.dp(12);
            r.addView(badge, 0, lp);
            rows.add(r);
        }
        ui.add(b, ui.group(rows.toArray(new View[0])), 12);

        String quiet = store.b("quiet_on", false) ? store.clock(store.i("quiet_from", 1320)) + " – " + store.clock(store.i("quiet_to", 420)) : "Off";
        String move = store.b("move_on", false) ? everyText(store.i("move_every", 60)) + ", " + store.clock(store.i("move_from", 540)) + "–" + store.clock(store.i("move_to", 1020)) : "Off";
        ui.add(b, ui.group(
                ui.navRow("Quiet hours", null, quiet, v -> a.go("QuietHours")),
                ui.navRow("Move reminder", null, move, v -> a.go("MoveReminder")),
                ui.navRow("Quick replies to texts", null, store.quickReplies().size() + " replies", v -> a.go("QuickReplies"))), 12);
        ui.add(b, ui.button("Send a test alert to the watch", false,
                v -> a.withWatch(s -> s.pushText(T60Protocol.MSG_OTHER, "T60: test alert from your phone", null, null))), 12);
        return null;
    }

    static String everyText(int min) { return min == 30 ? "Every 30 min" : min == 60 ? "Every hour" : "Every " + min + " min"; }

    private View moveReminder(LinearLayout page) {
        LinearLayout b = a.detail(page, "Move reminder");
        b.addView(ui.note("Your watch buzzes when you have been sitting too long."));
        ui.add(b, ui.group(ui.switchRow("Remind me to move", null, store.b("move_on", false), on -> store.set("move_on", on))), 12);
        int[] opts = {30, 60, 90};
        int cur = store.i("move_every", 60);
        int sel = cur == 30 ? 0 : cur == 90 ? 2 : 1;
        ui.add(b, ui.sectionLabel("After sitting for"), 14);
        ui.add(b, ui.chips(new String[]{"30 min", "1 hour", "90 min"}, sel, k -> { store.set("move_every", opts[k]); a.render(); }), 6);
        ui.add(b, ui.sectionLabel("Only between"), 14);
        ui.add(b, ui.group(a.timeRow("From", "move_from", 540), a.timeRow("Until", "move_to", 1020)), 6);
        int[] days = {store.i("move_days", 0x3E)};
        ui.add(b, ui.dayPicker(days), 14);
        ui.add(b, ui.note("The watch itself has no day setting, so on days you leave off the app turns the reminder off at midnight."), 8);
        ui.add(b, ui.button("Save to watch", true, v -> {
            store.set("move_days", days[0]);
            a.withWatch(T60BleService::sendMoveReminder);
            a.back();
        }), 16);
        return null;
    }

    private View quietHours(LinearLayout page) {
        LinearLayout b = a.detail(page, "Quiet hours");
        b.addView(ui.note("During these hours the watch stays still: no buzzing for calls, texts or app alerts."));
        ui.add(b, ui.group(ui.switchRow("Quiet hours", null, store.b("quiet_on", false), on -> store.set("quiet_on", on))), 12);
        ui.add(b, ui.group(a.timeRow("From", "quiet_from", 1320), a.timeRow("Until", "quiet_to", 420)), 12);
        ui.add(b, ui.note("Your alarms still go off."), 10);
        ui.add(b, ui.button("Save to watch", true, v -> { a.withWatch(T60BleService::sendQuietHours); a.back(); }), 16);
        return null;
    }

    private View quickReplies(LinearLayout page) {
        LinearLayout b = a.detail(page, "Quick replies");
        b.addView(ui.note("When a text arrives, pick one of these on the watch and your phone sends it."));
        ui.add(b, ui.group(ui.switchRow("Allow replies from watch", null, store.b("replies_on", true), on -> {
            store.set("replies_on", on);
            a.withWatch(T60BleService::sendQuickReplies);
        })), 12);
        ui.add(b, ui.sectionLabel("Your replies"), 14);
        List<String> r = store.quickReplies();
        List<View> rows = new ArrayList<>();
        for (int k = 0; k < r.size(); k++) {
            int idx = k;
            rows.add(ui.navRow(r.get(k), null, null, v -> { a.args.putInt("reply", idx); a.go("EditReply"); }));
        }
        if (!rows.isEmpty()) ui.add(b, ui.group(rows.toArray(new View[0])), 6);
        if (r.size() < 10) {
            TextView add = ui.text("+ Add a reply", 15, Ui.ACCENT, true);
            add.setPadding(ui.dp(4), ui.dp(14), 0, ui.dp(14));
            add.setOnClickListener(v -> { a.args.putInt("reply", -1); a.go("EditReply"); });
            b.addView(add);
        }
        ui.add(b, ui.note(a.has(Manifest.permission.SEND_SMS) ? "The reply goes from your phone number as a normal text."
                : "Needs permission to send texts. The reply goes from your phone number as a normal text."), 8);
        if (!a.has(Manifest.permission.SEND_SMS)) ui.add(b, ui.button("Allow sending texts", false, v -> a.requestPermissionsIfNeeded()), 8);
        return null;
    }

    private View editReply(LinearLayout page) {
        int idx = a.args.getInt("reply", -1);
        List<String> r = store.quickReplies();
        LinearLayout b = a.detail(page, idx < 0 ? "New reply" : "Edit reply");
        b.addView(ui.sectionLabel("What the reply says"));
        EditText e = ui.input(idx >= 0 && idx < r.size() ? r.get(idx) : "", "On my way.", false);
        ui.add(b, e, 6);
        ui.add(b, ui.note("Keep it short; the watch shows about 40 letters."), 8);
        ui.add(b, ui.button("Save", true, v -> {
            String t = e.getText().toString().trim();
            if (t.isEmpty()) return;
            if (idx >= 0 && idx < r.size()) r.set(idx, t); else r.add(t);
            store.setQuickReplies(r);
            a.withWatch(T60BleService::sendQuickReplies);
            a.back();
        }), 16);
        if (idx >= 0) {
            TextView del = ui.text("Delete this reply", 15, Ui.DANGER, true);
            del.setPadding(ui.dp(4), ui.dp(18), 0, ui.dp(12));
            del.setOnClickListener(v -> {
                r.remove(idx);
                store.setQuickReplies(r);
                a.withWatch(T60BleService::sendQuickReplies);
                a.back();
            });
            b.addView(del);
        }
        return null;
    }
}
