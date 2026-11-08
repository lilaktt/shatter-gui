package net.shattergui.params;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Params {
    public static final String GENERAL = "general";

    private static final Map<String, List<Param<?>>> GROUPS = new LinkedHashMap<>();
    private static final Map<String, Set<String>> RETIRED = Map.of("vortex", Set.of("flashIntensity", "ringParticles"));

    public static final Param.Float PARTICLE_DENSITY = add(new Param.Float(GENERAL, "particleDensity", 0f, 5f, 1f));
    public static final Param.Float EFFECT_SPEED = add(new Param.Float(GENERAL, "effectSpeed", 0.25f, 4f, 1f));
    public static final Param.Float OPEN_SPEED = add(new Param.Float(GENERAL, "openSpeed", 0.5f, 3f, 1.5f));
    public static final Param.Choice FOLLOW_CAMERA = add(new Param.Choice(GENERAL, "followCamera", FollowCamera.AUTO,
            FollowCamera.AUTO, FollowCamera.ALWAYS, FollowCamera.NEVER));
    public static final Param.Float SOUND_VOLUME = add(new Param.Float(GENERAL, "soundVolume", 0f, 1f, 1f));
    public static final Param.Int MAX_SHARDS = add(new Param.Int(GENERAL, "maxShards", 1, 10_000, 500));

    public static final Param.Int GLASS_COLUMNS = add(new Param.Int("glass", "columns", 1, 64, 10));
    public static final Param.Int GLASS_ROWS = add(new Param.Int("glass", "rows", 1, 64, 6));
    public static final Param.Int GLASS_LIFETIME = add(new Param.Int("glass", "lifetime", 1, 1200, 60));
    public static final Param.Float GLASS_FADE_START = add(new Param.Float("glass", "fadeStart", 0f, 1f, 0.75f));
    public static final Param.Float GLASS_GRAVITY = add(new Param.Float("glass", "gravity", 0f, 0.3f, 0.04f));
    public static final Param.Float GLASS_AIR_DRAG = add(new Param.Float("glass", "airDrag", 0f, 1f, 0.98f));
    public static final Param.Float GLASS_BURST = add(new Param.Float("glass", "burst", 0f, 1.5f, 0.25f));
    public static final Param.Float GLASS_SPIN = add(new Param.Float("glass", "spin", 0f, 2f, 0.35f));
    public static final Param.Float GLASS_BOUNCE = add(new Param.Float("glass", "bounce", 0f, 1f, 0.35f));
    public static final Param.Bool GLASS_FULLBRIGHT = add(new Param.Bool("glass", "fullbright", true));
    public static final Param.Float GLASS_SOUND = add(new Param.Float("glass", "soundVolume", 0f, 1f, 0.6f));

    public static final Param.Int BURN_DURATION = add(new Param.Int("burn", "duration", 5, 600, 30));
    public static final Param.Int BURN_SMOKE_DURATION = add(new Param.Int("burn", "smokeDuration", 0, 1200, 60));
    public static final Param.Float BURN_FLAME_DENSITY = add(new Param.Float("burn", "flameDensity", 0f, 5f, 1f));
    public static final Param.Float BURN_SMOKE_DENSITY = add(new Param.Float("burn", "smokeDensity", 0f, 1f, 0.35f));
    public static final Param.Float BURN_SOUND = add(new Param.Float("burn", "soundVolume", 0f, 1f, 0.6f));

    public static final Param.Int FLY_DURATION = add(new Param.Int("fly", "duration", 1, 100, 6));
    public static final Param.Choice FLY_EDGE_DIRECTION = add(new Param.Choice("fly", "edgeDirection", "random",
            "random", "top", "bottom", "left", "right"));
    public static final Param.Float FLY_SOUND = add(new Param.Float("fly", "soundVolume", 0f, 1f, 0.6f));

    public static final Param.Int DISSOLVE_DURATION = add(new Param.Int("dissolve", "duration", 2, 600, 20));
    public static final Param.Choice DISSOLVE_ORIGIN = add(new Param.Choice("dissolve", "origin", "random",
            "random", "edge", "center"));
    public static final Param.Int DISSOLVE_PIXEL_SIZE = add(new Param.Int("dissolve", "pixelSize", 4, 32, 8));
    public static final Param.Float DISSOLVE_SOUND = add(new Param.Float("dissolve", "soundVolume", 0f, 1f, 0.6f));

    public static final Param.Int VORTEX_DURATION = add(new Param.Int("vortex", "vortexDuration", 6, 200, 24));
    public static final Param.Float VORTEX_TURNS = add(new Param.Float("vortex", "spiralTurns", 0.25f, 6f, 2f));
    public static final Param.Float VORTEX_PULL = add(new Param.Float("vortex", "pullStrength", 0.3f, 3f, 1f));
    public static final Param.Float VORTEX_SPIN = add(new Param.Float("vortex", "spinSpeed", 0f, 1f, 0.3f));
    public static final Param.Choice VORTEX_TARGET = add(new Param.Choice("vortex", "targetPoint", "center",
            "center", "source", "player"));
    public static final Param.Float VORTEX_SOUND = add(new Param.Float("vortex", "soundVolume", 0f, 1f, 0.6f));

    static final Map<String, BundledParams.Set> BUNDLED = new LinkedHashMap<>();

    static {
        for (Map.Entry<String, Integer> entry : BundledParams.DURATIONS.entrySet()) {
            String id = entry.getKey();
            BUNDLED.put(id, new BundledParams.Set(
                    shared(new Param.Int(id, "duration", 4, BundledParams.quick(id) ? 40 : 400, entry.getValue())),
                    shared(new Param.Float(id, "intensity", 0.25f, 2f, 1f)),
                    shared(new Param.Float(id, "particles", 0f, 3f, 1f)),
                    shared(new Param.Float(id, "soundVolume", 0f, 1f, 0.6f))));
        }
    }

    private Params() {
    }

    private static <P extends Param<?>> P shared(P param) {
        param.sharedLang(BundledParams.LANG_GROUP);
        return add(param);
    }

    private static <P extends Param<?>> P add(P param) {
        GROUPS.computeIfAbsent(param.group(), g -> new ArrayList<>()).add(param);
        return param;
    }

    public static Set<String> groups() {
        return Collections.unmodifiableSet(GROUPS.keySet());
    }

    public static List<Param<?>> group(String group) {
        return Collections.unmodifiableList(GROUPS.getOrDefault(group, List.of()));
    }

    public static boolean retired(String group, String id) {
        return RETIRED.getOrDefault(group, Set.of()).contains(id);
    }

    public static Param<?> find(String group, String id) {
        for (Param<?> param : GROUPS.getOrDefault(group, List.of())) {
            if (param.id().equals(id)) {
                return param;
            }
        }
        return null;
    }
}
