package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Settled pieces and view bobbing ({@code -PtestOnly=bob}; also with Spatial GUI and with a shader pack). One magenta
 * piece lies on the ground, a red block next to it; the player looks at them from a platform. With view bobbing on, the
 * walk (and sprint) bobbing is driven on the spot (no camera motion, no parallax), then the player is hurt.
 *
 * <p>Check: the piece must move on the screen exactly as the world does. For each frame the expected motion comes from
 * vanilla's own formulas ({@code GameRenderer#bobHurt}/{@code bobView} on that frame's camera state, read when the frame
 * is requested): the piece's and the block's centroids of the first frame are put back into the world on their lines of
 * sight and projected with each frame's matrices. The measured offset piece − block minus the expected one must stay
 * within ±0.5 pixel of the first frame's. Screenshots {@code bob_<mode>_*}.
 */
final class BobScenario {
    private static final int LIFETIME = 1100;
    /** Allowed drift of the piece against the world (pixels). */
    private static final double TOLERANCE = 0.5;
    /**
     * How much worse than vanilla's own drawing of the same piece the mod's may measure (pixels). The measurement itself
     * has a floor: with the camera moving by fractions of a pixel the rasterized shapes change a little, so even
     * vanilla's drawing of the piece "drifts" by about 0.6 px at the peaks of the bobbing.
     */
    private static final double MARGIN = 0.15;
    /** The whole frame: the HUD and the hand are hidden (a hurt hand turns red). */
    private static final double[] VIEW = {0, 0, 1, 1};
    /** The height of a carpet (blocks). */
    private static final double CARPET = 1 / 16.0;

    /** What a frame was drawn with: the full level matrix (projection × bobbing × camera rotation) and the camera. */
    private record Pose(Matrix4f matrix, Vec3 camera, int width, int height, float bob, float hurt) {
    }

    private static final Map<String, Pose> POSES = new ConcurrentHashMap<>();

