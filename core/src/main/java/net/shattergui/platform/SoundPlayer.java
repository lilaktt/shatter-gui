package net.shattergui.platform;

import java.util.Collection;

/** Sounds of the UI and of the editor's preview, played as UI sounds (not positional). */
public interface SoundPlayer {
    /** The game's button click. */
    void buttonClick();

    /** The click of a pop-up closing: quieter and a little higher than {@link #buttonClick}. */
    void closeClick();

    /** The sound's id written as the game writes it ({@code "minecraft:block.glass.break"}), or null if it is not a valid id. */
    String normalize(String id);

    /** Whether the sound is registered ({@code id} as written in effect files; null and invalid ids: false). */
    boolean exists(String id);

    /** Every registered sound id, in the registry's order. */
    Collection<String> ids();

    /** Plays a registered sound as a UI sound; does nothing for an unknown one. */
    void play(String id, float pitch, float volume);
}
