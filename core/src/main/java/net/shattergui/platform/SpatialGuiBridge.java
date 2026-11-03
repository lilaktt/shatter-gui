package net.shattergui.platform;

/** Spatial GUI (the screen on a plane in the world), if installed: its plane is broken instead of the flat picture. */
public interface SpatialGuiBridge {
    /** Drops a break of its plane that waits for the next frame (the master switch went off). */
    void cancelPending();
}
