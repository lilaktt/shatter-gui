package net.shattergui.compat.iris;

import net.irisshaders.iris.api.v0.IrisApi;

/** The only class that links against Iris. Reached through {@link IrisCompat} only, which catches anything thrown. */
final class IrisBridge {
    private IrisBridge() {
    }

    static boolean shaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    /** The selected pack's name (its file or folder name), or null; Iris's own (not API) method. */
    static String packName() {
        return net.irisshaders.iris.Iris.getCurrentPackName();
    }

    static boolean shadersEnabled() {
        return IrisApi.getInstance().getConfig().areShadersEnabled();
    }

    static boolean renderingShadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }
}
