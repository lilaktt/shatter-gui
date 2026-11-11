package net.shattergui.shatter;

public final class SightLine {
    public static final int MAX_CELLS = 96;

    public interface Blocks {
        boolean solid(int x, int y, int z);
    }

    private SightLine() {
    }

    public static boolean blocked(double x0, double y0, double z0, double x1, double y1, double z1, Blocks blocks) {
        int x = floor(x0);
        int y = floor(y0);
        int z = floor(z0);
        int endX = floor(x1);
        int endY = floor(y1);
        int endZ = floor(z1);
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        int stepX = Double.compare(dx, 0);
        int stepY = Double.compare(dy, 0);
        int stepZ = Double.compare(dz, 0);
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double maxX = stepX == 0 ? Double.POSITIVE_INFINITY : deltaX * (stepX > 0 ? x + 1 - x0 : x0 - x);
        double maxY = stepY == 0 ? Double.POSITIVE_INFINITY : deltaY * (stepY > 0 ? y + 1 - y0 : y0 - y);
        double maxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : deltaZ * (stepZ > 0 ? z + 1 - z0 : z0 - z);
        for (int i = 0; i < MAX_CELLS; i++) {
            if (maxX < maxY && maxX < maxZ) {
                if (maxX > 1) {
                    return false;
                }
                x += stepX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                if (maxY > 1) {
                    return false;
                }
                y += stepY;
                maxY += deltaY;
            } else {
                if (maxZ > 1) {
                    return false;
                }
                z += stepZ;
                maxZ += deltaZ;
            }
            if (x == endX && y == endY && z == endZ) {
                return false;
            }
            if (blocks.solid(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
