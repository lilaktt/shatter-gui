package net.shattergui.shatter;

/**
 * When a piece of a close effect counts as settled, and how a settled piece is drawn. With {@code "alwaysOnTop"} a piece
 * in motion (flying out, falling, flying into a container) is drawn over the blocks, as before; a settled one is drawn in
 * the world's depth, so a wall or the block it lies under hides it. It is the same picture in both modes (same pipeline,
 * same matrices), only the depth test differs, so the switch shows nothing for a piece that nothing covers.
 *
 * <p>Settled: motionless for {@link #STILL_TICKS} ticks while resting on a block (a block right under it), or older than
 * {@link #AGE_TICKS}. Never while its centre is inside a block (stuck in it: it stays over the blocks, as before).
 */
public final class Settle {
    /**
     * How far a settled piece is moved towards the camera, in blocks, so a piece lying on a face does not flicker with
     * it in the depth test. Along the line of sight, so it does not move on the screen.
     */
    public static final float BIAS = 0.03f;
    /** Motionless this many ticks (and resting on a block): settled. */
    public static final int STILL_TICKS = 4;
    /** Older than this (ticks): settled whatever it does (unless inside a block). */
    public static final int AGE_TICKS = 100;
    /** Moved less than this in a tick (blocks): motionless. */
    public static final double MOVE_EPSILON = 1.0e-4;
    /**
     * Turned less than this in a tick: motionless. The sine of half the angle of the turn (the length of the vector part
     * of {@code q₁·q₀⁻¹}); 1.0e-4 is about 0.01 degree. Precise for small turns, unlike {@code q₀·q₁} in floats.
     */
    public static final float TURN_SIN = 1.0e-4f;
    /** How far under a piece's lowest possible point a block still holds it (blocks). */
    public static final float SUPPORT_REACH = 0.05f;

    private Settle() {
    }

    /** Whether a piece that moved {@code dx, dy, dz} and turned by {@code halfTurnSin} (see {@link #TURN_SIN}) moved. */
    public static boolean moved(double dx, double dy, double dz, float halfTurnSin) {
        return dx * dx + dy * dy + dz * dz > MOVE_EPSILON * MOVE_EPSILON || Math.abs(halfTurnSin) > TURN_SIN;
    }

    /**
     * The sine of half the angle between two unit quaternions (x, y, z, w each): the vector part of {@code q₁·q₀⁻¹}.
     */
    public static float halfTurnSin(float x0, float y0, float z0, float w0, float x1, float y1, float z1, float w1) {
        // q1 * conj(q0)
        float x = w0 * x1 - x0 * w1 - (y1 * z0 - z1 * y0);
        float y = w0 * y1 - y0 * w1 - (z1 * x0 - x1 * z0);
        float z = w0 * z1 - z0 * w1 - (x1 * y0 - y1 * x0);
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /** Ticks motionless after this tick. */
    public static int stillTicks(int before, boolean moved) {
        return moved ? 0 : Math.min(before + 1, 1 << 20);
    }

    /**
     * Whether the piece is settled.
     *
     * @param stillTicks  ticks it has been motionless
     * @param supported   a block right under it
     * @param age         its age in ticks
     * @param insideBlock its centre is inside a block
     */
    public static boolean settled(int stillTicks, boolean supported, int age, boolean insideBlock) {
        if (insideBlock) {
            return false;
        }
        return (stillTicks >= STILL_TICKS && supported) || age >= AGE_TICKS;
    }

    /**
     * Whether the settled state must be worked out again this tick (it needs block lookups): when the piece has just been
     * motionless long enough, when it just got old enough, and every tick it moves once it is old.
     */
    public static boolean recheck(int stillTicks, boolean moved, int age) {
        return stillTicks == STILL_TICKS || age == AGE_TICKS || (moved && age >= AGE_TICKS);
    }

    /**
     * The factor that moves a camera-relative point {@code bias} blocks towards the camera (the origin) along its line of
     * sight. Points nearer than twice the bias stay where they are.
     */
    public static float towardsCamera(float x, float y, float z, float bias) {
        float distance = (float) Math.sqrt(x * x + y * y + z * z);
        if (bias <= 0f || distance <= 2f * bias) {
            return 1f;
        }
        return 1f - bias / distance;
    }
}
