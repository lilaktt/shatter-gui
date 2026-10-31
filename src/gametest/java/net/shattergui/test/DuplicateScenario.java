package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.FrameAudit;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.params.BundledParams;
import net.shattergui.shatter.ShardManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The interface is never on screen twice ({@code -PtestOnly=duplicates}; with {@code -PspatialGui=1.8} in the first
 * and the third person; with {@code -Pshaders}): every frame is counted by {@link FrameAudit} — the flat screen,
 * Spatial GUI's plane, opening and closing waves that drew something — and may show at most one of them. Pairs of a
 * different open and close effect: every opening with closing by glass (pop for glass), and every close effect after
 * opening with {@code fly_from_block} and with pop; each closed during the opening, right after it, and 2 s later.
 * After the close effect nothing is left: no wave, no plane.
 */
final class DuplicateScenario {
    enum Moment {
        DURING, RIGHT_AFTER, LATER
    }

    private DuplicateScenario() {
    }

    /** Built-in and shipped effects (the test pack's are covered by the gathering test). */
    static List<String> shipped(List<String> ids) {
        List<String> out = new ArrayList<>();
        String only = System.getProperty("shatter.test.dupOnly", "");
        for (String id : ids) {
            if (!only.isEmpty() && !List.of(only.split(",")).contains(id)) {
                continue;
            }
            if (ShatterEffects.isBuiltIn(id) || BundledParams.ids().contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, String mode, Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        context.runOnClient(mc -> FrameAudit.enable(true));
        List<String> failures = new ArrayList<>();
        int runs = 0;
        try {
            List<String> opens = shipped(context.computeOnClient(mc -> ShatterEffects.openIds()));
            List<String> closes = shipped(context.computeOnClient(mc -> ShatterEffects.ids()));
            for (String o : opens) {
                String close = "glass".equals(o) ? "pop" : "glass";
                for (Moment m : Moment.values()) {
                    runs++;
                    one(context, failures, mode, ShatterConfig.OPEN_DATA_PREFIX + o, close, m, furnace, config);
                }
            }
            for (String c : closes) {
                for (String o : List.of(ShatterConfig.OPEN_FLY_FROM_BLOCK, ShatterConfig.OPEN_DATA_PREFIX + "pop")) {
                    if (o.endsWith(":" + c)) {
                        continue;
                    }
                    for (Moment m : Moment.values()) {
                        runs++;
                        one(context, failures, mode, o, c, m, furnace, config);
                    }
                }
            }
        } finally {
            config.effect = effect;
            config.openEffect = open;
            context.runOnClient(mc -> FrameAudit.enable(false));
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] duplicates {}: {} runs, {} failed", mode, runs, failures.size());
        for (String f : failures) {
            check.accept(false, f);
        }
    }

    private static void one(ClientGameTestContext context, List<String> failures, String mode, String open, String close,
                            Moment moment, Supplier<Screen> furnace, ShatterConfig config) {
        String name = "duplicates_" + mode + " " + open + " → " + close + " " + moment;
        config.openEffect = open;
        config.effect = close;
        waitClear(context);
        long from = context.computeOnClient(mc -> FrameAudit.next());
        context.runOnClient(mc -> mc.setScreen(furnace.get()));
        switch (moment) {
            case DURING -> context.waitTicks(3);
            case RIGHT_AFTER -> {
                context.waitTicks(3);
                for (int i = 0; i < 80 && context.computeOnClient(mc -> OpenAssemble.active()); i++) {
                    context.waitTick();
                }
                context.waitTick();
            }
            case LATER -> context.waitTicks(40);
        }
        boolean gathering = context.computeOnClient(mc -> OpenAssemble.active());
        context.runOnClient(mc -> mc.setScreen(null));
        long closed = context.computeOnClient(mc -> FrameAudit.next());
        waitClear(context);
        context.waitTicks(4);
        List<FrameAudit.Frame> frames = context.computeOnClient(mc -> FrameAudit.since(from));
        for (FrameAudit.Frame f : frames) {
            if (f.instances() > 1) {
                failures.add(name + ": frame " + (f.index() - from) + " (close at " + (closed - from) + ") shows the "
                        + "interface " + f.instances() + " times: " + f + (moment == Moment.DURING ? " (still gathering: "
                        + gathering + ")" : ""));
                return;
            }
        }
        // After the close effect: nothing at all.
        for (FrameAudit.Frame f : frames.subList(Math.max(0, frames.size() - 3), frames.size())) {
            if (f.instances() > 0 || f.planeVisible()) {
                failures.add(name + ": something is left after the close effect: " + f);
                return;
            }
        }
        if (context.computeOnClient(mc -> ShardManager.openingCount()) > 0) {
            failures.add(name + ": an opening wave outlived its screen");
        }
    }

    private static void waitClear(ClientGameTestContext context) {
        context.waitTicks(3);
        for (int i = 0; i < 600 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
    }
}
