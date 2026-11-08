package net.shattergui.platform.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.shattergui.effect.EffectParticles;
import net.shattergui.platform.ParticleSpawner;
import net.shattergui.platform.SoundPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class Registries26 {
    private Registries26() {
    }

    static final class Sounds implements SoundPlayer {
        @Override
        public void buttonClick() {
            AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
        }

        @Override
        public void closeClick() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f, 0.35f));
        }

        @Override
        public String normalize(String id) {
            Identifier parsed = Identifier.tryParse(id);
            return parsed == null ? null : parsed.toString();
        }

        @Override
        public boolean exists(String id) {
            Identifier parsed = id == null ? null : Identifier.tryParse(id);
            return parsed != null && BuiltInRegistries.SOUND_EVENT.containsKey(parsed);
        }

        @Override
        public Collection<String> ids() {
            List<String> out = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
                out.add(id.toString());
            }
            return out;
        }

        @Override
        public void play(String id, float pitch, float volume) {
            Identifier parsed = Identifier.tryParse(id);
            SoundEvent event = parsed == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(parsed);
            if (event != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
            }
        }
    }

    static final class Particles implements ParticleSpawner {
        @Override
        public Simple resolveSimple(String id) {
            Identifier parsed = Identifier.tryParse(id);
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
                    out.add(entry.getKey().identifier().toString());
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
