package net.shattergui.platform;

/** The shards in the world: built from captures, simulated each tick and drawn by the platform. */
public interface ShardRenderer {
    /** Drops every running effect at once (the master switch went off). */
    void clear();
}
