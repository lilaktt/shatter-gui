package net.shattergui.platform;

/** Shader packs (Iris), if installed: with a pack in use the shards are drawn with the game's own types. */
public interface ShaderCompat {
    /** A shader pack is in use right now. */
    boolean shaderPackInUse();
}
