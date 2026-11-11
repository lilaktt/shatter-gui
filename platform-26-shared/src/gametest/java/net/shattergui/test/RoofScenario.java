package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

final class RoofScenario {
    static final int TOLERANCE_TICKS = 2;
    private static final int[] SHOTS = {1, 2, 3, 4, 6, 8, 10, 15, 20, 30, 40, 55, 70};
    private static final int WATCH_TICKS = 90;
    private static final int LONG_LIFETIME = 400;
    private static final int BACK_OUT_AT = 60;
    private static final double[] VIEW = {0, 0, 1, 0.86};

    private record Tick(int tick, boolean cameraInside, int alive, int moving, int settled, int insideBlock, int occluded,
                        int drawnWithDepth, int minAge, int maxAge, int minLife, int maxLife, int vanishedEarly, int diedOfAge) {
        String describe() {
            return String.format(Locale.ROOT, "t%-3d camera %-7s alive %4d moving %4d settled %4d inBlock %3d behindBlocks %4d "
                            + "drawnWithDepth %4d overBlocks %4d age %d..%d life %d..%d diedOfAge %d vanishedEarly %d", tick,
                    cameraInside ? "inside" : "outside", alive, moving, settled, insideBlock, occluded, drawnWithDepth,
                    alive - drawnWithDepth, minAge, maxAge, minLife, maxLife, diedOfAge, vanishedEarly);
        }
    }

