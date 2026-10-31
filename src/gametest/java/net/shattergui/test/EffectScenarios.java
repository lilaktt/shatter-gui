package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.shattergui.ShatterKeys;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.params.Quality;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.fly.FlyOpen;
import net.shattergui.shatter.ShardManager;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Edge flights of screens without a source (several directions), the dissolve effect (also with reduceMotion), and
 * spam protection (rapid open/close with burn must not pile up effects).
 */
final class EffectScenarios {
    private final ClientGameTestContext context;
    private final BiConsumer<Boolean, String> check;
    private final Consumer<String> shot;

    EffectScenarios(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot) {
        this.context = context;
        this.check = check;
        this.shot = shot;
    }

    /** Inventory (never a source) opened and closed with fly: in from an edge, out to an edge. */
    void edgeFlights(String name) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        String edge = config.get(Params.FLY_EDGE_DIRECTION);
        config.effect = "fly";
        config.openEffect = ShatterConfig.OPEN_FLY_FROM_BLOCK;
        try {
            for (String direction : new String[]{"left", "top", "random", "random"}) {
                config.set(Params.FLY_EDGE_DIRECTION, direction);
                String run = name + "_" + direction + "_" + context.computeOnClient(mc -> ShatterProfiler.openCount());
                int opens = context.computeOnClient(mc -> ShatterProfiler.openCount());
                context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
                shot.accept(run + "_open_t0");
                boolean flying = context.computeOnClient(mc -> FlyOpen.isAnimating(mc.screen));
                shot.accept(run + "_open_t1");
                context.waitTicks(10);
                boolean landed = !context.computeOnClient(mc -> FlyOpen.isAnimating(mc.screen));
                check.accept(context.computeOnClient(mc -> ShatterProfiler.openCount()) > opens && flying && landed,
                        run + ": must fly in from an edge and land (flying=" + flying + ", landed=" + landed + ")");

                context.runOnClient(mc -> mc.setScreen(null));
                context.waitTick();
                String closed = context.computeOnClient(mc -> lastEffect());
                for (int i = 1; i <= 3; i++) {
                    shot.accept(run + "_close_" + i);
                }
                context.waitTicks(10);
                int waves = context.computeOnClient(mc -> ShardManager.waveCount());
                ShatterGuiClient.LOGGER.info("[ShatterTest] {}: closed with {}, waves after={}", run, closed, waves);
                check.accept("fly".equals(closed), run + ": must fly out to an edge, closed with " + closed);
                check.accept(waves == 0, run + ": nothing may be left after the flight, waves=" + waves);
                context.waitTicks(10);
            }
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            config.set(Params.FLY_EDGE_DIRECTION, edge);
        }
    }

    /** The furnace dissolves: screenshots across the wave; then once more with reduceMotion (shorter). */
    void dissolve(String name, Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        config.effect = "dissolve";
        config.openEffect = ShatterConfig.OPEN_NONE;
        try {
            for (String origin : new String[]{"edge", "center"}) {
                config.set(Params.DISSOLVE_ORIGIN, origin);
                context.setScreen(furnace);
                context.waitTicks(20);
                shot.accept(name + "_" + origin + "_before");
                context.runOnClient(mc -> mc.setScreen(null));
                int waited = 0;
                int shards = -1;
                String kind = "none";
                for (int tick : new int[]{3, 8, 14, 20, 27}) {
                    context.waitTicks(tick - waited);
                    waited = tick;
                    if (shards < 0) {
                        shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
                        kind = context.computeOnClient(mc -> lastEffect());
                    }
                    shot.accept(name + "_" + origin + "_t" + tick);
                }
                int duration = config.get(Params.DISSOLVE_DURATION) + 14;
                context.waitTicks(duration - waited);
                int left = context.computeOnClient(mc -> ShardManager.waveCount());
                ShatterGuiClient.LOGGER.info("[ShatterTest] {}_{}: shards={} waves after {} ticks={}", name, origin, shards,
                        duration, left);
                check.accept(shards > 0 && "dissolve".equals(kind),
                        name + "_" + origin + ": must dissolve (shards=" + shards + ", effect=" + kind + ")");
                check.accept(left == 0, name + "_" + origin + ": must be gone after " + duration + " ticks, waves=" + left);
                context.waitTicks(10);
            }

            // reduceMotion: shorter (0.65x), calmer.
            config.reduceMotion = true;
            context.setScreen(furnace);
            context.waitTicks(10);
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(10);
            shot.accept(name + "_reduce_motion_t10");
            int calm = Math.round((config.get(Params.DISSOLVE_DURATION) + 12) * 0.65f) + 3;
            context.waitTicks(calm - 10);
            int left = context.computeOnClient(mc -> ShardManager.waveCount());
            check.accept(left == 0, name + "_reduce_motion: must be over after " + calm + " ticks, waves=" + left);
        } finally {
            config.reduceMotion = false;
            config.set(Params.DISSOLVE_ORIGIN, "random");
            config.effect = effect;
            config.openEffect = openEffect;
        }
    }

    /** Rapid open/close with burn: waves and shards stay bounded, and everything is gone soon after. */
    void spam(String name) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        config.effect = "burn";
        try {
            int maxWaves = 0;
            int maxShards = 0;
            for (int i = 0; i < 12; i++) {
                context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
                context.waitTicks(2);
                context.runOnClient(mc -> mc.setScreen(null));
                context.waitTicks(3);
                maxWaves = Math.max(maxWaves, context.computeOnClient(mc -> ShardManager.waveCount()));
                maxShards = Math.max(maxShards, context.computeOnClient(mc -> ShardManager.liveShardCount()));
            }
            shot.accept(name + "_after_12_closes");
            int burnEnd = config.get(Params.BURN_DURATION) + 17 + config.get(Params.BURN_SMOKE_DURATION) + 10;
            context.waitTicks(burnEnd);
            int left = context.computeOnClient(mc -> ShardManager.waveCount());
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: max waves={} max shards={} left after {} ticks={}", name,
                    maxWaves, maxShards, burnEnd, left);
            // 4 running + the ones still fading out (6 ticks, one close every 5 ticks).
            check.accept(maxWaves <= 6, name + ": waves must stay bounded, max " + maxWaves);
            check.accept(maxShards <= 8000 + 3000, name + ": shards must stay bounded, max " + maxShards);
            check.accept(left == 0, name + ": everything must be gone after " + burnEnd + " ticks, waves=" + left);
        } finally {
            config.effect = effect;
        }
    }

    /**
     * Edge flights belong to fly only: glass, burn and dissolve on a screen without a source open and close without
     * any side flight; chat never flies from an edge, not even with fly.
     */
    void edgeOnlyForFly(String name) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        config.openEffect = ShatterConfig.OPEN_FLY_FROM_BLOCK;
        try {
            for (String fx : new String[]{"glass", "burn", "dissolve"}) {
                config.effect = fx;
                int opens = context.computeOnClient(mc -> ShatterProfiler.openCount());
                context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
                context.waitTick();
                boolean flying = context.computeOnClient(mc -> FlyOpen.isAnimating(mc.screen));
                boolean started = context.computeOnClient(mc -> ShatterProfiler.openCount()) > opens;
                context.runOnClient(mc -> mc.setScreen(null));
                context.waitTicks(2);
                String closed = context.computeOnClient(mc -> lastEffect());
                ShatterGuiClient.LOGGER.info("[ShatterTest] {}_{}: open flight={} closed with {}", name, fx,
                        flying || started, closed);
                check.accept(!flying && !started, name + "_" + fx + ": no edge flight on open");
                check.accept(fx.equals(closed), name + "_" + fx + ": must close with " + fx + ", was " + closed);
                context.waitTicks(130);
            }
            config.effect = "fly";
            int opens = context.computeOnClient(mc -> ShatterProfiler.openCount());
            context.setScreen(() -> new ChatScreen("", false));
            context.waitTick();
            boolean chatFlew = context.computeOnClient(mc -> ShatterProfiler.openCount()) > opens
                    || context.computeOnClient(mc -> FlyOpen.isAnimating(mc.screen));
            check.accept(!chatFlew, name + "_chat: chat must not fly in from an edge");
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(20);
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
        }
    }

    /** Master switch off: no capture, no shards, no fly-in; on again: works. */
    void masterSwitch(String name) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        config.effect = "burn";
        try {
            context.runOnClient(mc -> ShatterKeys.setEnabled(false));
            int closes = context.computeOnClient(mc -> ShatterProfiler.closeCount());
            int opens = context.computeOnClient(mc -> ShatterProfiler.openCount());
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(3);
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(3);
            boolean nothing = context.computeOnClient(mc -> ShatterProfiler.closeCount()) == closes
                    && context.computeOnClient(mc -> ShatterProfiler.openCount()) == opens
                    && context.computeOnClient(mc -> ShardManager.waveCount()) == 0;
            shot.accept(name + "_off");
            check.accept(nothing, name + ": switched off, nothing may happen");
            context.runOnClient(mc -> ShatterKeys.setEnabled(true));
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(3);
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(3);
            check.accept(context.computeOnClient(mc -> ShatterProfiler.closeCount()) > closes,
                    name + ": switched on again, the close effect must run");
            context.waitTicks(130);
        } finally {
            context.runOnClient(mc -> ShatterKeys.setEnabled(true));
            config.effect = effect;
        }
    }

    /** An old flat config is moved into the registry, clamped; only non-defaults are stored. */
    void configMigration(String name) {
        ShatterConfig old = ShatterConfig.parse("{\"effect\":\"burn\",\"gravity\":5.0,\"burnDurationTicks\":40,"
                + "\"gridColumns\":10,\"edgeDirection\":\"sideways\",\"particleDensity\":\"NaN\"}");
        String saved = old.toJsonString();
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: migrated to {}", name, saved.replaceAll("\\s+", ""));
        check.accept("burn".equals(old.effect), name + ": effect kept");
        check.accept(old.get(Params.GLASS_GRAVITY) == 0.3f, name + ": gravity clamped to 0.3, was " + old.get(Params.GLASS_GRAVITY));
        check.accept(old.get(Params.BURN_DURATION) == 40, name + ": burn duration migrated");
        check.accept("random".equals(old.get(Params.FLY_EDGE_DIRECTION)), name + ": unknown choice → default");
        check.accept(old.get(Params.PARTICLE_DENSITY) == 1f, name + ": NaN → default");
        check.accept(!saved.contains("gridColumns") && !saved.contains("\"columns\"") && saved.contains("\"duration\": 40"),
                name + ": only non-default params stored under effects/<id>/params");
        // Before the performance settings: quality and effects at once come from the preset; the look stays the same.
        check.accept(old.quality() == Quality.MEDIUM && old.maxConcurrent() == 2 && !old.skipNewEffects()
                && old.alwaysOnTop, name + ": an older config gets medium quality, 2 effects at once, finishOldest, on top");
        ShatterConfig subtle = ShatterConfig.parse("{\"preset\":\"minimal\"}");
        check.accept(subtle.quality() == Quality.LOW && subtle.maxConcurrent() == 1
                        && subtle.values().particleScale() == 0.35f && subtle.values().shardScale() == 0.5f,
                name + ": an older \"minimal\" config gets low quality (same counts as before) and 1 effect at once");
        ShatterConfig cinematic = ShatterConfig.parse("{\"preset\":\"cinematic\",\"maxConcurrentEffects\":12,"
                + "\"effectQuality\":\"ultra\",\"concurrentPolicy\":\"x\"}");
        check.accept(cinematic.quality() == Quality.HIGH && cinematic.maxConcurrent() == 8 && !cinematic.skipNewEffects(),
                name + ": unknown quality → the preset's, effects at once clamped to 8, unknown policy → finishOldest");
    }

    private static String lastEffect() {
        return ShardManager.batches().isEmpty() ? "none" : ShardManager.batches().getLast().effect().id();
    }
}
