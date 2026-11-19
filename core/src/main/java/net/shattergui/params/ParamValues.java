package net.shattergui.params;

import net.shattergui.config.ShatterConfig;

import java.util.HashMap;
import java.util.Map;

public final class ParamValues {
    private final Map<Param<?>, Object> values;
    private final Preset preset;
    private final Quality quality;
    private final boolean reduceMotion;

    public ParamValues(Map<Param<?>, Object> values, Preset preset, boolean reduceMotion) {
        this(values, preset, preset.quality(), reduceMotion);
    }

    public ParamValues(Map<Param<?>, Object> values, Preset preset, Quality quality, boolean reduceMotion) {
        this.values = new HashMap<>(values);
        this.preset = preset;
        this.quality = quality;
        this.reduceMotion = reduceMotion;
    }

    public static ParamValues current() {
        return ShatterConfig.get().values();
    }

    public ParamValues with(Map<Param<?>, Object> overrides) {
        Map<Param<?>, Object> merged = new HashMap<>(values);
        for (Map.Entry<Param<?>, Object> entry : overrides.entrySet()) {
            merged.put(entry.getKey(), entry.getKey().sanitize(entry.getValue()));
        }
        return new ParamValues(merged, preset, quality, reduceMotion);
    }

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

    public boolean fullQuality() {
        return quality.full();
    }

    public float durationScale() {
        return (reduceMotion ? 0.65f : 1f) / f(Params.EFFECT_SPEED) * preset.duration();
    }

    public int ticks(Param.Int param) {
        return Math.max(1, Math.round(i(param) * durationScale()));
    }

    public float particleScale() {
        return f(Params.PARTICLE_DENSITY) * (reduceMotion ? 0.5f : 1f) * quality.particles();
    }

    public float shardScale() {
        return quality.shards();
    }

    public String signature() {
        StringBuilder sb = new StringBuilder();
        for (String group : Params.groups()) {
            for (Param<?> param : Params.group(group)) {
                sb.append(get(param)).append(',');
            }
        }
        return sb.toString();
    }

    public float volume(Param.Float effectVolume) {
        return f(Params.SOUND_VOLUME) * f(effectVolume);
    }
}
