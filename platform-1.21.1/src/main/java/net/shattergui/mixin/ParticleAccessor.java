package net.shattergui.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code Particle.alpha} is protected; burn fades its own particles out before the effect ends. (26.1 keeps the field in
 * {@code SingleQuadParticle}, 1.21.1 in {@code Particle}.)
 */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("alpha")
    float shatterGui$getAlpha();

    @Accessor("alpha")
    void shatterGui$setAlpha(float alpha);
}
