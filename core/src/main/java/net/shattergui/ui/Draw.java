package net.shattergui.ui;

import net.shattergui.platform.UiTexture;

public final class Draw {
    private Draw() {
    }

    public static void rect(Ui ui, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0 && (color >>> 24) != 0) {
            ui.g.fill(x, y, x + w, y + h, color);
        }
    }

    private static int inset(int r, int i) {
        double d = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - d * d)));
    }

    public static void roundRect(Ui ui, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            ui.g.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            ui.g.fill(x + in, y + i, x + w - in, y + i + 1, color);
            ui.g.fill(x + in, y + h - i - 1, x + w - in, y + h - i, color);
        }
        ui.g.fill(x, y + r, x + w, y + h - r, color);
    }

    public static void roundGradient(Ui ui, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            ui.g.fill(x + in, y + i, x + w - in, y + i + 1, Theme.mix(top, bottom, (i + 0.5f) / h));
            ui.g.fill(x + in, y + h - i - 1, x + w - in, y + h - i, Theme.mix(top, bottom, (h - i - 0.5f) / h));
        }
        ui.g.fillGradient(x, y + r, x + w, y + h - r, Theme.mix(top, bottom, (float) r / h),
                Theme.mix(top, bottom, (float) (h - r) / h));
    }

    public static void roundOutline(Ui ui, int x, int y, int w, int h, int r, int color) {
        if (w <= 2 || h <= 2 || (color >>> 24) == 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            ui.g.outline(x, y, w, h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            int next = i + 1 < r ? inset(r, i + 1) : 0;
            int span = Math.max(1, in - next);
            ui.g.fill(x + in, y + i, x + in + span, y + i + 1, color);
            ui.g.fill(x + w - in - span, y + i, x + w - in, y + i + 1, color);
            ui.g.fill(x + in, y + h - i - 1, x + in + span, y + h - i, color);
            ui.g.fill(x + w - in - span, y + h - i - 1, x + w - in, y + h - i, color);
        }
        ui.g.fill(x + r, y, x + w - r, y + 1, color);
        ui.g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        ui.g.fill(x, y + r, x + 1, y + h - r, color);
        ui.g.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    public static void roundBox(Ui ui, int x, int y, int w, int h, int r, int fill, int border) {
        if ((border >>> 24) == 0) {
            roundRect(ui, x, y, w, h, r, fill);
            return;
        }
        roundRect(ui, x, y, w, h, r, border);
        roundRect(ui, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public static void shadow(Ui ui, int x, int y, int w, int h, int r, float strength) {
        if (strength <= 0.01f) {
            return;
        }
        int[] alphas = {40, 16};
        for (int i = 0; i < alphas.length; i++) {
            int a = Math.round(alphas[i] * strength);
            int grow = i + 1;
            roundRect(ui, x - grow, y - grow + 2 + i * 2, w + grow * 2, h + grow * 2, r + grow, a << 24);
        }
    }

    public static void icon(Ui ui, Icon icon, int x, int y, int scale, int color) {
        for (int py = 0; py < Icon.SIZE; py++) {
            int run = -1;
            for (int px = 0; px <= Icon.SIZE; px++) {
                boolean on = px < Icon.SIZE && icon.at(px, py);
                if (on && run < 0) {
                    run = px;
                } else if (!on && run >= 0) {
                    ui.g.fill(x + run * scale, y + py * scale, x + px * scale, y + (py + 1) * scale, color);
                    run = -1;
                }
            }
        }
    }

    public static void iconCentered(Ui ui, Icon icon, int x, int y, int w, int h, int color) {
        int size = Icon.SIZE;
        icon(ui, icon, x + (w - size) / 2, y + (h - size) / 2, 1, color);
    }

    public static void circle(Ui ui, int cx, int cy, int radius, int color) {
        roundRect(ui, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    public static void texture(Ui ui, UiTexture texture, float x, float y, float w, float h, int color) {
        float x1 = x + w;
        float y1 = y + h;
        texture(ui, texture, x, y, w, h, color, 0, 0, 1, 1, false);
    }

    public static void texture(Ui ui, UiTexture texture, float x, float y, float w, float h, int color,
                               float u0, float v0, float u1, float v1, boolean premultiplied) {
        float x1 = x + w;
        float y1 = y + h;
        float[] data = {x, y, u0, v0, x, y1, u0, v1, x1, y1, u1, v1, x, y, u0, v0, x1, y1, u1, v1, x1, y, u1, v0};
        triangles(ui, texture, premultiplied, data, new int[]{color, color, color, color, color, color}, 6);
    }

    public static void triangles(Ui ui, UiTexture texture, float[] data, int[] colors, int vertexCount) {
        triangles(ui, texture, false, data, colors, vertexCount);
    }

    public static void triangles(Ui ui, UiTexture texture, boolean premultiplied, float[] data, int[] colors,
                                 int vertexCount) {
        if (vertexCount < 3) {
            return;
        }
        if (premultiplied && texture != null) {
            colors = colors.clone();
            for (int i = 0; i < vertexCount; i++) {
                colors[i] = premultiply(colors[i]);
            }
        }
        ui.g.triangles(texture, premultiplied, data.clone(), colors.clone(), vertexCount, ui.clip());
    }

    private static int premultiply(int argb) {
        int a = argb >>> 24;
        int r = ((argb >> 16) & 0xFF) * a / 255;
        int g = ((argb >> 8) & 0xFF) * a / 255;
        int b = (argb & 0xFF) * a / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }
}
