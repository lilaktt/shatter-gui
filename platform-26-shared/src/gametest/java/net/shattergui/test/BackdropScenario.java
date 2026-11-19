package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.FrameAudit;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

final class BackdropScenario {
    static final int TICKS = 30;
    private static final double STEP_SHARE = 0.5;
    private static final double MIN_STEP = 6;
    private static final double OFF_SHARE = 0.25;
    private static final double MIN_OFF = 4;

    record Series(String name, double[] band, double[] outside, double[] panel, String[] state) {
    }

    private BackdropScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String mode) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        context.runOnClient(mc -> FrameAudit.enable(true));
        context.getInput().resizeWindow(960, 540);
        context.waitTicks(5);
        try {
            BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
            BlockPos chest = feet.offset(-1, 0, 2);
            BlockPos furnace = feet.offset(1, 0, 2);
            command(singleplayer, "setblock %d %d %d minecraft:chest[facing=north]", chest);
            command(singleplayer, "setblock %d %d %d minecraft:furnace[facing=north]", furnace);
            List<String> opens = List.of(ShatterConfig.OPEN_FLY_FROM_BLOCK, ShatterConfig.OPEN_DATA_PREFIX + "glass",
                    ShatterConfig.OPEN_DATA_PREFIX + "pop", ShatterConfig.OPEN_NONE);
            List<Series> all = new ArrayList<>();
            for (String open : opens) {
                for (String target : List.of("chest", "furnace", "inventory")) {
                    if (target.equals("inventory") && open.equals(ShatterConfig.OPEN_FLY_FROM_BLOCK)) {
                        continue;
                    }
                    BlockPos at = target.equals("chest") ? chest : target.equals("furnace") ? furnace : null;
                    String close = open.equals(ShatterConfig.OPEN_FLY_FROM_BLOCK) && at != null ? "fly" : "glass";
                    config.openEffect = open;
                    config.effect = close;
                    String name = "backdrop_" + mode + "_" + target + "_" + shortName(open) + "_" + close;
                    if (at != null) {
                        FlyScenario.lookAt(context, singleplayer, feet, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
                    } else {
                        FlyScenario.lookAt(context, singleplayer, feet, feet.getX() + 0.5, feet.getY() + 0.5, feet.getZ() + 3.5);
                    }
                    one(context, check, name, at != null, !open.equals(ShatterConfig.OPEN_NONE) && !mode.contains("third"),
                            all);
                }
            }
            StringBuilder table = new StringBuilder();
            for (Series s : all) {
                table.append(describe(s));
            }
            ShatterGuiClient.LOGGER.info("[ShatterTest] backdrop {} brightness per frame (band above the panel / outside "
                    + "but the hotbar / panel; state: screen, flat GUI drawn, opening and closing waves drawn):\n{}", mode, table);
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            context.runOnClient(mc -> FrameAudit.enable(false));
            context.getInput().resizeWindow(1920, 1080);
            context.waitTicks(5);
        }
    }

    private static void one(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name,
                            boolean useKey, boolean checked, List<Series> all) {
        waitUntilClear(context);
        context.waitTicks(5);
        long from = context.computeOnClient(mc -> FrameAudit.next());
        List<String> openFrames = new ArrayList<>();
        List<String> openState = new ArrayList<>();
        if (useKey) {
            context.getInput().pressKey(options -> options.keyUse);
        } else {
            context.runOnClient(mc -> Mc26.setScreen(new InventoryScreen(mc.player)));
        }
        context.getInput().setCursorPos(2, 2);
        for (int k = 0; k < TICKS; k++) {
            String frame = name + "_open_" + k;
            FrameGrab.request(context, frame, k < 12);
            openFrames.add(frame);
            openState.add(context.computeOnClient(BackdropScenario::state));
            context.waitTick();
        }
        double[] panel = context.computeOnClient(mc -> {
            if (Mc26.screen() == null) {
                return new double[]{0.3, 0.3, 0.7, 0.7};
            }
            net.shattergui.shatter.ImageRect r = net.shattergui.capture.ScreenCapture.guiBounds(Mc26.screen());
            return new double[]{r.x0(), r.y0(), r.x1(), r.y1()};
        });
        boolean opened = context.computeOnClient(mc -> Mc26.screen() != null);
        check.accept(opened, name + ": the screen must open");
        List<String> closeFrames = new ArrayList<>();
        List<String> closeState = new ArrayList<>();
        context.runOnClient(mc -> Mc26.setScreen(null));
        for (int k = 0; k < TICKS; k++) {
            String frame = name + "_close_" + k;
            FrameGrab.request(context, frame, k < 12);
            closeFrames.add(frame);
            closeState.add(context.computeOnClient(BackdropScenario::state));
            context.waitTick();
        }
        Series open = series(context, name + "_open", openFrames, openState, panel);
        Series close = series(context, name + "_close", closeFrames, closeState, panel);
        all.add(open);
        all.add(close);
        if (checked) {
            smooth(check, open);
            smooth(check, close);
        }
        if (name.contains("_glass_") && !name.contains("spatial")) {
            int first = -1;
            for (int k = 0; k < open.state().length && first < 0; k++) {
                first = open.state()[k].contains("hidden") && open.state()[k].contains("open=1") ? k : -1;
            }
            double world = open.panel()[0];
            double shown = open.panel()[open.panel().length - 1];
            check.accept(first >= 0 && Math.abs(open.panel()[first] - world) < Math.abs(shown - world) * 0.5,
                    String.format(Locale.ROOT, "%s: the first frame of the gathering already looks like the panel "
                            + "(%.1f; world %.1f, shown %.1f)", name, first >= 0 ? open.panel()[first] : Double.NaN,
                            world, shown));
        }
    }

    private static String state(Minecraft mc) {
        String screen = Mc26.screen() == null ? "-" : Mc26.screen().getClass().getSimpleName().replace("Screen", "");
        List<FrameAudit.Frame> last = FrameAudit.since(FrameAudit.next() - 1);
        String drawn = last.isEmpty() ? "" : String.format(Locale.ROOT,
                " [flat=%s plane=%.2f open=%d close=%d backdrop=%.2f]", last.getFirst().flat() ? "y" : "n",
                last.getFirst().planeAlpha(), last.getFirst().openings(), last.getFirst().closes(),
                last.getFirst().backdrop());
        return screen + (OpenAssemble.hides(Mc26.screen()) ? " hidden" : "") + (FlyOpen.isAnimating(Mc26.screen()) ? " flying" : "")
                + drawn;
    }

    private static Series series(ClientGameTestContext context, String name, List<String> frames, List<String> state,
                                 double[] panel) {
        int n = frames.size();
        double[] band = new double[n];
        double[] outside = new double[n];
        double[] inside = new double[n];
        double[] above = {0.02, 0.02, 0.98, Math.max(0.05, panel[1] - 0.04)};
        for (int k = 0; k < n; k++) {
            FrameGrab.Frame f = FrameGrab.get(context, frames.get(k));
            band[k] = luminance(f, above, null);
            outside[k] = luminance(f, new double[]{0, 0, 1, 0.86},
                    new double[]{panel[0] - 0.02, panel[1] - 0.02, panel[2] + 0.02, panel[3] + 0.02});
            inside[k] = luminance(f, new double[]{panel[0] + 0.02, panel[1] + 0.02, panel[2] - 0.02, panel[3] - 0.02}, null);
        }
        return new Series(name, band, outside, inside, state.toArray(String[]::new));
    }

    static double luminance(FrameGrab.Frame f, double[] area, double[] except) {
        if (f == null) {
            return Double.NaN;
        }
        double sum = 0;
        int cells = 0;
        for (int y = 0; y < FrameGrab.H; y++) {
            double fy = (y + 0.5) / FrameGrab.H;
            for (int x = 0; x < FrameGrab.W; x++) {
                double fx = (x + 0.5) / FrameGrab.W;
                if (fx < area[0] || fx > area[2] || fy < area[1] || fy > area[3]) {
                    continue;
                }
                if (except != null && fx >= except[0] && fx <= except[2] && fy >= except[1] && fy <= except[3]) {
                    continue;
                }
                int o = (y * FrameGrab.W + x) * 3;
                sum += 0.2126 * f.rgb()[o] + 0.7152 * f.rgb()[o + 1] + 0.0722 * f.rgb()[o + 2];
                cells++;
            }
        }
        return cells == 0 ? Double.NaN : sum / cells;
    }

    static void smooth(BiConsumer<Boolean, String> check, Series s) {
        smooth(check, s.name() + " (band above the panel)", s.band());
        smooth(check, s.name() + " (outside the panel)", s.outside());
    }

    static void smooth(BiConsumer<Boolean, String> check, String name, double[] b) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : b) {
            if (!Double.isNaN(v)) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        double total = max - min;
        double stepLimit = Math.max(MIN_STEP, total * STEP_SHARE);
        double offLimit = Math.max(MIN_OFF, total * OFF_SHARE);
        double worstStep = 0;
        int stepAt = -1;
        double worstOff = 0;
        int offAt = -1;
        for (int k = 1; k < b.length; k++) {
            if (Double.isNaN(b[k]) || Double.isNaN(b[k - 1])) {
                continue;
            }
            double step = Math.abs(b[k] - b[k - 1]);
            if (step > worstStep) {
                worstStep = step;
                stepAt = k;
            }
            if (k + 1 < b.length && !Double.isNaN(b[k + 1])) {
                double off = Math.abs(b[k] - (b[k - 1] + b[k + 1]) / 2);
                if (off > worstOff) {
                    worstOff = off;
                    offAt = k;
                }
            }
        }
        check.accept(worstStep <= stepLimit, String.format(Locale.ROOT,
                "%s: the brightness jumps by %.1f between frames %d and %d (allowed %.1f, whole change %.1f)",
                name, worstStep, stepAt - 1, stepAt, stepLimit, total));
        check.accept(worstOff <= offLimit, String.format(Locale.ROOT,
                "%s: frame %d is %.1f off the curve of its neighbours (allowed %.1f, whole change %.1f)",
                name, offAt, worstOff, offLimit, total));
    }

    private static String describe(Series s) {
        StringBuilder out = new StringBuilder("  ").append(s.name()).append('\n');
        for (int k = 0; k < s.band().length; k++) {
            out.append(String.format(Locale.ROOT, "    %2d  %6.1f %6.1f %6.1f  %s%n", k, s.band()[k], s.outside()[k],
                    s.panel()[k], s.state()[k]));
        }
        return out.toString();
    }

    private static String shortName(String open) {
        int colon = open.indexOf(':');
        return colon >= 0 ? open.substring(colon + 1) : open;
    }

    private static void waitUntilClear(ClientGameTestContext context) {
        for (int i = 0; i < 400 && context.computeOnClient(mc -> ShardManager.waveCount() > 0 || Mc26.screen() != null); i++) {
            if (i == 200) {
                context.runOnClient(mc -> Mc26.setScreen(null));
            }
            context.waitTick();
        }
    }

    private static void command(TestSingleplayerContext singleplayer, String format, BlockPos pos) {
        singleplayer.getServer().runCommand(String.format(Locale.ROOT, format, pos.getX(), pos.getY(), pos.getZ()));
    }
}
