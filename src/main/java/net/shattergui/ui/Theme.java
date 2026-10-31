package net.shattergui.ui;

import java.util.Locale;

/**
 * The one style file of the editor: every colour, size and animation speed of the {@code ui} widgets. Dark, with one
 * accent colour the player can choose ({@link Accent}); everything accent-tinted is derived from it.
 */
public final class Theme {
    /** The accent colours on offer (config value: the lower-case name). */
    public enum Accent {
        VIOLET(0x8B5CF6), BLUE(0x3B82F6), TEAL(0x14B8A6), GREEN(0x22C55E), AMBER(0xF59E0B), ROSE(0xF43F5E);

        public final int rgb;

        Accent(int rgb) {
            this.rgb = rgb;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Accent byId(String id) {
            for (Accent accent : values()) {
                if (accent.id().equals(id)) {
                    return accent;
                }
            }
            return VIOLET;
        }
    }

    // --- surfaces (darkest to lightest) ---
    public static final int BACKDROP_TOP = 0xE00B0B10;
    public static final int BACKDROP_BOTTOM = 0xF0060609;
    public static final int PANEL = 0xF2141519;
    public static final int SIDEBAR = 0xF2101115;
    public static final int CARD = 0xFF1B1D24;
    public static final int CARD_HOVER = 0xFF22252E;
    public static final int RAISED = 0xFF2A2D38;
    public static final int RAISED_HOVER = 0xFF343845;
    public static final int FIELD = 0xFF0F1014;
    public static final int BORDER = 0xFF2C2F3B;
    public static final int BORDER_STRONG = 0xFF3D4152;
    public static final int SHADOW = 0x000000;

    // --- text ---
    public static final int TEXT = 0xFFECEEF4;
    public static final int TEXT_DIM = 0xFFA4A9BA;
    public static final int TEXT_FAINT = 0xFF6C7186;
    public static final int TEXT_ON_ACCENT = 0xFFFFFFFF;

    // --- states ---
    public static final int DANGER = 0xFFEF4444;
    public static final int SUCCESS = 0xFF22C55E;
    public static final int WARNING = 0xFFF59E0B;

    // --- sizes (GUI pixels) ---
    public static final int RADIUS_CARD = 6;
    public static final int RADIUS_CONTROL = 4;
    public static final int GAP = 6;
    public static final int PAD = 8;
    public static final int CONTROL_HEIGHT = 18;
    public static final int ROW_HEIGHT = 22;
    public static final int LINE = 10;

    // --- animation (per second, see Anim) ---
    public static final float SPEED_HOVER = 14f;
    public static final float SPEED_PRESS = 22f;
    public static final float SPEED_APPEAR = 9f;
    public static final float SPEED_INDICATOR = 12f;
    public static final float TOOLTIP_DELAY = 0.4f;
    /** Scroll inertia: share of the speed kept after one second. */
    public static final float SCROLL_FRICTION = 0.004f;

    private static Accent accent = Accent.VIOLET;

    private Theme() {
    }

    public static void setAccent(Accent value) {
        accent = value;
    }

    public static Accent accentChoice() {
        return accent;
    }

    public static int accent() {
        return 0xFF000000 | accent.rgb;
    }

    public static int accentHover() {
        return 0xFF000000 | mix(accent.rgb, 0xFFFFFF, 0.18f);
    }

    public static int accentPressed() {
        return 0xFF000000 | mix(accent.rgb, 0x000000, 0.2f);
    }

    /** The accent at the given opacity (0–255). */
    public static int accentAlpha(int alpha) {
        return (alpha << 24) | accent.rgb;
    }

    /** Accent toned down for large surfaces (selected card background). */
    public static int accentSurface() {
        return 0xFF000000 | mix(0x1B1D24, accent.rgb, 0.16f);
    }

    // --- colour maths ---

    /** RGB (or ARGB, alpha from {@code a}) linear mix. */
    public static int mix(int a, int b, float t) {
        t = Math.clamp(t, 0f, 1f);
        int aa = a >>> 24;
        int ba = b >>> 24;
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        int al = Math.round(aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    /** Multiplies the alpha of an ARGB colour. */
    public static int fade(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.clamp(alpha, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