    private RoofScenario() {
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
        try {
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.effect = "glass";
            config.alwaysOnTop = true;
            clear(singleplayer, feet);
            shelter(singleplayer, feet);
            one(context, singleplayer, check, "roof_" + mode + "_default", feet, lifetime, false);
            config.set(Params.GLASS_LIFETIME, LONG_LIFETIME);
            one(context, singleplayer, check, "roof_" + mode + "_long", feet, LONG_LIFETIME, true);
        } finally {
            config.effect = effect;
            config.openEffect = open;
            config.alwaysOnTop = onTop;
            config.set(Params.GLASS_LIFETIME, lifetime);
            context.runOnClient(mc -> ShardManager.clear());
            clear(singleplayer, feet);
            SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 0);
            SettleScenario.command(singleplayer, "gamerule random_tick_speed 3");
            SettleScenario.command(singleplayer, "gamerule advance_time true");
            context.waitTicks(5);
        }
    }

    private static void clear(TestSingleplayerContext singleplayer, BlockPos feet) {
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", feet.getX() - 8, feet.getY(),
                feet.getZ() - 10, feet.getX() + 8, feet.getY() + 6, feet.getZ() + 10);
    }

    private static void shelter(TestSingleplayerContext singleplayer, BlockPos feet) {
        SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone hollow", feet.getX() - 3, feet.getY() - 1,
                feet.getZ() - 8, feet.getX() + 3, feet.getY() + 3, feet.getZ() - 2);
    }

    private static boolean inside(Vec3 camera, BlockPos feet) {
        return camera.z < feet.getZ() - 2 && camera.z > feet.getZ() - 8 && Math.abs(camera.x - (feet.getX() + 0.5)) < 3;
    }

    private static void one(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                            BiConsumer<Boolean, String> check, String name, BlockPos feet, int lifetime, boolean backOut) {
        context.runOnClient(mc -> ShardManager.clear());
        SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 30);
        context.waitTicks(10);
        boolean pack = context.computeOnClient(mc -> net.shattergui.compat.iris.IrisCompat.shaderPackInUse());
        context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
        context.setScreen(() -> new SettleScenario.SolidScreen(SettleScenario.MAGENTA));
        context.waitTicks(5);
        context.runOnClient(mc -> Mc26.setScreen(null));
        SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ() - 5, 0, 20);

        Map<ShardInstance, int[]> seen = new IdentityHashMap<>();
        List<Tick> ticks = new ArrayList<>();
        Map<Integer, String> shots = new LinkedHashMap<>();
        int entered = -1;
        int shot = 0;
        for (int tick = 1; tick <= WATCH_TICKS; tick++) {
            context.waitTick();
            if (backOut && tick == BACK_OUT_AT) {
                SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 30);
            }
            final int t = tick;
            Tick row = context.computeOnClient(mc -> sample(mc, feet, t, seen));
            ticks.add(row);
            if (entered < 0 && row.cameraInside()) {
                entered = tick;
            }
            if (shot < SHOTS.length && SHOTS[shot] == tick) {
                String frame = name + "_t" + tick;
                shots.put(tick, frame);
                context.runOnClient(mc -> FullFrame.grab(mc, frame, true,
                        image -> FullFrame.blob(image, FullFrame::magenta, VIEW).count()));
                shot++;
            }
            if (backOut && tick == BACK_OUT_AT + 10) {
                String frame = name + "_back_out";
                shots.put(-tick, frame);
                context.runOnClient(mc -> FullFrame.grab(mc, frame, true,
                        image -> FullFrame.blob(image, FullFrame::magenta, VIEW).count()));
            }
            if (!backOut && row.alive() == 0 && tick > 10) {
                break;
            }
        }
        Map<Integer, Integer> magenta = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> frame : shots.entrySet()) {
            Object count = FullFrame.analysis(context, frame.getValue());
            magenta.put(frame.getKey(), count instanceof Integer i ? i : -1);
        }

        for (Tick row : ticks) {
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} ({})", name, row.describe(),
                    pack ? "shader pack: over blocks in the pass before the hand" : "no shader pack: over blocks after the hand");
        }
        StringBuilder frames = new StringBuilder();
        for (Map.Entry<Integer, Integer> m : magenta.entrySet()) {
            frames.append(m.getKey() < 0 ? " back-out" : " t" + m.getKey()).append('=').append(m.getValue());
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: camera inside the shelter from tick {}; magenta pixels:{}; lifetime "
                + "setting {}", name, entered, frames, lifetime);

        int early = ticks.stream().mapToInt(Tick::vanishedEarly).sum();
        check.accept(early == 0, name + ": " + early + " pieces vanished before the end of their life");
        check.accept(entered > 0, name + ": the camera must get into the shelter");
        List<String> seenThrough = new ArrayList<>();
        for (Map.Entry<Integer, Integer> m : magenta.entrySet()) {
            int tick = m.getKey();
            if (tick < 0 || entered < 0 || tick < entered + TOLERANCE_TICKS || backOut && tick >= BACK_OUT_AT) {
                continue;
            }
            Tick row = ticks.get(tick - 1);
            if (m.getValue() > 0 && row.alive() > 0) {
                seenThrough.add("t" + tick + "=" + m.getValue() + " px (" + row.occluded() + " of " + row.alive() + " behind blocks)");
            }
        }
        check.accept(seenThrough.isEmpty(), name + ": pieces behind the shelter's walls must not show through them "
                + TOLERANCE_TICKS + " ticks after the camera got in: " + seenThrough);
        if (backOut) {
            Tick back = ticks.get(Math.min(ticks.size(), BACK_OUT_AT + 10) - 1);
            Integer out = magenta.get(-(BACK_OUT_AT + 10));
            check.accept(back.alive() > 0 && out != null && out > 0, name + ": back outside before the end of their life the "
                    + "pieces must be there and show (" + back.alive() + " alive, " + out + " magenta pixels)");
        }
    }

    private static Tick sample(Minecraft mc, BlockPos feet, int tick, Map<ShardInstance, int[]> seen) {
        Vec3 camera = Mc26.camera().position();
        int alive = 0;
        int moving = 0;
        int settled = 0;
        int insideBlock = 0;
        int occluded = 0;
        int depth = 0;
        int minAge = Integer.MAX_VALUE;
        int maxAge = 0;
        int minLife = Integer.MAX_VALUE;
        int maxLife = 0;
        Map<ShardInstance, int[]> now = new IdentityHashMap<>();
        for (ShardManager.Batch batch : ShardManager.batches()) {
            for (ShardInstance shard : batch.shards()) {
                alive++;
                if (shard.settled()) {
                    settled++;
                } else {
                    moving++;
                }
                if (shard.settled() || shard.hidden()) {
                    depth++;
                }
                Vec3 at = new Vec3(shard.pos().x, shard.pos().y, shard.pos().z);
                if (net.shattergui.shatter.BlockCollision.collides(mc.level, at.x, at.y, at.z)) {
                    insideBlock++;
                }
                HitResult hit = mc.level.clip(new ClipContext(camera, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        mc.player));
                if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(camera) < at.distanceToSqr(camera) - 0.01) {
                    occluded++;
                }
                minAge = Math.min(minAge, shard.age());
                maxAge = Math.max(maxAge, shard.age());
                minLife = Math.min(minLife, shard.lifetime());
                maxLife = Math.max(maxLife, shard.lifetime());
                now.put(shard, new int[]{shard.age(), shard.lifetime()});
            }
        }
        int early = 0;
        int ofAge = 0;
        for (Map.Entry<ShardInstance, int[]> before : seen.entrySet()) {
            if (!now.containsKey(before.getKey())) {
                if (before.getValue()[0] + 1 >= before.getValue()[1]) {
                    ofAge++;
                } else {
                    early++;
                    ShatterGuiClient.LOGGER.warn("[ShatterTest] roof: a piece vanished at age {} of {}", before.getValue()[0],
                            before.getValue()[1]);
                }
            }
        }
        seen.clear();
        seen.putAll(now);
        return new Tick(tick, inside(camera, feet), alive, moving, settled, insideBlock, occluded, depth,
                alive == 0 ? 0 : minAge, maxAge, alive == 0 ? 0 : minLife, maxLife, early, ofAge);
    }
}
