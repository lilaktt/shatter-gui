package net.shattergui.params;

import net.shattergui.config.ShatterConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * The values an effect runs with, taken once when it starts (the saved config, or the editor's unsaved values for a
 * preview), so later changes do not alter an effect that is already running. Every value is already sanitized.
 */
public final class ParamValues {
    private final Map<Param<?>, Object> values;
    private final Preset preset;
    private final Quality quality;
    private final boolean reduceMotion;

    /** With the quality that goes with the preset (previews). */
    public ParamValues(Map<Param<?>, Object> values, Preset preset, boolean reduceMotion) {
        this(values, preset, preset.quality(), reduceMotion);
    }

    public ParamValues(Map<Param<?>, Object> values, Preset preset, Quality quality, boolean reduceMotion) {
        this.values = new HashMap<>(values);
        this.preset = preset;
        this.quality = quality;
        this.reduceMotion = reduceMotion;
    }

    /** The saved settings. */
    public static ParamValues current() {
        return ShatterConfig.get().values();
    }

    /** A copy with some values replaced (a custom effect's own parameters); they are sanitized again. */
    public ParamValues with(Map<Param<?>, Object> overrides) {
        Map<Param<?>, Object> merged = new HashMap<>(values);
        for (Map.Entry<Param<?>, Object> entry : overrides.entrySet()) {
            merged.put(entry.getKey(), entry.getKey().sanitize(entry.getValue()));
        }
        return new ParamValues(merged, preset, quality, reduceMotion);
    }

    /** The same values with another preset and the quality that goes with it (the editor's preset cards). */
    public ParamValues withPreset(Preset other) {
        return new ParamValues(values, other, other.quality(), reduceMotion);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Param<T> param) {
        Object value = values.get(param);
        return value != null ? (T) value : param.def();
    }

    public float f(Param.Float param) {
        return get(param);
    }

    public int i(Param.Int param) {
        return get(param);
    }

    public boolean b(Param.Bool param) {
        return get(param);
    }

    public String s(Param.Choice param) {
        return get(param);
    }

    public Preset preset() {
        return preset;
    }

    public boolean reduceMotion() {
        return reduceMotion;
    }

    public Quality quality() {
        return quality;
    }

    /** False on low quality: no block collisions, no glow layer and no texture overlays. */
    public boolean fullQuality() {
        return quality.full();
    }

    /** Multiplier for every duration: shorter with reduceMotion and a higher effectSpeed, scaled by the preset. */
    public float durationScale() {
        return (reduceMotion ? 0.65f : 1f) / f(Params.EFFECT_SPEED) * preset.duration();
    }

    /** A duration parameter in ticks after {@link #durationScale}, at least 1. */
    public int ticks(Param.Int param) {
        return Math.max(1, Math.round(i(param) * durationScale()));
    }

    /** Multiplier for particle counts: particleDensity, halved with reduceMotion, scaled by the quality. */
    public float particleScale() {
        return f(Params.PARTICLE_DENSITY) * (reduceMotion ? 0.5f : 1f) * quality.particles();
    }

    /** Multiplier for the number of shards (quality). */
    public float shardScale() {
        return quality.shards();
    }

    /** Every value in registry order, as text: equal for equal settings (the editor restarts previews on change). */
    public String signature() {
        StringBuilder sb = new StringBuilder();
        for (String group : Params.groups()) {
            for (Param<?> param : Params.group(group)) {
                sb.append(get(param)).append(',');
            }
        }
        return sb.toString();
    }

    /** General volume × an effect's own volume. */
    public float volume(Param.Float effectVolume) {
        return f(Params.SOUND_VOLUME) * f(effectVolume);
    }
}
