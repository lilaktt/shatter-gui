package net.shattergui.ui;

public final class MouseGuard {
    public static final int NONE = -1_000_000;
    private static final double LIMIT = 1e7;
    private static boolean ignored;

    private MouseGuard() {
    }

    public static boolean valid(double v) {
        return Double.isFinite(v) && Math.abs(v) < LIMIT;
    }

    public static boolean valid(double x, double y) {
        return valid(x) && valid(y);
    }

    public static boolean valid(int x, int y) {
        return !ignored && Math.abs((long) x) < LIMIT && Math.abs((long) y) < LIMIT;
    }

    public static void ignoreForTests(boolean ignore) {
        ignored = ignore;
    }

    public static int sanitize(int v) {
        return Math.abs((long) v) < LIMIT ? v : NONE;
    }
}
