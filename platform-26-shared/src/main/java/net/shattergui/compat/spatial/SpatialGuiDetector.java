package net.shattergui.compat.spatial;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class SpatialGuiDetector {
    private static final String[] MOD_IDS = {"spatial_gui", "spatial-gui"};
    public static final String SUPPORTED_VERSIONS = ">=1.8 <2";

    private static final boolean SUPPORTED = detect();

    private SpatialGuiDetector() {
    }

    public static boolean isSupportedVersionInstalled() {
        return SUPPORTED;
    }

    private static boolean detect() {
        Optional<ModContainer> mod = Optional.empty();
        for (String id : MOD_IDS) {
            mod = FabricLoader.getInstance().getModContainer(id);
            if (mod.isPresent()) {
                break;
            }
        }
        if (mod.isEmpty()) {
            return false;
        }
        Version version = mod.get().getMetadata().getVersion();
        try {
            if (VersionPredicate.parse(SUPPORTED_VERSIONS).test(version)) {
                return true;
            }
        } catch (Exception e) {
        }
        LoggerFactory.getLogger("Shatter GUI").warn("Spatial GUI {} found, but the integration supports {}; "
                + "using the normal effect", version.getFriendlyString(), SUPPORTED_VERSIONS);
        return false;
    }
}
