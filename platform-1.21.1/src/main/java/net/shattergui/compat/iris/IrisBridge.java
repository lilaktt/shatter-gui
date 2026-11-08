package net.shattergui.compat.iris;

import net.irisshaders.iris.api.v0.IrisApi;

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
