package com.paul.t60;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** View helpers matching the approved design (dark cards, blue accent, Manrope-like bold type). */
final class Ui {
    static final int BG = 0xFF0A0C0F, CARD = 0xFF14181E, BORDER = 0xFF232A33, TILE = 0xFF1A2028,
            TILE_BORDER = 0xFF2B3440, ACCENT = 0xFF5AB0FF, HEART = 0xFFFF8A5B, TEXT = 0xFFF2F4F7,
            SUB = 0xFF9AA4B2, DIM = 0xFF8792A2, BODY = 0xFFC9D1DB, GREEN = 0xFF3DDC84, OFF = 0xFF3A4452,
            NAV = 0xFF0F1216, PURPLE = 0xFFB18CFF, DANGER = 0xFFFF6B6B;

    final Context c;
    Ui(Context c) { this.c = c; }

    int dp(float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int fill, int stroke, float radiusPx, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    TextView mono(String s, float sp) {
        TextView t = text(s, sp, TEXT, true);
        t.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        return t;
    }

    LinearLayout col() { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    /** Adds v to parent with a top gap in dp. */
    View add(LinearLayout parent, View v, int gapDp) {
        LinearLayout.LayoutParams lp = v.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) v.getLayoutParams() : fill();
        lp.topMargin = dp(gapDp);
        parent.addView(v, lp);
        return v;
    }

    LinearLayout card() {
        LinearLayout l = col();
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        l.setBackground(round(CARD, BORDER, dp(20), dp(1)));
        return l;
    }

    /** Card heading row: grey title on the left, optional blue link on the right. */
    LinearLayout cardHead(String title, String link, View.OnClickListener onLink) {
        LinearLayout r = row();
        r.addView(text(title, 14, SUB, true), weight(1));
        if (link != null) {
            TextView l = text(link, 13, ACCENT, true);
            l.setPadding(dp(8), dp(4), 0, dp(4));
            l.setOnClickListener(onLink);
            r.addView(l);
        }
        return r;
    }

    TextView sectionLabel(String s) {
        TextView t = text(s.toUpperCase(), 12, DIM, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(10), 0, dp(2));
        return t;
    }

    TextView note(String s) {
        TextView t = text(s, 13, DIM, false);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    TextView button(String s, boolean primary, View.OnClickListener onClick) {
        TextView b = text(s, 15, primary ? 0xFF06121F : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(50));
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setBackground(primary ? round(ACCENT, 0, dp(14), 0) : round(TILE, TILE_BORDER, dp(14), dp(1)));
        b.setOnClickListener(onClick);
        return b;
    }

    TextView dangerButton(String s, View.OnClickListener onClick) {
        TextView b = button(s, false, onClick);
        b.setTextColor(0xFF1A0505);
        b.setBackground(round(DANGER, 0, dp(14), 0));
        return b;
    }

    /** Tappable list row: title, optional subtitle, optional value + chevron. */
    LinearLayout navRow(String title, String sub, String value, View.OnClickListener onClick) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(52));
        r.setPadding(dp(16), dp(10), dp(14), dp(10));
        LinearLayout t = col();
        t.addView(text(title, 15, TEXT, true));
        if (sub != null) t.addView(text(sub, 13, SUB, false));
        r.addView(t, weight(1));
        if (value != null) r.addView(text(value, 14, SUB, false));
        if (onClick != null) {
            r.addView(text("  ›", 18, DIM, false));
            r.setOnClickListener(onClick);
        }
        return r;
    }

    interface OnBool { void set(boolean on); }

