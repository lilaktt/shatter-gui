package net.shattergui.effect;

import net.minecraft.network.chat.Component;
import net.shattergui.ShatterGuiClient;
import net.shattergui.custom.CustomEffect;
import net.shattergui.custom.CustomEffectDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of effects by config name: the built-in ones, then the custom ones ({@link #setCustom}). This order is the
 * order the keybind cycles through and the order of every effect list in the editor.
 */
public final class ShatterEffects {
    private static final Map<String, ShatterEffect> BUILT_IN = new LinkedHashMap<>();
    private static final Map<String, CustomEffect> CUSTOM = new LinkedHashMap<>();
    /** Schema-2 effects (net.shattergui.fx). */
    private static final Map<String, ShatterEffect> DATA = new LinkedHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    /** Backwards versions of code effects, made on first use (dropped when the custom effects change). */
    private static final Map<ShatterEffect, ReversedEffect> REVERSED = new java.util.IdentityHashMap<>();

    static {
        register(new GlassEffect());
        register(new BurnEffect());
        register(new FlyEffect());
        register(new DissolveEffect());
        register(new VortexEffect());
    }

    private ShatterEffects() {
    }

    private static void register(ShatterEffect effect) {
        BUILT_IN.put(effect.id(), effect);
    }

    /** Replaces the custom effects (loading, reloading, the editor's Save). Ids of built-in effects are not taken. */
    public static void setCustom(Collection<CustomEffectDef> defs) {
        CUSTOM.clear();
        for (CustomEffectDef def : defs) {
            ShatterEffect base = BUILT_IN.get(def.base());
            if (base != null && !BUILT_IN.containsKey(def.id())) {
                CUSTOM.put(def.id(), new CustomEffect(def, base));
            }
        }
        REVERSED.clear();
        WARNED.clear();
    }

    /** Replaces the schema-2 effects (loading, F3+T, the editor's reload). Ids of built-in or custom effects are not taken. */
    public static void setData(Collection<? extends ShatterEffect> effects) {
        DATA.clear();
        for (ShatterEffect effect : effects) {
            if (!BUILT_IN.containsKey(effect.id()) && !CUSTOM.containsKey(effect.id())) {
                DATA.put(effect.id(), effect);
            }
        }
        WARNED.clear();
    }

    /** Ids of the schema-2 effects. */
    public static List<String> dataIds() {
        return List.copyOf(DATA.keySet());
    }

    public static ShatterEffect data(String id) {
        return DATA.get(id);
    }

    /** A built-in effect (bases of custom effects). */
    public static ShatterEffect builtIn(String id) {
        return BUILT_IN.get(id);
    }

    /** The named effect (built-in or custom), or null (with a one-time warning) if there is none. */
    public static ShatterEffect byName(String name) {
        ShatterEffect effect = BUILT_IN.get(name);
        if (effect == null) {
            effect = CUSTOM.get(name);
        }
        if (effect == null) {
            effect = DATA.get(name);
        }
        if (effect == null && WARNED.add(name)) {
            ShatterGuiClient.LOGGER.warn("Unknown shatter effect '{}' in config, known: {}", name, ids());
        }
        return effect;
    }

    /** The named effect, or glass if the name is unknown. */
    public static ShatterEffect resolve(String name) {
        ShatterEffect effect = byName(name);
        return effect != null ? effect : BUILT_IN.get(GlassEffect.ID);
    }

    /** Built-in ids, then custom (schema 1) ids, then schema-2 ids. */
    public static List<String> ids() {
        List<String> ids = new ArrayList<>(BUILT_IN.keySet());
        ids.addAll(CUSTOM.keySet());
        ids.addAll(DATA.keySet());
        return ids;
    }

    public static List<String> builtInIds() {
        return List.copyOf(BUILT_IN.keySet());
    }

    public static boolean isBuiltIn(String id) {
        return BUILT_IN.containsKey(id);
    }

    /** One of the effects shipped with the mod as data files (translated, its own settings, cloned to edit). */
    public static boolean isBundled(String id) {
        return DATA.get(id) instanceof net.shattergui.fx.game.DataEffect data && data.bundled;
    }

    /** Ids that a player's effect may not take: the built-in and the shipped ones (loaded or not). */
    public static boolean isReserved(String id) {
        return BUILT_IN.containsKey(id) || net.shattergui.params.BundledParams.ids().contains(id);
    }

    /** Name for the GUI: translated for built-in effects, the player's name for custom ones. */
    public static Component displayName(String id) {
        CustomEffect custom = CUSTOM.get(id);
        if (custom != null) {
            return Component.literal(custom.def().name());
        }
        if (DATA.get(id) instanceof net.shattergui.fx.game.DataEffect data && !data.bundled) {
            return Component.literal(data.def.name);
        }
        return Component.translatable("effect.shatter_gui." + id);
    }

    /**
     * The effect {@code id} played backwards, for opening a screen, or null if it has no opening: unknown, a schema-2
     * effect without {@code "open": true}, or fly (and effects on it), which opens by flying out of the block instead.
     */
    public static ShatterEffect opening(String id) {
        ShatterEffect effect = id == null ? null : BUILT_IN.containsKey(id) ? BUILT_IN.get(id)
                : CUSTOM.containsKey(id) ? CUSTOM.get(id) : DATA.get(id);
        if (effect instanceof net.shattergui.fx.game.DataEffect data) {
            return data.def.openable ? data.reversed() : null;
        }
        if (effect == null || effect.base() instanceof FlyEffect) {
            return null;
        }
        return REVERSED.computeIfAbsent(effect, ReversedEffect::new);
    }

    /** Whether {@link #opening} has something for {@code id}. */
    public static boolean canOpen(String id) {
        return opening(id) != null;
    }

    /** Ids that can open screens, in list order. */
    public static List<String> openIds() {
        List<String> ids = new ArrayList<>();
        for (String id : ids()) {
            if (canOpen(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** The effect after {@code current}, wrapping around. */
    public static String next(String current) {
        List<String> ids = ids();
        int index = ids.indexOf(current);
        return ids.get((index + 1) % ids.size());
    }
}
