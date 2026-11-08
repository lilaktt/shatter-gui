package net.shattergui.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.Vec3;

public final class EffectParticles {
    private static final double FULL_SIZE_DISTANCE = 4.0;
    private static final float MIN_SIZE = 0.15f;
    private static boolean muted;

    private EffectParticles() {
    }

    public static boolean allowed() {
        return !muted && Minecraft.getInstance().options.particles().get() != ParticleStatus.MINIMAL;
    }

    public static boolean muted() {
        return muted;
    }

    static void mute(boolean on) {
        muted = on;
    }

    public static float sizeAt(Vec3 point) {
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(point);
        return (float) Math.clamp(distance / FULL_SIZE_DISTANCE, MIN_SIZE, 1.0);
    }

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
