package net.shattergui.shatter;

import net.minecraft.client.Minecraft;

public final class EffectClock {
    private static final float NANOS_PER_TICK = 50_000_000f;
    private static long lastTickNanos = System.nanoTime();

    private EffectClock() {
    }

    public static void tick() {
        lastTickNanos = System.nanoTime();
    }

    public static float partialTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isPaused()) {
            return mc.getTimer().getGameTimeDeltaPartialTick(false);
        }
        return Math.clamp((System.nanoTime() - lastTickNanos) / NANOS_PER_TICK, 0f, 1f);
    }
}
