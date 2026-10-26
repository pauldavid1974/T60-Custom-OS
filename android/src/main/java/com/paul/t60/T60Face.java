package com.paul.t60;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Builds a photo watch face in the watch maker's custom-dial format (GloryFit PicUtils /
 * Rgb, "watchFaceBeansToData"), drawing the digit and weekday pictures here instead of
 * downloading the maker's template. Layout per piece follows GloryFit's built-in
 * DialCustom/dial_2 template: digits are 14-picture strips in character order "-./0123456789:",
 * weekdays are 7 Chinese then 7 English pictures, AM/PM is 4 pictures (Chinese AM/PM, AM, PM).
 * Pure black (0x0000) is the watch's transparent colour inside a piece.
 */
final class T60Face {

    static final int W = 320, H = 380;

    private T60Face() {}

    /** One piece of the face (CustomWatchFaceDataBean). */
    private static final class Piece {
        final int type, w, h, x, y, count;
        final byte[] data;
        int start;
        Piece(int type, int w, int h, int x, int y, int count, byte[] data) {
            this.type = type; this.w = w; this.h = h; this.x = x; this.y = y; this.count = count; this.data = data;
        }
    }

    /** RGB565, big-endian, as Rgb.RGB888ToRGB565 + bmpToRgb565Byte. */
    static int rgb565(int c) {
        return (((c >> 19) & 0x1F) << 11) | (((c >> 10) & 0x3F) << 5) | ((c & 0xFF) >> 3);
    }

    static byte[] background(Bitmap bg) {
        byte[] out = new byte[W * H * 2];
        int[] px = new int[W * H];
        bg.getPixels(px, 0, W, 0, 0, W, H);
        for (int k = 0; k < px.length; k++) {
            int v = rgb565(px[k]);
            out[k * 2] = (byte) (v >> 8);
            out[k * 2 + 1] = (byte) v;
        }
        return out;
    }

    /** Rgb.antiAliasingDefault: glyph drawn in colour, edges blended into the photo behind it. */
    static byte[] glyph(Bitmap bg, Bitmap g, int x, int y, int color) {
        int gw = g.getWidth(), gh = g.getHeight();
        byte[] out = new byte[gw * gh * 2];
        int i = 0;
        for (int yy = 0; yy < gh; yy++) {
            for (int xx = 0; xx < gw; xx++) {
                int p = g.getPixel(xx, yy), a = Color.alpha(p);
                int r = Color.red(p), gr = Color.green(p), b = Color.blue(p);
                if (r > 0 || gr > 0 || b > 0) { r = Color.red(color); gr = Color.green(color); b = Color.blue(color); }
                if (a == 0) { r = 0; gr = 0; b = 0; }
                else if (a != 255) {
                    int under = bg.getPixel(Math.min(W - 1, x + xx), Math.min(H - 1, y + yy));
                    float f = a / 255f, nf = 1 - f;
                    r = clamp(Color.red(under) * nf + r * f);
                    gr = clamp(Color.green(under) * nf + gr * f);
                    b = clamp(Color.blue(under) * nf + b * f);
                    if (r <= 16 && gr <= 8 && b <= 16) { r = 17; gr = 9; b = 17; }
                }
                int v = rgb565(Color.rgb(r, gr, b));
                out[i++] = (byte) (v >> 8);
                out[i++] = (byte) v;
            }
        }
        return out;
    }

    private static int clamp(float v) { return Math.max(0, Math.min(255, (int) v)); }

    /**
     * Rgb.picDataCompress exactly as the bytecode behaves (its run search restarts at every
     * pixel, so every run has length 1): each row becomes records "[n][1][n pixels]" of up to
     * 255 pixels. Output: LE32 total, one LE32 row offset per row (from the start of the
     * offset table), then the rows. Checked byte-for-byte against the original method.
     */
    static byte[] compress(byte[] px, int w, int h) {
        ByteArrayOutputStream table = new ByteArrayOutputStream(), rows = new ByteArrayOutputStream();
        int offset = h * 4;
        for (int row = 0; row < h; row++) {
            int start = rows.size();
            for (int k = 0; k < w; k += 255) {
                int n = Math.min(255, w - k);
                rows.write(n);
                rows.write(1);
                rows.write(px, (row * w + k) * 2, n * 2);
            }
            le(table, offset);
            offset += rows.size() - start;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        le(out, table.size() + rows.size());
        out.write(table.toByteArray(), 0, table.size());
        out.write(rows.toByteArray(), 0, rows.size());
        return out.toByteArray();
    }

    private static void le(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >> 8); o.write(v >> 16); o.write(v >> 24); }

    /** Arc digit types and needles are sent uncompressed (PicUtils z2 rule). */
    static boolean compressed(int type) {
        return !((type >= 1 && type <= 3) || (type >= 35 && type <= 46) || (type >= 52 && type <= 70));
    }

