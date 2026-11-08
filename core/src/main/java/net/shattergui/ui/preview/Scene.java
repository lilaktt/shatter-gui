package net.shattergui.ui.preview;

import net.shattergui.custom.CustomEffectDef;
import net.shattergui.params.ParamValues;
import net.shattergui.platform.EffectCatalog;

public record Scene(String open, String base, ParamValues params, CustomEffectDef custom, boolean hasSource,
                    boolean edgeAllowed, EffectCatalog.Data fx) {
    public static final String OPEN_FLY = "fly_from_block";
    public static final String DATA = "data";

    public Scene(String open, String base, ParamValues params, CustomEffectDef custom, boolean hasSource, boolean edgeAllowed) {
        this(open, base, params, custom, hasSource, edgeAllowed, null);
    }

    public static Scene data(String open, EffectCatalog.Data fx, ParamValues params, boolean hasSource, boolean edgeAllowed) {
        return new Scene(open, DATA, params, null, hasSource, edgeAllowed, fx);
    }

    public String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(open).append('|').append(base).append('|').append(hasSource).append('|').append(edgeAllowed);
        sb.append('|').append(params.preset()).append('|').append(params.quality()).append('|').append(params.reduceMotion());
        sb.append('|').append(params.signature());
        if (custom != null) {
            sb.append('|').append(custom.toJsonString().hashCode());
        }
        if (fx != null) {
            sb.append('|').append(fx.def().id).append(System.identityHashCode(fx));
        }
        return sb.toString();
    }
}
