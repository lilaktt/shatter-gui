package net.shattergui.shatter;

import net.minecraft.client.Minecraft;

/**
 * Time of the opening animations (gathering waves, {@code OpenAssemble}, {@code FlyOpen}). They are the interface's own
 * appearance, not part of the world: they keep running while the game is paused. The pause menu pauses a singleplayer
 * game from its first tick; on game time its opening would never land and the menu would stay hidden.
 *
 * <p>Every client tick counts ({@code Minecraft#tick} and Fabric's {@code END_CLIENT_TICK} run while paused). The partial
 * tick is the game's own while it runs; while paused the game's is frozen ({@code DeltaTracker} returns the residual
 * of the moment it paused), so it is measured from the last tick in real time. Close effects keep game time: they pause
 * with the world, like vanilla particles.
 */
public final class EffectClock {
    private static final float NANOS_PER_TICK = 50_000_000f;
    private static long lastTickNanos = System.nanoTime();

    private EffectClock() {
    }

    /** END_CLIENT_TICK, before the waves tick. */
    public static void tick() {
        lastTickNanos = System.nanoTime();
    }

    /** Partial tick for drawing an opening: smooth also while the game is paused. */
    public static float partialTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isPaused()) {
            return mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        }
        return Math.clamp((System.nanoTime() - lastTickNanos) / NANOS_PER_TICK, 0f, 1f);
    }
}
