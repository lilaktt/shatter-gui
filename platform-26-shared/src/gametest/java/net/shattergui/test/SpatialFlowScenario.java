package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.shattergui.ShatterGuiClient;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.FrameAudit;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.render.ShardRenderer;
import net.shattergui.shatter.ShardManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

final class SpatialFlowScenario {
    static final int MAGENTA = 0xFFFF00FF;
    private static final int OPEN_WAIT = 30;
    private static final long CLOSE_WATCH_MS = 900;
    private static final long FROZEN_WATCH_MS = 600;

    record OpenState(boolean open, boolean ours, int drawn, boolean spatial) {
        @Override
        public String toString() {
            return (open ? "open" : "closed") + (ours ? " ours" : "") + " drawn " + drawn + (spatial ? " spatial" : "");
        }
    }

    record CloseState(boolean open, int live, long closeElapsed) {
        @Override
        public String toString() {
            return (open ? "open" : "closed") + " live " + live + " closeElapsed " + closeElapsed;
        }
    }

    static final class PanelScreen extends Screen {
        PanelScreen() {
            super(Component.literal("panel"));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int w = 176;
            int h = 166;
            int x = (width - w) / 2;
            int y = (height - h) / 2;
            graphics.fill(x, y, x + w, y + h, MAGENTA);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private SpatialFlowScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check) {
        boolean spatial = FabricLoader.getInstance().isModLoaded("spatial-gui");
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        float burst = config.get(Params.GLASS_BURST);
        float gravity = config.get(Params.GLASS_GRAVITY);
        float spin = config.get(Params.GLASS_SPIN);
        SpatialScenarios setup = spatial ? new SpatialScenarios(context, check) : null;
        Map<String, Object> saved = new HashMap<>();
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        context.getInput().resizeWindow(960, 540);
        context.waitTicks(5);
        SettleScenario.command(singleplayer, "gamerule advance_time false");
        SettleScenario.command(singleplayer, "time set 6000");
        try {
            if (spatial) {
                userProfile(setup, saved);
            }
            SettleScenario.command(singleplayer, "gamemode creative @p");
            context.waitTicks(5);
            for (String mode : List.of("first", "third")) {
                String tag = (spatial ? "spatial_" : "flat_") + mode;
                camera(context, setup, mode);
                SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 10);
                context.waitTicks(10);
                closeFlow(context, check, setup, tag + "_close_frozen", true, "glass");
                closeFlow(context, check, setup, tag + "_close_glass", false, "glass");
                closeFlow(context, check, setup, tag + "_close_burn", false, "burn");
                List<String> ids = context.computeOnClient(mc -> ShatterEffects.openIds());
                for (String id : ids) {
                    openFlow(context, check, spatial, tag + "_open_" + id, ShatterConfig.OPEN_DATA_PREFIX + id,
                            () -> Mc26.setScreen(new InventoryScreen(Minecraft.getInstance().player)), null);
                }
                BlockPos furnace = feet.offset(0, 0, 3);
                SettleScenario.command(singleplayer, "setblock %d %d %d minecraft:furnace[facing=north]", furnace.getX(),
                        furnace.getY(), furnace.getZ());
                FlyScenario.lookAt(context, singleplayer, feet, furnace.getX() + 0.5, furnace.getY() + 0.5, furnace.getZ() + 0.5);
                for (String open : List.of(ShatterConfig.OPEN_FLY_FROM_BLOCK, ShatterConfig.OPEN_DATA_PREFIX + "glass")) {
                    openFlow(context, check, spatial, tag + "_furnace_" + open.replace(':', '_'), open, null,
                            () -> context.getInput().pressKey(options -> options.keyUse));
                }
                SettleScenario.command(singleplayer, "setblock %d %d %d minecraft:air", furnace.getX(), furnace.getY(),
                        furnace.getZ());
            }
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            config.set(Params.GLASS_BURST, burst);
            config.set(Params.GLASS_GRAVITY, gravity);
            config.set(Params.GLASS_SPIN, spin);
            if (spatial) {
                for (Map.Entry<String, Object> e : saved.entrySet()) {
                    setup.trySetSpatial(e.getKey(), e.getValue());
                }
                setup.setSpatial("firstPersonModeInventory", false);
                setup.setSpatial("firstPersonModeContainers", false);
            }
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            SettleScenario.command(singleplayer, "gamemode survival @p");
            SettleScenario.command(singleplayer, "gamerule advance_time true");
            SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 0);
            context.getInput().resizeWindow(1920, 1080);
            context.waitTicks(10);
        }
    }

    private static void userProfile(SpatialScenarios setup, Map<String, Object> saved) {
        Object[][] values = {
                {"transitionDurationMs", 300}, {"renderPlayerInFront", true}, {"screenOverPlayerAlpha", 50},
                {"dropShadowEnabled", true}, {"enableCloseAnimation", true}, {"enableFadeAnimation", true},
                {"fadeDurationMs", 150}, {"enableScaleAnimation", true}, {"openAnimationDurationMs", 300},
                {"animationStartScalePercent", 75}, {"animationEasing", "Back"}, {"screenDistance", 2.5},
                {"screenSideOffset", -0.6}, {"screenHeightOffset", -0.4}, {"screenYawOffset", 160.0},
                {"screenScale", 3.5}, {"cameraDistance", 1.7}, {"cameraSideOffset", -1.2}, {"cameraHeightOffset", 1.5},
                {"cameraTargetPitch", 10.0}, {"lerpCameraPitch", true}, {"thirdPersonFrontSpecialCase", true},
                {"inventory", true}, {"containers", true}, {"allScreens", true}};
        StringBuilder applied = new StringBuilder();
        for (Object[] v : values) {
            String field = (String) v[0];
            Object before = setup.getSpatial(field);
            if (setup.trySetSpatial(field, v[1])) {
                saved.put(field, before);
                applied.append(' ').append(field);
            } else {
                applied.append(" !").append(field);
            }
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] spatialflow: Spatial GUI set like the user's config:{}", applied);
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

    private static void waitClear(ClientGameTestContext context) {
        for (int i = 0; i < 400 && context.computeOnClient(mc -> ShardManager.waveCount() > 0 || Mc26.screen() != null); i++) {
            context.waitTick();
        }
        context.waitTicks(10);
    }

    private static void closeFlow(ClientGameTestContext context, BiConsumer<Boolean, String> check, SpatialScenarios setup,
                                  String name, boolean frozen, String effect) {
        ShatterConfig config = ShatterConfig.get();
        config.effect = effect;
        config.openEffect = ShatterConfig.OPEN_NONE;
        float burst = config.get(Params.GLASS_BURST);
        float gravity = config.get(Params.GLASS_GRAVITY);
        float spin = config.get(Params.GLASS_SPIN);
        if (frozen) {
            config.set(Params.GLASS_BURST, 0);
            config.set(Params.GLASS_GRAVITY, 0);
            config.set(Params.GLASS_SPIN, 0);
        }
        try {
            waitClear(context);
            context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
            context.runOnClient(mc -> FrameAudit.enable(true));
            context.setScreen(PanelScreen::new);
            context.waitTicks(OPEN_WAIT);
            boolean hooked = context.computeOnClient(mc -> SpatialGuiCompat.isShowing(Mc26.screen()));
            long[] closedAt = {0};
            int before = 4;
            List<FrameSeries.Shot> shots = FrameSeries.record(context, before, () -> {
                        closedAt[0] = System.nanoTime();
                        Mc26.setScreen(null);
                    }, () -> new CloseState(Mc26.screen() != null, ShardManager.liveShardCount(),
                            SpatialGuiCompat.closeStartElapsedForTests()),
                    () -> closedAt[0] != 0 && System.nanoTime() - closedAt[0] > CLOSE_WATCH_MS * 1_000_000L, 400);
            List<FrameAudit.Frame> audit = context.computeOnClient(mc -> FrameAudit.since(0));
            context.runOnClient(mc -> FrameAudit.enable(false));
            analyseClose(context, check, name, frozen, hooked, setup != null, shots, audit, before, closedAt[0]);
        } finally {
            config.set(Params.GLASS_BURST, burst);
            config.set(Params.GLASS_GRAVITY, gravity);
            config.set(Params.GLASS_SPIN, spin);
        }
    }

    private static FrameAudit.Frame frame(List<FrameAudit.Frame> audit, long index) {
        for (FrameAudit.Frame f : audit) {
            if (f.index() == index) {
                return f;
            }
        }
        return null;
    }

    private static void analyseClose(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name,
                                     boolean frozen, boolean hooked, boolean spatial, List<FrameSeries.Shot> shots,
                                     List<FrameAudit.Frame> audit, int before, long closedAt) {
        if (shots.size() <= before + 2) {
            check.accept(false, name + ": too few frames recorded (" + shots.size() + ")");
            return;
        }
        FrameSeries.Shot ref = shots.get(before - 1);
        int lastPlane = before - 1;
        int firstEffect = -1;
        StringBuilder rows = new StringBuilder();
        for (int k = 0; k < shots.size(); k++) {
            FrameSeries.Shot s = shots.get(k);
            FrameAudit.Frame f = frame(audit, s.audit());
            boolean plane = f != null && f.planeVisible();
            boolean ours = f != null && f.closes() > 0;
            if (k >= before && plane && !ours && firstEffect < 0) {
                lastPlane = k;
            }
            if (k >= before && ours && firstEffect < 0) {
                firstEffect = k;
            }
            rows.append(String.format(Locale.ROOT, "%n    #%d %+6.1f ms magenta %6d box %-22s plane %s ours %s %s", k,
                    (s.nanos() - closedAt) / 1e6, s.magenta(), s.box() == null ? "-" : java.util.Arrays.toString(s.box()),
                    plane, ours, s.state()));
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: Spatial showed the screen {}, frames:{}", name, hooked, rows);
        if (spatial) {
            check.accept(hooked, name + ": Spatial GUI must show the panel");
        }
        check.accept(ref.magenta() > 500, name + ": the panel must be visible before the close (" + ref.magenta() + " px)");
        check.accept(firstEffect > 0, name + ": the effect must be drawn after the close");
        if (firstEffect < 0 || ref.magenta() <= 500) {
            return;
        }
        FrameSeries.Shot planeShot = shots.get(lastPlane);
        List<String> gaps = new ArrayList<>();
        for (int k = lastPlane + 1; k <= firstEffect; k++) {
            if (shots.get(k).magenta() < planeShot.magenta() / 2) {
                gaps.add("#" + k + "=" + shots.get(k).magenta());
            }
        }
        check.accept(gaps.isEmpty(), name + ": no frame without the interface between the last frame of the plane (#"
                + lastPlane + ", " + planeShot.magenta() + " px) and the first frame of the effect (#" + firstEffect + "): " + gaps);
        FrameSeries.Shot first = shots.get(firstEffect);
        double dcx = Math.abs(first.cx() - planeShot.cx());
        double dcy = Math.abs(first.cy() - planeShot.cy());
        double wRatio = first.boxWidth() / Math.max(1, planeShot.boxWidth());
        double hRatio = first.boxHeight() / Math.max(1, planeShot.boxHeight());
        double nRatio = first.magenta() / (double) Math.max(1, planeShot.magenta());
        String match = String.format(Locale.ROOT, "centre shift %.3f/%.3f of the frame, box %.2fx%.2f, area %.2f "
                + "(plane %s, effect %s)", dcx, dcy, wRatio, hRatio, nRatio, java.util.Arrays.toString(planeShot.box()),
                java.util.Arrays.toString(first.box()));
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: first frame of the effect vs last frame of the plane: {}", name, match);
        if (frozen) {
            check.accept(dcx <= 0.02 && dcy <= 0.02 && wRatio > 0.9 && wRatio < 1.11 && hRatio > 0.9 && hRatio < 1.11
                            && nRatio > 0.8 && nRatio < 1.25,
                    name + ": the first frame of the effect must match the last frame of the plane: " + match);
            List<String> later = new ArrayList<>();
            for (int k = firstEffect; k < shots.size(); k++) {
                FrameSeries.Shot s = shots.get(k);
                if ((s.nanos() - closedAt) / 1e6 <= FROZEN_WATCH_MS && s.magenta() < planeShot.magenta() * 0.35) {
                    later.add("#" + k + String.format(Locale.ROOT, "(%+.0f ms)=", (s.nanos() - closedAt) / 1e6) + s.magenta());
                }
            }
            check.accept(later.isEmpty(), name + ": the still pieces must stay on screen for " + FROZEN_WATCH_MS
                    + " ms after the close: " + later);
        } else {
            FrameSeries.saveStrip(context, FrameSeries.pickNine(shots, lastPlane, Math.min(shots.size() - 1, lastPlane + 24)),
                    name);
        }
    }

    private static void openFlow(ClientGameTestContext context, BiConsumer<Boolean, String> check, boolean spatial,
                                 String name, String openEffect, Runnable onRender, Runnable fromTest) {
        ShatterConfig config = ShatterConfig.get();
        config.openEffect = openEffect;
        config.effect = "glass";
        waitClear(context);
        context.runOnClient(mc -> FrameAudit.enable(true));
        int opens = context.computeOnClient(mc -> ShatterProfiler.openCount());
        int[] shownFor = {0};
        boolean[] seen = {false};
        BooleanSupplier done = () -> {
            Screen s = Mc26.screen();
            if (s == null) {
                return false;
            }
            seen[0] = true;
            boolean ours = OpenAssemble.hides(s) || FlyOpen.isAnimating(s);
            shownFor[0] = ours ? 0 : shownFor[0] + 1;
            return shownFor[0] >= 10;
        };
        int before = 3;
        List<FrameSeries.Shot> shots = FrameSeries.record(context, before, onRender, fromTest, () -> {
            Screen s = Mc26.screen();
            boolean ours = s != null && (OpenAssemble.hides(s) || FlyOpen.isAnimating(s));
            return new OpenState(s != null, ours, ShardRenderer.gatherShardsDrawn(), s != null && SpatialGuiCompat.isShowing(s));
        }, done, 360);
        List<FrameAudit.Frame> audit = context.computeOnClient(mc -> FrameAudit.since(0));
        context.runOnClient(mc -> FrameAudit.enable(false));
        boolean opened = context.computeOnClient(mc -> ShatterProfiler.openCount()) > opens;
        String screen = context.computeOnClient(mc -> Mc26.screen() == null ? "none" : Mc26.screen().getClass().getSimpleName());
        boolean container = context.computeOnClient(mc -> Mc26.screen() instanceof AbstractContainerScreen<?>);
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.waitTicks(5);

        int start = -1;
        int shown = -1;
        for (int k = before; k < shots.size(); k++) {
            OpenState st = (OpenState) shots.get(k).state();
            if (start < 0 && st.open()) {
                start = k;
            }
            if (start >= 0 && st.open() && !st.ours()) {
                shown = k;
                break;
            }
        }
        StringBuilder rows = new StringBuilder();
        for (int k = 0; k < shots.size(); k++) {
            FrameSeries.Shot s = shots.get(k);
            FrameAudit.Frame f = frame(audit, s.audit());
            rows.append(String.format(Locale.ROOT, "%n    #%d %s planeAlpha %.2f", k, s.state(), f == null ? -9f : f.planeAlpha()));
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: opened {} ({}), our phase #{}..#{}, frames:{}", name, opened, screen,
                start, shown, rows);
        check.accept(container || screen.contains("Inventory"), name + ": the screen must open (" + screen + ")");
        check.accept(opened, name + ": our opening effect must start");
        if (!opened || start < 0 || shown <= start) {
            check.accept(false, name + ": our phase of the opening must have frames (start #" + start + ", shown #" + shown + ")");
            return;
        }
        boolean fly = openEffect.equals(ShatterConfig.OPEN_FLY_FROM_BLOCK);
        boolean onPlane = ((OpenState) shots.get(shown).state()).spatial();
        if (spatial) {
            check.accept(onPlane, name + ": Spatial GUI must show the screen once it is open");
        }
        List<String> planeFrames = new ArrayList<>();
        for (int k = start; k < shown; k++) {
            FrameAudit.Frame f = frame(audit, shots.get(k).audit());
            if (!fly && f != null && f.planeVisible()) {
                planeFrames.add("#" + k + String.format(Locale.ROOT, "=%.2f", f.planeAlpha()));
            }
        }
        check.accept(planeFrames.isEmpty(), name + ": Spatial GUI's own plane must not show before our effect has put "
                + "the screen together: " + planeFrames);
        int n = start + (shown - start) / 2;
        FrameSeries.Shot mid = shots.get(n);
        OpenState midState = (OpenState) mid.state();
        FrameSeries.Shot last = shots.getLast();
        FrameSeries.Shot prev = shots.get(shots.size() - 2);
        double noise = FrameGrab.changed(last.grid(), prev.grid(), 24);
        double limit = Math.max(0.004, noise * 3 + 0.002);
        double midVsFinal = FrameGrab.changed(mid.grid(), last.grid(), 24);
        FrameAudit.Frame midAudit = frame(audit, mid.audit());
        String midText = String.format(Locale.ROOT, "frame #%d of #%d..#%d: %s, plane alpha %.2f, differs from the open "
                + "screen in %.2f%% (needs > %.2f%%)", n, start, shown, midState, midAudit == null ? -9f : midAudit.planeAlpha(),
                midVsFinal * 100, limit * 100);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {}", name, midText);
        check.accept((fly || midState.drawn() > 0) && midVsFinal > limit, name + ": our effect must be visible and the "
                + "open screen not yet: " + midText);
        if (!fly) {
            check.accept(midAudit == null || !midAudit.planeVisible(), name + ": no Spatial GUI plane in frame N: " + midText);
        }
        FrameSeries.saveStrip(context, FrameSeries.pickNine(shots, start - 1, Math.min(shots.size() - 1, shown + 1)), name);
    }
}
