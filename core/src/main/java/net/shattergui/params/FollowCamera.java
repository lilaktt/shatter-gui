package net.shattergui.params;

/**
 * Whether an effect follows the camera ({@link Params#FOLLOW_CAMERA}): the player's setting, then the effect's own
 * (schema 2 {@code "followCamera"}), then the default of its role — openings follow, closes do not.
 */
public final class FollowCamera {
    public static final String AUTO = "auto";
    public static final String ALWAYS = "always";
    public static final String NEVER = "never";

    private FollowCamera() {
    }

    /**
     * @param setting the player's setting ({@code auto}, {@code always}, {@code never})
     * @param effect  the effect's own choice, or null if it has none
     * @param opening whether the effect plays as an opening
     */
    public static boolean follows(String setting, Boolean effect, boolean opening) {
        if (ALWAYS.equals(setting)) {
            return true;
        }
        if (NEVER.equals(setting)) {
            return false;
        }
        return effect != null ? effect : opening;
    }

    /** As above, with the setting taken from {@code params}. */
    public static boolean follows(ParamValues params, Boolean effect, boolean opening) {
        return follows(params.s(Params.FOLLOW_CAMERA), effect, opening);
    }
}
