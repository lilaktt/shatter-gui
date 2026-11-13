package net.shattergui.shatter;

public final class BoxSight {
    private BoxSight() {
    }

    public static boolean blocked(double ex, double ey, double ez, double px, double py, double pz, double minX,
                                  double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (ex >= minX && ex <= maxX && ey >= minY && ey <= maxY && ez >= minZ && ez <= maxZ) {
            return false;
        }
        double[] range = {0.0, 1.0};
        return slab(ex, px - ex, minX, maxX, range) && slab(ey, py - ey, minY, maxY, range)
                && slab(ez, pz - ez, minZ, maxZ, range);
    }

    private static boolean slab(double start, double delta, double min, double max, double[] range) {
        if (Math.abs(delta) < 1.0e-12) {
            return start >= min && start <= max;
        }
        double a = (min - start) / delta;
        double b = (max - start) / delta;
        range[0] = Math.max(range[0], Math.min(a, b));
        range[1] = Math.min(range[1], Math.max(a, b));
        return range[0] <= range[1];
    }
}
