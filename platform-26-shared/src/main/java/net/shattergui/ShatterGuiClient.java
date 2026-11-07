package net.shattergui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.shattergui.config.ScreenCatalog;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.screen.PreviewRunner;
import net.shattergui.custom.CustomEffects;
import net.shattergui.fly.FlyController;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.render.ShardRenderTypes;
import net.shattergui.render.ShardRenderer;
import net.shattergui.shatter.EffectClock;
import net.shattergui.shatter.ShardManager;
import org.slf4j.Logger;

public final class ShatterGuiClient implements ClientModInitializer {
    public static final String MOD_ID = ShatterMod.MOD_ID;
    public static final Logger LOGGER = ShatterMod.LOGGER;

    @Override
    public void onInitializeClient() {
        // First, so a warning while starting up is kept for the diagnostics too.
        net.shattergui.debug.WarningLog.install();
        ShatterConfig.load();
        ScreenCatalog.load();
        CustomEffects.init();
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath(MOD_ID, "custom_effects"),
                (ResourceManagerReloadListener) CustomEffects::reload);
        ShardRenderTypes.init();
        ModKeys.register();
        FlyController.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ShatterKeys.tick(client);
            EffectClock.tick();
            FlyOpen.tick();
            PreviewRunner.tick();
            ShardManager.tick();
            // After the waves: an opening that just ended unlocks input in this same tick.
            OpenAssemble.tick();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ShardRenderer::submit);
    }
}
