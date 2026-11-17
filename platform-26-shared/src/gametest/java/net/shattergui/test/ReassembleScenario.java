package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.capture.CapturePool;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.config.ShatterConfig;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.Reassemble;
import net.shattergui.shatter.Reassembly;
import net.shattergui.shatter.ShardManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

final class ReassembleScenario {
    private static final int OPEN_WAIT = 12;
    private static final int REOPEN_AFTER = 14;
    private static final double CENTRE = 0.03;
    private static final double JUMP = 0.05;
    private static final double LAST_CENTRE = 0.03;

    private record State(boolean running, boolean landed, float progress, int live, int all, boolean screen, boolean hidden,
                         double jump) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s%s e=%.3f pieces %d/%d screen %s%s jump %.4f", running ? "gathering" : "-",
                    landed ? " landed" : "", progress, live, all, screen ? "open" : "none", hidden ? " hidden" : "", jump);
        }
    }

    private ReassembleScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check) {
        boolean spatial = FabricLoader.getInstance().isModLoaded("spatial-gui");
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        boolean reassemble = config.get(Params.GLASS_REASSEMBLE);
        boolean reduce = config.reduceMotion;
        Integer max = config.maxConcurrentEffects;
        String policy = config.concurrentPolicy;
        SpatialScenarios setup = spatial ? new SpatialScenarios(context, check) : null;
        Object allScreens = spatial ? setup.getSpatial("allScreens") : null;
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        context.getInput().resizeWindow(960, 540);
        context.waitTicks(5);
        SettleScenario.command(singleplayer, "gamerule advance_time false");
        SettleScenario.command(singleplayer, "time set 6000");
        SettleScenario.command(singleplayer, "gamemode creative @p");
        context.runOnClient(mc -> McTest.setHudHidden(mc, true));
        try {
            if (spatial) {
                setup.trySetSpatial("allScreens", true);
            }
            config.effect = "glass";
            config.openEffect = ShatterConfig.OPEN_DATA_PREFIX + "glass";
            config.set(Params.GLASS_REASSEMBLE, true);
            String prefix = spatial ? "reassemble_spatial" : "reassemble_flat";
            measure(context, singleplayer, feet, prefix);
            for (String mode : List.of("first", "third")) {
                String tag = prefix + "_" + mode;
                camera(context, setup, mode);
                SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 25);
                context.waitTicks(10);
                main(context, check, tag, spatial, spatial && mode.equals("third"));
                config.set(Params.GLASS_REASSEMBLE, false);
                usual(context, check, tag + "_off", REOPEN_AFTER, SpatialFlowScenario.PanelScreen::new, "does not reassemble");
                config.set(Params.GLASS_REASSEMBLE, true);
                usual(context, check, tag + "_gone", -1, SpatialFlowScenario.PanelScreen::new, "no pieces");
                usual(context, check, tag + "_other", REOPEN_AFTER, () -> new SettleScenario.SolidScreen(SettleScenario.MAGENTA),
                        "different screen");
                if (mode.equals("first")) {
                    closeMidway(context, check, tag + "_close_midway");
                    spam(context, check, tag + "_spam");
                    config.reduceMotion = true;
                    reduced(context, check, tag + "_reduced");
                    config.reduceMotion = reduce;
                    config.maxConcurrentEffects = 1;
                    config.concurrentPolicy = ShatterConfig.POLICY_SKIP_NEW;
                    started(context, check, tag + "_one_effect_skip_new");
                    config.maxConcurrentEffects = max;
                    config.concurrentPolicy = policy;
                    resized(context, check, tag + "_resize");
                }
            }
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            config.set(Params.GLASS_REASSEMBLE, reassemble);
            config.reduceMotion = reduce;
            config.maxConcurrentEffects = max;
            config.concurrentPolicy = policy;
            if (spatial) {
                setup.trySetSpatial("allScreens", allScreens == null ? false : allScreens);
                setup.setSpatial("firstPersonModeInventory", false);
                setup.setSpatial("firstPersonModeContainers", false);
            }
            context.runOnClient(mc -> McTest.setHudHidden(mc, false));
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.runOnClient(mc -> Mc26.setScreen(null));
            context.waitTicks(2);
            context.runOnClient(mc -> ShardManager.clear());
            SettleScenario.command(singleplayer, "gamemode survival @p");
            SettleScenario.command(singleplayer, "gamerule advance_time true");
            SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 0);
            context.getInput().resizeWindow(1920, 1080);
            context.waitTicks(10);
        }
    }

    private static void camera(ClientGameTestContext context, SpatialScenarios setup, String mode) {
        boolean first = mode.equals("first");
        if (setup != null) {
            setup.setSpatial("firstPersonModeInventory", first);
            setup.setSpatial("firstPersonModeContainers", first);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        } else {
            context.runOnClient(mc -> mc.options.setCameraType(first ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK));
        }
        context.waitTicks(3);
    }

    private static void clear(ClientGameTestContext context) {
        context.runOnClient(mc -> Mc26.setScreen(null));
        for (int i = 0; i < 300 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
        context.runOnClient(mc -> ShardManager.clear());
        context.waitTicks(5);
    }

    private static void closePanel(ClientGameTestContext context) {
        clear(context);
        context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
        context.setScreen(SpatialFlowScenario.PanelScreen::new);
        context.waitTicks(OPEN_WAIT);
        context.runOnClient(mc -> Mc26.setScreen(null));
    }

    private static void measure(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos feet, String prefix) {
        SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 25);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(10);
        closePanel(context);
        StringBuilder rows = new StringBuilder();
        int firstBelowAlive = -1;
        int firstBelowOpacity = -1;
        for (int tick = 1; tick <= 120; tick++) {
            context.waitTick();
            int[] counts = context.computeOnClient(mc -> Reassembly.closeCounts());
            double opacity = context.computeOnClient(mc -> Reassembly.closeOpacity());
            if (counts[1] <= 0) {
                rows.append(String.format(Locale.ROOT, " t%d=gone", tick));
                break;
            }
            double alive = counts[0] / (double) counts[1];
            if (firstBelowAlive < 0 && alive < Reassemble.MIN_ALIVE) {
                firstBelowAlive = tick;
            }
            if (firstBelowOpacity < 0 && opacity < Reassemble.MIN_OPACITY) {
                firstBelowOpacity = tick;
            }
            if (tick % 4 == 0 || tick < 4) {
                rows.append(String.format(Locale.ROOT, " t%d=%.2f/%.2f", tick, alive, opacity));
            }
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}_measure: default glass (lifetime {}, fade from {}), alive share / mean "
                        + "opacity of live pieces per tick:{}; alive below {} from t{}, opacity below {} from t{}", prefix,
                Params.GLASS_LIFETIME.def(), Params.GLASS_FADE_START.def(), rows, Reassemble.MIN_ALIVE, firstBelowAlive,
                Reassemble.MIN_OPACITY, firstBelowOpacity);
        clear(context);
    }

    private static State state() {
        int[] counts = Reassembly.counts();
        return new State(Reassembly.running(), Reassembly.landedNow(), Reassembly.progress(), counts[0], counts[1],
                Mc26.screen() != null, Mc26.screen() != null && OpenAssemble.hides(Mc26.screen()), Reassembly.jumpFromStart());
    }

    private static void main(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name, boolean spatial,
                             boolean cameraMoves) {
        closePanel(context);
        context.waitTicks(REOPEN_AFTER);
        int[] before = context.computeOnClient(mc -> Reassembly.closeCounts());
        int lead = 4;
        List<FrameSeries.Shot> shots = FrameSeries.record(context, lead, () -> Mc26.setScreen(new SpatialFlowScenario.PanelScreen()),
                ReassembleScenario::state, () -> !Reassembly.running(), 160);
        context.waitTicks(10);
        List<FrameSeries.Shot> tail = FrameSeries.record(context, 1, null, ReassembleScenario::state, null, 3);
        String decision = context.computeOnClient(mc -> Reassembly.lastDecision());
        boolean showing = context.computeOnClient(mc -> SpatialGuiCompat.isShowing(Mc26.screen()));
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} (pieces before the reopen {}/{}, Spatial GUI plane {}){}", name, decision,
                before[0], before[1], showing, FrameSeries.describe(shots));
        check.accept(decision.startsWith("reassemble"), name + ": reopening while the pieces live must reassemble (" + decision + ")");
        check.accept(!spatial || showing, name + ": the screen must be on the Spatial GUI plane");
        FrameSeries.Shot last = shots.size() > lead - 1 ? shots.get(lead - 1) : null;
        FrameSeries.Shot first = null;
        FrameSeries.Shot end = null;
        int all = -1;
        for (FrameSeries.Shot s : shots) {
            if (s.state() instanceof State st && st.running()) {
                if (first == null) {
                    first = s;
                    all = st.all();
                    check.accept(st.live() == st.all() && st.all() == before[1], name + ": all " + before[1]
                            + " pieces of the close take part (" + st.live() + " of " + st.all() + ")");
                }
                if (!st.landed()) {
                    end = s;
                }
            }
        }
        FrameSeries.Shot real = tail.isEmpty() ? null : tail.getLast();
        check.accept(first != null && last != null, name + ": the gathering must be recorded");
        if (first == null || last == null || end == null || real == null) {
            return;
        }
        double shift = Math.hypot(first.cx() - last.cx(), first.cy() - last.cy());
        double area = first.magenta() / (double) Math.max(1, last.magenta());
        ShatterGuiClient.LOGGER.info(String.format(Locale.ROOT, "[ShatterTest] %s: first frame of the gathering vs last frame of "
                + "the scatter: centre shift %.3f, magenta %d vs %d (%.2f); last gathering frame vs the real screen: centre "
                + "shift %.3f, box %s vs %s, magenta %d vs %d", name, shift, first.magenta(), last.magenta(), area,
                Math.hypot(end.cx() - real.cx(), end.cy() - real.cy()), java.util.Arrays.toString(end.box()),
                java.util.Arrays.toString(real.box()), end.magenta(), real.magenta()));
        double jump = first.state() instanceof State st ? st.jump() : -1;
        check.accept(jump >= 0 && jump <= JUMP, String.format(Locale.ROOT, "%s: in the first frame of the gathering every "
                + "piece is where the scatter left it (largest move %.4f blocks, e=%.3f)", name, jump,
                first.state() instanceof State st ? st.progress() : -1f));
        if (!cameraMoves) {
            check.accept(shift <= CENTRE && area > 0.8 && area < 1.25, String.format(Locale.ROOT, "%s: the first frame of "
                    + "the gathering must look like the last frame of the scatter (shift %.3f, area %.2f)", name, shift, area));
        }
        double endShift = Math.hypot(end.cx() - real.cx(), end.cy() - real.cy());
        double w = real.boxWidth() > 0 ? end.boxWidth() / real.boxWidth() : 0;
        double h = real.boxHeight() > 0 ? end.boxHeight() / real.boxHeight() : 0;
        double endArea = end.magenta() / (double) Math.max(1, real.magenta());
        check.accept(endShift <= LAST_CENTRE && w > 0.9 && w < 1.11 && h > 0.9 && h < 1.11 && endArea > 0.8 && endArea < 1.25,
                String.format(Locale.ROOT, "%s: the last frame of the gathering must match the real screen (shift %.3f, box "
                        + "%.2fx%.2f, area %.2f)", name, endShift, w, h, endArea));
        int lastIndex = shots.indexOf(end);
        FrameSeries.saveStrip(context, FrameSeries.pickNine(shots, lead - 1, Math.min(shots.size() - 1, lastIndex + 3)), name);
        check.accept(all > 0, name + ": pieces counted");
    }

    private static void usual(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name, int after,
                              java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen, String reason) {
        closePanel(context);
        if (after < 0) {
            for (int i = 0; i < 300 && context.computeOnClient(mc -> Reassembly.closeCounts()[0] > 0); i++) {
                context.waitTick();
            }
        } else {
            context.waitTicks(after);
        }
        context.setScreen(screen);
        context.waitTicks(2);
        String decision = context.computeOnClient(mc -> Reassembly.lastDecision());
        boolean running = context.computeOnClient(mc -> Reassembly.running());
        int openings = context.computeOnClient(mc -> ShardManager.openingCount());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} (gathering waves {})", name, decision, openings);
        check.accept(!running && decision.startsWith("usual opening") && decision.contains(reason), name
                + ": must open as usual because of " + reason + " (" + decision + ")");
        context.waitTicks(20);
    }

    private static void closeMidway(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        closePanel(context);
        context.waitTicks(REOPEN_AFTER);
        context.setScreen(SpatialFlowScenario.PanelScreen::new);
        for (int i = 0; i < 40 && context.computeOnClient(mc -> Reassembly.progress() < 0.5f); i++) {
            context.waitTick();
        }
        float at = context.computeOnClient(mc -> Reassembly.progress());
        int[] gathering = context.computeOnClient(mc -> Reassembly.counts());
        List<FrameSeries.Shot> shots = FrameSeries.record(context, 3, () -> Mc26.setScreen(null), ReassembleScenario::state, null, 8);
        int[] after = context.computeOnClient(mc -> Reassembly.closeCounts());
        boolean running = context.computeOnClient(mc -> Reassembly.running());
        double shift = shots.size() > 3 ? Math.hypot(shots.get(3).cx() - shots.get(2).cx(), shots.get(3).cy() - shots.get(2).cy())
                : Double.NaN;
        ShatterGuiClient.LOGGER.info(String.format(Locale.ROOT, "[ShatterTest] %s: closed at e=%.2f with %d/%d pieces gathering; "
                + "after: %d pieces scatter again, gathering %s, centre shift between the frames %.3f%s", name, at, gathering[0],
                gathering[1], after[0], running, shift, FrameSeries.describe(shots)));
        check.accept(!running && after[0] > 0, name + ": closing while gathering must break the pieces again (" + after[0] + ")");
        check.accept(shift <= 0.05, name + ": the pieces break again from where they are (shift " + shift + ")");
        clear(context);
    }

    private static void spam(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        closePanel(context);
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            context.waitTicks(2);
            boolean open = context.computeOnClient(mc -> Mc26.screen() != null);
            context.runOnClient(mc -> Mc26.setScreen(open ? null : new SpatialFlowScenario.PanelScreen()));
            seen.add(context.computeOnClient(mc -> (Reassembly.running() ? "G" : "-") + ShardManager.waveCount()));
        }
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.waitTicks(5);
        int textures = context.computeOnClient(mc -> CapturePool.texturesWithMemory());
        int waves = context.computeOnClient(mc -> ShardManager.waveCount());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: Esc spam, gathering/waves per step {}; textures {} waves {}", name, seen,
                textures, waves);
        check.accept(textures <= CapturePool.MAX_SLOTS + 1 && waves <= 4, name + ": Esc spam must stay bounded (textures "
                + textures + ", waves " + waves + ")");
        clear(context);
    }

    private static void reduced(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        closePanel(context);
        context.waitTicks(REOPEN_AFTER);
        context.setScreen(SpatialFlowScenario.PanelScreen::new);
        context.waitTicks(1);
        String decision = context.computeOnClient(mc -> Reassembly.lastDecision());
        int expected = context.computeOnClient(mc -> Reassemble.duration(ParamValues.current().f(Params.OPEN_SPEED),
                ParamValues.current().durationScale()));
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} (expected {} ticks)", name, decision, expected);
        check.accept(decision.startsWith("reassemble") && decision.contains(", " + expected + " ticks"), name
                + ": with less motion the gathering is shorter (" + decision + ", expected " + expected + ")");
        clear(context);
    }

    private static void started(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        closePanel(context);
        context.waitTicks(REOPEN_AFTER);
        int playing = context.computeOnClient(mc -> ShardManager.playingCount());
        context.setScreen(SpatialFlowScenario.PanelScreen::new);
        context.waitTicks(1);
        String decision = context.computeOnClient(mc -> Reassembly.lastDecision());
        int playingAfter = context.computeOnClient(mc -> ShardManager.playingCount());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} (effects playing {} before, {} after)", name, decision, playing,
                playingAfter);
        check.accept(decision.startsWith("reassemble") && playingAfter <= playing, name + ": the gathering reuses the closing "
                + "wave and does not count as a new effect (" + decision + ", playing " + playing + " -> " + playingAfter + ")");
        clear(context);
    }

    private static void resized(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        closePanel(context);
        context.waitTicks(REOPEN_AFTER);
        context.getInput().resizeWindow(1000, 560);
        context.waitTicks(2);
        context.setScreen(SpatialFlowScenario.PanelScreen::new);
        context.waitTicks(1);
        String decision = context.computeOnClient(mc -> Reassembly.lastDecision());
        check.accept(decision.startsWith("usual opening") && decision.contains("window size"), name
                + ": after a resize the old pieces are not used (" + decision + ")");
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.getInput().resizeWindow(960, 540);
        context.waitTicks(5);
        closePanel(context);
        context.waitTicks(REOPEN_AFTER);
        context.setScreen(SpatialFlowScenario.PanelScreen::new);
        context.waitTicks(2);
        boolean gathering = context.computeOnClient(mc -> Reassembly.running());
        context.getInput().resizeWindow(1000, 560);
        context.waitTicks(3);
        boolean still = context.computeOnClient(mc -> Reassembly.running());
        boolean hidden = context.computeOnClient(mc -> Mc26.screen() != null && OpenAssemble.hides(Mc26.screen()));
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {}; resized while gathering: gathering {} -> {}, screen hidden {}", name,
                decision, gathering, still, hidden);
        check.accept(gathering && !still && !hidden, name + ": a resize while gathering clears it and shows the screen");
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.getInput().resizeWindow(960, 540);
        context.waitTicks(5);
        clear(context);
    }
}
