package net.shattergui;

import net.shattergui.config.ShatterConfig;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.platform.GameInfo;
import net.shattergui.platform.KeyBindings;
import net.shattergui.platform.Platform;
import net.shattergui.platform.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Keybinds, all unbound by default: the next favourite close effect (favourites are starred on the Effects tab; with
 * none the action bar says so), switching the whole mod on or off, and the settings. Effect and switch changes show in
 * the action bar (the effect by its name) and are saved. The platform registers them ({@link KeyBindings}).
 */
public final class ModKeys {
    public static final String CYCLE_EFFECT = "key.shatter_gui.cycle_effect";
    public static final String TOGGLE = "key.shatter_gui.toggle";
    public static final String OPEN_CONFIG = "key.shatter_gui.open_config";

    private ModKeys() {
    }

    /** Registers the bindings (client start). */
    public static void register() {
        KeyBindings.Binding cycle = new KeyBindings.Binding(CYCLE_EFFECT, ModKeys::cycleEffect);
        KeyBindings.Binding toggle = new KeyBindings.Binding(TOGGLE, ModKeys::toggle);
        KeyBindings.Binding open = new KeyBindings.Binding(OPEN_CONFIG, ModKeys::openConfig);
        Platform.get().keys().register(List.of(cycle, toggle, open), List.of(open, toggle, cycle));
    }

    /** The settings, over the world only (not over another screen). */
    static void openConfig() {
        GameInfo game = Platform.get().game();
        if (!game.screenOpen()) {
            game.showScreen(new ShatterConfigScreen(null));
        }
    }

    static void toggle() {
        setEnabled(!ShatterConfig.get().enabled);
        Platform.get().game().actionBar(Text.translatable(ShatterConfig.get().enabled
                ? "message.shatter_gui.enabled" : "message.shatter_gui.disabled"));
    }

    static void cycleEffect() {
        ShatterConfig config = ShatterConfig.get();
        if (!config.enabled) {
            return;
        }
        String next = nextFavorite(config);
        if (next == null) {
            Platform.get().game().actionBar(Text.translatable("message.shatter_gui.no_favorites"));
            return;
        }
        config.effect = next;
        ShatterConfig.save();
        Platform.get().game().actionBar(Text.translatable("message.shatter_gui.effect",
                Platform.get().effects().displayName(config.effect)));
    }

    /**
     * The favourite after the current close effect, in the order of the effect list (wrapping around), or null if no
     * favourite is a loaded effect.
     */
    public static String nextFavorite(ShatterConfig config) {
        List<String> favorites = new ArrayList<>();
        for (String id : Platform.get().effects().ids()) {
            if (config.favorites.contains(id)) {
                favorites.add(id);
            }
        }
        if (favorites.isEmpty()) {
            return null;
        }
        int index = favorites.indexOf(Platform.get().effects().resolve(config.effect));
        return favorites.get((index + 1) % favorites.size());
    }

    /** The master switch. Off: every running effect and pending capture is dropped at once. */
    public static void setEnabled(boolean enabled) {
        ShatterConfig.get().enabled = enabled;
        ShatterConfig.save();
        if (!enabled) {
            Platform platform = Platform.get();
            platform.input().stopOpening();
            platform.capture().cancelPending();
            platform.spatialGui().cancelPending();
            platform.shards().clear();
        }
    }
}
