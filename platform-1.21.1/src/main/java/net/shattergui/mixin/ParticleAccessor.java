package net.shattergui.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("alpha")
    float shatterGui$getAlpha();

    @Accessor("alpha")
    void shatterGui$setAlpha(float alpha);
}
