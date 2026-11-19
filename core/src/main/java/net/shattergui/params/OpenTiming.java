package net.shattergui.params;

public final class OpenTiming {
    public static final int QUICK_CAP = 10;
    public static final int LARGE_CAP = 15;
    public static final int MIN_TICKS = 3;
    private static final int QUICK_LENGTH = 20;

    private OpenTiming() {
    }

    public static int ticks(int natural, boolean quick, float openSpeed) {
        int capped = Math.min(Math.max(1, natural), quick ? QUICK_CAP : LARGE_CAP);
        return Math.max(MIN_TICKS, Math.round(capped / Math.max(0.1f, openSpeed)));
    }

    public static int ticks(int natural, boolean quick, ParamValues params) {
        return ticks(natural, quick, params.f(Params.OPEN_SPEED));
    }

    public static int playback(int natural, boolean quick, ParamValues params) {
        return Math.max(2, ticks(natural, quick, params) - 2);
    }

    public static boolean quick(String id, boolean bundled, int fileDuration) {
        return bundled ? BundledParams.quick(id) : fileDuration <= QUICK_LENGTH;
    }
}