    private BobScenario() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String mode) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        boolean onTop = config.alwaysOnTop;
        int lifetime = config.get(Params.GLASS_LIFETIME);
        int columns = config.get(Params.GLASS_COLUMNS);
        int rows = config.get(Params.GLASS_ROWS);
        boolean bobbing = context.computeOnClient(mc -> mc.options.bobView().get());
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        SettleScenario.command(singleplayer, "gamerule random_tick_speed 0");
        // The same light in every frame: the sun stands still at noon.
        SettleScenario.command(singleplayer, "gamerule advance_time false");
        SettleScenario.command(singleplayer, "time set 6000");
        BlockPos red = null;
        BlockPos blue = null;
        BlockPos platform = null;
        try {
            config.openEffect = ShatterConfig.OPEN_NONE;
            config.effect = "glass";
            config.alwaysOnTop = true;
            config.set(Params.GLASS_LIFETIME, LIFETIME);
            // Big pieces: one is easy to find and to measure.
            config.set(Params.GLASS_COLUMNS, 4);
            config.set(Params.GLASS_ROWS, 3);
            SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", feet.getX() - 8, feet.getY(),
                    feet.getZ() - 12, feet.getX() + 8, feet.getY() + 8, feet.getZ() + 8);
            context.runOnClient(mc -> ShardManager.clear());
            SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 40);
            context.waitTicks(10);
            SettleScenario.hold(context, false);
            context.runOnClient(mc -> ShardManager.seedForTests(OnTopScenario.TEST_SEED));
            context.setScreen(() -> new SettleScenario.SolidScreen(SettleScenario.MAGENTA));
            context.waitTicks(5);
            context.runOnClient(mc -> Mc26.setScreen(null));
            int still = SettleScenario.waitStill(context, 240);
            context.waitTicks(SettleScenario.SETTLE_TICKS + 12);
            // Keep one piece, lying as flat as possible near two blocks ahead (from above a flat piece shows the most
            // pixels); the rest go.
            Vector3d target = new Vector3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 2.5);
            Vector3d anchor = context.computeOnClient(mc -> {
                ShardInstance best = null;
                double bestScore = -Double.MAX_VALUE;
                for (ShardManager.Batch batch : ShardManager.batches()) {
                    for (ShardInstance shard : batch.shards()) {
                        if (shard.pos().y >= feet.getY() + 0.5) {
                            continue;
                        }
                        org.joml.Vector3f up = shard.rot().transform(new org.joml.Vector3f(shard.normal()));
                        double score = Math.abs(up.y) - 0.15 * Math.sqrt(shard.pos().distanceSquared(target));
                        if (score > bestScore) {
                            bestScore = score;
                            best = shard;
                        }
                    }
                }
                if (best == null) {
                    return null;
                }
                ShardInstance keep = best;
                for (ShardManager.Batch batch : ShardManager.batches()) {
                    batch.shards().removeIf(shard -> shard != keep);
                }
                return new Vector3d(keep.pos());
            });
            int settled = SettleScenario.settledCount(context);
            String name = "bob_" + mode;
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: at rest after {} ticks, piece kept at {}, settled {}", name, still,
                    anchor, settled);
            if (anchor == null) {
                check.accept(false, name + ": a piece must come to rest on the ground");
                return;
            }
            // A red carpet on each side of the piece: the piece is measured against one, the other block against it too
            // (world against world: how exact the measurement itself is). Red reads as red with a shader pack as well.
            red = BlockPos.containing(anchor.x + 2.2, feet.getY(), anchor.z + 0.5);
            blue = BlockPos.containing(anchor.x - 2.2, feet.getY(), anchor.z + 0.5);
            SettleScenario.command(singleplayer, "setblock %d %d %d minecraft:red_carpet", red.getX(), red.getY(), red.getZ());
            SettleScenario.command(singleplayer, "setblock %d %d %d minecraft:red_carpet", blue.getX(), blue.getY(),
                    blue.getZ());
            Vec3 pieceCentre = new Vec3(anchor.x, anchor.y, anchor.z);
            // The player on a stone pillar 7 blocks back and 4 up, looking at the piece (from where the eyes really are).
            platform = BlockPos.containing(anchor.x, feet.getY(), anchor.z - 7);
            SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:stone", platform.getX(), platform.getY(),
                    platform.getZ(), platform.getX(), platform.getY() + 3, platform.getZ());
            SettleScenario.tp(singleplayer, platform.getX(), platform.getY() + 4, platform.getZ(), 0, 30);
            context.waitTicks(10);
            Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
            double lookX = anchor.x - eye.x;
            double lookY = anchor.y - eye.y;
            double lookZ = anchor.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-lookX, lookZ));
            float pitch = (float) -Math.toDegrees(Math.atan2(lookY, Math.hypot(lookX, lookZ)));
            SettleScenario.tp(singleplayer, platform.getX(), platform.getY() + 4, platform.getZ(), yaw, pitch);
            context.runOnClient(mc -> {
                mc.options.bobView().set(true);
                McTest.setHudHidden(mc, true);
            });
            context.waitTicks(25);
            Vec3 standing = context.computeOnClient(mc -> mc.player.position());
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: player at {}, eyes at {} before turning, looking {} / {}", name,
                    standing, eye, yaw, pitch);

            // The same bobbing twice: first with the piece drawn by vanilla in the level (alwaysOnTop off: what the
            // pixels can show at best), then by the mod's pass for settled pieces.
            Drift[] results = new Drift[2];
            for (boolean on : new boolean[]{false, true}) {
                config.alwaysOnTop = on;
                context.waitTicks(10);
                String run = name + (on ? "_on" : "_vanilla");
                List<String> frames = new ArrayList<>();
                // At rest first (no bobbing): the frame every other one is compared with.
                drive(context, run + "_rest", 2, 0f, false, frames);
                // Walking: about 0.22 blocks a tick; sprinting: about 0.28 and the sprint's wider field of view.
                drive(context, run + "_walk", 18, 0.22f, false, frames);
                drive(context, run + "_sprint", 14, 0.28f, true, frames);
                context.runOnClient(mc -> mc.player.setSprinting(false));
                SettleScenario.command(singleplayer, "gamemode survival @p");
                context.waitTicks(2);
                SettleScenario.command(singleplayer, "damage @p 1");
                drive(context, run + "_hurt", 10, 0f, false, frames);
                SettleScenario.command(singleplayer, "gamemode creative @p");
                SettleScenario.command(singleplayer, "effect give @p minecraft:instant_health 1 5 true");
                // The bobbing settles and the view comes back to rest before the next run.
                context.waitTicks(30);
                results[on ? 1 : 0] = measure(context, check, run, frames, pieceCentre, red, blue);
            }
            Drift vanilla = results[0];
            Drift mod = results[1];
            double allowed = Math.max(TOLERANCE, vanilla.worst + MARGIN);
            ShatterGuiClient.LOGGER.info(String.format(java.util.Locale.ROOT, "[ShatterTest] %s: settled piece against the "
                    + "world: the mod's pass %.2f px at most (%.2f px RMS), vanilla drawing the same piece %.2f px (%.2f px "
                    + "RMS); allowed %.2f", name, mod.worst, mod.rms(), vanilla.worst, vanilla.rms(), allowed));
            check.accept(mod.worst <= allowed, String.format(java.util.Locale.ROOT, "%s: the settled piece must bob with the "
                    + "world (it drifted %.2f px against the carpets, vanilla drawing it %.2f px, allowed %.2f)", name,
                    mod.worst, vanilla.worst, allowed));
            check.accept(mod.rms() <= vanilla.rms() + MARGIN, String.format(java.util.Locale.ROOT, "%s: on average the "
                    + "settled piece must follow the world as closely as vanilla's drawing of it (%.2f px RMS, vanilla %.2f)",
                    name, mod.rms(), vanilla.rms()));
        } finally {
            config.effect = effect;
            config.openEffect = open;
            config.alwaysOnTop = onTop;
            config.set(Params.GLASS_LIFETIME, lifetime);
            config.set(Params.GLASS_COLUMNS, columns);
            config.set(Params.GLASS_ROWS, rows);
            context.runOnClient(mc -> {
                McTest.setHudHidden(mc, false);
                mc.options.bobView().set(bobbing);
                mc.player.setSprinting(false);
                ShardManager.clear();
            });
            if (red != null) {
                SettleScenario.command(singleplayer, "setblock %d %d %d minecraft:air", red.getX(), red.getY(), red.getZ());
            }
            if (blue != null) {
                SettleScenario.command(singleplayer, "setblock %d %d %d minecraft:air", blue.getX(), blue.getY(), blue.getZ());
            }
            if (platform != null) {
                SettleScenario.command(singleplayer, "fill %d %d %d %d %d %d minecraft:air", platform.getX(), platform.getY(),
                        platform.getZ(), platform.getX(), platform.getY() + 3, platform.getZ());
            }
            SettleScenario.command(singleplayer, "gamemode creative @p");
            SettleScenario.tp(singleplayer, feet.getX(), feet.getY(), feet.getZ(), 0, 0);
            SettleScenario.command(singleplayer, "gamerule random_tick_speed 3");
            SettleScenario.command(singleplayer, "gamerule advance_time true");
            context.waitTicks(5);
        }
    }

    /**
     * {@code ticks} frames: each tick the walk distance grows by {@code step} and the bob is pushed up as when walking
     * (both read by vanilla's bobbing, the player does not move), then the last drawn frame is read back with the
     * camera state it was drawn with.
     */
    private static void drive(ClientGameTestContext context, String name, int ticks, float step, boolean sprint,
                              List<String> frames) {
        for (int i = 0; i < ticks; i++) {
            context.waitTick();
            String frame = name + "_" + i;
            boolean save = i % 3 == 0;
            context.runOnClient(mc -> {
                if (step > 0f) {
                    mc.player.setSprinting(sprint);
                    mc.player.avatarState().addWalkDistance(step);
                    mc.player.avatarState().updateBob(0.1f);
                    mc.player.avatarState().updateBob(0.1f);
                }
                // At the end of the next frame: its picture and the camera state it was drawn with.
                net.shattergui.debug.FrameAudit.atFrameEnd(() -> {
                    POSES.put(frame, pose(mc));
                    FullFrame.grab(mc, frame, save, BobScenario::blobs);
                });
            });
            frames.add(frame);
        }
    }

    /** The matrices the last frame's level was drawn with, by vanilla's formulas (GameRenderer#renderLevel). */
    private static Pose pose(Minecraft mc) {
        GameRenderState state = McTest.renderState(mc);
        CameraRenderState camera = state.levelRenderState.cameraRenderState;
        Matrix4f projection = new Matrix4f(camera.projectionMatrix);
        Matrix4f bob = new Matrix4f();
        bobHurt(camera, state.optionsRenderState.damageTiltStrength, bob);
        if (state.optionsRenderState.bobView) {
            bobView(camera, bob);
        }
        projection.mul(bob);
        Matrix4f view = new Matrix4f().rotation(new Quaternionf(camera.orientation).conjugate());
        return new Pose(projection.mul(view), camera.pos, Mc26.mainTarget().width, Mc26.mainTarget().height,
                camera.entityRenderState.bob, camera.entityRenderState.hurtTime);
    }

    /**
     * {@code GameRenderer#bobHurt} (26.1–26.3, the same code; there on a {@code PoseStack}, whose {@code mulPose} and
     * {@code rotateDegrees} multiply on the right, as {@code Matrix4f#rotateZ} does).
     */
    private static void bobHurt(CameraRenderState camera, double tiltStrength, Matrix4f pose) {
        if (camera.entityRenderState.isLiving) {
            float hurt = camera.entityRenderState.hurtTime;
            if (camera.entityRenderState.isDeadOrDying) {
                float duration = Math.min(camera.entityRenderState.deathTime, 20.0F);
                pose.rotateZ((float) Math.toRadians(40.0F - 8000.0F / (duration + 200.0F)));
            }
            if (hurt < 0.0F) {
                return;
            }
            hurt /= camera.entityRenderState.hurtDuration;
            hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
            float dir = camera.entityRenderState.hurtDir;
            pose.rotateY((float) Math.toRadians(-dir));
            float tilt = (float) (-hurt * 14.0 * tiltStrength);
            pose.rotateZ((float) Math.toRadians(tilt));
            pose.rotateY((float) Math.toRadians(dir));
        }
    }

    /** {@code GameRenderer#bobView} (26.1–26.3, the same code). */
    private static void bobView(CameraRenderState camera, Matrix4f pose) {
        if (camera.entityRenderState.isPlayer) {
            float walk = camera.entityRenderState.backwardsInterpolatedWalkDistance;
            float bob = camera.entityRenderState.bob;
            pose.translate(Mth.sin(walk * (float) Math.PI) * bob * 0.5F, -Math.abs(Mth.cos(walk * (float) Math.PI) * bob),
                    0.0F);
            pose.rotateZ((float) Math.toRadians(Mth.sin(walk * (float) Math.PI) * bob * 3.0F));
            pose.rotateX((float) Math.toRadians(Math.abs(Mth.cos(walk * (float) Math.PI - 0.2F) * bob) * 5.0F));
        }
    }

    /** Screen position (pixels from the top left) of a world point, or null behind the camera. */
    private static double[] project(Pose pose, Vec3 world) {
        Vector4f clip = pose.matrix.transform(new Vector4f((float) (world.x - pose.camera.x),
                (float) (world.y - pose.camera.y), (float) (world.z - pose.camera.z), 1f));
        if (clip.w <= 1.0e-6f) {
            return null;
        }
        return new double[]{(clip.x / clip.w * 0.5 + 0.5) * pose.width, (0.5 - clip.y / clip.w * 0.5) * pose.height};
    }

    /** What a blob shows: the point where a line of sight through it meets it. */
    interface Surface {
        /** The first point of the line from {@code origin} along {@code dir} (unit) on it, or null. */
        Vec3 hit(Vec3 origin, Vec3 dir);
    }

    /** A block: the line enters its box. */
    static Surface box(BlockPos pos) {
        return (origin, dir) -> {
            double near = -Double.MAX_VALUE;
            double far = Double.MAX_VALUE;
            double[] o = {origin.x, origin.y, origin.z};
            double[] d = {dir.x, dir.y, dir.z};
            double[] lo = {pos.getX(), pos.getY(), pos.getZ()};
            for (int k = 0; k < 3; k++) {
                if (Math.abs(d[k]) < 1e-12) {
                    if (o[k] < lo[k] || o[k] > lo[k] + 1) {
                        return null;
                    }
                    continue;
                }
                double t0 = (lo[k] - o[k]) / d[k];
                double t1 = (lo[k] + 1 - o[k]) / d[k];
                near = Math.max(near, Math.min(t0, t1));
                far = Math.min(far, Math.max(t0, t1));
            }
            return near <= far && near > 0 ? origin.add(dir.scale(near)) : null;
        };
    }

    /** A flat piece lying at height {@code y}. */
    static Surface level(double y) {
        return (origin, dir) -> Math.abs(dir.y) < 1e-9 ? null : origin.add(dir.scale((y - origin.y) / dir.y));
    }

    /**
     * The world point a pixel shows on {@code surface}: the line of sight through the pixel (from the frame's matrices,
     * view bobbing included) met with it; if it misses (an edge pixel), the nearest point of the line to {@code fallback}.
     */
    private static Vec3 unproject(Pose pose, double sx, double sy, Surface surface, Vec3 fallback) {
        Matrix4f inverse = new Matrix4f(pose.matrix).invert();
        float nx = (float) (sx / pose.width * 2 - 1);
        float ny = (float) (1 - sy / pose.height * 2);
        Vector4f a = inverse.transform(new Vector4f(nx, ny, 0.5f, 1f));
        Vector4f b = inverse.transform(new Vector4f(nx, ny, 0.9f, 1f));
        Vec3 pa = pose.camera.add(a.x / a.w, a.y / a.w, a.z / a.w);
        Vec3 pb = pose.camera.add(b.x / b.w, b.y / b.w, b.z / b.w);
        Vec3 dir = pb.subtract(pa).normalize();
        // The line of sight goes away from the camera.
        if (dir.dot(pa.subtract(pose.camera)) < 0) {
            dir = dir.scale(-1);
        }
        Vec3 origin = pa.subtract(dir.scale(pa.distanceTo(pose.camera) + 1));
        Vec3 hit = surface.hit(origin, dir);
        if (hit != null) {
            return hit;
        }
        return origin.add(dir.scale(fallback.subtract(origin).dot(dir)));
    }

    /** How one blob moves against another, against what the frames' matrices expect (see the class comment). */
    private static final class Drift {
        Vec3 aWorld;
        Vec3 bWorld;
        double[] first;
        double worst;
        double squares;
        int frames;
        final StringBuilder log = new StringBuilder();

        double rms() {
            return frames == 0 ? 0 : Math.sqrt(squares / frames);
        }

        /** Adds a frame; false if it could not be measured. */
        boolean add(Pose pose, FullFrame.Blob a, FullFrame.Blob b, Surface aSurface, Surface bSurface, Vec3 aCentre,
                    Vec3 bCentre, String label) {
            if (aWorld == null) {
                aWorld = unproject(pose, a.x(), a.y(), aSurface, aCentre);
                bWorld = unproject(pose, b.x(), b.y(), bSurface, bCentre);
            }
            double[] pa = project(pose, aWorld);
            double[] pb = project(pose, bWorld);
            if (pa == null || pb == null) {
                return false;
            }
            double rx = (a.x() - b.x()) - (pa[0] - pb[0]);
            double ry = (a.y() - b.y()) - (pa[1] - pb[1]);
            if (first == null) {
                first = new double[]{rx, ry};
            }
            double drift = Math.hypot(rx - first[0], ry - first[1]);
            worst = Math.max(worst, drift);
            squares += drift * drift;
            frames++;
            log.append(String.format(Locale.ROOT, " %s:%.2f", label, drift));
return true;
        }
    }

    /** The piece and the carpets left and right of it in a frame (+x is on the left when looking south). */
    private static FullFrame.Blob[] blobs(FullFrame.Image image) {
        FullFrame.Blob magenta = FullFrame.weighted(image, FullFrame::magentaWeight, VIEW);
        double split = Double.isNaN(magenta.x()) ? 0.5 : magenta.x() / image.width();
        return new FullFrame.Blob[]{magenta, FullFrame.weighted(image, FullFrame::redWeight, new double[]{0, 0, split, 1}),
                FullFrame.weighted(image, FullFrame::redWeight, new double[]{split, 0, 1, 1})};
    }

    /** Measures a run's frames; the piece's drift against the left carpet. */
    private static Drift measure(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name,
                                List<String> frames, Vec3 piece, BlockPos blockPos, BlockPos controlPos) {
        // Carpets: flat like the piece (a block's faces show in other proportions as the bobbing moves the eye, and its
        // centroid drifts by itself).
        Vec3 block = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + CARPET, blockPos.getZ() + 0.5);
        Vec3 control = new Vec3(controlPos.getX() + 0.5, controlPos.getY() + CARPET, controlPos.getZ() + 0.5);
        Surface pieceSurface = level(piece.y);
        Surface blockSurface = level(block.y);
        Surface controlSurface = level(control.y);
        Drift main = new Drift();
        Drift reference = new Drift();
        double blockTravel = 0;
        double[] blockStart = null;
        int measured = 0;
        StringBuilder bobs = new StringBuilder();
        for (String frame : frames) {
            FullFrame.Blob[] blobs = (FullFrame.Blob[]) FullFrame.analysis(context, frame);
            Pose pose = POSES.remove(frame);
            if (blobs == null || pose == null) {
                check.accept(false, name + ": frame " + frame + " must arrive");
                continue;
            }
            FullFrame.Blob magentaBlob = blobs[0];
            FullFrame.Blob redBlob = blobs[1];
            FullFrame.Blob blueBlob = blobs[2];
            if (redBlob.count() < 50 || magentaBlob.count() < 20 || blueBlob.count() < 50) {
                check.accept(false, String.format(Locale.ROOT, "%s: %s must show the blocks and the piece (%d red on the left, "
                        + "%d on the right, %d magenta pixels)", name, frame, redBlob.count(), blueBlob.count(), magentaBlob.count()));
                continue;
            }
            String label = frame.substring(name.length() + 1);
            if (!main.add(pose, magentaBlob, redBlob, pieceSurface, blockSurface, piece, block, label)
                    || !reference.add(pose, blueBlob, redBlob, controlSurface, blockSurface, control, block, label)) {
                continue;
            }
            if (blockStart == null) {
                blockStart = new double[]{redBlob.x(), redBlob.y()};
            }
            blockTravel = Math.max(blockTravel, Math.hypot(redBlob.x() - blockStart[0], redBlob.y() - blockStart[1]));
            measured++;
            bobs.append(String.format(Locale.ROOT, " %s:bob %.3f hurt %.1f", label, pose.bob, pose.hurt));
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} frames; the red block moved up to {} px; drift of the piece "
                        + "against the left block {} px (allowed {}), of the right block against it {} px (the measurement)",
                name, measured, String.format(Locale.ROOT, "%.2f", blockTravel), String.format(Locale.ROOT, "%.2f",
                        main.worst), TOLERANCE, String.format(Locale.ROOT, "%.2f", reference.worst));
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: piece per frame:{}", name, main.log);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: right block per frame:{}", name, reference.log);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: camera per frame:{}", name, bobs);
        check.accept(measured >= frames.size() - 2, name + ": the frames must be measured (" + measured + " of "
                + frames.size() + ")");
        check.accept(blockTravel >= 3, String.format(Locale.ROOT, "%s: the view must bob (the block moved %.2f px)", name,
                blockTravel));
        // A sanity check of the measurement itself (world against world); its floor is about 0.5 px.
        check.accept(reference.worst <= 1.0, String.format(Locale.ROOT, "%s: the measurement must hold a carpet against "
                + "a carpet (%.2f px)", name, reference.worst));
        return main;
    }
}
