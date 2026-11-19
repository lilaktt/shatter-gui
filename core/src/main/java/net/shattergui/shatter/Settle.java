package net.shattergui.shatter;

public final class Settle {
    public static final float BIAS = 0.03f;
    public static final int STILL_TICKS = 4;
    public static final int AGE_TICKS = 100;
    public static final double MOVE_EPSILON = 1.0e-4;
    public static final float TURN_SIN = 1.0e-4f;
    public static final float SUPPORT_REACH = 0.05f;

    private Settle() {
    }

    public static boolean moved(double dx, double dy, double dz, float halfTurnSin) {
        return dx * dx + dy * dy + dz * dz > MOVE_EPSILON * MOVE_EPSILON || Math.abs(halfTurnSin) > TURN_SIN;
    }

    public static float halfTurnSin(float x0, float y0, float z0, float w0, float x1, float y1, float z1, float w1) {
        float x = w0 * x1 - x0 * w1 - (y1 * z0 - z1 * y0);
        float y = w0 * y1 - y0 * w1 - (z1 * x0 - x1 * z0);
        float z = w0 * z1 - z0 * w1 - (x1 * y0 - y1 * x0);
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public static int stillTicks(int before, boolean moved) {
        return moved ? 0 : Math.min(before + 1, 1 << 20);
    }

    public static boolean settled(int stillTicks, boolean supported, int age, boolean insideBlock) {
        if (insideBlock) {
            return false;
        }
        return (stillTicks >= STILL_TICKS && supported) || age >= AGE_TICKS;
    }

    public static boolean recheck(int stillTicks, boolean moved, int age) {
        return stillTicks == STILL_TICKS || age == AGE_TICKS || (moved && age >= AGE_TICKS);
    }

    public static float towardsCamera(float x, float y, float z, float bias) {
        float distance = (float) Math.sqrt(x * x + y * y + z * z);
        if (bias <= 0f || distance <= 2f * bias) {
            return 1f;
        }
        return 1f - bias / distance;
    }
}
