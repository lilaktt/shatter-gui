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
import net.shattergui.shatter.EffectClock;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Submits shard triangles to the level render. Each shard is drawn as a fan of three triangles around its centroid so
 * the effect can color the center and the rim differently; the render types draw quads, so each triangle is sent as a
 * quad with a repeated corner.
 *
 * <p>Layers per wave:
 * <ul>
 *   <li>No shader pack: {@link ShardRenderTypes} (vanilla entity shader, premultiplied blending, CPU shading).</li>
 *   <li>Iris shader pack: vanilla types Iris knows — {@code entity_cutout} for opaque shards (lit and depth-written like
 *       any entity), {@code entity_translucent} for fading ones. Our own pipeline is unknown to Iris and would render
 *       nearly invisible.</li>
 *   <li>Glow ({@link ShardTint#glow}): vanilla {@code eyes} (emissive, fullbright, no depth write) over the same cells,
 *       in both cases.</li>
 * </ul>
 */
public final class ShardRenderer {
    /** Floats per vertex: x, y, z, u, v. */
    private static final int STRIDE = 5;
    /** 3 fan triangles × 4 quad vertices. */
    private static final int VERTICES_PER_SHARD = 12;
    /** Ticks over which shards go from unshaded to shaded by their facing. */
    private static final float LIGHT_EASE_TICKS = 10f;
    /** Vanilla level light directions ({@code Lighting.DIFFUSE_LIGHT_0/1}) and the entity shader's light formula. */
    private static final Vector3f LIGHT_0 = new Vector3f(0.2f, 1f, -0.7f).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2f, 1f, 0.7f).normalize();
    private static final float LIGHT_POWER = 0.6f;
    private static final float AMBIENT_LIGHT = 0.4f;
    /**
     * The glow layer is lifted towards the camera so it never z-fights. It keeps the cell's size: larger cells would
     * overlap their neighbours and the doubled edges would draw the triangle grid.
     */
    private static final float GLOW_SCALE = 1f;
    private static final float GLOW_LIFT = 0.004f;
    private static final float GLOW_MAX_ALPHA = 0.85f;

    private ShardRenderer() {
    }

    /** Shards of opening (gathering) waves drawn in the last frame, for tests and the profile log. */
    private static int gatherShardsDrawn;

    public static int gatherShardsDrawn() {
        return gatherShardsDrawn;
    }

    /** Where the geometry of this frame goes: the level's submit collector, or the pass over the world. */
    interface Sink {
        void add(RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry);
    }

    public static void submit(LevelRenderContext context) {
        gatherShardsDrawn = 0;
        boolean shaders = IrisCompat.shaderPackInUse();
        if (shaders && IrisCompat.renderingShadowPass()) {
            return;
        }
        boolean onTop = ShatterConfig.get().alwaysOnTop;
        if (onTop) {
            OnTopPass.begin();
        }
        if (ShardManager.batches().isEmpty()) {
            return;
        }
        PoseStack poseStack = context.poseStack();
        Sink sink;
        if (onTop) {
            // Drawn after the world and the shader pack's composite, with the mod's own pipeline.
            shaders = false;
            sink = OnTopPass::add;
        } else {
            sink = (type, geometry) -> context.submitNodeCollector().submitCustomGeometry(poseStack, type, geometry);
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float gamePartialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float openPartialTick = EffectClock.partialTick();

        Vec3 liveCamera = camera;
        for (ShardManager.Batch batch : byDistance(ShardManager.batches(), liveCamera)) {
            camera = batch.camera(liveCamera);
            if (!batch.belongsToScreen()) {
                // A gathering whose screen is gone is never drawn, not even for the frame before it is removed.
                continue;
            }
            // Openings also play while the game is paused (the pause menu), on their own clock.
            float partialTick = batch.effect().opens() ? openPartialTick : gamePartialTick;
            // Overlays and own particles of schema-2 effects (particles may outlive the shards). Over the world they
            // come after the shards: they are lifted towards the camera and would hide the shards in the depth test.
            WaveDecor decor = batch.wave() instanceof WaveDecor d ? d : null;
            Quaternionf orientation = context.levelState().cameraRenderState.orientation;
            if (decor != null && (!onTop || batch.shards().isEmpty())) {
                DecorRenderer.submit(sink, batch, decor, camera, orientation, partialTick, shaders);
            }
            if (batch.shards().isEmpty()) {
                continue;
            }
            boolean first = batch.markRendered();
            long start = ShatterProfiler.now();
            Layers layers = Layers.build(batch, camera, partialTick, shaders);
            if (batch.effect().opens()) {
                gatherShardsDrawn += layers.opaque.count + layers.fading.count;
            }
            if (layers.opaque.count + layers.fading.count + layers.glow.count > 0) {
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
            if (decor != null && onTop) {
                DecorRenderer.submit(sink, batch, decor, camera, orientation, partialTick, false);
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
    }

    private static void submit(Sink sink, RenderType type, Geometry geometry, boolean profile) {
        if (!geometry.isEmpty()) {
            geometry.profile = profile;
            sink.add(type, geometry::emit);
        }
    }

    /**
     * Batches from the farthest to the nearest (by their first shard), so translucent pieces of a nearer wave blend
     * over a farther one instead of hiding it in the depth test.
     */
    private static List<ShardManager.Batch> byDistance(List<ShardManager.Batch> batches, Vec3 camera) {
        if (batches.size() < 2) {
            return batches;
        }
        List<ShardManager.Batch> sorted = new ArrayList<>(batches);
        sorted.sort(Comparator.comparingDouble((ShardManager.Batch b) -> {
            if (b.shards().isEmpty()) {
                return 0;
            }
            Vec3 c = b.camera(camera);
            return b.shards().getFirst().pos().distanceSquared(c.x, c.y, c.z);
        }).reversed());
        return sorted;
    }

    /** All layers of one wave for this frame, computed at submit time so later ticks cannot change them. */
    private static final class Layers {
        /** Opaque shards (with a shader pack), or all shards (without). */
        final Geometry opaque;
        /** Shards with some transparency, with a shader pack. */
        final Geometry fading;
        final Geometry glow;

        private Layers(int capacity) {
            opaque = new Geometry(capacity);
            fading = new Geometry(capacity / 4 + 1);
            glow = new Geometry(capacity / 4 + 1);
        }

        static Layers build(ShardManager.Batch batch, Vec3 camera, float partialTick, boolean shaders) {
            ShatterEffect effect = batch.effect();
            Layers layers = new Layers(batch.shards().size());
            Quaternionf rot = new Quaternionf();
            Vector3f normal = new Vector3f();
            Vector3f[] corners = {new Vector3f(), new Vector3f(), new Vector3f()};
            ShardTint tint = new ShardTint();
            // Below 1 only while the wave is hurried out (too many effects at once).
            float fade = batch.fade(partialTick);

            for (ShardInstance shard : batch.shards()) {
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
                shard.prevRot().slerp(shard.rot(), partialTick, rot);
                float px = (float) (lerp(shard.prevPos().x, shard.pos().x, partialTick) - camera.x);
                float py = (float) (lerp(shard.prevPos().y, shard.pos().y, partialTick) - camera.y);
                float pz = (float) (lerp(shard.prevPos().z, shard.pos().z, partialTick) - camera.z);

                // Light the side that faces the camera (camera is at the origin here).
                rot.transform(shard.normal(), normal);
                if (normal.x * px + normal.y * py + normal.z * pz > 0) {
                    normal.negate();
                }
                for (int k = 0; k < 3; k++) {
                    rot.transform(shard.corners()[k], corners[k]);
                }

                if ((tint.center >>> 24) != 0 || (tint.edge >>> 24) != 0) {
                    int center = tint.center;
                    int edge = tint.edge;
                    Geometry target = layers.opaque;
                    if (shaders) {
                        // Packs blend with SRC_ALPHA and light the shards themselves: straight colors, no CPU shading.
                        if ((center >>> 24) != 255 || (edge >>> 24) != 255) {
                            target = layers.fading;
                        }
                    } else {
                        // The GUI was unlit, so the first frame is unshaded and face shading eases in.
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
                    target.add(shard, px, py, pz, corners, tint.scale, 0f, normal, center, edge);
                }
                if (tint.glow > 0f && batch.params().fullQuality()) {
                    int alpha = Math.round(Math.min(1f, tint.glow) * GLOW_MAX_ALPHA * 255);
                    int color = (alpha << 24) | (tint.glowColor & 0xFFFFFF);
                    layers.glow.add(shard, px, py, pz, corners, tint.scale * GLOW_SCALE, GLOW_LIFT, normal, color, color);
                }
            }
            return layers;
        }
    }

    /** Vertex data of one layer. */
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

        /**
         * One shard as a fan around its centroid, corners scaled by {@code scale} and everything moved {@code lift}
         * along the camera-facing normal.
         */
        void add(ShardInstance shard, float px, float py, float pz, Vector3f[] corners, float scale, float lift,
                 Vector3f normal, int centerColor, int edgeColor) {
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
                // Quad: centroid, corner k, corner n, corner n again.
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

        private int put(int v, float x, float y, float z, float u, float tv) {
            vertices[v++] = x;
            vertices[v++] = y;
            vertices[v++] = z;
            vertices[v++] = u;
            vertices[v++] = tv;
            return v;
        }
    }

    /** ARGB with its alpha multiplied by {@code factor}. */
    private static int withAlpha(int argb, float factor) {
        return (Math.round((argb >>> 24) * factor) << 24) | (argb & 0xFFFFFF);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    /** Same as {@code minecraft_mix_light} in light.glsl, for a normal facing the camera. */
    private static float diffuse(Vector3f normal) {
        float light = Math.max(0f, LIGHT_0.dot(normal)) + Math.max(0f, LIGHT_1.dot(normal));
        return Math.min(1f, light * LIGHT_POWER + AMBIENT_LIGHT);
    }
}
