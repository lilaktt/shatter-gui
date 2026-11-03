package net.shattergui.fx;

import java.util.List;
import java.util.Map;

/**
 * A schema-2 effect, as data: how the interface breaks (fracture), when each piece starts (trigger), what moves it
 * (layers of modules over time), how it looks (appearance), particles, sounds and an optional edge flight. Built by
 * {@link FxParser}; every number is already validated and clamped.
 */
public final class FxDef {
    /** A point: the GUI centre, the block it was opened from, in front of the player, or fixed GUI fractions (u right, v down). */
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
        /** Cuts of fewer pieces than this are designed (a door's two halves, blinds): the quality leaves them as they are. */
        static final int MIN_SCALED_PIECES = 24;

        /**
         * The same cut with about {@code density} times as many pieces (effect quality). Only cuts into many similar
         * pieces change: voronoi and grid by count, grids of at least {@link #MIN_SCALED_PIECES} cells, pixels.
         */
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

    /**
     * Which pieces a layer acts on. group: all, wave (trigger value in a..b), edge (within a of the border), random
     * (chance a), region, outside, alternate (every other piece in cutting order: alternate strips).
     */
    public record Target(String group, float a, float b, int seed, boolean invert) {
        public static final List<String> GROUPS = List.of("all", "wave", "edge", "random", "region", "outside", "alternate");
        public static final Target ALL = new Target("all", 0, 1, 0, false);
    }

    /**
     * One module acting on its target during a window of the time base.
     *
     * @param rest collide: what a piece does once it lies still on a block, "lie" (until its time is over) or "fade"
     *             (fades out at once)
     * @param axis spin: the in-plane axis of the tumble (radians from "right" towards "up": π/2 turns like a card
     *             about the vertical), or NaN for a random axis per piece
     * @param pivot spin: a point of the interface (u, v: 0..1 from the left and from the top) the tumble axis goes
     *              through, like the hinges of a door; null: through each piece's centre
     */
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

    /**
     * @param palette colours one of which each piece gets at random, multiplied with {@code color} and blended in over
     *                the first 15 % of the piece's time (empty: none)
     * @param mosaic  0..1 over the whole effect's time: each piece shows the colour of its centre (coarse pixels)
     */
    public record Appearance(Gradient color, boolean colorGlobal, Value alpha, Value emissive, Gradient emissiveColor,
                             boolean fullbright, List<Overlay> overlays, Mask mask, List<Integer> palette, Value mosaic) {
    }

    public record Emitter(String particle, String sprite, Value rate, int burst, float from, float to, String zone,
                          Point at, Point lineFrom, Point lineTo, float[] velocity, Value spread, Value life,
                          float gravity, float drag, Gradient color, Value size, Value alpha, boolean emissive, int max) {
        public static final List<String> ZONES = List.of("surface", "edge", "front", "point", "line");
    }

    /** at: "start", "end" or "time" (then {@code time}, 0..1 of the effect). */
    public record Sound(String sound, float volume, float pitch, String at, float time) {
    }

    public record Edge(String direction, boolean enter, boolean exit, float distance, float enterTime, float exitStart,
                       Easing curve) {
        public static final List<String> DIRECTIONS = List.of("left", "right", "top", "bottom", "random");
    }

    /** What a texture is used for (decides its size limit). */
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
    /** Every texture the effect uses, with its role. */
    public final Map<String, TextureRole> textures;
    /** The JSON it was read from (for export as a code). */
    public final String json;
    /** It can also open a screen: played backwards, the pieces fly together into the interface. */
    public final boolean openable;
    /** The version for "reduce motion" (the effect's "reduced" overrides applied), or null for the same. */
    FxDef reduced;

    FxDef(String id, String name, String description, int duration, Fracture fracture, Trigger trigger,
          List<Layer> layers, Appearance appearance, List<Emitter> emitters, List<Sound> sounds, Edge edge,
          Map<String, TextureRole> textures, String json, boolean openable) {
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
    }

    /** The definition to run: its "reduced" variant when the player asked for less motion and it has one. */
    public FxDef forReducedMotion(boolean reduceMotion) {
        return reduceMotion && reduced != null ? reduced : this;
    }

    public boolean hasReducedVariant() {
        return reduced != null;
    }
}
