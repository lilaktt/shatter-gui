package net.shattergui.params;

/**
 * How long an opening (an effect played backwards) lasts: the effect's own length (already with effectSpeed, reduce
 * motion and the preset), capped — quick effects at {@link #QUICK_CAP} ticks, larger ones at {@link #LARGE_CAP} — and
 * divided by {@link Params#OPEN_SPEED}. With the default 1.5 a quick effect opens in at most 7 ticks (0.35 s), a large
 * one (glass, burn, vortex, the long shipped effects) in at most 10 (0.5 s). Never shorter than {@link #MIN_TICKS}.
 */
public final class OpenTiming {
    public static final int QUICK_CAP = 10;
    public static final int LARGE_CAP = 15;
    public static final int MIN_TICKS = 3;
    /** A schema-2 effect of the player's counts as quick up to this length. */
    private static final int QUICK_LENGTH = 20;

    private OpenTiming() {
    }

    /** {@code natural}: the effect's length in ticks after the general duration scale. */
    public static int ticks(int natural, boolean quick, float openSpeed) {
        int capped = Math.min(Math.max(1, natural), quick ? QUICK_CAP : LARGE_CAP);
        return Math.max(MIN_TICKS, Math.round(capped / Math.max(0.1f, openSpeed)));
    }

    public static int ticks(int natural, boolean quick, ParamValues params) {
        return ticks(natural, quick, params.f(Params.OPEN_SPEED));
    }

    /**
     * Ticks the pieces move: {@link #ticks} less two — one in which the pieces rest exactly where the interface is (so
     * the real screen replaces them without a jump) and the one the capture takes — so the interface is there and takes
     * input {@link #ticks} after the opening started.
     */
    public static int playback(int natural, boolean quick, ParamValues params) {
        return Math.max(2, ticks(natural, quick, params) - 2);
    }

    /** Whether a schema-2 effect is a quick one: the shipped list, or a short own effect. */
    public static boolean quick(String id, boolean bundled, int fileDuration) {
        return bundled ? BundledParams.quick(id) : fileDuration <= QUICK_LENGTH;
    }
}
