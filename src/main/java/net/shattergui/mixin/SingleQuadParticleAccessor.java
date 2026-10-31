package net.shattergui.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code SingleQuadParticle.alpha} is protected; burn fades its own particles out before the effect ends. */
@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleAccessor {
    @Accessor("alpha")
    float shatterGui$getAlpha();

    @Accessor("alpha")
    void shatterGui$setAlpha(float alpha);
}
