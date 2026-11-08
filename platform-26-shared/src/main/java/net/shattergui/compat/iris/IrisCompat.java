package net.shattergui.compat.iris;

import net.fabricmc.loader.api.FabricLoader;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;

public final class IrisCompat {
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("iris");
    private static boolean failed;

    private IrisCompat() {
    }

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

    public static String describePack() {
        if (!INSTALLED) {
            return "Iris not installed";
        }
        try {
            if (!IrisBridge.shadersEnabled()) {
                return "shaders off";
            }
            String name = IrisBridge.packName();
            return name == null || name.isBlank() ? "none" : name + (IrisBridge.shaderPackInUse() ? "" : " (not in use)");
        } catch (Throwable t) {
            return "unknown (" + t.getClass().getSimpleName() + ")";
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
