package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.BlockCollision;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

final class WaterScenario {
    private static final int LIFETIME = 900;
    private static final int WATCH_TICKS = 70;
    private static final int PAIRS = 12;
    private static final int THRESHOLD = 40;
    private static final int SEEN = 150;
    private static final double[] VIEW = {0, 0, 1, 0.86};
    private static final int[] SHOTS = {1, 3, 6, 10, 15, 20, 30, 45, 60, 70};
    private static final int HALF = 4;
    private static final int DEPTH = 3;

    private WaterScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String mode) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        boolean onTop = config.alwaysOnTop;
        int lifetime = config.get(Params.GLASS_LIFETIME);
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        SettleScenario.command(singleplayer, "gamerule random_tick_speed 0");
        SettleScenario.command(singleplayer, "gamerule advance_time false");
        SettleScenario.command(singleplayer, "time set 6000");
        SettleScenario.command(singleplayer, "effect give @p minecraft:water_breathing infinite 0 true");
        SpatialScenarios spatial = null;
        Object allScreens = null;
        try {
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.effect = "glass";
            config.alwaysOnTop = true;
            config.set(Params.GLASS_LIFETIME, LIFETIME);
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("spatial-gui")) {
                spatial = new SpatialScenarios(context, check);
                allScreens = spatial.getSpatial("allScreens");
                spatial.trySetSpatial("allScreens", true);
                spatial.setSpatial("firstPersonModeInventory", false);
                spatial.setSpatial("firstPersonModeContainers", false);
            }
            clear(singleplayer, feet);
            pool(singleplayer, feet);
            context.waitTicks(10);
            one(context, singleplayer, check, "water_" + mode + "_first", feet, CameraType.FIRST_PERSON, true);
            one(context, singleplayer, check, "water_" + mode + "_third", feet, CameraType.THIRD_PERSON_BACK, false);
        } finally {
            if (spatial != null) {
                spatial.trySetSpatial("allScreens", allScreens == null ? false : allScreens);
            }
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.runOnClient(mc -> net.shattergui.render.ShardRenderer.suppressForTests(false));
            config.effect = effect;
            config.openEffect = open;
            config.alwaysOnTop = onTop;
            config.set(Params.GLASS_LIFETIME, lifetime);
            context.runOnClient(mc -> ShardManager.clear());
            clear(singleplayer, feet);
            SettleScenario.command(singleplayer, "effect clear @p minecraft:water_breathing");
            SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 0);
            SettleScenario.command(singleplayer, "gamerule random_tick_speed 3");
            SettleScenario.command(singleplayer, "gamerule advance_time true");
            context.waitTicks(5);
        }
    }

    private static void clear(TestSingleplayerContext singleplayer, BlockPos feet) {
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", feet.getX() - 8, feet.getY(),
                feet.getZ() - 8, feet.getX() + 8, feet.getY() + 8, feet.getZ() + 8);
    }

    private static void pool(TestSingleplayerContext singleplayer, BlockPos feet) {
        int x = feet.getX();
        int y = feet.getY();
        int z = feet.getZ();
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", x - HALF - 1, y, z - HALF - 1,
                x + HALF + 1, y + DEPTH, z + HALF + 1);
        water(singleplayer, feet);
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", x, y + 1, z, x, y + DEPTH, z);
    }

    private static void water(TestSingleplayerContext singleplayer, BlockPos feet) {
        int x = feet.getX();
        int y = feet.getY();
        int z = feet.getZ();
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:water", x - HALF, y + 1, z - HALF,
                x + HALF, y + DEPTH, z + HALF);
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", x, y + 1, z, x, y + DEPTH, z);
    }

    private static void lid(TestSingleplayerContext singleplayer, BlockPos feet, boolean on) {
        int x = feet.getX();
        int y = feet.getY() + DEPTH;
        int z = feet.getZ();
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:%s", x - HALF, y, z - HALF, x + HALF, y,
                z + HALF, on ? "stone" : "water");
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", x, y, z, x, y, z);
    }

    static boolean inWater(Minecraft mc, double x, double y, double z) {
        BlockPos cell = BlockPos.containing(x, y, z);
        FluidState fluid = mc.level.getFluidState(cell);
        return fluid.is(FluidTags.WATER) && y < cell.getY() + fluid.getHeight(mc.level, cell);
    }

    private static void one(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                            BiConsumer<Boolean, String> check, String name, BlockPos feet, CameraType view, boolean full) {
        context.runOnClient(mc -> ShardManager.clear());
        context.runOnClient(mc -> mc.options.setCameraType(view));
        SettleScenario.tp(singleplayer, feet.getX(), feet.getY() + DEPTH + 1, feet.getZ(), 0, 55);
        context.waitTicks(15);
        boolean pack = context.computeOnClient(mc -> net.shattergui.compat.iris.IrisCompat.shaderPackInUse());
        context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
        context.setScreen(() -> new SettleScenario.SolidScreen(SettleScenario.MAGENTA));
        context.waitTicks(5);
        context.runOnClient(mc -> Mc26.setScreen(null));

        ShardInstance[] tracked = new ShardInstance[1];
        Map<ShardInstance, int[]> seen = new IdentityHashMap<>();
        List<String> rows = new ArrayList<>();
        Map<Integer, String> frames = new java.util.LinkedHashMap<>();
        int early = 0;
        int shot = 0;
        for (int tick = 1; tick <= WATCH_TICKS; tick++) {
            context.waitTick();
            final int t = tick;
            Object[] row = context.computeOnClient(mc -> sample(mc, t, tracked, seen));
            rows.add((String) row[0]);
            early += (Integer) row[1];
            if (shot < SHOTS.length && SHOTS[shot] == tick) {
                String frame = name + "_t" + tick;
                frames.put(tick, frame);
                context.runOnClient(mc -> FullFrame.grab(mc, frame, true,
                        image -> FullFrame.blob(image, FullFrame::magenta, VIEW).count()));
                shot++;
            }
        }
        StringBuilder magenta = new StringBuilder();
        for (Map.Entry<Integer, String> frame : frames.entrySet()) {
            Object count = FullFrame.analysis(context, frame.getValue());
            int pixels = count instanceof Integer i ? i : -1;
            magenta.append(" t").append(frame.getKey()).append('=').append(pixels);
        }
        for (String row : rows) {
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {}", name, row);
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: magenta pixels:{} ({}, {})", name, magenta,
                pack ? "shader pack" : "no shader pack", view);
        check.accept(early == 0, name + ": " + early + " pieces vanished before the end of their life");

        SettleScenario.waitStill(context, 200);
        int[] counts = context.computeOnClient(WaterScenario::counts);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: at rest: alive {} inWater {} settled {} settledInWater {}", name,
                counts[0], counts[1], counts[2], counts[3]);
        check.accept(counts[1] > 0, name + ": some pieces must end up in the water (" + counts[1] + " of " + counts[0] + ")");
        int removed = context.computeOnClient(mc -> {
            int gone = 0;
            for (ShardManager.Batch batch : ShardManager.batches()) {
                int before = batch.shards().size();
                batch.shards().removeIf(shard -> !inWater(mc, shard.pos().x, shard.pos().y, shard.pos().z));
                gone += before - batch.shards().size();
            }
            return gone;
        });
        context.waitTicks(2);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} pieces outside the water removed before measuring", name, removed);

        int bottom = persistent(context, PAIRS);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: on the bottom under water, seen from above: {} pixels differ from the "
                + "frame without the pieces", name, bottom);
        check.accept(bottom > SEEN, name + ": pieces on the bottom must show through the water (" + bottom + " px)");

        if (full) {
            blockOnPiece(context, singleplayer, name, tracked[0]);
        }

        lid(singleplayer, feet, true);
        context.waitTicks(4);
        int lidded = persistent(context, PAIRS);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: under a stone lid: {} pixels differ from the frame without the pieces",
                name, lidded);
        check.accept(lidded == 0, name + ": a solid block between the camera and the pieces must hide them (" + lidded + " px)");
        lid(singleplayer, feet, false);
        context.waitTicks(4);

        if (full) {
            double[] eye = context.computeOnClient(mc -> underwaterEye(feet));
            SettleScenario.command(singleplayer, "tp @p %.3f %.3f %.3f %.3f %.3f", eye[0], eye[1], eye[2], eye[3], eye[4]);
            context.waitTicks(6);
            boolean under = context.computeOnClient(mc -> inWater(mc, Mc26.camera().position().x,
                    Mc26.camera().position().y, Mc26.camera().position().z));
            int inside = persistent(context, PAIRS);
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: camera under water {}: {} pixels differ from the frame without "
                    + "the pieces", name, under, inside);
            check.accept(under, name + ": the camera must be under water for the view from inside");
            check.accept(inside > SEEN, name + ": pieces must show from inside the water (" + inside + " px)");
        }
        context.runOnClient(mc -> ShardManager.clear());
        if (full) {
            landingBlock(context, singleplayer, name, feet);
        }
    }

    private static void landingBlock(ClientGameTestContext context, TestSingleplayerContext singleplayer, String name,
                                     BlockPos feet) {
        int x = feet.getX();
        int y = feet.getY();
        int z = feet.getZ();
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", x - 3, y + 1, z + 1, x + 3, y + DEPTH,
                z + 3);
        SettleScenario.tp(singleplayer, x, y + DEPTH + 1, z, 0, 55);
        context.waitTicks(10);
        context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
        context.setScreen(() -> new SettleScenario.SolidScreen(SettleScenario.MAGENTA));
        context.waitTicks(5);
        context.runOnClient(mc -> Mc26.setScreen(null));
        SettleScenario.waitStill(context, 200);
        int[] where = context.computeOnClient(mc -> {
            int onBlock = 0;
            int wet = 0;
            for (ShardManager.Batch batch : ShardManager.batches()) {
                for (ShardInstance shard : batch.shards()) {
                    if (inWater(mc, shard.pos().x, shard.pos().y, shard.pos().z)) {
                        wet++;
                    } else if (shard.pos().y > y + DEPTH + 0.9 && Math.abs(shard.pos().x - (x + 0.5)) < 3.6
                            && shard.pos().z > z + 0.9 && shard.pos().z < z + 4.1) {
                        onBlock++;
                    }
                }
            }
            return new int[]{onBlock, wet};
        });
        int shown = persistent(context, PAIRS);
        int magenta = magenta(context, name + "_landing_block");
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: block under the landing spot up to the water surface: {} pieces lie on "
                + "it above the water, {} in the water; {} pixels differ from the frame without the pieces, {} magenta",
                name, where[0], where[1], shown, magenta);
        context.runOnClient(mc -> ShardManager.clear());
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:water", x - 3, y + 1, z + 1, x + 3, y + DEPTH,
                z + 3);
        context.waitTicks(4);
    }

    private static double[] underwaterEye(BlockPos feet) {
        double cx = 0;
        double cz = 0;
        int n = 0;
        for (ShardManager.Batch batch : ShardManager.batches()) {
            for (ShardInstance shard : batch.shards()) {
                cx += shard.pos().x;
                cz += shard.pos().z;
                n++;
            }
        }
        if (n == 0) {
            return new double[]{feet.getX() + 2.5, feet.getY() + 1.0, feet.getZ() + 2.5, 0, 40};
        }
        cx /= n;
        cz /= n;
        double feetY = feet.getY() + 1.0;
        double bottom = feet.getY() + 1.0;
        for (int k = 0; k < 16; k++) {
            double angle = Math.PI * 2 * k / 16;
            double x = cx + Math.sin(angle) * 2.6;
            double z = cz - Math.cos(angle) * 2.6;
            boolean inPool = Math.abs(x - (feet.getX() + 0.5)) < HALF + 0.2 && Math.abs(z - (feet.getZ() + 0.5)) < HALF + 0.2;
            boolean pillar = Math.abs(x - (feet.getX() + 0.5)) < 0.9 && Math.abs(z - (feet.getZ() + 0.5)) < 0.9;
            if (inPool && !pillar) {
                double dx = cx - x;
                double dz = cz - z;
                double yaw = Math.toDegrees(Math.atan2(-dx, dz));
                double eyeY = feetY + 1.62;
                double pitch = Math.toDegrees(Math.atan2(eyeY - bottom, Math.hypot(dx, dz)));
                return new double[]{x, feetY, z, yaw, pitch};
            }
        }
        return new double[]{cx, feetY, cz - 2.6, 0, 30};
    }

    private static void blockOnPiece(ClientGameTestContext context, TestSingleplayerContext singleplayer, String name,
                                     ShardInstance piece) {
        if (piece == null) {
            return;
        }
        BlockPos cell = context.computeOnClient(mc -> BlockPos.containing(piece.pos().x, piece.pos().y, piece.pos().z));
        String before = context.computeOnClient(mc -> describe(mc, piece)) + ", magenta pixels " + magenta(context, name
                + "_block_before");
        SettleScenario.command(singleplayer, "setblock %d %d %d minecraft:stone", cell.getX(), cell.getY(), cell.getZ());
        context.waitTicks(3);
        String after = context.computeOnClient(mc -> describe(mc, piece)) + ", magenta pixels " + magenta(context, name
                + "_block_after");
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: block placed on the tracked piece at {}, before: {}", name,
                cell.toShortString(), before);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: block placed on the tracked piece, after: {}", name, after);
        SettleScenario.command(singleplayer, "setblock %d %d %d minecraft:water", cell.getX(), cell.getY(), cell.getZ());
        context.waitTicks(3);
    }

    private static int magenta(ClientGameTestContext context, String frame) {
        context.runOnClient(mc -> FullFrame.grab(mc, frame, true,
                image -> FullFrame.blob(image, FullFrame::magenta, VIEW).count()));
        Object count = FullFrame.analysis(context, frame);
        return count instanceof Integer i ? i : -1;
    }

    private static int persistent(ClientGameTestContext context, int n) {
        List<FullFrame.Image[]> pairs = ModelMaskScenario.pairs(context, n);
        FullFrame.Image first = null;
        for (FullFrame.Image[] pair : pairs) {
            if (pair[0] != null && pair[1] != null) {
                first = pair[0];
                break;
            }
        }
        if (first == null) {
            return -1;
        }
        int w = first.width();
        int h = first.height();
        int x0 = (int) (VIEW[0] * w);
        int y0 = (int) (VIEW[1] * h);
        int x1 = (int) (VIEW[2] * w);
        int y1 = (int) (VIEW[3] * h);
        int[] hits = new int[w * h];
        int used = 0;
        for (FullFrame.Image[] pair : pairs) {
            if (pair[0] == null || pair[1] == null) {
                continue;
            }
            used++;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int a = pair[0].at(x, y);
                    int b = pair[1].at(x, y);
                    int d = Math.max(Math.abs(FullFrame.r(a) - FullFrame.r(b)), Math.max(Math.abs(FullFrame.g(a)
                            - FullFrame.g(b)), Math.abs(FullFrame.b(a) - FullFrame.b(b))));
                    if (d > THRESHOLD) {
                        hits[y * w + x]++;
                    }
                }
            }
        }
        int need = Math.max(1, (used * 3 + 3) / 4);
        int count = 0;
        for (int hit : hits) {
            if (hit >= need) {
                count++;
            }
        }
        return count;
    }

    private static int[] counts(Minecraft mc) {
        int alive = 0;
        int water = 0;
        int settled = 0;
        int settledWater = 0;
        for (ShardManager.Batch batch : ShardManager.batches()) {
            for (ShardInstance shard : batch.shards()) {
                alive++;
                boolean wet = inWater(mc, shard.pos().x, shard.pos().y, shard.pos().z);
                water += wet ? 1 : 0;
                settled += shard.settled() ? 1 : 0;
                settledWater += wet && shard.settled() ? 1 : 0;
            }
        }
        return new int[]{alive, water, settled, settledWater};
    }

    private static String describe(Minecraft mc, ShardInstance shard) {
        boolean wet = inWater(mc, shard.pos().x, shard.pos().y, shard.pos().z);
        boolean inside = BlockCollision.collides(mc.level, shard.pos().x, shard.pos().y, shard.pos().z);
        String pass = shard.settled() || shard.hidden() || shard.submerged() ? "world depth" : "over the world";
        return String.format(Locale.ROOT, "pos %.2f %.2f %.2f speed %.3f settled %s hidden %s inBlock %s inWater %s drawn %s "
                        + "age %d of %d", shard.pos().x, shard.pos().y, shard.pos().z, shard.vel().length(), shard.settled(),
                shard.hidden(), inside, wet, pass, shard.age(), shard.lifetime());
    }

    private static Object[] sample(Minecraft mc, int tick, ShardInstance[] tracked, Map<ShardInstance, int[]> seen) {
        int alive = 0;
        int water = 0;
        int settled = 0;
        int depth = 0;
        Map<ShardInstance, int[]> now = new IdentityHashMap<>();
        for (ShardManager.Batch batch : ShardManager.batches()) {
            for (ShardInstance shard : batch.shards()) {
                alive++;
                boolean wet = inWater(mc, shard.pos().x, shard.pos().y, shard.pos().z);
                water += wet ? 1 : 0;
                settled += shard.settled() ? 1 : 0;
                depth += shard.settled() || shard.hidden() || shard.submerged() ? 1 : 0;
                if (tracked[0] == null && wet) {
                    tracked[0] = shard;
                }
                now.put(shard, new int[]{shard.age(), shard.lifetime()});
            }
        }
        int early = 0;
        for (Map.Entry<ShardInstance, int[]> before : seen.entrySet()) {
            if (!now.containsKey(before.getKey()) && before.getValue()[0] + 1 < before.getValue()[1]) {
                early++;
            }
        }
        seen.clear();
        seen.putAll(now);
        String piece = tracked[0] == null ? "no piece in the water yet"
                : now.containsKey(tracked[0]) ? describe(mc, tracked[0]) : "tracked piece gone";
        String row = String.format(Locale.ROOT, "t%-3d alive %3d inWater %3d settled %3d drawnWithDepth %3d overWorld %3d | "
                + "tracked: %s", tick, alive, water, settled, depth, alive - depth, piece);
        return new Object[]{row, early};
    }
}
