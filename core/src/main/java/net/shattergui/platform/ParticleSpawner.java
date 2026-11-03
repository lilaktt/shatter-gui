package net.shattergui.platform;

import java.util.Collection;

/**
 * Particles as the effect files and the editor see them: the registry of particle types without options (the only
 * ones effects may use) and the player's particle setting. Spawning in the world belongs to the effects of the
 * platform; {@link #resolveSimple} hands them the game's particle type.
 */
public interface ParticleSpawner {
    /** A particle type without options: its id as the game writes it, its path, and the game's type object. */
    record Simple(String id, String path, Object type) {
    }

    /** The registered particle without options that {@code id} names, or null (unknown, has options, invalid id). */
    Simple resolveSimple(String id);

    /** Ids of every registered particle without options, in the registry's order. */
    Collection<String> simpleIds();

    /** False with particles set to "minimal" (and while an effect is recorded ahead of time). */
    boolean allowed();
}