    /** Draws text centred in a transparent w x h picture. */
    static Bitmap text(String s, int w, int h, float size, boolean bold) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        while (p.measureText(s) > w && size > 6) p.setTextSize(size -= 1);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2, p);
        return b;
    }

    private static Piece strip(Bitmap bg, int type, String[] items, int w, int h, int x, int y, float size, boolean bold, int color) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (String s : items) {
            byte[] g = glyph(bg, text(s, w, h, size, bold), x, y, color);
            if (compressed(type)) g = compress(g, w, h);
            o.write(g, 0, g.length);
        }
        return new Piece(type, w, h, x, y, items.length, o.toByteArray());
    }

    /** The picture the watch shows for this face in its face list: the photo with a sample time. */
    static Bitmap preview(Bitmap photo, int position, int color) {
        Bitmap b = Bitmap.createScaledBitmap(photo, W, H, true).copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF000000 | color);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        p.setTextSize(80);
        int ty = position == 1 ? 236 : 56;
        c.drawText("10:08", W / 2f, ty + 60, p);
        p.setTextSize(24);
        c.drawText("MON 10/26", W / 2f, ty + 68 + 26, p);
        return b;
    }

    static final String[] DIGITS = {"-", ".", "/", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ":"};

    /**
     * The whole face file. position 0 = time at the top, 1 = bottom. compat is the watch's
     * reported compatibility level (2 on this T60), used in the header like GloryFit does.
     */
    static byte[] build(Bitmap photo, Bitmap preview, int position, int color, int compat) {
        int col = Color.rgb(Math.max(17, Color.red(color)), Math.max(9, Color.green(color)), Math.max(17, Color.blue(color)));
        Bitmap bg = Bitmap.createScaledBitmap(photo, W, H, true);
        List<Piece> pieces = new ArrayList<>();
        byte[] bgData = background(bg);
        pieces.add(new Piece(17, W, H, 0, 0, 1, compressed(17) ? compress(bgData, W, H) : bgData));
        byte[] pv = background(Bitmap.createScaledBitmap(preview, W, H, true));
        pieces.add(new Piece(10, W, H, 0, 0, 1, compressed(10) ? compress(pv, W, H) : pv));
        int dw = 44, dh = 68, ty = position == 1 ? 236 : 56, cw = 20;
        int x0 = (W - (4 * dw + cw)) / 2;
        pieces.add(strip(bg, 57, DIGITS, dw, dh, x0, ty, 76, true, col));
        pieces.add(strip(bg, 58, DIGITS, dw, dh, x0 + dw, ty, 76, true, col));
        pieces.add(strip(bg, 7, new String[]{":"}, cw, dh, x0 + 2 * dw, ty, 70, true, col));
        pieces.add(strip(bg, 59, DIGITS, dw, dh, x0 + 2 * dw + cw, ty, 76, true, col));
        pieces.add(strip(bg, 60, DIGITS, dw, dh, x0 + 3 * dw + cw, ty, 76, true, col));
        int sy = ty + dh + 4, sw = 14, sh = 26, ww = 58;
        int sx = (W - (ww + 10 + 5 * sw)) / 2;
        pieces.add(strip(bg, 15, new String[]{"周日", "周一", "周二", "周三", "周四", "周五", "周六", "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"},
                ww, sh, sx, sy, 24, true, col));
        int dx = sx + ww + 10;
        pieces.add(strip(bg, 63, DIGITS, sw, sh, dx, sy, 24, true, col));
        pieces.add(strip(bg, 64, DIGITS, sw, sh, dx + sw, sy, 24, true, col));
        pieces.add(strip(bg, 14, new String[]{"/"}, sw, sh, dx + 2 * sw, sy, 24, true, col));
        pieces.add(strip(bg, 65, DIGITS, sw, sh, dx + 3 * sw, sy, 24, true, col));
        pieces.add(strip(bg, 66, DIGITS, sw, sh, dx + 4 * sw, sy, 24, true, col));
        pieces.add(strip(bg, 16, new String[]{"上午", "下午", "AM", "PM"}, 40, 24, x0 + 4 * dw + cw - 40, ty - 26, 20, true, col));
        pieces.sort((a, b) -> a.type - b.type);

        // Table + data (watchFaceBeansToData): addresses count from the file start.
        int addr = (pieces.size() + 1) * 24 + 2;
        ByteArrayOutputStream table = new ByteArrayOutputStream(), data = new ByteArrayOutputStream();
        data.write(0);
        data.write(0);
        for (Piece p : pieces) {
            p.start = addr;
            addr += p.data.length;
            byte[] t = new byte[24];
            t[0] = (byte) p.type; t[1] = (byte) (p.type >> 8);
            t[2] = (byte) p.w; t[3] = (byte) (p.w >> 8);
            t[4] = (byte) p.start; t[5] = (byte) (p.start >> 8); t[6] = (byte) (p.start >> 16); t[7] = (byte) (p.start >> 24);
            t[8] = (byte) p.h; t[9] = (byte) (p.h >> 8);
            t[10] = (byte) p.x; t[11] = (byte) (p.x >> 8);
            t[12] = (byte) p.y; t[13] = (byte) (p.y >> 8);
            t[16] = (byte) p.count;
            table.write(t, 0, 24);
            data.write(p.data, 0, p.data.length);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(table.toByteArray(), 0, table.size());
        body.write(data.toByteArray(), 0, data.size());
        byte[] b = body.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(b);
        int c = (int) crc.getValue(), len = b.length;
        byte[] head = {(byte) 0xFE, -1, -1, -1, (byte) len, (byte) (len >> 8), (byte) (len >> 16), (byte) (len >> 24),
                (byte) c, (byte) (c >> 8), (byte) (c >> 16), (byte) (c >> 24), (byte) W, (byte) (W >> 8), (byte) H, (byte) (H >> 8),
                0 /* screenType field, never set in PicUtils */, 1 /* hasBg */, -1, (byte) compat, -1, -1, -1, -1};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(head, 0, 24);
        out.write(b, 0, b.length);
        return out.toByteArray();
    }
}
