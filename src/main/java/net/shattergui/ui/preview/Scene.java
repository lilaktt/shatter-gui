package net.shattergui.ui.preview;

import net.shattergui.custom.CustomEffectDef;
import net.shattergui.fx.game.DataEffect;
import net.shattergui.params.ParamValues;

/**
 * What a preview plays.
 *
 * @param open        open effect ("none", "fly_from_block", "open:<data effect>"), or null to start with the sample
 *                    already there
 * @param base        built-in close effect id ("glass", "burn", "fly", "dissolve", "vortex"), or null for no close
 * @param params      the values the effect runs with (unsaved editor values; a custom effect's own on top)
 * @param custom      a custom effect's definition for its extra particles, or null
 * @param hasSource   the interface was opened from a block (the chest in the preview)
 * @param edgeAllowed without a source, fly may leave through an edge (not for chat, signs, books)
 * @param fx          a schema-2 effect (base {@link #DATA}), or null
 */
public record Scene(String open, String base, ParamValues params, CustomEffectDef custom, boolean hasSource,
                    boolean edgeAllowed, DataEffect fx) {
    public static final String OPEN_FLY = "fly_from_block";
    /** {@link #base} of a schema-2 effect (then {@link #fx} is set). */
    public static final String DATA = "data";

    public Scene(String open, String base, ParamValues params, CustomEffectDef custom, boolean hasSource, boolean edgeAllowed) {
        this(open, base, params, custom, hasSource, edgeAllowed, null);
    }

    /** A schema-2 effect closing. */
    public static Scene data(String open, DataEffect fx, ParamValues params, boolean hasSource, boolean edgeAllowed) {
        return new Scene(open, DATA, params, null, hasSource, edgeAllowed, fx);
    }

    /** A key that changes whenever the preview would look different (to restart it). */
    public String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(open).append('|').append(base).append('|').append(hasSource).append('|').append(edgeAllowed);
        sb.append('|').append(params.preset()).append('|').append(params.quality()).append('|').append(params.reduceMotion());
        sb.append('|').append(params.signature());
        if (custom != null) {
            sb.append('|').append(custom.toJsonString().hashCode());
        }
        if (fx != null) {
            sb.append('|').append(fx.def.id).append(System.identityHashCode(fx));
        }
        return sb.toString();
    }
}
