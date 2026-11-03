package net.shattergui.ui;

/**
 * Mouse coordinates as they enter the UI. Other mods (Spatial GUI projecting the screen onto a plane) can hand over
 * NaN or infinite positions when the cursor is off the plane; those mean "no mouse": nothing is hovered, a drag is
 * cancelled, a click or scroll is ignored. Pure maths, tested without the game.
 */
public final class MouseGuard {
    /** Where "no mouse" is: far outside any screen, so no widget contains it. */
    public static final int NONE = -1_000_000;
    /** Larger than any real GUI coordinate; beyond it a position is treated as broken. */
    private static final double LIMIT = 1e7;

    private MouseGuard() {
    }

    public static boolean valid(double v) {
        return Double.isFinite(v) && Math.abs(v) < LIMIT;
    }

    public static boolean valid(double x, double y) {
        return valid(x) && valid(y);
    }

    /** Int coordinates (a cast infinity becomes Integer.MAX_VALUE / MIN_VALUE). */
    public static boolean valid(int x, int y) {
        return Math.abs((long) x) < LIMIT && Math.abs((long) y) < LIMIT;
    }

    /** The coordinate, or {@link #NONE} if it is not a usable position. */
    public static int sanitize(int v) {
        return Math.abs((long) v) < LIMIT ? v : NONE;
    }
}
