package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.render.ShardRenderer;
import net.shattergui.shatter.ShardManager;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class GatherScenario {
    private static final int CELL_THRESHOLD = 24;
    private static final float[] MIDDLE = {0.35f, 0.5f, 0.65f, 0.8f};
    private static final Set<String> SKIP = Set.of();

    private GatherScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, String mode, boolean worldComparable,
                    Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        context.runOnClient(mc -> net.shattergui.debug.FrameAudit.enable(true));
        try {
            config.effect = "flip";
            List<String> ids = context.computeOnClient(mc -> ShatterEffects.openIds());
            check.accept(ids.containsAll(List.of("glass", "burn", "dissolve", "vortex", "flip", "recede", "door")),
                    mode + ": the open list has the code effects and the shipped ones, got " + ids);
            check.accept(!ids.contains("fly"), mode + ": fly opens by flying out of the block, not in the gather list");
            StringBuilder table = new StringBuilder();
            for (String id : ids) {
                if (!SKIP.contains(id)) {
                    one(context, check, mode, worldComparable, furnace, config, id, table);
                }
            }
            ShatterGuiClient.LOGGER.info("[ShatterTest] gather {} summary (id: length, middle vs final, middle vs world, "
                    + "end vs final, final vs world, noise):\n{}", mode, table);
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            context.runOnClient(mc -> net.shattergui.debug.FrameAudit.enable(false));
        }
    }

    private static String lastFrames(ClientGameTestContext context, long from) {
        StringBuilder out = new StringBuilder();
        for (net.shattergui.debug.FrameAudit.Frame f : context.computeOnClient(mc -> net.shattergui.debug.FrameAudit.since(from))) {
            out.append(String.format(Locale.ROOT, "%n    t=%.2f flat=%s plane=%.2f open=%d close=%d", f.time(), f.flat(),
                    f.planeAlpha(), f.openings(), f.closes()));
        }
        return out.toString();
    }

    private static void one(ClientGameTestContext context, BiConsumer<Boolean, String> check, String mode,
                            boolean worldComparable, Supplier<Screen> furnace, ShatterConfig config, String id,
                            StringBuilder table) {
        String name = "gather_" + mode + "_" + id;
        config.openEffect = ShatterConfig.OPEN_DATA_PREFIX + id;
        context.waitTicks(4);
        waitUntilClear(context);
        context.waitTicks(3);
        FrameGrab.request(context, name + "_world", true);
        FrameGrab.Frame world = FrameGrab.get(context, name + "_world");

        long auditFrom = context.computeOnClient(mc -> net.shattergui.debug.FrameAudit.next());
        context.runOnClient(mc -> Mc26.setScreen(furnace.get()));
        context.getInput().setCursorPos(2, 2);
        int waited = 0;
        while (waited < 20 && context.computeOnClient(mc -> OpenAssemble.waveTicks()) == 0) {
            context.waitTick();
            waited++;
        }
        int length = context.computeOnClient(mc -> OpenAssemble.waveTicks());
        check.accept(length > 0, name + ": the gathering must start (waited " + waited + " ticks)");
        boolean quick = net.shattergui.params.BundledParams.quick(id);
        int most = quick ? 7 : 10;
        check.accept(length <= most, name + ": the opening lasts " + length + " ticks, at most " + most);
        if (length <= 0) {
            context.runOnClient(mc -> Mc26.setScreen(null));
            return;
        }
        int at = 0;
        boolean allHidden = true;
        int minDrawn = Integer.MAX_VALUE;
        for (int k = 0; k < MIDDLE.length; k++) {
            int tick = Math.max(at + 1, Math.round(length * MIDDLE[k]));
            context.waitTicks(tick - at);
            at = tick;
            FrameGrab.request(context, name + "_mid" + k, true);
            boolean hidden = context.computeOnClient(mc -> OpenAssemble.hides(Mc26.screen()));
            int drawn = context.computeOnClient(mc -> ShardRenderer.gatherShardsDrawn());
            allHidden &= hidden;
            minDrawn = Math.min(minDrawn, drawn);
        }
        int left = 0;
        while (left < length + 20 && context.computeOnClient(mc -> Mc26.screen() != null && OpenAssemble.hides(Mc26.screen()))) {
            FrameGrab.request(context, name + "_last", true);
            context.waitTick();
            left++;
        }
        FrameGrab.request(context, name + "_end", true);
        context.waitTick();
        FrameGrab.request(context, name + "_end2", false);
        boolean shown = context.computeOnClient(mc -> Mc26.screen() != null && !OpenAssemble.hides(Mc26.screen()));
        context.waitTicks(10);
        FrameGrab.request(context, name + "_final", true);
        context.waitTicks(6);
        FrameGrab.request(context, name + "_final2", false);

        FrameGrab.Frame[] middle = new FrameGrab.Frame[MIDDLE.length];
        for (int k = 0; k < MIDDLE.length; k++) {
            middle[k] = FrameGrab.get(context, name + "_mid" + k);
        }
        FrameGrab.Frame end = FrameGrab.get(context, name + "_end");
        FrameGrab.Frame last = FrameGrab.get(context, name + "_last");
        FrameGrab.Frame end2 = FrameGrab.get(context, name + "_end2");
        FrameGrab.Frame fin = FrameGrab.get(context, name + "_final");
        FrameGrab.Frame fin2 = FrameGrab.get(context, name + "_final2");
        boolean onPlane = context.computeOnClient(mc -> net.shattergui.compat.spatial.SpatialGuiCompat.isShowing(Mc26.screen()));
        double[] panel = context.computeOnClient(mc -> {
            net.shattergui.shatter.ImageRect r = net.shattergui.capture.ScreenCapture.guiBounds(Mc26.screen());
            return new double[]{r.x0(), r.y0(), r.x1(), r.y1()};
        });
        context.runOnClient(mc -> Mc26.setScreen(null));
        if (onPlane) {
            int[] box = worldComparable ? FrameGrab.box(fin, world, CELL_THRESHOLD) : null;
            panel = box == null ? new double[]{0, 0, 1, 1} : new double[]{box[0] / (double) FrameGrab.W,
                    box[1] / (double) FrameGrab.H, box[2] / (double) FrameGrab.W, box[3] / (double) FrameGrab.H};
        }

        double noise = FrameGrab.changed(fin, fin2, CELL_THRESHOLD);
        double limit = Math.max(0.004, noise * 3 + 0.002);
        double midVsFinal = FrameGrab.changed(middle[1], fin, CELL_THRESHOLD, panel);
        double midVsWorld = 0;
        for (FrameGrab.Frame m : middle) {
            midVsWorld = Math.max(midVsWorld, FrameGrab.changed(m, world, CELL_THRESHOLD));
        }
        double endVsFinal = FrameGrab.changed(end, fin, CELL_THRESHOLD);
        double[] inner = {panel[0] + 1.5 / FrameGrab.W, panel[1] + 1.5 / FrameGrab.H, panel[2] - 1.5 / FrameGrab.W,
                panel[3] - 1.5 / FrameGrab.H};
        double jump = Math.max(FrameGrab.changedBeyondShift(last, end, CELL_THRESHOLD, inner),
                FrameGrab.changedBeyondShift(end, last, CELL_THRESHOLD, inner));
        double cameraMotion = FrameGrab.changedBeyondShift(end, end2, CELL_THRESHOLD);
        double finalVsWorld = FrameGrab.changed(fin, world, CELL_THRESHOLD);
        table.append(String.format(Locale.ROOT, "  %-20s %3d t  mid/final (panel) %.3f  mid/world %.3f  end/final %.4f  jump %.4f  final/world %.3f  noise %.4f%n",
                id, length, midVsFinal, midVsWorld, endVsFinal, jump, finalVsWorld, noise));

        check.accept(allHidden, name + ": the real screen is hidden in the middle of the gathering");
        check.accept(minDrawn > 0, name + ": pieces are drawn in the middle of the gathering (fewest " + minDrawn + ")");
        check.accept(midVsFinal > limit, name + ": the middle frame must not show the real screen (differs from the "
                + "final frame in " + pct(midVsFinal) + " of the panel, needs > " + pct(limit) + ")");
        if (worldComparable) {
            check.accept(midVsWorld > limit, name + ": pieces visible in the middle (differs from the world in "
                    + pct(midVsWorld) + ", needs > " + pct(limit) + ")");
            check.accept(finalVsWorld > 0.05, name + ": the real screen is shown at the end (" + pct(finalVsWorld)
                    + " of the frame differs from the world)");
        }
        check.accept(shown, name + ": the screen is shown once the gathering is over");
        double allowed = limit + 0.004 + cameraMotion;
        String jumpText = name + ": no jump from the gathering to the real screen (" + pct(jump)
                + " of the frame changes, allowed " + pct(allowed) + ", of it camera motion " + pct(cameraMotion) + ")";
        if (context.computeOnClient(mc -> net.shattergui.compat.iris.IrisCompat.shaderPackInUse())) {
            if (jump > allowed) {
                ShatterGuiClient.LOGGER.warn("[ShatterTest] KNOWN (shader pack colours the pieces): {}", jumpText);
            }
        } else {
            check.accept(jump <= allowed, jump <= allowed ? jumpText : jumpText + lastFrames(context, auditFrom));
        }
        if (worldComparable) {
            check.accept(endVsFinal <= limit + 0.004, name + ": the end of the gathering is the real screen ("
                    + pct(endVsFinal) + " differs from the final frame, allowed " + pct(limit + 0.004) + ")");
        }
        waitUntilClear(context);
    }

    private static String pct(double share) {
        return String.format(Locale.ROOT, "%.2f%%", share * 100);
    }

    private static void waitUntilClear(ClientGameTestContext context) {
        for (int i = 0; i < 400 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
    }
}
