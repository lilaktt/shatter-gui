package net.shattergui.test;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.shattergui.ShatterMod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What a scenario can do, from the test thread: run code on the client thread, wait for client ticks, take
 * screenshots, check conditions. A small stand-in for Fabric's client game test context, which Fabric API for 1.21.1
 * does not have.
 */
final class TestContext {
    private static final AtomicLong TICKS = new AtomicLong();
    private static final AtomicLong FRAMES = new AtomicLong();
    /** Nanoseconds between the last frames (ring), for the freeze check. */
    private static final long[] FRAME_GAPS = new long[64];
    private static long lastFrame;

    private final List<String> failures = new ArrayList<>();
    private int checks;

    /** END_CLIENT_TICK. */
    static void onTick() {
        synchronized (TICKS) {
            TICKS.incrementAndGet();
            TICKS.notifyAll();
        }
    }

    /** Every frame (HUD callback). */
    static void onFrame() {
        long now = System.nanoTime();
        long n = FRAMES.incrementAndGet();
        if (lastFrame != 0) {
            FRAME_GAPS[(int) (n % FRAME_GAPS.length)] = now - lastFrame;
        }
        lastFrame = now;
    }

    static long frames() {
        return FRAMES.get();
    }

    /** The longest gap between two frames among the frames after {@code fromFrame} (ms), at most 60 frames back. */
    static double longestFrameGapSince(long fromFrame) {
        long now = FRAMES.get();
        long worst = 0;
        for (long n = Math.max(fromFrame + 1, now - FRAME_GAPS.length + 1); n <= now; n++) {
            worst = Math.max(worst, FRAME_GAPS[(int) (n % FRAME_GAPS.length)]);
        }
        return worst / 1e6;
    }

    void runOnClient(Consumer<Minecraft> task) {
        computeOnClient(mc -> {
            task.accept(mc);
            return null;
        });
    }

    <T> T computeOnClient(Function<Minecraft, T> task) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<T> future = mc.submit(() -> task.apply(mc));
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("client task failed", e);
        }
    }

    void waitTick() {
        waitTicks(1);
    }

    void waitTicks(int ticks) {
        synchronized (TICKS) {
            long until = TICKS.get() + ticks;
            while (TICKS.get() < until) {
                try {
                    TICKS.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /** Waits until {@code condition} holds on the client, at most {@code maxTicks}; false if it never did. */
    boolean waitFor(Predicate<Minecraft> condition, int maxTicks) {
        for (int t = 0; t < maxTicks; t++) {
            if (computeOnClient(condition::test)) {
                return true;
            }
            waitTick();
        }
        return computeOnClient(condition::test);
    }

    /** The last frame, saved to screenshots/<name>.png of the run folder. */
    void shot(String name) {
        runOnClient(mc -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {
        }));
    }

    /** The last frame as a picture (close it). */
    NativeImage frame() {
        return computeOnClient(mc -> Screenshot.takeScreenshot(mc.getMainRenderTarget()));
    }

    void check(boolean ok, String what) {
        checks++;
        if (ok) {
            ShatterMod.LOGGER.info("[ShatterTest] OK   {}", what);
        } else {
            failures.add(what);
            ShatterMod.LOGGER.error("[ShatterTest] FAIL {}", what);
        }
    }

    void log(String format, Object... args) {
        ShatterMod.LOGGER.info("[ShatterTest] " + format, args);
    }

    void command(String command) {
        runOnClient(mc -> mc.player.connection.sendCommand(command));
    }

    List<String> failures() {
        return failures;
    }

    int checks() {
        return checks;
    }
}
