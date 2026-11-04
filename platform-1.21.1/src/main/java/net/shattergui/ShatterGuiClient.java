package net.shattergui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
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
        ResourceLocation reloadId = ResourceLocation.fromNamespaceAndPath(MOD_ID, "custom_effects");
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return reloadId;
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        CustomEffects.reload(manager);
                    }
                });
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
        // 1.21.1 draws the level at once: the shards go in at the end of the level (after the translucent layers,
        // Fabulous' composite, clouds and weather), before the hand (see OnTopPass).
        WorldRenderEvents.END.register(ShardRenderer::render);
    }
}
