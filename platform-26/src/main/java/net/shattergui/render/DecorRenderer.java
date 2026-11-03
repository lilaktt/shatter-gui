package net.shattergui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws a wave's {@link WaveDecor}: texture overlays on its shards (each a triangle following the shard, lifted a
 * little towards the camera) and its sprite particles (camera-facing squares). Vanilla render types except multiply
 * without a shader pack, which uses {@link ShardRenderTypes#multiply}; with a shader pack multiply falls back to normal
 * blending (shader packs only light vanilla types).
 */
final class DecorRenderer {
    private static final float OVERLAY_LIFT = 0.006f;
    private static final int FULL_BRIGHT = 0xF000F0;

    private DecorRenderer() {
    }

    static void submit(ShardRenderer.Sink sink, ShardManager.Batch batch, WaveDecor decor, Vec3 camera,
                       Quaternionf cameraRotation, float partialTick, boolean shaders) {
        float fade = batch.fade(partialTick);
        overlays(sink, batch, decor, camera, partialTick, shaders, fade);
        sprites(sink, decor, camera, cameraRotation, partialTick, fade);
    }

    private static void overlays(ShardRenderer.Sink sink, ShardManager.Batch batch, WaveDecor decor, Vec3 camera,
                                 float partialTick, boolean shaders, float fade) {
        Quaternionf rot = new Quaternionf();
        Vector3f normal = new Vector3f();
        Vector3f corner = new Vector3f();
        float[] uv = new float[6];
        // Low quality: no texture overlays.
        int overlays = batch.params().fullQuality() ? decor.overlayCount() : 0;
        for (int k = 0; k < overlays; k++) {
            WaveDecor.Blend blend = decor.overlayBlend(k);
            boolean multiply = blend == WaveDecor.Blend.MULTIPLY && !shaders;
            Mesh mesh = new Mesh(batch.shards().size() * 4);
            for (ShardInstance shard : batch.shards()) {
                int color = decor.overlayColor(shard, k, partialTick);
                float alpha = (color >>> 24) / 255f * fade;
                if (alpha <= 0.004f || !decor.overlayUv(shard, k, partialTick, uv)) {
                    continue;
                }
                if (multiply) {
                    // Tint premultiplied by the opacity (see the multiply pipeline's blending).
                    color = ShardRenderTypes.premultiply((Math.round(alpha * 255) << 24) | (color & 0xFFFFFF), 1f);
                } else {
                    color = (Math.round(alpha * 255) << 24) | (color & 0xFFFFFF);
                }
                shard.prevRot().slerp(shard.rot(), partialTick, rot);
                float px = (float) (shard.prevPos().x + (shard.pos().x - shard.prevPos().x) * partialTick - camera.x);
                float py = (float) (shard.prevPos().y + (shard.pos().y - shard.prevPos().y) * partialTick - camera.y);
                float pz = (float) (shard.prevPos().z + (shard.pos().z - shard.prevPos().z) * partialTick - camera.z);
                rot.transform(shard.normal(), normal);
                if (normal.x * px + normal.y * py + normal.z * pz > 0) {
                    normal.negate();
                }
                float lift = OVERLAY_LIFT * (k + 1);
                float scale = decor.scale(shard, partialTick);
                float[] xyz = new float[9];
                for (int c = 0; c < 3; c++) {
                    rot.transform(shard.corners()[c], corner);
                    xyz[c * 3] = px + normal.x * lift + corner.x * scale;
                    xyz[c * 3 + 1] = py + normal.y * lift + corner.y * scale;
                    xyz[c * 3 + 2] = pz + normal.z * lift + corner.z * scale;
                }
                mesh.triangle(xyz, uv, color, normal);
            }
            if (mesh.count == 0) {
                continue;
            }
            Identifier texture = decor.overlayTexture(k);
            RenderType type = switch (blend) {
                case ADDITIVE -> RenderTypes.eyes(texture);
                case MULTIPLY -> multiply ? ShardRenderTypes.multiply(texture) : RenderTypes.entityTranslucent(texture);
                default -> RenderTypes.entityTranslucent(texture);
            };
            sink.add(type, mesh::emit);
        }
    }

    private static void sprites(ShardRenderer.Sink sink, WaveDecor decor, Vec3 camera,
                                Quaternionf cameraRotation, float partialTick, float fade) {
        Map<String, Mesh> meshes = new LinkedHashMap<>();
        Map<String, Identifier> textures = new LinkedHashMap<>();
        Vector3f facing = cameraRotation.transform(new Vector3f(0, 0, 1));
        Vector3f[] corners = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        float[][] base = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        float[] uvQuad = {0, 1, 0, 0, 1, 0, 1, 1};
        decor.sprites(partialTick, (texture, emissive, x, y, z, half, argb) -> {
            float alpha = (argb >>> 24) / 255f * fade;
            if (alpha <= 0.004f || half <= 0) {
                return;
            }
            int color = (Math.round(alpha * 255) << 24) | (argb & 0xFFFFFF);
            String key = texture + (emissive ? "#e" : "#n");
            textures.putIfAbsent(key, texture);
            Mesh mesh = meshes.computeIfAbsent(key, k -> new Mesh(64));
            float cx = (float) (x - camera.x);
            float cy = (float) (y - camera.y);
            float cz = (float) (z - camera.z);
            float[] xyz = new float[12];
            for (int i = 0; i < 4; i++) {
                cameraRotation.transform(corners[i].set(base[i][0] * half, base[i][1] * half, 0));
                xyz[i * 3] = cx + corners[i].x;
                xyz[i * 3 + 1] = cy + corners[i].y;
                xyz[i * 3 + 2] = cz + corners[i].z;
            }
            mesh.quad(xyz, uvQuad, color, facing);
        });
        for (Map.Entry<String, Mesh> entry : meshes.entrySet()) {
            Identifier texture = textures.get(entry.getKey());
            RenderType type = entry.getKey().endsWith("#e") ? RenderTypes.eyes(texture) : RenderTypes.entityTranslucent(texture);
            sink.add(type, entry.getValue()::emit);
        }
    }

    /** Quads (a triangle is a quad with its last corner repeated): position, uv, colour, normal per vertex. */
    private static final class Mesh {
        private float[] data;
        private int[] colors;
        int count;

        Mesh(int capacity) {
            data = new float[Math.max(4, capacity) * 4 * 8];
            colors = new int[Math.max(4, capacity) * 4];
        }

        private void grow() {
            if ((count + 1) * 4 > colors.length) {
                data = Arrays.copyOf(data, data.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
            }
        }

        void triangle(float[] xyz, float[] uv, int color, Vector3f normal) {
            grow();
            int[] order = {0, 1, 2, 2};
            for (int i = 0; i < 4; i++) {
                int c = order[i];
                put(count * 4 + i, xyz[c * 3], xyz[c * 3 + 1], xyz[c * 3 + 2], uv[c * 2], uv[c * 2 + 1], color, normal);
            }
            count++;
        }

        void quad(float[] xyz, float[] uv, int color, Vector3f normal) {
            grow();
            for (int i = 0; i < 4; i++) {
                put(count * 4 + i, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], uv[i * 2], uv[i * 2 + 1], color, normal);
            }
            count++;
        }

        private void put(int vertex, float x, float y, float z, float u, float v, int color, Vector3f normal) {
            int i = vertex * 8;
            data[i] = x;
            data[i + 1] = y;
            data[i + 2] = z;
            data[i + 3] = u;
            data[i + 4] = v;
            data[i + 5] = normal.x;
            data[i + 6] = normal.y;
            data[i + 7] = normal.z;
            colors[vertex] = color;
        }

        void emit(PoseStack.Pose pose, VertexConsumer consumer) {
            for (int vertex = 0; vertex < count * 4; vertex++) {
                int i = vertex * 8;
                consumer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                        .setColor(colors[vertex])
                        .setUv(data[i + 3], data[i + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT)
                        .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
            }
        }
    }
}
