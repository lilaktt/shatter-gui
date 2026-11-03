package net.shattergui.test;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.shattergui.capture.CapturePool;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Closing screens with the glass effect (the minimum of the port): the inventory and a real chest, closed with Esc;
 * the pieces must start in the first frame after the close without a freeze, fall onto the floor without going
 * through it, and with {@code alwaysOnTop} stay visible through a wall that hides them otherwise.
 */
final class CloseScenarios {
    /** Feet of the player on the flat world (grass top at y = -61 + 1). */
    private static final int FEET_Y = -60;
    private static final BlockPos CHEST = new BlockPos(0, FEET_Y, 2);

    private CloseScenarios() {
    }

    /** The inventory and a chest, opened and closed with Esc, glass. */
    static void glass(TestContext context) {
        standFacingChest(context, 10);

        // Creative: the creative inventory (its tabs lie outside the panel); survival: the player model, the recipe book.
        close(context, "glass_creative_inventory", () -> new InventoryScreen(Minecraft.getInstance().player), null);
        context.command("gamemode survival");
        context.waitTicks(5);
        close(context, "glass_inventory", () -> new InventoryScreen(Minecraft.getInstance().player), null);
        context.command("gamemode creative");
        context.waitTicks(5);
        close(context, "glass_chest", null, CHEST);
        int textures = context.computeOnClient(mc -> CapturePool.texturesWithMemory());
        context.check(textures <= 1, "glass: after the effects at most one capture texture keeps its memory (" + textures + ")");
    }

    /** Looking down at the floor: no piece goes below it, most come to rest on it. */
    static void collide(TestContext context) {
        standFacingChest(context, 40);
        double floorTop = FEET_Y;
        openInventory(context);
        pressEscape(context);
        context.waitTick();
        int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
        context.check(shards > 0, "collide: glass spawned shards (" + shards + ")");
        double lowest = Double.MAX_VALUE;
        double landed = 0;
        for (int t = 1; t <= 50; t++) {
            double[] s = context.computeOnClient(mc -> {
                double low = Double.MAX_VALUE;
                int near = 0;
                int count = 0;
                for (ShardManager.Batch batch : ShardManager.batches()) {
                    for (ShardInstance shard : batch.shards()) {
                        low = Math.min(low, shard.pos().y);
                        near += shard.pos().y < floorTop + 0.3 ? 1 : 0;
                        count++;
                    }
                }
                return new double[]{low, count == 0 ? -1 : near / (double) count};
            });
            lowest = Math.min(lowest, s[0]);
            if (s[1] >= 0) {
                landed = s[1];
            }
            if (t == 5 || t == 15 || t == 30) {
                context.shot("collide_t" + t);
            }
            context.waitTick();
        }
        context.log("collide: lowest centre {} (floor top {}), on the floor {}%", lowest, floorTop, Math.round(landed * 100));
        context.check(lowest >= floorTop - 0.01, "collide: no shard below the floor (lowest centre " + lowest + ")");
        context.check(landed >= 0.5, "collide: shards lie on the floor (" + Math.round(landed * 100) + "% within 0.3)");
        context.waitFor(mc -> ShardManager.waveCount() == 0, 400);
    }

    /**
     * A wall right in front of the player: the plane of the pieces lies inside it (1 block from the camera), so in the
     * world they are hidden; drawn over the world they are not.
     */
    static void onTop(TestContext context) {
        standFacingChest(context, 10);
        context.command(String.format(Locale.ROOT, "fill -2 %d 1 2 %d 1 minecraft:stone", FEET_Y, FEET_Y + 2));
        context.waitTicks(10);
        try {
            NativeImage wall = context.frame();
            double on = visibleThroughWall(context, true, wall);
            double off = visibleThroughWall(context, false, wall);
            wall.close();
            context.log("ontop: changed share of the frame centre: on {}, off {}", on, off);
            context.check(on > 0.3, "ontop: with alwaysOnTop the pieces show over the wall (" + on + ")");
            context.check(off < 0.05, "ontop: without alwaysOnTop the wall hides them (" + off + ")");
        } finally {
            context.runOnClient(mc -> ShatterConfig.get().alwaysOnTop = true);
            context.command(String.format(Locale.ROOT, "fill -2 %d 1 2 %d 1 minecraft:air", FEET_Y, FEET_Y + 2));
            context.waitTicks(5);
        }
    }

