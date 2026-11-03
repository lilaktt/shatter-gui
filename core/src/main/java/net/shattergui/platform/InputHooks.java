package net.shattergui.platform;

/**
 * Where the game's screens and input reach the mod: a screen closing or opening (the platform's hooks into the game),
 * using a block or entity that opens a screen (the fly-out opening), and the input held back while an opening plays.
 */
public interface InputHooks {
    /** Ends a fly-out opening at once, showing its screen (the master switch went off). */
    void stopOpening();
}
