package net.shattergui.compat.iris;

import net.irisshaders.iris.api.v0.IrisApi;

final class IrisBridge {
    private IrisBridge() {
    }

    static boolean shaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

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
