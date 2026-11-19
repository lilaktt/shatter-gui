package net.shattergui.fx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FxSchema {
    public enum Unit {
        NONE,
        FRACTION,
        FACTOR,
        BLOCKS,
        SPEED,
        ACCEL,
        ANGLE,
        TURN,
        TICKS,
        RATE,
        COUNT,
        SWIRL,
        PIXELS
    }

    public record Field(String key, float def, float min, float max, Unit unit) {
        public boolean whole() {
            return unit == Unit.COUNT || unit == Unit.TICKS && key.startsWith("effect.") || unit == Unit.PIXELS;
        }
    }

    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();

    public static final Map<FxDef.Module, List<Field>> MODULE_PARAMS = new LinkedHashMap<>();

    static {
        add("effect.duration", 40, FxLimits.MIN_DURATION, FxLimits.MAX_DURATION, Unit.TICKS);
        add("fracture.count", 60, 1, FxLimits.MAX_PIECES, Unit.COUNT);
        add("fracture.voronoiCount", 60, 1, FxLimits.MAX_VORONOI_CELLS, Unit.COUNT);
        add("fracture.radialCount", 12, 1, FxLimits.MAX_PIECES, Unit.COUNT);
        add("fracture.cols", 0, 0, 64, Unit.COUNT);
        add("fracture.rows", 0, 0, 64, Unit.COUNT);
        add("fracture.size", 8, 2, 64, Unit.PIXELS);
        add("fracture.jitter", 0.35f, 0, 1, Unit.FRACTION);
        add("fracture.segments", 1, 1, 32, Unit.COUNT);
        add("fracture.rings", 3, 1, 12, Unit.COUNT);
        add("trigger.scale", 4, 0.5f, 32, Unit.FACTOR);
        add("trigger.noise", 0, 0, 1, Unit.FRACTION);
        add("trigger.spread", 0.5f, 0, 0.95f, Unit.FRACTION);
        add("layer.from", 0, 0, 1, Unit.FRACTION);
        add("layer.to", 1, 0, 1, Unit.FRACTION);
        add("target.from", 0, 0, 1, Unit.FRACTION);
        add("target.to", 1, 0, 1, Unit.FRACTION);
        add("target.width", 0.15f, 0, 0.5f, Unit.FRACTION);
        add("target.chance", 0.5f, 0, 1, Unit.FRACTION);
        add("target.seed", 0, 0, 1_000_000, Unit.COUNT);
        module(FxDef.Module.STAY);
        module(FxDef.Module.LAUNCH, add("launch.speed", 0.08f, 0, 1, Unit.SPEED),
                add("launch.spread", 0.4f, 0, (float) Math.PI, Unit.ANGLE), add("launch.out", 0.02f, -0.5f, 0.5f, Unit.SPEED));
        module(FxDef.Module.GRAVITY, add("gravity.strength", 0.04f, -0.5f, 0.5f, Unit.ACCEL));
        module(FxDef.Module.DRAG, add("drag.amount", 0.05f, 0, 1, Unit.FRACTION));
        module(FxDef.Module.ATTRACT, add("attract.strength", 0.02f, -0.5f, 0.5f, Unit.ACCEL),
                add("attract.swirl", 0, -5, 5, Unit.SWIRL), add("attract.absorb", 0, 0, 1, Unit.FRACTION));
        module(FxDef.Module.DRIFT, add("drift.amount", 0.01f, 0, 0.3f, Unit.SPEED),
                add("drift.frequency", 0.1f, 0, 2, Unit.FACTOR), add("drift.out", 0, 0, 0.3f, Unit.SPEED));
        module(FxDef.Module.SPIN, add("spin.speed", 0.2f, -2, 2, Unit.TURN), add("spin.tumble", 0, -2, 2, Unit.TURN));
        module(FxDef.Module.SCALE, add("scale.value", 1, 0, 4, Unit.FACTOR));
        module(FxDef.Module.FOLLOW_PATH, add("follow-path.progress", 0, 0, 1, Unit.FRACTION));
        module(FxDef.Module.FORCE, add("force.x", 0, -0.2f, 0.2f, Unit.ACCEL), add("force.y", 0, -0.2f, 0.2f, Unit.ACCEL),
                add("force.z", 0, -0.2f, 0.2f, Unit.ACCEL));
        module(FxDef.Module.COLLIDE, add("collide.bounce", 0.35f, 0, 1, Unit.FRACTION),
                add("collide.friction", 0.4f, 0, 1, Unit.FRACTION));
        add("spin.axis", (float) (Math.PI / 2), 0, (float) Math.PI, Unit.ANGLE);
        add("spin.turn", 0, (float) -Math.PI * 2, (float) Math.PI * 2, Unit.ANGLE);
        add("appearance.alpha", 1, 0, 1, Unit.FRACTION);
        add("appearance.emissive", 0, 0, 1, Unit.FRACTION);
        add("appearance.mosaic", 0, 0, 1, Unit.FRACTION);
        add("overlay.opacity", 1, 0, 1, Unit.FRACTION);
        add("overlay.scroll", 0, -10, 10, Unit.FACTOR);
        add("overlay.repeat", 1, 0.05f, 32, Unit.FACTOR);
        add("mask.threshold", 0, 0, 1, Unit.FRACTION);
        add("mask.softness", 0.1f, 0.001f, 1, Unit.FRACTION);
        add("emitter.rate", 1, 0, 50, Unit.RATE);
        add("emitter.burst", 0, 0, 200, Unit.COUNT);
        add("emitter.from", 0, 0, 1, Unit.FRACTION);
        add("emitter.to", 1, 0, 1, Unit.FRACTION);
        add("emitter.velocity", 0, -1, 1, Unit.SPEED);
        add("emitter.spread", 0.01f, 0, 0.5f, Unit.SPEED);
        add("emitter.life", 20, 1, FxLimits.MAX_PARTICLE_LIFE, Unit.TICKS);
        add("emitter.gravity", 0, -0.2f, 0.2f, Unit.ACCEL);
        add("emitter.drag", 0.02f, 0, 1, Unit.FRACTION);
        add("emitter.size", 0.06f, 0, 1, Unit.BLOCKS);
        add("emitter.alpha", 1, 0, 1, Unit.FRACTION);
        add("emitter.max", 200, 1, FxLimits.MAX_SPRITES_PER_WAVE, Unit.COUNT);
        add("sound.volume", 1, 0, 1, Unit.FRACTION);
        add("sound.pitch", 1, 0.5f, 2, Unit.FACTOR);
        add("sound.time", 0, 0, 1, Unit.FRACTION);
        add("edge.distance", 1.3f, 0.1f, 4, Unit.FACTOR);
        add("edge.enterTime", 0.25f, 0.01f, 1, Unit.FRACTION);
        add("edge.exitStart", 0.4f, 0, 0.99f, Unit.FRACTION);
    }

    private FxSchema() {
    }

    private static Field add(String key, float def, float min, float max, Unit unit) {
        Field field = new Field(key, def, min, max, unit);
        FIELDS.put(key, field);
        return field;
    }

    private static void module(FxDef.Module module, Field... params) {
        MODULE_PARAMS.put(module, List.of(params));
    }

    public static Field field(String key) {
        return FIELDS.get(key);
    }

    public static String paramName(Field field) {
        return field.key().substring(field.key().indexOf('.') + 1);
    }

    public static String moduleName(FxDef.Module module) {
        return FxDef.Module.NAMES.get(module.ordinal());
    }
}
