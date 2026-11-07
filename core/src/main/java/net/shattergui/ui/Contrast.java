package net.shattergui.ui;

/** Contrast of text against its background (WCAG 2: relative luminance of sRGB colours, ratio 1–21). */
public final class Contrast {
    /** Normal text: at least this ratio (WCAG AA). */
    public static final double READABLE = 4.5;
    /** The dark text used on light fills: black (any lighter and some fills on the way read worse than 4.5:1). */
    public static final int DARK_TEXT = 0xFF000000;

    private Contrast() {
    }

    /** Relative luminance of an opaque ARGB colour, 0–1. */
    public static double luminance(int argb) {
        return 0.2126 * channel((argb >> 16) & 0xFF) + 0.7152 * channel((argb >> 8) & 0xFF) + 0.0722 * channel(argb & 0xFF);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Contrast ratio of two opaque colours, 1–21. */
    public static double ratio(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /**
     * The text colour for {@code fill}: {@code preferred} while it is readable on it, otherwise whichever of white and
     * {@link #DARK_TEXT} contrasts more (one of them always reaches 4.58).
     */
    public static int readableOn(int fill, int preferred) {
        if (ratio(preferred, fill) >= READABLE) {
            return preferred;
        }
        return ratio(0xFFFFFFFF, fill) >= ratio(DARK_TEXT, fill) ? 0xFFFFFFFF : DARK_TEXT;
    }
}
