package net.shattergui.editor;

import net.shattergui.fx.Gradient;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Picks an ARGB colour: a saturation/brightness square, a hue strip, an opacity strip (over a checkerboard), a hex
 * field (#RGB, #RRGGBB, #AARRGGBB) and a palette of ready colours. Hue and saturation are kept while the colour is
 * black or grey, so dragging through them does not lose the hue.
 */
final class ColorPicker extends Container {
    private static final int[] PALETTE = {0xFFFFFFFF, 0xFFBFBFBF, 0xFF404040, 0xFF000000, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55,
            0xFF55FF55, 0xFF55FFFF, 0xFF5555FF, 0xFFFF55FF, 0xFFB77BFF, 0xFFFF8A2A, 0xFF1A1210};
    private static final int SQUARE_H = 40;
    private static final int STRIP_H = 7;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final TextField hex = new TextField(9);
    private float hue;
    private float sat;
    private int drag = -1;
    private int lastSeen;

    ColorPicker(IntSupplier getter, IntConsumer setter) {
        this.getter = getter;
        this.setter = setter;
        hex.filter(c -> c == '#' || Character.digit(c, 16) >= 0);
        hex.onEnter(this::applyHex);
        hex.onChange(s -> {
            if (s.length() == 4 || s.length() == 7 || s.length() == 9) {
                applyHex();
            }
        });
        add(hex);
        syncFrom(getter.getAsInt());
    }

    private void syncFrom(int argb) {
        float[] hsv = hsv(argb);
        if (hsv[2] > 0.01f && hsv[1] > 0.01f) {
            hue = hsv[0];
        }
        if (hsv[2] > 0.01f) {
            sat = hsv[1];
        }
        lastSeen = argb;
        hex.value(Gradient.format(argb));
    }

    private void applyHex() {
        Integer c = Gradient.parseColor(hex.value().startsWith("#") ? hex.value() : "#" + hex.value());
        if (c != null) {
            setter.accept(c);
            syncFrom(c);
        }
    }

    @Override
    protected int measure(int width) {
        int cy = y + SQUARE_H + 3 + STRIP_H + 3 + STRIP_H + 4;
        hex.layout(x, cy, Math.min(70, width));
        return cy - y + hex.h + 4 + 10;
    }

    private int squareY() {
        return y;
    }

    private int hueY() {
        return y + SQUARE_H + 3;
    }

    private int alphaY() {
        return hueY() + STRIP_H + 3;
    }

    private int paletteY() {
        return hex.y + hex.h + 4;
    }

    @Override
    protected void draw(Ui ui) {
        int argb = getter.getAsInt();
        if (argb != lastSeen && drag < 0) {
            syncFrom(argb);
        }
        float[] hsv = hsv(argb);
        float value = hsv[2];
        float alpha = (argb >>> 24) / 255f;
        // Saturation (left → right) and brightness (top → bottom).
        int cols = Math.max(8, w / 4);
        for (int i = 0; i < cols; i++) {
            int x0 = x + i * w / cols;
            int x1 = x + (i + 1) * w / cols;
            float s = (i + 0.5f) / cols;
            ui.g.fillGradient(x0, squareY(), x1, squareY() + SQUARE_H, 0xFF000000 | rgb(hue, s, 1), 0xFF000000);
        }
        int mx = x + Math.round(sat * (w - 1));
        int my = squareY() + Math.round((1 - value) * (SQUARE_H - 1));
        Draw.rect(ui, mx - 2, my - 2, 5, 5, 0xFFFFFFFF);
        Draw.rect(ui, mx - 1, my - 1, 3, 3, 0xFF000000 | (argb & 0xFFFFFF));
        // Hue.
        EditorUi.hGradient(ui, x, hueY(), w, STRIP_H, i -> 0xFF000000 | rgb(i / (float) w, 1, 1));
        int hx = x + Math.round(hue * (w - 1));
        Draw.rect(ui, hx - 1, hueY() - 1, 3, STRIP_H + 2, 0xFFFFFFFF);
        // Opacity.
        EditorUi.checker(ui, x, alphaY(), w, STRIP_H, 3);
        int opaque = 0xFF000000 | (argb & 0xFFFFFF);
        EditorUi.hGradient(ui, x, alphaY(), w, STRIP_H, i -> Theme.fade(opaque, i / (float) w));
        int ax = x + Math.round(alpha * (w - 1));
        Draw.rect(ui, ax - 1, alphaY() - 1, 3, STRIP_H + 2, 0xFFFFFFFF);
        super.draw(ui);
        // Palette.
        int px = x;
        for (int c : PALETTE) {
            if (px + 9 > x + w) {
                break;
            }
            Draw.rect(ui, px, paletteY(), 9, 9, 0xFF2C2F3B);
            Draw.rect(ui, px + 1, paletteY() + 1, 7, 7, c);
            px += 10;
        }
        ui.textSmall(EditorUi.tr("color.alpha", Math.round(alpha * 100)).getString(), hex.x + hex.w + 4, hex.y + 5,
                w - hex.w - 4, Theme.TEXT_DIM);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (my >= squareY() && my < squareY() + SQUARE_H) {
            drag = 0;
        } else if (my >= hueY() - 1 && my < hueY() + STRIP_H + 1) {
            drag = 1;
        } else if (my >= alphaY() - 1 && my < alphaY() + STRIP_H + 1) {
            drag = 2;
        } else if (my >= paletteY() && my < paletteY() + 9) {
            int i = (int) ((mx - x) / 10);
            if (i >= 0 && i < PALETTE.length && x + i * 10 + 9 <= x + w) {
                int alpha = getter.getAsInt() & 0xFF000000;
                int c = alpha | (PALETTE[i] & 0xFFFFFF);
                setter.accept(c);
                syncFrom(c);
            }
            return true;
        } else {
            return false;
        }
        mouseDrag(mx, my);
        return true;
    }

    @Override
    public void mouseDrag(double mx, double my) {
        int argb = getter.getAsInt();
        float[] hsv = hsv(argb);
        float fx = (float) Math.clamp((mx - x) / Math.max(1, w - 1), 0, 1);
        int alpha = argb >>> 24;
        switch (drag) {
            case 0 -> {
                sat = fx;
                float value = (float) Math.clamp(1 - (my - squareY()) / (SQUARE_H - 1), 0, 1);
                set((alpha << 24) | rgb(hue, sat, value));
            }
            case 1 -> {
                hue = fx;
                set((alpha << 24) | rgb(hue, sat, hsv[2]));
            }
            case 2 -> set((Math.round(fx * 255) << 24) | (argb & 0xFFFFFF));
            default -> {
            }
        }
    }

    private void set(int argb) {
        setter.accept(argb);
        lastSeen = argb;
        hex.value(Gradient.format(argb));
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        drag = -1;
    }

    // --- colour maths ---

    static float[] hsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 1e-6f) {
            if (max == r) {
                h = ((g - b) / d) % 6;
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
            if (h < 0) {
                h += 1;
            }
        }
        return new float[]{h, max <= 0 ? 0 : d / max, max};
    }

    static int rgb(float h, float s, float v) {
        float hh = (h % 1f + 1f) % 1f * 6;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }
}
