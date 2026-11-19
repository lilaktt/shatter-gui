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

public final class ShatterEffects {
    private static final Map<String, ShatterEffect> BUILT_IN = new LinkedHashMap<>();
    private static final Map<String, CustomEffect> CUSTOM = new LinkedHashMap<>();
    private static final Map<String, ShatterEffect> DATA = new LinkedHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
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

    public static void setData(Collection<? extends ShatterEffect> effects) {
        DATA.clear();
        for (ShatterEffect effect : effects) {
            if (!BUILT_IN.containsKey(effect.id()) && !CUSTOM.containsKey(effect.id())) {
                DATA.put(effect.id(), effect);
            }
        }
        WARNED.clear();
    }

    public static List<String> dataIds() {
        return List.copyOf(DATA.keySet());
    }

    public static ShatterEffect data(String id) {
        return DATA.get(id);
    }

    public static ShatterEffect builtIn(String id) {
        return BUILT_IN.get(id);
    }

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

    public static ShatterEffect resolve(String name) {
        ShatterEffect effect = byName(name);
        return effect != null ? effect : BUILT_IN.get(GlassEffect.ID);
    }

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

    public static boolean isBundled(String id) {
        return DATA.get(id) instanceof net.shattergui.fx.game.DataEffect data && data.bundled;
    }

    public static boolean isReserved(String id) {
        return BUILT_IN.containsKey(id) || net.shattergui.params.BundledParams.ids().contains(id);
    }

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

    public static boolean canOpen(String id) {
        return opening(id) != null;
    }

    public static List<String> openIds() {
        List<String> ids = new ArrayList<>();
        for (String id : ids()) {
            if (canOpen(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static String next(String current) {
        List<String> ids = ids();
        int index = ids.indexOf(current);
        return ids.get((index + 1) % ids.size());
    }
}
