package net.shattergui.fly;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.EffectClock;

public final class OpenBackdrop {
    public static final int GRADIENT_TOP = 0xC0101010;
    public static final int GRADIENT_BOTTOM = 0xD0101010;
    public static final int MENU_COLOR = 0x40000000;
    public static final int GRADIENT = 1;
    public static final int MENU = 2;
    public static final int BLUR = 4;
    static final int CLOSE_TICKS = 10;

    private static Screen lastScreen;
    private static int lastKind;
    private static float lastFactor = 1f;
    private static Screen drawing;

    private static int closingKind;
    private static double closeStart;
    private static float closeLength;
    private static float closeFrom;

    private OpenBackdrop() {
    }

    static float smooth(float t) {
        return Math.clamp(t, 0f, 1f);
    }

    public static float factor(Screen s) {
        return smooth(Math.min(FlyOpen.progress(s), OpenAssemble.progress(s)));
    }

    public static void beginDrawing(Screen s, int part) {
        if (lastScreen != s) {
            lastScreen = s;
            lastKind = 0;
        }
        lastKind |= part;
        lastFactor = factor(s);
        drawing = s;
    }

    public static void endDrawing() {
        drawing = null;
    }

    public static int fade(int argb) {
        if (drawing == null) {
            return argb;
        }
        return withAlpha(argb, factor(drawing));
    }

    public static int menuTint() {
        return withAlpha(0xFFFFFFFF, drawing == null ? 1f : factor(drawing));
    }

    public static int withAlpha(int argb, float factor) {
        int alpha = Math.round((argb >>> 24) * Math.clamp(factor, 0f, 1f));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    public static float blurRadius(float radius) {
        Minecraft mc = Minecraft.getInstance();
        if (Mc26.screen() != null) {
            return radius * factor(Mc26.screen());
        }
        return (closingKind & BLUR) != 0 ? radius * closeFactor() : radius;
    }

    public static void startClose(Screen old, ParamValues params) {
        closingKind = 0;
        if (old == null || old != lastScreen || lastKind == 0 || Mc26.screen() != null) {
            return;
        }
        closingKind = lastKind;
        closeFrom = lastFactor;
        closeStart = EffectClock.now();
        closeLength = Math.max(3f, CLOSE_TICKS / Math.max(0.1f, params.f(Params.EFFECT_SPEED)));
    }

    public static void cancelClose() {
        closingKind = 0;
    }

    public static int closingKind() {
        if (closingKind != 0 && closeFactor() <= 0f) {
            closingKind = 0;
        }
        return closingKind;
    }

    public static float closeFactor() {
        if (closingKind == 0) {
            return 0f;
        }
        double t = (EffectClock.now() - closeStart) / closeLength;
        if (t >= 1) {
            return 0f;
        }
        return closeFrom * (1f - smooth((float) t));
    }

    public static float shown() {
        Minecraft mc = Minecraft.getInstance();
        return Mc26.screen() != null ? factor(Mc26.screen()) : closeFactor();
    }
}
