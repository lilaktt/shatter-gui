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
import net.shattergui.custom.CustomEffects;
import net.shattergui.config.screen.PreviewRunner;
import net.shattergui.fly.FlyController;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.render.ShardRenderTypes;
import net.shattergui.render.ShardRenderer;
import net.shattergui.shatter.EffectClock;
import net.shattergui.shatter.ShardManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShatterGuiClient implements ClientModInitializer {
    public static final String MOD_ID = "shatter_gui";
    public static final Logger LOGGER = LoggerFactory.getLogger("Shatter GUI");

    @Override
    public void onInitializeClient() {
        ShatterConfig.load();
        ScreenCatalog.load();
        CustomEffects.init();
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath(MOD_ID, "custom_effects"),
                (ResourceManagerReloadListener) CustomEffects::reload);
        ShardRenderTypes.init();
        ShatterKeys.register();
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
