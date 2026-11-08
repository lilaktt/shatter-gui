package net.shattergui.fx;

public final class FxLimits {
    public static final int SCHEMA = 2;
    public static final int MAX_JSON_BYTES = 65_536;
    public static final int MIN_DURATION = 2;
    public static final int MAX_DURATION = 400;
    public static final int MAX_PIECES = 1500;
    public static final int MAX_TRIANGLES = 3000;
    public static final int MAX_VORONOI_CELLS = 320;
    public static final int MAX_LAYERS = 16;
    public static final int MAX_EMITTERS = 8;
    public static final int MAX_SOUNDS = 16;
    public static final int MAX_KEYS = 32;
    public static final int MAX_GRADIENT_STOPS = 16;
    public static final int MAX_OVERLAYS = 4;
    public static final int MAX_PATH_POINTS = 8;
    public static final int MAX_TEXTURES = 16;
    public static final int MAX_TEXTURE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_SPRITE_SIZE = 256;
    public static final int MAX_OVERLAY_SIZE = 512;
    public static final int MAX_LOADED_TEXTURES = 64;
    public static final int MAX_SPRITES_PER_WAVE = 400;
    public static final int MAX_SPRITES_TOTAL = 1200;
    public static final int MAX_VANILLA_PER_TICK = 24;
    public static final int MAX_VANILLA_PER_WAVE = 600;
    public static final int MAX_PARTICLE_LIFE = 200;
    public static final int MAX_COLLIDING_PIECES = 600;
    public static final int MAX_PACKAGE_ENTRIES = 64;
    public static final long MAX_PACKAGE_BYTES = 4L * 1024 * 1024;

    private FxLimits() {
    }
}
