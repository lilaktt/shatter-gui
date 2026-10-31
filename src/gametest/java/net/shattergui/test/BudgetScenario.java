package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.shatter.ShardManager;

import java.util.function.BiConsumer;

/**
 * The limit of effects at once ({@code -PtestOnly=budget}): 20 quick opens and closes of the inventory in a row, with an
 * opening that gathers and a close effect (glass; burn, whose smoke outlives its pieces), for both policies and limits 1
 * and 2. The most effects playing at once — counted by the mod whenever a wave starts, plus every tick here — must stay
 * within {@code maxConcurrentEffects}. "finishOldest": the new effects do start, and an effect that makes room is gone
 * within {@link ShardManager#FINISH_TICKS} (+1); "skipNew": some do not start, and none is cut short.
 */
final class BudgetScenario {
    private static final int CYCLES = 20;

    private BudgetScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, String mode) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        Integer max = config.maxConcurrentEffects;
        String policy = config.concurrentPolicy;
        try {
            config.openEffect = ShatterConfig.OPEN_DATA_PREFIX + "pop";
            for (String close : new String[]{"glass", "burn"}) {
                for (String p : new String[]{ShatterConfig.POLICY_FINISH_OLDEST, ShatterConfig.POLICY_SKIP_NEW}) {
                    for (int limit : new int[]{2, 1}) {
                        config.effect = close;
                        config.concurrentPolicy = p;
                        config.maxConcurrentEffects = limit;
                        one(context, check, "budget_" + mode + "_" + close + "_" + p + "_" + limit, limit,
                                ShatterConfig.POLICY_SKIP_NEW.equals(p));
                    }
                }
            }
        } finally {
            config.effect = effect;
            config.openEffect = open;
            config.maxConcurrentEffects = max;
            config.concurrentPolicy = policy;
        }
    }

    private static void one(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name, int limit,
                            boolean skipNew) {
        waitClear(context);
        context.runOnClient(mc -> ShardManager.resetPeak());
        int[] init = {0, -1};
        int closes = context.computeOnClient(mc -> ShatterProfiler.closeCount());
        int skippedBefore = context.computeOnClient(mc -> ShardManager.skippedCount());
        int[] sampled = init;
        for (int i = 0; i < CYCLES; i++) {
            context.runOnClient(mc -> mc.setScreen(new InventoryScreen(Minecraft.getInstance().player)));
            for (int t = 0; t < 2; t++) {
                context.waitTick();
                sample(context, sampled);
            }
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTick();
            sample(context, sampled);
        }
        for (int t = 0; t < ShardManager.FINISH_TICKS + 2; t++) {
            context.waitTick();
            sample(context, sampled);
        }
        int peak = context.computeOnClient(mc -> ShardManager.peakPlaying());
        int started = context.computeOnClient(mc -> ShatterProfiler.closeCount()) - closes;
        int waves = context.computeOnClient(mc -> ShardManager.waveCount());
        int skipped = context.computeOnClient(mc -> ShardManager.skippedCount()) - skippedBefore;
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: peak {} at a wave's start, {} sampled per tick (limit {}), "
                + "longest finishing {} ticks, {} closes counted, {} effects skipped, {} waves left", name, peak,
                sampled[0], limit, sampled[1], started, skipped, waves);
        check.accept(peak <= limit, name + ": " + peak + " effects played at once (limit " + limit + ")");
        check.accept(sampled[0] <= limit, name + ": " + sampled[0] + " effects played at once in a tick (limit " + limit + ")");
        if (skipNew) {
            check.accept(sampled[1] < 0, name + ": skipNew must not cut effects short");
            check.accept(skipped > 0, name + ": skipNew must skip effects over the limit");
        } else {
            check.accept(sampled[1] <= ShardManager.FINISH_TICKS, name + ": an effect that made room was still there "
                    + sampled[1] + " ticks later (at most " + ShardManager.FINISH_TICKS + ")");
            check.accept(started >= CYCLES && skipped == 0, name + ": every close must start its effect (" + started
                    + " of " + CYCLES + ", " + skipped + " skipped)");
        }
        waitClear(context);
    }

    /** {@code out[0]}: most effects playing; {@code out[1]}: longest time a wave has been finishing (-1: none ever). */
    private static void sample(ClientGameTestContext context, int[] out) {
        out[0] = Math.max(out[0], context.computeOnClient(mc -> ShardManager.playingCount()));
        out[1] = Math.max(out[1], context.computeOnClient(mc -> ShardManager.longestFinishing()));
    }

    private static void waitClear(ClientGameTestContext context) {
        context.waitTicks(2);
        for (int i = 0; i < 600 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
    }
}
