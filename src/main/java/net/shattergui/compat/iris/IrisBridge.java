package net.shattergui.compat.iris;

import net.irisshaders.iris.api.v0.IrisApi;

/** The only class that links against Iris. Reached through {@link IrisCompat} only, which catches anything thrown. */
final class IrisBridge {
    private IrisBridge() {
    }

    static boolean shaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    static boolean renderingShadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }
}
