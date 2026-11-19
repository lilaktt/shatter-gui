package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;

import java.util.Locale;
import java.util.function.BiConsumer;

final class SettleScenario {
    static final int MAGENTA = 0xFFFF00FF;
    private static final double[] VIEW = {0, 0, 1, 0.86};
    private static final double[] WALL_VIEW = {0.02, 0.02, 0.98, 0.7};
    private static final int LIFETIME = 900;
    static final int SETTLE_TICKS = 4;

    private SettleScenario() {
    }

    static class SolidScreen extends Screen {
        private final int color;

        SolidScreen(int color) {
            super(Component.literal("solid"));
            this.color = color;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, color);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String mode) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        boolean onTop = config.alwaysOnTop;
        int lifetime = config.get(Params.GLASS_LIFETIME);
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        command(singleplayer, "gamerule random_tick_speed 0");
        command(singleplayer, "gamerule advance_time false");
        command(singleplayer, "time set 6000");
        try {
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.effect = "glass";
            config.set(Params.GLASS_LIFETIME, LIFETIME);
            clear(singleplayer, feet);
            for (boolean on : new boolean[]{true, false}) {
                one(context, singleplayer, check, mode, on, feet);
            }
        } finally {
            config.effect = effect;
            config.openEffect = open;
            config.alwaysOnTop = onTop;
            config.set(Params.GLASS_LIFETIME, lifetime);
            hold(context, false);
            context.runOnClient(mc -> ShardManager.clear());
            clear(singleplayer, feet);
            tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 0);
            command(singleplayer, "gamerule random_tick_speed 3");
            command(singleplayer, "gamerule advance_time true");
            context.waitTicks(5);
        }
    }

    private static void clear(TestSingleplayerContext singleplayer, BlockPos feet) {
        command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", feet.getX() - 8, feet.getY(), feet.getZ() - 12,
                feet.getX() + 8, feet.getY() + 6, feet.getZ() + 8);
    }

    private static void one(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                            BiConsumer<Boolean, String> check, String mode, boolean on, BlockPos feet) {
        String name = "settle_" + mode + "_" + (on ? "on" : "off");
        ShatterConfig.get().alwaysOnTop = on;
        context.runOnClient(mc -> ShardManager.clear());
        tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 40);
        context.waitTicks(10);
        hold(context, true);
        context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
        context.setScreen(() -> new SolidScreen(MAGENTA));
        context.waitTicks(5);
        context.runOnClient(mc -> Mc26.setScreen(null));
        int still = waitStill(context, 240);
        context.waitTicks(12);
        int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} shards at rest after {} ticks", name, shards, still);
        check.accept(shards > 0 && still >= 0, name + ": the pieces must come to rest (" + shards + " shards, "
                + still + " ticks)");

        FullFrame.request(context, name + "_a_held", true);
        FullFrame.Image held = FullFrame.get(context, name + "_a_held");
        hold(context, false);
        context.waitTicks(SETTLE_TICKS + 4);
        int settled = settledCount(context);
        FullFrame.request(context, name + "_b_settled", true);
        FullFrame.Image settledFrame = FullFrame.get(context, name + "_b_settled");
        FullFrame.Blob magentaHeld = held == null ? null : FullFrame.blob(held, FullFrame::magenta, VIEW);
        FullFrame.Blob magentaSettled = settledFrame == null ? null
                : FullFrame.blob(settledFrame, FullFrame::magenta, VIEW);
        double[] jump = FullFrame.changed(held, settledFrame, 6, VIEW, FullFrame::magenta);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: settled {} of {}; magenta pixels {} held, {} settled; changed {} of "
                        + "{} magenta pixels, largest difference {}", name, settled, shards,
                magentaHeld == null ? -1 : magentaHeld.count(), magentaSettled == null ? -1 : magentaSettled.count(),
                String.format(Locale.ROOT, "%.4f", jump[0]), (long) jump[2], (int) jump[1]);
        check.accept(held != null && settledFrame != null, name + ": the frames before and after settling must arrive");
        if (settled >= 0) {
            check.accept(settled >= shards * 0.9, name + ": the pieces at rest must be settled (" + settled + " of "
                    + shards + ")");
        }
        if (on) {
            check.accept(magentaHeld != null && magentaHeld.count() > 200, name + ": the pieces must show ("
                    + (magentaHeld == null ? -1 : magentaHeld.count()) + " magenta pixels)");
            check.accept(jump[0] <= 0.002, String.format(Locale.ROOT, "%s: settling must not change the pieces on the "
                    + "screen (%.4f of their pixels changed, largest difference %d)", name, jump[0], (int) jump[1]));
        }

        double minZ = context.computeOnClient(mc -> {
            double min = Double.MAX_VALUE;
            for (ShardManager.Batch batch : ShardManager.batches()) {
                for (ShardInstance shard : batch.shards()) {
                    min = Math.min(min, shard.pos().z - 0.5);
                }
            }
            return min;
        });
        int wallZ = (int) Math.floor(minZ) - 1;
        int standZ = wallZ - 2;
        tp(singleplayer, feet.getX(), feet.getY(), standZ, 0, 25);
        context.waitTicks(10);
        FullFrame.request(context, name + "_c_nowall", true);
        FullFrame.Image noWall = FullFrame.get(context, name + "_c_nowall");
        command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", feet.getX() - 8, feet.getY(), wallZ,
                feet.getX() + 8, feet.getY() + 5, wallZ);
        context.waitTicks(6);
        FullFrame.request(context, name + "_d_wall", true);
        FullFrame.Image wall = FullFrame.get(context, name + "_d_wall");
        int left = context.computeOnClient(mc -> ShardManager.liveShardCount());
        context.runOnClient(mc -> ShardManager.clear());
        context.waitTicks(4);
        FullFrame.request(context, name + "_e_wall_empty", true);
        FullFrame.Image empty = FullFrame.get(context, name + "_e_wall_empty");
        command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", feet.getX() - 8, feet.getY(), wallZ,
                feet.getX() + 8, feet.getY() + 5, wallZ);

        int visible = noWall == null ? -1 : FullFrame.blob(noWall, FullFrame::magenta, VIEW).count();
        int through = wall == null ? -1 : FullFrame.blob(wall, FullFrame::magenta, VIEW).count();
        boolean pack = context.computeOnClient(mc -> net.shattergui.compat.iris.IrisCompat.shaderPackInUse());
        double[] behind = FullFrame.changed(wall, empty, pack ? 48 : 8, WALL_VIEW, null);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} shards; magenta pixels without the wall {}, with it {}; pixels "
                        + "changed behind the wall {} (largest difference {})", name, left, visible, through,
                String.format(Locale.ROOT, "%.5f", behind[0]), (int) behind[1]);
        check.accept(visible > 100, name + ": without the wall the settled pieces must show (" + visible + " pixels)");
        check.accept(through == 0, name + ": the wall must hide the settled pieces (" + through + " magenta pixels)");
        check.accept(behind[0] == 0, String.format(Locale.ROOT, "%s: behind the wall the frame must be as without the "
                + "pieces (%.5f changed, largest difference %d)", name, behind[0], (int) behind[1]));
    }

    static int waitStill(ClientGameTestContext context, int limit) {
        int row = 0;
        for (int tick = 0; tick < limit; tick++) {
            context.waitTick();
            boolean still = context.computeOnClient(mc -> {
                if (ShardManager.liveShardCount() == 0) {
                    return false;
                }
                for (ShardManager.Batch batch : ShardManager.batches()) {
                    for (ShardInstance shard : batch.shards()) {
                        org.joml.Quaternionf turn = new org.joml.Quaternionf(shard.rot())
                                .mul(new org.joml.Quaternionf(shard.prevRot()).conjugate());
                        if (shard.pos().distanceSquared(shard.prevPos()) > 1.0e-8
                                || turn.x * turn.x + turn.y * turn.y + turn.z * turn.z > 1.0e-8f) {
                            return false;
                        }
                    }
                }
                return true;
            });
            row = still ? row + 1 : 0;
            if (row >= 3) {
                return tick;
            }
        }
        return -1;
    }

    static void hold(ClientGameTestContext context, boolean held) {
        context.runOnClient(mc -> {
            try {
                ShardManager.class.getMethod("holdSettledForTests", boolean.class).invoke(null, held);
            } catch (ReflectiveOperationException e) {
            }
        });
    }

    static int settledCount(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            try {
                return (Integer) ShardManager.class.getMethod("settledShardCount").invoke(null);
            } catch (ReflectiveOperationException e) {
                return -1;
            }
        });
    }

    static void tp(TestSingleplayerContext singleplayer, int x, int y, int z, float yaw, float pitch) {
        command(singleplayer, "tp @p %.1f %d %.1f %.3f %.3f", x + 0.5, y, z + 0.5, yaw, pitch);
    }

    static void command(TestSingleplayerContext singleplayer, String format, Object... args) {
        singleplayer.getServer().runCommand(String.format(Locale.ROOT, format, args));
    }
}
