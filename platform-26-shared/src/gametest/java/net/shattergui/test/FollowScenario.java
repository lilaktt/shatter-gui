package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.FrameAudit;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ImageRect;
import net.shattergui.shatter.ShardManager;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Openings while the player moves ({@code -PtestOnly=follow}): a chest and a furnace opened by the use key and the
 * inventory, each with the fly-out from the block (not the inventory: it has no block) and gathering ({@code glass},
 * {@code pop}), while the player sprints forward and while the camera turns. In every frame of the opening the centre of
 * its plane (as drawn: {@code FrameAudit#openPlane}) must be in view and 0.5–2 blocks from the camera, and in its last
 * frame it must be where the real screen is (the flat GUI: its centre on the plane 1 block in front of the camera;
 * Spatial GUI: its plane). Before the fix the gathering stayed where the screen was opened and fell behind.
 */
final class FollowScenario {
    /** Sprinting speed on the ground, blocks per tick. */
    private static final double SPRINT = 0.28;
    /** Turning speed for the turning runs, degrees per tick. */
    private static final float TURN = 4f;
    private static final double NEAR = 0.5;
    private static final double FAR = 2.0;
    /** The last frame of a gathering is the real screen's place exactly; a flight is a step short of it. */
    private static final double END_GATHER = 0.03;
    private static final double END_FLY = 0.25;
    /** Projection margin (the frame edge itself counts as in view). */
    private static final double VIEW_MARGIN = 1.02;

    private FollowScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String mode) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String openEffect = config.openEffect;
        float openSpeed = config.get(Params.OPEN_SPEED);
        context.runOnClient(mc -> FrameAudit.enable(true));
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        // Room to run: 14 blocks of air ahead (south), the block two up and one ahead (above the head while running).
        BlockPos block = feet.offset(0, 2, 1);
        command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", feet.getX() - 2, feet.getY(), feet.getZ() - 1,
                feet.getX() + 2, feet.getY() + 4, feet.getZ() + 15);
        StringBuilder table = new StringBuilder();
        try {
            // Default open speed: the openings take their usual 4–10 ticks.
            config.effect = "pop";
            for (String target : List.of("chest", "furnace", "inventory")) {
                for (String open : List.of(ShatterConfig.OPEN_FLY_FROM_BLOCK, ShatterConfig.OPEN_DATA_PREFIX + "glass",
                        ShatterConfig.OPEN_DATA_PREFIX + "pop")) {
                    if (target.equals("inventory") && open.equals(ShatterConfig.OPEN_FLY_FROM_BLOCK)) {
                        continue;
                    }
                    for (String motion : List.of("sprint", "turn")) {
                        config.openEffect = open;
                        one(context, singleplayer, check, mode, target, open, motion, feet, block, table);
                    }
                }
            }
            ShatterGuiClient.LOGGER.info("[ShatterTest] follow {} (per case: frames, nearest/farthest, worst off view, "
                    + "end vs real screen):\n{}", mode, table);
        } finally {
            config.effect = effect;
            config.openEffect = openEffect;
            config.set(Params.OPEN_SPEED, openSpeed);
            command(singleplayer, "setblock %d %d %d minecraft:air", block.getX(), block.getY(), block.getZ());
            command(singleplayer, "tp @p %d.5 %d %d.5 0 0", feet.getX(), feet.getY(), feet.getZ());
            context.runOnClient(mc -> FrameAudit.enable(false));
            context.waitTicks(5);
        }
    }

    private static void one(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                            BiConsumer<Boolean, String> check, String mode, String target, String open, String motion,
                            BlockPos feet, BlockPos block, StringBuilder table) {
        String name = "follow_" + mode + "_" + target + "_" + open.replace("open:", "") + "_" + motion;
        boolean useKey = !target.equals("inventory");
        command(singleplayer, "tp @p %d.5 %d %d.5 0 0", feet.getX(), feet.getY(), feet.getZ());
        if (useKey) {
            command(singleplayer, "setblock %d %d %d minecraft:" + target + "[facing=north]", block.getX(), block.getY(),
                    block.getZ());
            FlyScenario.lookAt(context, singleplayer, feet, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        } else {
            command(singleplayer, "setblock %d %d %d minecraft:air", block.getX(), block.getY(), block.getZ());
            FlyScenario.lookAt(context, singleplayer, feet, feet.getX() + 0.5, feet.getY() + 1.6, feet.getZ() + 4.5);
        }
        context.waitTicks(3);
        long from = context.computeOnClient(mc -> FrameAudit.next());
        // The motion starts with the click and goes on through the whole opening (vanilla releases the movement keys
        // when a screen opens, so the speed is set every tick, as momentum or a server would move the player).
        move(context, motion);
        if (useKey) {
            context.getInput().pressKey(options -> options.keyUse);
        } else {
            context.runOnClient(mc -> Mc26.setScreen(new InventoryScreen(mc.player)));
        }
        List<String> shots = new ArrayList<>();
        // The use key goes through the integrated server, which may lag far behind (shader packs on a slow machine):
        // first wait for the screen, moving all the while.
        for (int wait = 0; wait < 200 && context.computeOnClient(mc -> Mc26.screen() == null); wait++) {
            move(context, motion);
            context.waitTick();
        }
        int ticks = 0;
        boolean seenOpening = false;
        // Until the opening is over (and at least until it started), at most 30 ticks.
        while (ticks < 30) {
            move(context, motion);
            if (ticks < 12) {
                FrameGrab.request(context, name + "_" + ticks, true);
                shots.add(name + "_" + ticks);
            }
            context.waitTick();
            ticks++;
            boolean running = context.computeOnClient(mc -> Mc26.screen() != null
                    && (FlyOpen.isAnimating(Mc26.screen()) || OpenAssemble.hides(Mc26.screen())));
            seenOpening |= running;
            if (seenOpening && !running) {
                break;
            }
        }
        // One more frame with the real screen, to compare the last frame of the opening with.
        context.waitTick();
        Expected expected = context.computeOnClient(FollowScenario::expected);
        List<FrameAudit.Frame> frames = context.computeOnClient(mc -> FrameAudit.since(from));
        for (String shot : shots) {
            FrameGrab.get(context, shot);
        }
        context.runOnClient(mc -> Mc26.setScreen(null));
        waitUntilClear(context);

        List<float[]> planes = new ArrayList<>();
        // Spatial GUI's plane in the same frames: where the real screen is (it may move with its camera).
        List<float[]> screens = new ArrayList<>();
        for (FrameAudit.Frame f : frames) {
            if (f.plane() != null) {
                planes.add(f.plane());
                screens.add(f.spatialPlane());
            }
        }
        check.accept(seenOpening, name + ": the opening must run");
        check.accept(planes.size() >= 2, name + ": the opening's plane must be drawn in its frames (got " + planes.size() + ")");
        if (planes.isEmpty()) {
            return;
        }
        double near = Double.MAX_VALUE;
        double far = 0;
        double worstView = 0;
        int k = 0;
        String bounds = "";
        for (float[] p : planes) {
            double distance = length(p);
            near = Math.min(near, distance);
            far = Math.max(far, distance);
            double view = view(p, expected);
            worstView = Math.max(worstView, view);
            // The real screen on Spatial GUI's plane is where Spatial GUI puts it: in its third person about 2.6 blocks
            // from its camera and level (below the view when the camera looks up). There the bounds also take in
            // that plane in the same frame: the opening must be no further off than the screen it becomes.
            float[] screen = screens.get(k);
            double viewLimit = VIEW_MARGIN;
            double nearLimit = NEAR;
            double farLimit = FAR;
            if (screen != null) {
                viewLimit = Math.max(viewLimit, view(screen, expected) + 0.05);
                nearLimit = Math.min(nearLimit, length(screen) - 0.1);
                farLimit = Math.max(farLimit, length(screen) + 0.1);
                if (viewLimit > VIEW_MARGIN || farLimit > FAR) {
                    bounds = String.format(Locale.ROOT, "  (Spatial GUI's plane: %.2f blocks, view %.2f)", length(screen),
                            view(screen, expected));
                }
            }
            check.accept(view <= viewLimit, String.format(Locale.ROOT,
                    "%s: frame %d of the opening: its centre (%.2f, %.2f, %.2f) projects outside the view (%.2f, allowed %.2f)",
                    name, k, p[0], p[1], p[2], view, viewLimit));
            check.accept(distance >= nearLimit && distance <= farLimit, String.format(Locale.ROOT,
                    "%s: frame %d of the opening: its centre is %.2f blocks from the camera (%.2f–%.2f)", name, k, distance,
                    nearLimit, farLimit));
            k++;
        }
        float[] last = planes.getLast();
        boolean fly = open.equals(ShatterConfig.OPEN_FLY_FROM_BLOCK);
        double[] real = fly ? expected.panel : expected.image;
        float[] lastScreen = screens.getLast();
        if (lastScreen != null) {
            // Spatial GUI: its plane in the very frame of the opening's last frame.
            real = new double[]{lastScreen[0], lastScreen[1], lastScreen[2]};
        }
        double end = Math.sqrt(Math.pow(last[0] - real[0], 2) + Math.pow(last[1] - real[1], 2) + Math.pow(last[2] - real[2], 2));
        double allowed = fly ? END_FLY : END_GATHER;
        check.accept(end <= allowed, String.format(Locale.ROOT, "%s: the last frame of the opening is %.3f blocks from "
                + "where the real screen is (allowed %.2f): (%.2f, %.2f, %.2f) vs (%.2f, %.2f, %.2f)", name, end, allowed,
                last[0], last[1], last[2], real[0], real[1], real[2]));
        table.append(String.format(Locale.ROOT, "  %-48s %2d frames  %.2f–%.2f blocks  view %.2f  end %.3f%s%n", name,
                planes.size(), near, far, worstView, end, bounds));
    }

    /**
     * Where the real screen is now, in camera coordinates (right, up, forward): the panel's centre and the picture's
     * centre (the whole window), on the flat GUI's plane 1 block ahead or on Spatial GUI's plane.
     */
    private record Expected(double[] panel, double[] image, double tanHalf, double aspect) {
    }

    private static Expected expected(Minecraft mc) {
        Camera camera = Mc26.camera();
        double tanHalf = Math.tan(Math.toRadians(camera.getFov()) / 2);
        double aspect = (double) Mc26.mainTarget().width / Math.max(1, Mc26.mainTarget().height);
        Vector3d spatial = Mc26.screen() != null && SpatialGuiCompat.isShowing(Mc26.screen()) ? SpatialGuiCompat.planeCenterNow() : null;
        if (spatial != null) {
            double[] c = local(camera, spatial);
            // On the plane the panel is where the content is; the opening is checked against the plane's centre.
            return new Expected(c, c, tanHalf, aspect);
        }
        double[] image = {0, 0, 1};
        double[] panel = image;
        if (Mc26.screen() instanceof AbstractContainerScreen<?>) {
            ImageRect r = net.shattergui.capture.ScreenCapture.guiBounds(Mc26.screen());
            double cx = (r.x0() + r.x1()) / 2;
            double cy = (r.y0() + r.y1()) / 2;
            panel = new double[]{(cx * 2 - 1) * tanHalf * aspect, (1 - cy * 2) * tanHalf, 1};
        }
        return new Expected(panel, image, tanHalf, aspect);
    }

    private static double length(float[] p) {
        return Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
    }

    /** How far outside the view a point projects (≤ 1 is in view; behind the camera is far out). */
    private static double view(float[] p, Expected e) {
        return p[2] <= 0 ? 99 : Math.max(Math.abs(p[0]) / (p[2] * e.tanHalf * e.aspect), Math.abs(p[1]) / (p[2] * e.tanHalf));
    }

    /** A camera-relative point in world axes → right, up, forward. */
    private static double[] local(Camera camera, Vector3d v) {
        org.joml.Vector3fc left = camera.leftVector();
        org.joml.Vector3fc up = camera.upVector();
        org.joml.Vector3fc forward = camera.forwardVector();
        return new double[]{-(v.x * left.x() + v.y * left.y() + v.z * left.z()), v.x * up.x() + v.y * up.y() + v.z * up.z(),
                v.x * forward.x() + v.y * forward.y() + v.z * forward.z()};
    }

    private static void move(ClientGameTestContext context, String motion) {
        context.runOnClient(mc -> {
            if (mc.player == null) {
                return;
            }
            if (motion.equals("sprint")) {
                mc.player.setSprinting(true);
                double yaw = Math.toRadians(mc.player.getYRot());
                mc.player.setDeltaMovement(-Math.sin(yaw) * SPRINT, mc.player.getDeltaMovement().y, Math.cos(yaw) * SPRINT);
            } else {
                mc.player.setYRot(mc.player.getYRot() + TURN);
            }
        });
    }

    private static void waitUntilClear(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setSprinting(false);
                mc.player.setDeltaMovement(0, 0, 0);
            }
        });
        for (int i = 0; i < 300 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
        context.waitTicks(3);
    }

    private static void command(TestSingleplayerContext singleplayer, String format, Object... args) {
        singleplayer.getServer().runCommand(String.format(Locale.ROOT, format, args));
    }
}