    private static double visibleThroughWall(TestContext context, boolean onTop, NativeImage wall) {
        context.runOnClient(mc -> ShatterConfig.get().alwaysOnTop = onTop);
        openInventory(context);
        pressEscape(context);
        context.waitTicks(2);
        String name = "ontop_" + (onTop ? "on" : "off");
        context.shot(name + "_t2");
        NativeImage frame = context.frame();
        double changed = changedShare(wall, frame);
        frame.close();
        context.waitFor(mc -> ShardManager.waveCount() == 0, 400);
        return changed;
    }

    /** Share of pixels in the middle third of the frame that differ noticeably from {@code before}. */
    private static double changedShare(NativeImage before, NativeImage after) {
        int w = Math.min(before.getWidth(), after.getWidth());
        int h = Math.min(before.getHeight(), after.getHeight());
        int changed = 0;
        int total = 0;
        for (int y = h / 3; y < 2 * h / 3; y += 2) {
            for (int x = w / 3; x < 2 * w / 3; x += 2) {
                int a = before.getPixelRGBA(x, y);
                int b = after.getPixelRGBA(x, y);
                int d = Math.abs((a & 0xFF) - (b & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF))
                        + Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF));
                changed += d > 40 ? 1 : 0;
                total++;
            }
        }
        return total == 0 ? 0 : changed / (double) total;
    }

    /**
     * Opens a screen (built on the client, or a real chest used like a right click), shoots it, closes it with Esc and
     * checks the start of the effect.
     */
    private static void close(TestContext context, String name, java.util.function.Supplier<Screen> screen, BlockPos block) {
        if (block != null) {
            useBlock(context, block);
            boolean open = context.waitFor(mc -> mc.screen instanceof ContainerScreen, 40);
            context.check(open, name + ": the chest opens");
        } else {
            context.runOnClient(mc -> mc.setScreen(screen.get()));
        }
        context.waitTicks(10);
        context.shot(name + "_open");
        int before = context.computeOnClient(mc -> ShatterProfiler.closeCount());
        long frame = TestContext.frames();
        pressEscape(context);
        context.check(context.computeOnClient(mc -> mc.screen == null), name + ": Esc closes the screen at once");
        context.waitTick();
        int waves = context.computeOnClient(mc -> ShardManager.waveCount());
        int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
        context.check(waves == 1 && shards > 0, name + ": one glass wave with shards after one tick (" + waves
                + " waves, " + shards + " shards)");
        context.shot(name + "_t1");
        context.waitTicks(2);
        context.shot(name + "_t3");
        double gap = TestContext.longestFrameGapSince(frame);
        double latency = context.computeOnClient(mc -> ShatterProfiler.lastCloseLatencyMs());
        context.log("{}: close #{} -> latency {} ms, longest frame gap after the close {} ms", name,
                before + 1, String.format(Locale.ROOT, "%.1f", latency), String.format(Locale.ROOT, "%.1f", gap));
        context.check(gap < 100, name + ": no freeze after the close (longest frame gap " + gap + " ms)");
        context.waitTicks(5);
        context.shot(name + "_t8");
        context.waitTicks(12);
        context.shot(name + "_t20");
        boolean gone = context.waitFor(mc -> ShardManager.waveCount() == 0, 400);
        context.check(gone, name + ": the wave ends");
        context.shot(name + "_after");
    }

    private static void openInventory(TestContext context) {
        context.runOnClient(mc -> mc.setScreen(new InventoryScreen(mc.player)));
        context.waitTicks(10);
    }

    /** Esc as the keyboard sends it: the screen's own key handling (closes containers through the player). */
    private static void pressEscape(TestContext context) {
        context.runOnClient(mc -> {
            if (mc.screen != null) {
                mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
        });
    }

    private static void useBlock(TestContext context, BlockPos pos) {
        context.runOnClient(mc -> {
            Vec3 hit = Vec3.atCenterOf(pos).add(0, 0, -0.5);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.NORTH, pos, false));
        });
    }

    /** On the flat floor in front of a chest, looking along +z and {@code pitch} degrees down; no screen. */
    private static void standFacingChest(TestContext context, int pitch) {
        context.runOnClient(mc -> mc.setScreen(null));
        context.command(String.format(Locale.ROOT, "fill -3 %d -2 3 %d 4 minecraft:air", FEET_Y, FEET_Y + 3));
        context.command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:chest[facing=north]",
                CHEST.getX(), CHEST.getY(), CHEST.getZ()));
        context.command(String.format(Locale.ROOT, "tp @s 0.5 %d 0.5 0 %d", FEET_Y, pitch));
        context.waitTicks(10);
        context.waitFor(mc -> ShardManager.waveCount() == 0, 400);
    }
}
