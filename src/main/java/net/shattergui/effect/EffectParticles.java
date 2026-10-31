package net.shattergui.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.Vec3;

/**
 * Spawning particles for the effects. Vanilla particles have a fixed world size; the flat GUI is one block from the
 * camera, so particles are shrunk (and slowed) by how close they are, like in burn. Respects the game's particle setting.
 */
public final class EffectParticles {
    private static final double FULL_SIZE_DISTANCE = 4.0;
    private static final float MIN_SIZE = 0.15f;
    /** While an effect is recorded ahead of time (to play it backwards): no particles and no sounds. */
    private static boolean muted;

    private EffectParticles() {
    }

    /** False with particles set to "minimal", or while muted. */
    public static boolean allowed() {
        return !muted && Minecraft.getInstance().options.particles().get() != ParticleStatus.MINIMAL;
    }

    /** Recording an effect ahead of time ({@link ReversedEffect}): its particles and sounds are not wanted. */
    public static boolean muted() {
        return muted;
    }

    static void mute(boolean on) {
        muted = on;
    }

    /** 1 at {@link #FULL_SIZE_DISTANCE} and beyond, smaller closer to the camera. */
    public static float sizeAt(Vec3 point) {
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceTo(point);
        return (float) Math.clamp(distance / FULL_SIZE_DISTANCE, MIN_SIZE, 1.0);
    }

    /** Spawns one particle, its velocity and size multiplied by {@code size}; null if the engine refused it. */
    public static Particle spawn(ParticleOptions type, Vec3 pos, double vx, double vy, double vz, float size) {
        if (muted) {
            return null;
        }
        Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z,
                vx * size, vy * size, vz * size);
        if (particle != null && size != 1f) {
            particle.scale(size);
        }
        return particle;
    }
}
