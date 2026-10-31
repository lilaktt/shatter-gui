package net.shattergui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fly.FlyOpen;
import net.shattergui.shatter.ShardManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Keybinds, all unbound by default: the next favourite close effect (favourites are starred on the Effects tab; with
 * none the action bar says so), switching the whole mod on or off, and the settings. Effect and switch changes show in
 * the action bar (the effect by its name) and are saved.
 */
public final class ShatterKeys {
    private static KeyMapping cycleEffect;
    private static KeyMapping toggleMod;
    private static KeyMapping openConfig;

    private ShatterKeys() {
    }

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "main"));
        cycleEffect = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shatter_gui.cycle_effect",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        toggleMod = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shatter_gui.toggle",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shatter_gui.open_config",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
    }

    public static void tick(Minecraft mc) {
        while (openConfig.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new ShatterConfigScreen(null));
            }
        }
        while (toggleMod.consumeClick()) {
            setEnabled(!ShatterConfig.get().enabled);
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.translatable(ShatterConfig.get().enabled
                        ? "message.shatter_gui.enabled" : "message.shatter_gui.disabled"));
            }
        }
        while (cycleEffect.consumeClick()) {
            ShatterConfig config = ShatterConfig.get();
            if (!config.enabled) {
                continue;
            }
            String next = nextFavorite(config);
            if (next == null) {
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.translatable("message.shatter_gui.no_favorites"));
                }
                continue;
            }
            config.effect = next;
            ShatterConfig.save();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.translatable("message.shatter_gui.effect",
                        ShatterEffects.displayName(config.effect)));
            }
        }
    }

    /**
     * The favourite after the current close effect, in the order of the effect list (wrapping around), or null if no
     * favourite is a loaded effect.
     */
    public static String nextFavorite(ShatterConfig config) {
        List<String> favorites = new ArrayList<>();
        for (String id : ShatterEffects.ids()) {
            if (config.favorites.contains(id)) {
                favorites.add(id);
            }
        }
        if (favorites.isEmpty()) {
            return null;
        }
        int index = favorites.indexOf(ShatterEffects.resolve(config.effect).id());
        return favorites.get((index + 1) % favorites.size());
    }

    /** The cycle key (tests bind it). */
    public static KeyMapping cycleKey() {
        return cycleEffect;
    }

    /** The master switch. Off: every running effect and pending capture is dropped at once. */
    public static void setEnabled(boolean enabled) {
        ShatterConfig.get().enabled = enabled;
        ShatterConfig.save();
        if (!enabled) {
            FlyOpen.stop();
            ScreenCapture.cancelPending();
            SpatialGuiCompat.cancelPending();
            ShardManager.clear();
        }
    }
}
