package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fx.game.FxEffects;
import net.shattergui.params.BundledParams;
import net.shattergui.shatter.ShardManager;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The mod starts and works with whatever optional mods are present (none, Mod Menu, Spatial GUI, Iris + Sodium, all):
 * all 19 effects are there, the shipped ones load without messages, a furnace closes with glass and with a shipped
 * effect, and with Mod Menu its config button opens the settings. Runs first in every game test run.
 */
final class StartupScenario {
    private StartupScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Supplier<Screen> furnace) {
        FabricLoader loader = FabricLoader.getInstance();
        String present = String.join(", ", List.of("modmenu", "spatial-gui", "spatial_gui", "iris", "sodium").stream()
                .filter(loader::isModLoaded).toList());
        ShatterGuiClient.LOGGER.info("[ShatterTest] startup with optional mods: [{}]", present);
        List<String> ids = context.computeOnClient(mc -> ShatterEffects.ids());
        check.accept(ids.size() >= 19 && ids.containsAll(BundledParams.ids()), "startup: all effects present, got " + ids);
        List<String> messages = context.computeOnClient(mc -> FxEffects.messages());
        check.accept(messages.stream().noneMatch(m -> m.contains("assets/shatter_gui/effects")),
                "startup: shipped effects load cleanly: " + messages);

        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        try {
            config.openEffect = ShatterConfig.OPEN_NONE;
            for (String id : List.of("glass", "frost")) {
                config.effect = id;
                context.setScreen(furnace);
                context.waitTicks(5);
                context.runOnClient(mc -> mc.setScreen(null));
                context.waitTicks(3);
                int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
                check.accept(shards > 0, "startup: closing a furnace with " + id + " makes shards (" + present + ")");
                context.waitTicks(80);
            }
        } finally {
            config.effect = effect;
            config.openEffect = open;
        }

        if (loader.isModLoaded("modmenu")) {
            String opened = context.computeOnClient(mc -> {
                Screen screen = new net.shattergui.compat.modmenu.ShatterModMenu().getModConfigScreenFactory().create(null);
                return screen == null ? "null" : screen.getClass().getSimpleName();
            });
            check.accept("ShatterConfigScreen".equals(opened), "startup: Mod Menu's config button opens the settings, got " + opened);
        }
    }
}
