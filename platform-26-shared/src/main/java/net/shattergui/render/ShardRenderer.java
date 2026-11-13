package net.shattergui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.shattergui.compat.iris.IrisCompat;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.ShardTint;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.fly.OpenBackdrop;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.CameraSpace;
import net.shattergui.shatter.EffectClock;
import net.shattergui.shatter.Settle;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class ShardRenderer {
    private static final int STRIDE = 5;
    private static final int VERTICES_PER_SHARD = 12;
    private static final float LIGHT_EASE_TICKS = 10f;
    private static final Vector3f LIGHT_0 = new Vector3f(0.2f, 1f, -0.7f).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2f, 1f, 0.7f).normalize();
    private static final float LIGHT_POWER = 0.6f;
    private static final float AMBIENT_LIGHT = 0.4f;
    private static final float GLOW_SCALE = 1f;
    private static final float GLOW_LIFT = 0.004f;
    private static final float GLOW_MAX_ALPHA = 0.85f;

    private ShardRenderer() {
    }

    private static int gatherShardsDrawn;

    public static int gatherShardsDrawn() {
        return gatherShardsDrawn;
    }

    interface Sink {
        void add(RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry);
    }

    enum Part {
        ALL, MOVING, SETTLED, SHOWN, HIDDEN;

        boolean takes(boolean settled) {
            return takes(settled, false);
        }

        boolean takes(boolean settled, boolean hidden) {
            return switch (this) {
                case ALL -> true;
                case MOVING -> !settled && !hidden;
                case SETTLED -> settled || hidden;
                case SHOWN -> !hidden;
                case HIDDEN -> hidden;
            };
        }

        boolean overWorld() {
            return this != SETTLED && this != HIDDEN;
        }
    }

    private static boolean suppressedForTests;

    public static void suppressForTests(boolean suppressed) {
        suppressedForTests = suppressed;
    }

    public static void submit(LevelRenderContext context) {
        boolean shaders = IrisCompat.shaderPackInUse();
        if (shaders && IrisCompat.renderingShadowPass()) {
            return;
        }
        boolean onTop = ShatterConfig.get().alwaysOnTop;
        OnTopPass.begin();
        if (ShardManager.batches().isEmpty() || suppressedForTests) {
            return;
        }
        PoseStack poseStack = context.poseStack();
        boolean overBackdrop = onTop || OpenBackdrop.closingKind() != 0;
        boolean splitSettled = onTop && OpenBackdrop.closingKind() == 0;
        Sink inWorld = (type, geometry) -> context.submitNodeCollector().submitCustomGeometry(poseStack, type, geometry);
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Quaternionf orientation = context.levelState().cameraRenderState.orientation;
        for (ShardManager.Batch batch : byDistance(ShardManager.batches(), camera)) {
            if (batch.effect().opens()) {
                continue;
            }
            Sink sink = overBackdrop ? OnTopPass::add : inWorld;
            CameraSpace space = CameraSpace.world(camera);
            if (batch.follows() && !batch.shards().isEmpty()) {
                ShardInstance first = batch.shards().getFirst();
                space = batch.space(camera).towardsWorld(camera, net.shattergui.effect.FlyEffect.towardsTarget(first,
                        partialTick));
            }
            if (overBackdrop && !batch.follows()) {
                submitBatch(OnTopPass::add, batch, space, orientation, partialTick, false, true,
                        splitSettled ? Part.MOVING : Part.SHOWN);
                submitBatch(OnTopPass::addSettled, batch, space, orientation, partialTick, false, true,
                        splitSettled ? Part.SETTLED : Part.HIDDEN);
            } else {
                submitBatch(sink, batch, space, orientation, partialTick, shaders && !overBackdrop, overBackdrop, Part.ALL);
            }
        }
    }

    public static void drawOpenings(com.mojang.blaze3d.buffers.GpuBufferSlice noFog) {
        gatherShardsDrawn = 0;
        if (ShardManager.batches().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        net.minecraft.client.Camera live = Mc26.camera();
        Vec3 camera = live.position();
        Quaternionf orientation = new Quaternionf(live.rotation());
        List<OnTopPass.Entry> flat = new ArrayList<>();
        List<OnTopPass.Entry> level = new ArrayList<>();
        for (ShardManager.Batch batch : byDistance(ShardManager.batches(), camera)) {
            if (!batch.effect().opens() || !batch.belongsToScreen()) {
                continue;
            }
            CameraSpace space = batch.space(camera);
            org.joml.Vector3d c = batch.planeCenter();
            if (c != null) {
                net.shattergui.debug.FrameAudit.openPlane(space.point(c.x, c.y, c.z, new Vector3f()));
            }
            List<OnTopPass.Entry> into = batch.onSpatialPlane() || !batch.follows() ? level : flat;
            submitBatch((type, geometry) -> into.add(new OnTopPass.Entry(type, geometry)), batch, space, orientation,
                    0f, false, true, Part.ALL);
        }
        OnTopPass.drawOpenings(level, false, noFog);
        OnTopPass.drawOpenings(flat, true, noFog);
    }

    public static void drawFirstFrame(ShardManager.Batch batch, com.mojang.blaze3d.buffers.GpuBufferSlice noFog) {
        Minecraft mc = Minecraft.getInstance();
        if (batch == null || mc.level == null || !ShardManager.batches().contains(batch)) {
            return;
        }
        net.minecraft.client.Camera live = Mc26.camera();
        List<OnTopPass.Entry> entries = new ArrayList<>();
        submitBatch((type, geometry) -> entries.add(new OnTopPass.Entry(type, geometry)), batch,
                CameraSpace.world(live.position()), new Quaternionf(live.rotation()), 0f, false, true, Part.ALL);
        OnTopPass.drawOpenings(entries, false, noFog);
    }

    private static void submitBatch(Sink sink, ShardManager.Batch batch, CameraSpace space, Quaternionf orientation,
                                    float gamePartialTick, boolean shaders, boolean decorAfter, Part part) {
        float partialTick = batch.effect().opens() ? EffectClock.partialTick() : gamePartialTick;
        WaveDecor decor = batch.wave() instanceof WaveDecor d ? d : null;
        if (decor != null && (!decorAfter || batch.shards().isEmpty())) {
            DecorRenderer.submit(sink, batch, decor, space, orientation, partialTick, shaders, part);
        }
        if (batch.shards().isEmpty()) {
            return;
        }
        boolean first = batch.markRendered();
        long start = ShatterProfiler.now();
        Layers layers = Layers.build(batch, space, partialTick, shaders, part);
        if (batch.effect().opens()) {
            gatherShardsDrawn += layers.opaque.count + layers.fading.count;
        }
        if (layers.opaque.count + layers.fading.count + layers.glow.count > 0 && part.overWorld()) {
            net.shattergui.debug.FrameAudit.wave(batch.effect().opens());
        }
        Identifier texture = batch.texture();
        if (shaders) {
            submit(sink, RenderTypes.entityCutout(texture), layers.opaque, first);
            submit(sink, RenderTypes.entityTranslucent(texture), layers.fading, false);
        } else {
            submit(sink, ShardRenderTypes.shard(texture), layers.opaque, first);
        }
        submit(sink, RenderTypes.eyes(texture), layers.glow, false);
        if (decor != null && decorAfter) {
            DecorRenderer.submit(sink, batch, decor, space, orientation, partialTick, false, part);
        }
        if (first) {
            if (!batch.effect().opens() && !batch.preview()) {
                ShatterProfiler.closeEffectDrawn();
            }
            if (shaders) {
                ShatterProfiler.value("render.shaderPackInUse", "drawn as entity_cutout / entity_translucent for Iris");
            }
            ShatterProfiler.step("render.firstFrame.buildAndSubmit (frame +" + ShatterProfiler.frameIndex() + ")",
                    start);
        }
    }

    private static void submit(Sink sink, RenderType type, Geometry geometry, boolean profile) {
        if (!geometry.isEmpty()) {
            geometry.profile = profile;
            sink.add(type, geometry::emit);
        }
    }

    private static List<ShardManager.Batch> byDistance(List<ShardManager.Batch> batches, Vec3 camera) {
        if (batches.size() < 2) {
            return batches;
        }
        List<ShardManager.Batch> sorted = new ArrayList<>(batches);
        sorted.sort(Comparator.comparingDouble((ShardManager.Batch b) -> {
            if (b.shards().isEmpty()) {
                return 0;
            }
            return b.shards().getFirst().pos().distanceSquared(camera.x, camera.y, camera.z);
        }).reversed());
        return sorted;
    }

    private static final class Layers {
        final Geometry opaque;
        final Geometry fading;
        final Geometry glow;

        private Layers(int capacity) {
            opaque = new Geometry(capacity);
            fading = new Geometry(capacity / 4 + 1);
            glow = new Geometry(capacity / 4 + 1);
        }

        static Layers build(ShardManager.Batch batch, CameraSpace space, float partialTick, boolean shaders, Part part) {
            ShatterEffect effect = batch.effect();
            Layers layers = new Layers(batch.shards().size());
            Quaternionf rot = new Quaternionf();
            Vector3f normal = new Vector3f();
            Vector3f[] corners = {new Vector3f(), new Vector3f(), new Vector3f()};
            Vector3f at = new Vector3f();
            ShardTint tint = new ShardTint();
            float fade = batch.fade(partialTick);

            for (ShardInstance shard : batch.shards()) {
                boolean settled = shard.settled();
                if (!part.takes(settled, shard.hidden())) {
                    continue;
                }
                float bias = settled ? Settle.BIAS : 0f;
                tint.reset();
                effect.tint(shard, partialTick, batch.params(), tint);
                if (fade < 1f) {
                    tint.center = withAlpha(tint.center, fade);
                    tint.edge = withAlpha(tint.edge, fade);
                    tint.glow *= fade;
                }
                if ((tint.center >>> 24) == 0 && (tint.edge >>> 24) == 0 && tint.glow <= 0f) {
                    continue;
                }
                if (shard.prevRot().equals(shard.rot())) {
                    rot.set(shard.rot());
                } else {
                    shard.prevRot().slerp(shard.rot(), partialTick, rot);
                }
                space.point(lerp(shard.prevPos().x, shard.pos().x, partialTick),
                        lerp(shard.prevPos().y, shard.pos().y, partialTick),
                        lerp(shard.prevPos().z, shard.pos().z, partialTick), at);
                float px = at.x;
                float py = at.y;
                float pz = at.z;

                space.direction(rot.transform(shard.normal(), normal));
                if (normal.x * px + normal.y * py + normal.z * pz > 0) {
                    normal.negate();
                }
                for (int k = 0; k < 3; k++) {
                    space.offset(rot.transform(shard.corners()[k], corners[k]));
                }

                if ((tint.center >>> 24) != 0 || (tint.edge >>> 24) != 0) {
                    int center = tint.center;
                    int edge = tint.edge;
                    Geometry target = layers.opaque;
                    if (shaders) {
                        if ((center >>> 24) != 255 || (edge >>> 24) != 255) {
                            target = layers.fading;
                        }
                    } else {
                        float shade = 1f;
                        if (!tint.unlit) {
                            float age = Float.isNaN(tint.lightAge) ? shard.age() + partialTick : tint.lightAge;
                            float ease = Math.clamp(age / LIGHT_EASE_TICKS, 0f, 1f);
                            ease = ease * ease * (3 - 2 * ease);
                            shade = 1f + (diffuse(normal) - 1f) * ease;
                        }
                        center = ShardRenderTypes.premultiply(center, shade);
                        edge = ShardRenderTypes.premultiply(edge, shade);
                    }
                    target.add(shard, px, py, pz, corners, tint.scale, 0f, normal, center, edge, bias);
                }
                if (tint.glow > 0f && batch.params().fullQuality()) {
                    int alpha = Math.round(Math.min(1f, tint.glow) * GLOW_MAX_ALPHA * 255);
                    int color = (alpha << 24) | (tint.glowColor & 0xFFFFFF);
                    layers.glow.add(shard, px, py, pz, corners, tint.scale * GLOW_SCALE, GLOW_LIFT, normal, color, color,
                            bias);
                }
            }
            return layers;
        }
    }

    private static final class Geometry {
        private float[] vertices;
        private int[] colors;
        private float[] normals;
        private int[] lights;
        private int count;
        boolean profile;

        Geometry(int capacity) {
            int shards = Math.max(1, capacity);
            vertices = new float[shards * VERTICES_PER_SHARD * STRIDE];
            colors = new int[shards * VERTICES_PER_SHARD];
            normals = new float[shards * 3];
            lights = new int[shards];
        }

        boolean isEmpty() {
            return count == 0;
        }

        void add(ShardInstance shard, float px, float py, float pz, Vector3f[] corners, float scale, float lift,
                 Vector3f normal, int centerColor, int edgeColor, float bias) {
            this.bias = bias;
            if (count == lights.length) {
                int grown = count * 2;
                vertices = Arrays.copyOf(vertices, grown * VERTICES_PER_SHARD * STRIDE);
                colors = Arrays.copyOf(colors, grown * VERTICES_PER_SHARD);
                normals = Arrays.copyOf(normals, grown * 3);
                lights = Arrays.copyOf(lights, grown);
            }
            float cx = px + normal.x * lift;
            float cy = py + normal.y * lift;
            float cz = pz + normal.z * lift;
            float[] uv = shard.uv();
            float cu = (uv[0] + uv[2] + uv[4]) / 3f;
            float cv = (uv[1] + uv[3] + uv[5]) / 3f;
            int v = count * VERTICES_PER_SHARD * STRIDE;
            int c = count * VERTICES_PER_SHARD;
            for (int k = 0; k < 3; k++) {
                int n = (k + 1) % 3;
                v = put(v, cx, cy, cz, cu, cv);
                colors[c++] = centerColor;
                v = put(v, cx + corners[k].x * scale, cy + corners[k].y * scale, cz + corners[k].z * scale,
                        uv[k * 2], uv[k * 2 + 1]);
                colors[c++] = edgeColor;
                for (int r = 0; r < 2; r++) {
                    v = put(v, cx + corners[n].x * scale, cy + corners[n].y * scale, cz + corners[n].z * scale,
                            uv[n * 2], uv[n * 2 + 1]);
                    colors[c++] = edgeColor;
                }
            }
            normals[count * 3] = normal.x;
            normals[count * 3 + 1] = normal.y;
            normals[count * 3 + 2] = normal.z;
            lights[count] = shard.light();
            count++;
        }

        void emit(PoseStack.Pose pose, VertexConsumer consumer) {
            long start = ShatterProfiler.now();
            for (int s = 0; s < count; s++) {
                for (int k = 0; k < VERTICES_PER_SHARD; k++) {
                    int vertex = s * VERTICES_PER_SHARD + k;
                    int i = vertex * STRIDE;
                    consumer.addVertex(pose, vertices[i], vertices[i + 1], vertices[i + 2])
                            .setColor(colors[vertex])
                            .setUv(vertices[i + 3], vertices[i + 4])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(lights[s])
                            .setNormal(pose, normals[s * 3], normals[s * 3 + 1], normals[s * 3 + 2]);
                }
            }
            if (profile) {
                ShatterProfiler.step("render.firstFrame.emitVertices (" + count + " shards)", start);
            }
        }

        private float bias;

        private int put(int v, float x, float y, float z, float u, float tv) {
            float k = Settle.towardsCamera(x, y, z, bias);
            vertices[v++] = x * k;
            vertices[v++] = y * k;
            vertices[v++] = z * k;
            vertices[v++] = u;
            vertices[v++] = tv;
            return v;
        }
    }

    private static int withAlpha(int argb, float factor) {
        return (Math.round((argb >>> 24) * factor) << 24) | (argb & 0xFFFFFF);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static float diffuse(Vector3f normal) {
        float light = Math.max(0f, LIGHT_0.dot(normal)) + Math.max(0f, LIGHT_1.dot(normal));
        return Math.min(1f, light * LIGHT_POWER + AMBIENT_LIGHT);
    }
}
