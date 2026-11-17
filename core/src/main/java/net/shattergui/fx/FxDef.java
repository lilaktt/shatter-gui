package net.shattergui.fx;

import java.util.List;
import java.util.Map;

public final class FxDef {
    public record Point(Kind kind, float u, float v) {
        public enum Kind {
            CENTER, SOURCE, PLAYER, FIXED
        }

        public static final Point CENTER = new Point(Kind.CENTER, 0.5f, 0.5f);
    }

    public enum Module {
        STAY, LAUNCH, GRAVITY, DRAG, ATTRACT, DRIFT, SPIN, SCALE, FOLLOW_PATH, FORCE, COLLIDE;

        public static final List<String> NAMES = List.of("stay", "launch", "gravity", "drag", "attract", "drift", "spin",
                "scale", "follow-path", "force", "collide");

        public static Module byName(String name) {
            int i = NAMES.indexOf(name.replace('_', '-'));
            return i < 0 ? null : values()[i];
        }
    }

    public record Fracture(String mode, int count, int cols, int rows, float size, float jitter, boolean split,
                           boolean vertical, int segments, int rings, Point center, float[] region) {
        public static final List<String> MODES = List.of("grid", "voronoi", "strips", "radial", "pixels");
        static final int MIN_SCALED_PIECES = 24;

        public Fracture withDensity(float density) {
            if (Math.abs(density - 1f) < 1e-3f) {
                return this;
            }
            float side = (float) Math.sqrt(density);
            return switch (mode) {
                case "voronoi" -> count >= MIN_SCALED_PIECES ? withSize(Math.max(MIN_SCALED_PIECES / 2, Math.round(count * density)),
                        cols, rows, size) : this;
                case "pixels" -> withSize(count, cols, rows, size / side);
                case "grid" -> {
                    if (cols > 0 && rows > 0) {
                        yield cols * rows >= MIN_SCALED_PIECES ? withSize(count, Math.max(2, Math.round(cols * side)),
                                Math.max(2, Math.round(rows * side)), size) : this;
                    }
                    yield count >= MIN_SCALED_PIECES ? withSize(Math.max(MIN_SCALED_PIECES / 2, Math.round(count * density)),
                            cols, rows, size) : this;
                }
                default -> this;
            };
        }

        private Fracture withSize(int count, int cols, int rows, float size) {
            return new Fracture(mode, count, cols, rows, size, jitter, split, vertical, segments, rings, center, region);
        }
    }

    public record Trigger(String type, float dirU, float dirV, Point from, float scale, String texture, boolean invert,
                          float noise, float spread, Easing curve) {
        public static final List<String> TYPES = List.of("none", "wave", "radial", "noise", "mask", "random");
    }

    public record Target(String group, float a, float b, int seed, boolean invert) {
        public static final List<String> GROUPS = List.of("all", "wave", "edge", "random", "region", "outside", "alternate");
        public static final Target ALL = new Target("all", 0, 1, 0, false);
    }

    public record Layer(Target target, Module module, float from, float to, boolean global, Map<String, Value> values,
                        Point point, float[][] path, boolean world, boolean once, String rest, float axis, float[] pivot) {
        public static final List<String> RESTS = List.of("lie", "fade");

        public Value value(String name) {
            return values.get(name);
        }
    }

    public record Overlay(String texture, String blend, Value opacity, Gradient color, float scrollU, float scrollV,
                          float repeatU, float repeatV) {
        public static final List<String> BLENDS = List.of("normal", "additive", "multiply");
    }

    public record Mask(String texture, Value threshold, float softness, boolean invert) {
    }

    public record Appearance(Gradient color, boolean colorGlobal, Value alpha, Value emissive, Gradient emissiveColor,
                             boolean fullbright, List<Overlay> overlays, Mask mask, List<Integer> palette, Value mosaic) {
    }

    public record Emitter(String particle, String sprite, Value rate, int burst, float from, float to, String zone,
                          Point at, Point lineFrom, Point lineTo, float[] velocity, Value spread, Value life,
                          float gravity, float drag, Gradient color, Value size, Value alpha, boolean emissive, int max) {
        public static final List<String> ZONES = List.of("surface", "edge", "front", "point", "line");
    }

    public record Sound(String sound, float volume, float pitch, String at, float time) {
    }

    public record Edge(String direction, boolean enter, boolean exit, float distance, float enterTime, float exitStart,
                       Easing curve) {
        public static final List<String> DIRECTIONS = List.of("left", "right", "top", "bottom", "random");
    }

    public enum TextureRole {
        SPRITE, OVERLAY, MASK
    }

    public final String id;
    public final String name;
    public final String description;
    public final int duration;
    public final Fracture fracture;
    public final Trigger trigger;
    public final List<Layer> layers;
    public final Appearance appearance;
    public final List<Emitter> emitters;
    public final List<Sound> sounds;
    public final Edge edge;
    public final Map<String, TextureRole> textures;
    public final String json;
    public final boolean openable;
    public final Boolean followCamera;
    public final boolean reassembleOnReopen;
    FxDef reduced;

    FxDef(String id, String name, String description, int duration, Fracture fracture, Trigger trigger,
          List<Layer> layers, Appearance appearance, List<Emitter> emitters, List<Sound> sounds, Edge edge,
          Map<String, TextureRole> textures, String json, boolean openable, Boolean followCamera,
          boolean reassembleOnReopen) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.duration = duration;
        this.fracture = fracture;
        this.trigger = trigger;
        this.layers = layers;
        this.appearance = appearance;
        this.emitters = emitters;
        this.sounds = sounds;
        this.edge = edge;
        this.textures = textures;
        this.json = json;
        this.openable = openable;
        this.followCamera = followCamera;
        this.reassembleOnReopen = reassembleOnReopen;
    }

    public FxDef forReducedMotion(boolean reduceMotion) {
        return reduceMotion && reduced != null ? reduced : this;
    }

    public boolean hasReducedVariant() {
        return reduced != null;
    }
}