    /** Switch in the design colours (blue track + white knob when on); the stock one follows the phone theme and fades out. */
    Switch toggle(boolean on) {
        Switch s = new Switch(c);
        s.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{Color.WHITE, BODY}));
        s.setTrackTintList(new ColorStateList(states, new int[]{ACCENT, OFF}));
        return s;
    }

    LinearLayout switchRow(String title, String sub, boolean on, OnBool cb) {
        LinearLayout r = navRow(title, sub, null, null);
        Switch s = toggle(on);
        s.setOnCheckedChangeListener((b, v) -> cb.set(v));
        r.addView(s);
        r.setOnClickListener(v -> s.toggle());
        return r;
    }

    /** A card made of rows separated by thin lines. */
    LinearLayout group(View... rows) {
        LinearLayout g = col();
        g.setBackground(round(CARD, BORDER, dp(20), dp(1)));
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                View line = new View(c);
                line.setBackgroundColor(BORDER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                lp.leftMargin = dp(16);
                g.addView(line, lp);
            }
            g.addView(rows[i], fill());
        }
        return g;
    }

    interface OnPick { void pick(int index); }

    /** Segmented chips (Day / Week / Month, 30 min / 1 hour ...). */
    LinearLayout chips(String[] labels, int selected, OnPick cb) {
        LinearLayout r = row();
        r.setPadding(dp(4), dp(4), dp(4), dp(4));
        r.setBackground(round(TILE, TILE_BORDER, dp(14), dp(1)));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = text(labels[i], 14, i == selected ? 0xFF06121F : BODY, true);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(40));
            if (i == selected) t.setBackground(round(ACCENT, 0, dp(10), 0));
            t.setOnClickListener(v -> cb.pick(idx));
            r.addView(t, weight(1));
        }
        return r;
    }

    /** Day-of-week toggles, bit 0 = Sunday. */
    LinearLayout dayPicker(int[] mask) {
        LinearLayout r = row();
        String[] d = {"S", "M", "T", "W", "T", "F", "S"};
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView t = text(d[i], 14, TEXT, true);
            t.setGravity(Gravity.CENTER);
            Runnable paint = () -> {
                boolean on = (mask[0] & bit) != 0;
                t.setTextColor(on ? 0xFF06121F : BODY);
                t.setBackground(on ? round(ACCENT, 0, dp(22), 0) : round(TILE, TILE_BORDER, dp(22), dp(1)));
            };
            paint.run();
            t.setOnClickListener(v -> { mask[0] ^= bit; paint.run(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
            lp.rightMargin = dp(6);
            r.addView(t, lp);
        }
        return r;
    }

    interface OnInt { void set(int v); }

    /** − value + stepper. */
    LinearLayout stepper(int[] value, int step, int min, int max, String suffix, OnInt changed) {
        return stepper(value, step, min, max, suffix, changed, 34);
    }

    LinearLayout stepper(int[] value, int step, int min, int max, String suffix, OnInt changed, float sp) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER);
        TextView minus = text("−", 26, TEXT, true), plus = text("+", 26, TEXT, true);
        TextView val = mono("", sp);
        val.setSingleLine(true);
        val.setGravity(Gravity.CENTER);
        Runnable show = () -> val.setText(String.format(java.util.Locale.US, "%,d", value[0]) + suffix);
        for (TextView b : new TextView[]{minus, plus}) {
            b.setGravity(Gravity.CENTER);
            b.setBackground(round(TILE, TILE_BORDER, dp(28), dp(1)));
        }
        minus.setOnClickListener(v -> { value[0] = Math.max(min, value[0] - step); show.run(); if (changed != null) changed.set(value[0]); });
        plus.setOnClickListener(v -> { value[0] = Math.min(max, value[0] + step); show.run(); if (changed != null) changed.set(value[0]); });
        show.run();
        r.addView(minus, new LinearLayout.LayoutParams(dp(56), dp(56)));
        r.addView(val, weight(1));
        r.addView(plus, new LinearLayout.LayoutParams(dp(56), dp(56)));
        return r;
    }

    EditText input(String value, String hint, boolean number) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(DIM);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(round(TILE, TILE_BORDER, dp(12), dp(1)));
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    View bar(float fraction, int color) {
        return new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas cv) {
                float h = getHeight(), w = getWidth();
                p.setColor(BORDER);
                cv.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
                p.setColor(color);
                cv.drawRoundRect(new RectF(0, 0, Math.max(h, w * Math.min(1f, fraction)), h), h / 2, h / 2, p);
            }
        };
    }

    /** Simple vertical bar chart; labels under each bar; highlight index drawn in accent. */
    View barChart(float[] values, String[] labels, int color, int highlight) {
        return new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas cv) {
                float max = 1;
                for (float v : values) max = Math.max(max, v);
                int n = values.length;
                float w = getWidth(), labelH = labels != null ? dp(18) : 0, h = getHeight() - labelH;
                float slot = w / n, bw = Math.min(slot * 0.6f, dp(26));
                p.setTextSize(dp(11));
                p.setTextAlign(Paint.Align.CENTER);
                for (int i = 0; i < n; i++) {
                    float x = slot * i + (slot - bw) / 2, bh = Math.max(dp(3), h * values[i] / max);
                    p.setColor(i == highlight || highlight < 0 ? color : (color & 0x00FFFFFF) | 0x66000000);
                    if (values[i] > 0) cv.drawRoundRect(new RectF(x, h - bh, x + bw, h), dp(4), dp(4), p);
                    if (labels != null && labels[i] != null) {
                        p.setColor(DIM);
                        float half = p.measureText(labels[i]) / 2, lx = Math.max(half, Math.min(w - half, slot * i + slot / 2));
                        cv.drawText(labels[i], lx, getHeight() - dp(3), p);
                    }
                }
            }
        };
    }

    /** Back-arrow header for detail screens. */
    LinearLayout detailHeader(String title, Runnable onBack) {
        LinearLayout r = row();
        r.setPadding(dp(8), dp(14), dp(16), dp(8));
        TextView back = text("‹", 30, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> onBack.run());
        r.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        r.addView(text(title, 20, TEXT, true), weight(1));
        return r;
    }

    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

    static int parse(String hex) { return Color.parseColor(hex); }
}
