package net.shattergui;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.shattergui.config.ScreenCatalog;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.Themes;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.ShatterEffects;

import net.shattergui.ui.UiScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScreenRules {
    public static final String INVENTORY_KEY = net.shattergui.config.ScreenKeys.INVENTORY;

    private static final List<Class<? extends Screen>> NEVER = List.of(LevelLoadingScreen.class, ProgressScreen.class,
            ConnectScreen.class, GenericMessageScreen.class, DisconnectedScreen.class);

    private static final Map<String, String> SEEN = new LinkedHashMap<>();

    private ScreenRules() {
    }

    public static void remember(Screen screen, List<String> keys) {
        if (screen instanceof UiScreen) {
            return;
        }
        for (Class<? extends Screen> type : NEVER) {
            if (type.isInstance(screen)) {
                return;
            }
        }
        SEEN.putIfAbsent(keys.getFirst(), screen.getClass().getSimpleName());
        ScreenCatalog.record(screen, keys);
    }

    public static Map<String, String> seen() {
        return Collections.unmodifiableMap(SEEN);
    }

    public static List<String> keys(Screen screen) {
        List<String> keys = new ArrayList<>(3);
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            keys.add(menuKey(containerScreen));
        }
        keys.add(screen.getClass().getName());
        keys.add(screen.getClass().getSimpleName());
        return keys;
    }

    public static boolean shouldShatter(Screen screen, List<String> keys, ShatterConfig config) {
        for (Class<? extends Screen> type : NEVER) {
            if (type.isInstance(screen)) {
                return false;
            }
        }
        if (config.containersOnly() && !(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        if (screen instanceof UiScreen && keys.stream().noneMatch(config.overrides::containsKey)) {
            return false;
        }
        for (String key : keys) {
            if (config.disabledScreens.contains(key)) {
                return false;
            }
        }
        return true;
    }

    public static ShatterEffect effectFor(List<String> keys, ShatterConfig config) {
        return effectFor(keys, config, null);
    }

    public static ShatterEffect effectFor(List<String> keys, ShatterConfig config, String sourceKey) {
        Map<String, String> overrides = config.overrides;
        for (String key : keys) {
            String value = overrides.get(key);
            if (value == null) {
                continue;
            }
            for (String name : names(value)) {
                if (!ShatterConfig.isOpenEffect(name)) {
                    ShatterEffect effect = ShatterEffects.byName(name);
                    if (effect != null) {
                        return effect;
                    }
                }
            }
        }
        if (config.thematic) {
            String themed = Themes.effectFor(Themes.rules(), config.themes, keys, sourceKey);
            ShatterEffect effect = themed != null ? ShatterEffects.byName(themed) : null;
            if (effect != null) {
                return effect;
            }
        }
        return ShatterEffects.resolve(config.effect);
    }

    public static String openEffectFor(List<String> keys, ShatterConfig config) {
        for (String key : keys) {
            String value = config.overrides.get(key);
            if (value == null) {
                continue;
            }
            for (String name : names(value)) {
                if (ShatterConfig.isOpenEffect(name)) {
                    return name;
                }
            }
        }
        return config.openEffect;
    }

    private static List<String> names(String value) {
        List<String> names = new ArrayList<>(2);
        for (String part : value.split(",")) {
            String name = part.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private static String menuKey(AbstractContainerScreen<?> screen) {
        try {
            ResourceLocation id = BuiltInRegistries.MENU.getKey(screen.getMenu().getType());
            return id != null ? id.toString() : INVENTORY_KEY;
        } catch (UnsupportedOperationException e) {
            return INVENTORY_KEY;
        }
    }
}
