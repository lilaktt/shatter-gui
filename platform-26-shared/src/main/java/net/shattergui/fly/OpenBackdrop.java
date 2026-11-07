package net.shattergui.fly;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.EffectClock;

/**
 * The darkened (and, for menus, blurred) world behind a screen fades in with the screen's opening effect and out with its
 * close effect, instead of appearing in the last frame of the opening and vanishing in the first frame of the close.
 *
 * <p>What vanilla draws behind a screen in the world ({@code Screen#extractBackground}): container screens
 * ({@code isInGameUi}) a vertical gradient {@link #GRADIENT_TOP} → {@link #GRADIENT_BOTTOM}; other screens the menu
 * blur (if the player's blur setting is at least 1) and the in-world menu texture, which is black at alpha 64 in every
 * pixel ({@link #MENU_COLOR}). Spatial GUI removes all of it for screens on its plane, so there is nothing to fade there.
 *
 * <ul>
 *   <li>Opening: the screen's own background is drawn with its opacity (and the blur radius) multiplied by
 *       {@link #factor}, the opening's progress: 0 on its first frame, 1 (vanilla, exactly) when the real screen is
 *       shown. While a screen is gathered, only its background is drawn (the pieces are drawn after the GUI,
 *       over it). See {@code ScreenMixin}.</li>
 *   <li>Closing: the last background drawn ({@link #startClose}) is drawn by the mod over the world and the hand, under
 *       the pieces, fading from where it was (1 for a screen that was open) to 0 within {@link #CLOSE_TICKS}
 *       (divided by the effect speed). See {@code OnTopPass#drawAfterLevel}.</li>
 * </ul>
 */
public final class OpenBackdrop {
    /** Vanilla {@code Screen#extractTransparentBackground}: the gradient behind container screens in the world. */
    public static final int GRADIENT_TOP = 0xC0101010;
    public static final int GRADIENT_BOTTOM = 0xD0101010;
    /** {@code textures/gui/inworld_menu_background.png}: every pixel black at alpha 64. */
    public static final int MENU_COLOR = 0x40000000;
    /** Parts of a background ({@link #kind}). */
    public static final int GRADIENT = 1;
    public static final int MENU = 2;
    public static final int BLUR = 4;
    /** Length of the closing fade at effect speed 1. */
    static final int CLOSE_TICKS = 10;

    /** The screen whose background was drawn last, what it was made of and how strongly. */
    private static Screen lastScreen;
    private static int lastKind;
    private static float lastFactor = 1f;
    /** The screen whose background is being drawn now (between HEAD and RETURN of a background method). */
    private static Screen drawing;

    private static int closingKind;
    private static double closeStart;
    private static float closeLength;
    private static float closeFrom;

    private OpenBackdrop() {
    }

    /**
     * The fade over the effect's time: linear. An ease-in-out was tried first; its steep middle made a quick opening
     * (6 ticks) step by more than half its range between two frames whenever a frame came two ticks after the last.
     */
    static float smooth(float t) {
        return Math.clamp(t, 0f, 1f);
    }

    /** Opacity of the background of {@code s} now: 1 unless it is opening by flying in or gathering. */
    public static float factor(Screen s) {
        return smooth(Math.min(FlyOpen.progress(s), OpenAssemble.progress(s)));
    }

    /** HEAD of a background method of {@code s} (not while it is captured): what it draws, and with which opacity. */
    public static void beginDrawing(Screen s, int part) {
        if (lastScreen != s) {
            lastScreen = s;
            lastKind = 0;
        }
        lastKind |= part;
        lastFactor = factor(s);
        drawing = s;
    }

    /** RETURN of a background method. */
    public static void endDrawing() {
        drawing = null;
    }

    /** A colour of the background being drawn, its alpha multiplied by the opacity of its screen now. */
    public static int fade(int argb) {
        if (drawing == null) {
            return argb;
        }
        return withAlpha(argb, factor(drawing));
    }

    /** The vertex colour of the menu texture of the background being drawn (white, faded). */
    public static int menuTint() {
        return withAlpha(0xFFFFFFFF, drawing == null ? 1f : factor(drawing));
    }

    /** ARGB with its alpha multiplied by {@code factor}. */
    public static int withAlpha(int argb, float factor) {
        int alpha = Math.round((argb >>> 24) * Math.clamp(factor, 0f, 1f));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /**
     * The menu blur radius for this frame ({@code GameRenderer#render} hands it to the blur shader): scaled by the
     * opening of the open screen, or by the closing fade of a menu that just closed.
     */
    public static float blurRadius(float radius) {
        Minecraft mc = Minecraft.getInstance();
        if (Mc26.screen() != null) {
            return radius * factor(Mc26.screen());
        }
        return (closingKind & BLUR) != 0 ? radius * closeFactor() : radius;
    }

    /**
     * A close effect started for the flat screen {@code old}: its background (if it was the last one drawn) fades out from
     * where it was. Nothing for a screen that drew none (Spatial GUI's plane, chat, screens of other mods that skip it).
     */
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

    /** A screen opened (it draws its own background now) or the world was left: no closing fade any more. */
    public static void cancelClose() {
        closingKind = 0;
    }

    /** What the closing fade draws ({@link #GRADIENT}, {@link #MENU}, {@link #BLUR}), 0 if none runs. */
    public static int closingKind() {
        if (closingKind != 0 && closeFactor() <= 0f) {
            closingKind = 0;
        }
        return closingKind;
    }

    /** Opacity of the closing fade now, 0 when it is over. */
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

    /** For tests: opacity of the background behind the screen on display (or of the closing fade), 0..1. */
    public static float shown() {
        Minecraft mc = Minecraft.getInstance();
        return Mc26.screen() != null ? factor(Mc26.screen()) : closeFactor();
    }
}
