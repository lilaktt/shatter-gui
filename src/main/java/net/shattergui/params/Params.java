package net.shattergui.params;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of every setting, grouped by effect id plus {@link #GENERAL}. Defaults are the values the mod always had;
 * ranges are hard limits that also apply to anything read from a config or typed into the editor.
 */
public final class Params {
    public static final String GENERAL = "general";

    private static final Map<String, List<Param<?>>> GROUPS = new LinkedHashMap<>();
    /** Parameters that existed once and were removed: old configs and effect files may still have them; skipped silently. */
    private static final Map<String, Set<String>> RETIRED = Map.of("vortex", Set.of("flashIntensity", "ringParticles"));

    // --- general ---
    /** Multiplier for the number of particles of every effect. */
    public static final Param.Float PARTICLE_DENSITY = add(new Param.Float(GENERAL, "particleDensity", 0f, 5f, 1f));
    /** Playback speed of every effect: durations are divided by it. */
    public static final Param.Float EFFECT_SPEED = add(new Param.Float(GENERAL, "effectSpeed", 0.25f, 4f, 1f));
    /** Speed of openings only (effects played backwards, the fly-out): their length is divided by it, see OpenTiming. */
    public static final Param.Float OPEN_SPEED = add(new Param.Float(GENERAL, "openSpeed", 0.5f, 3f, 1.5f));
    /** Volume of all sounds of the mod (multiplies each effect's own volume). */
    public static final Param.Float SOUND_VOLUME = add(new Param.Float(GENERAL, "soundVolume", 0f, 1f, 1f));
    /** Live shards of effects that count towards the limit (glass, fly). */
    public static final Param.Int MAX_SHARDS = add(new Param.Int(GENERAL, "maxShards", 1, 10_000, 500));

    // --- glass ---
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

    // --- burn ---
    public static final Param.Int BURN_DURATION = add(new Param.Int("burn", "duration", 5, 600, 30));
    public static final Param.Int BURN_SMOKE_DURATION = add(new Param.Int("burn", "smokeDuration", 0, 1200, 60));
    public static final Param.Float BURN_FLAME_DENSITY = add(new Param.Float("burn", "flameDensity", 0f, 5f, 1f));
    public static final Param.Float BURN_SMOKE_DENSITY = add(new Param.Float("burn", "smokeDensity", 0f, 1f, 0.35f));
    public static final Param.Float BURN_SOUND = add(new Param.Float("burn", "soundVolume", 0f, 1f, 0.6f));

    // --- fly ---
    public static final Param.Int FLY_DURATION = add(new Param.Int("fly", "duration", 1, 100, 6));
    public static final Param.Choice FLY_EDGE_DIRECTION = add(new Param.Choice("fly", "edgeDirection", "random",
            "random", "top", "bottom", "left", "right"));
    public static final Param.Float FLY_SOUND = add(new Param.Float("fly", "soundVolume", 0f, 1f, 0.6f));

    // --- dissolve ---
    public static final Param.Int DISSOLVE_DURATION = add(new Param.Int("dissolve", "duration", 2, 600, 20));
    public static final Param.Choice DISSOLVE_ORIGIN = add(new Param.Choice("dissolve", "origin", "random",
            "random", "edge", "center"));
    public static final Param.Int DISSOLVE_PIXEL_SIZE = add(new Param.Int("dissolve", "pixelSize", 4, 32, 8));
    public static final Param.Float DISSOLVE_SOUND = add(new Param.Float("dissolve", "soundVolume", 0f, 1f, 0.6f));

    // --- vortex ---
    public static final Param.Int VORTEX_DURATION = add(new Param.Int("vortex", "vortexDuration", 6, 200, 24));
    public static final Param.Float VORTEX_TURNS = add(new Param.Float("vortex", "spiralTurns", 0.25f, 6f, 2f));
    public static final Param.Float VORTEX_PULL = add(new Param.Float("vortex", "pullStrength", 0.3f, 3f, 1f));
    public static final Param.Float VORTEX_SPIN = add(new Param.Float("vortex", "spinSpeed", 0f, 1f, 0.3f));
    public static final Param.Choice VORTEX_TARGET = add(new Param.Choice("vortex", "targetPoint", "center",
            "center", "source", "player"));
    public static final Param.Float VORTEX_SOUND = add(new Param.Float("vortex", "soundVolume", 0f, 1f, 0.6f));

    // --- effects shipped as data files (assets/shatter_gui/effects): the same four settings each ---
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

    /** Group ids in registration order: "general", then the effects. */
    public static Set<String> groups() {
        return Collections.unmodifiableSet(GROUPS.keySet());
    }

    /** The parameters of a group, or an empty list. */
    public static List<Param<?>> group(String group) {
        return Collections.unmodifiableList(GROUPS.getOrDefault(group, List.of()));
    }

    /** Whether {@code id} is a removed parameter of the group (ignored without a warning). */
    public static boolean retired(String group, String id) {
        return RETIRED.getOrDefault(group, Set.of()).contains(id);
    }

    /** A parameter by group and id, or null. */
    public static Param<?> find(String group, String id) {
        for (Param<?> param : GROUPS.getOrDefault(group, List.of())) {
            if (param.id().equals(id)) {
                return param;
            }
        }
        return null;
    }
}
