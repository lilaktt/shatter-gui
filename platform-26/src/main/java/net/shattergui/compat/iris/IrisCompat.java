package net.shattergui.compat.iris;

import net.fabricmc.loader.api.FabricLoader;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;

/**
 * Optional Iris (shader packs) support through its public API ({@code net.irisshaders.iris.api.v0.IrisApi}).
 *
 * <p>Iris maps every vanilla {@code RenderPipeline} to a shader pack program. Our own shard pipeline is unknown to it
 * ("Missing program shatter_gui:pipeline/shard in override list"), so with a pack active the shards are drawn with
 * the vanilla translucent entity type instead, which Iris maps to {@code gbuffers_entities_translucent}.
 *
 * <p>This class never touches Iris classes, so it is safe to load without the mod; {@link IrisBridge} does. Any failure
 * logs one warning and turns shader support off for the session (shards then render as without Iris).
 */
public final class IrisCompat {
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("iris");
    private static boolean failed;

    private IrisCompat() {
    }

    /** A shader pack is active and shader support is enabled. */
    public static boolean shaderPackInUse() {
        if (!active()) {
            return false;
        }
        try {
            return IrisBridge.shaderPackInUse();
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    /** Iris is rendering its shadow map; shards are not drawn there. */
    public static boolean renderingShadowPass() {
        if (!active()) {
            return false;
        }
        try {
            return IrisBridge.renderingShadowPass();
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    private static boolean active() {
        return INSTALLED && !failed && ShatterConfig.get().shaderCompat;
    }

    private static void fail(Throwable t) {
        failed = true;
        ShatterGuiClient.LOGGER.warn("Iris shader support disabled for this session; shards render as without shaders", t);
    }
}
