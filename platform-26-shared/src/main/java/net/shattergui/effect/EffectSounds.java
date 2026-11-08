package net.shattergui.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

public final class EffectSounds {
    private EffectSounds() {
    }

    public static void play(SoundEvent sound, float volume, float pitch) {
        if (volume <= 0f || sound == null || EffectParticles.muted()) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, Math.clamp(pitch, 0.5f, 2f),
                Math.clamp(volume, 0f, 1f)));
    }
}
