package net.shattergui.platform.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.shattergui.effect.EffectParticles;
import net.shattergui.platform.ParticleSpawner;
import net.shattergui.platform.SoundPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Sounds and particles: the game's registries, its sound manager and its particle setting. */
final class Registries121 {
    private Registries121() {
    }

    static final class Sounds implements SoundPlayer {
        @Override
        public void buttonClick() {
            // The sound of 1.21.1's buttons (AbstractWidget#playDownSound).
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }

        @Override
        public void closeClick() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f, 0.35f));
        }

        @Override
        public String normalize(String id) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            return parsed == null ? null : parsed.toString();
        }

        @Override
        public boolean exists(String id) {
            ResourceLocation parsed = id == null ? null : ResourceLocation.tryParse(id);
            return parsed != null && BuiltInRegistries.SOUND_EVENT.containsKey(parsed);
        }

        @Override
        public Collection<String> ids() {
            List<String> out = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
                out.add(id.toString());
            }
            return out;
        }

        @Override
        public void play(String id, float pitch, float volume) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            SoundEvent event = parsed == null ? null : BuiltInRegistries.SOUND_EVENT.get(parsed);
            if (event != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
            }
        }
    }

    static final class Particles implements ParticleSpawner {
        @Override
        public Simple resolveSimple(String id) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null && BuiltInRegistries.PARTICLE_TYPE.getOptional(parsed).orElse(null) instanceof SimpleParticleType type) {
                return new Simple(parsed.toString(), parsed.getPath(), type);
            }
            return null;
        }

        @Override
        public Collection<String> simpleIds() {
            List<String> out = new ArrayList<>();
            for (var entry : BuiltInRegistries.PARTICLE_TYPE.entrySet()) {
                if (entry.getValue() instanceof SimpleParticleType) {
                    out.add(entry.getKey().location().toString());
                }
            }
            return out;
        }

        @Override
        public boolean allowed() {
            return EffectParticles.allowed();
        }
    }
}
