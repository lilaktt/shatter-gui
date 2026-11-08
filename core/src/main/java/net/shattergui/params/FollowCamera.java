package net.shattergui.params;

public final class FollowCamera {
    public static final String AUTO = "auto";
    public static final String ALWAYS = "always";
    public static final String NEVER = "never";

    private FollowCamera() {
    }

    public static boolean follows(String setting, Boolean effect, boolean opening) {
        if (ALWAYS.equals(setting)) {
            return true;
        }
        if (NEVER.equals(setting)) {
            return false;
        }
        return effect != null ? effect : opening;
    }

    public static boolean follows(ParamValues params, Boolean effect, boolean opening) {
        return follows(params.s(Params.FOLLOW_CAMERA), effect, opening);
    }
}
