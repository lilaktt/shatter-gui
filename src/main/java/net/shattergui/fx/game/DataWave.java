package net.shattergui.fx.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.EffectParticles;
import net.shattergui.effect.EffectSounds;
import net.shattergui.effect.ShatterWave;
import net.shattergui.fly.FlyTarget;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.params.BundledParams;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxSimulation;
import net.shattergui.fx.Fracture;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.render.WaveDecor;
import net.shattergui.shatter.BlockCollision;
import net.shattergui.shatter.ImageRect;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardMesh;
import net.shattergui.shatter.ShardPlane;
import net.shattergui.shatter.ShardSource;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * One closed screen running a schema-2 effect: the simulation in the interface's plane, mapped to the world with the
 * plane's own axes (so it works the same on the flat GUI and on a Spatial GUI plane), the shards of every piece, and
 * the decorations the renderer draws (overlays, own sprite particles).
 */
final class DataWave implements ShatterWave, WaveDecor, FxSimulation.Host {
    /** Which piece a shard belongs to, its offset from the piece centre (plane, blocks) and its GUI uv corners. */
    record Ref(DataWave wave, FxSimulation.Piece piece, float dx, float dy, float[] guiUv, float[] uv) {
    }

    private final DataEffect effect;
    /** The definition it runs (the effect's, or its "reduced" version for reduce motion). */
    private final FxDef def;
    private final ShardSource source;
    private final ParamValues params;
    private final FxSimulation sim;
    private final List<ShardInstance> shards = new ArrayList<>();
    private final Vector3d origin;
    private final Vector3f right;
    private final Vector3f up;
    private final Vector3f normal;
    private final List<Integer> overlays = new ArrayList<>();
    private final Quaternionf rot = new Quaternionf();
    private final Vector3f offset = new Vector3f();
    private boolean pendingTick = true;
    private boolean reported;
    /** Block lookups for recording an opening run (all in one tick), or -1: the shared per-tick budget. */
    private static final int RECORDING_CHECKS = 40_000;
    private int recordingChecks;
    private final Vector3d collidePos = new Vector3d();
    private final Vector3d collideVel = new Vector3d();
    /** The close this wave belongs to: a later close may be the profiler's current one when it ends. */
    private final int closeNumber = ShatterProfiler.closeCount();

    DataWave(DataEffect effect, FxDef def, ShardSource source, List<Fracture.Piece> cut, float width, float height,
             Random random) {
        this.effect = effect;
        this.def = def;
        this.source = source;
        this.params = source.params();
        ShardPlane plane = source.plane();
        ImageRect b = source.bounds();
        double xc = (b.x0() + b.x1()) / 2;
        double yc = (b.y0() + b.y1()) / 2;
        origin = plane.toWorld(xc, yc);
        right = axis(plane.toWorld(b.x1(), yc).sub(plane.toWorld(b.x0(), yc)));
        up = axis(plane.toWorld(xc, b.y0()).sub(plane.toWorld(xc, b.y1())));
        normal = new Vector3f(plane.normal()).normalize();
        // Opening runs the whole effect now to record it: its own budget, then the pieces move freely.
        recordingChecks = effect.reversed ? RECORDING_CHECKS : -1;
        sim = DataEffect.simulate(def, effect.bundled, effect.reversed, cut, width, height, this, random, params);
        if (effect.reversed) {
            OpenAssemble.waveStarted(sim.length());
        }
        for (int k = 0; k < def.appearance.overlays().size(); k++) {
            FxTextures.Tex tex = effect.textures.get(def.appearance.overlays().get(k).texture());
            if (tex != null && tex.gpu() != null) {
                overlays.add(k);
            }
        }
        // Backwards the pieces are gone the tick the screen is shown (no uncut copy left lying in the world under it);
        // forwards they may outlive the run a little for their sprites.
        int lifetime = sim.reversed() ? sim.length() + 1 : sim.duration + 40;
        for (FxSimulation.Piece piece : sim.pieces()) {
            float[] t = piece.triangles;
            for (int i = 0; i < t.length; i += 6) {
                double[][] tri = new double[3][];
                for (int c = 0; c < 3; c++) {
                    tri[c] = new double[]{b.x0() + t[i + c * 2] * b.width(), b.y0() + t[i + c * 2 + 1] * b.height()};
                }
                ShardInstance shard = ShardMesh.triangle(tri, source);
                shard.setLifetime(lifetime);
                float tu = (t[i] + t[i + 2] + t[i + 4]) / 3;
                float tv = (t[i + 1] + t[i + 3] + t[i + 5]) / 3;
                float[] guiUv = {t[i], t[i + 1], t[i + 2], t[i + 3], t[i + 4], t[i + 5]};
                shard.effectState = new Ref(this, piece, (tu - piece.u) * width, -(tv - piece.v) * height, guiUv,
                        shard.uv().clone());
                shards.add(shard);
            }
        }
        if (sim.maskMissing()) {
            ShatterProfiler.value("fx." + def.id + ".trigger", "mask texture missing, noise used instead");
        }
        recordingChecks = -1;
        if (sim.reversed()) {
            // Gathering: the pieces start where the forward run ended.
            for (ShardInstance shard : shards) {
                position(shard, (Ref) shard.effectState);
                shard.prevPos().set(shard.pos());
                shard.prevRot().set(shard.rot());
            }
        }
    }

    private static Vector3f axis(Vector3d v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z).normalize();
    }

    FxSimulation sim() {
        return sim;
    }

    List<ShardInstance> shards() {
        return shards;
    }

    /** Runs the simulation once per tick (the first shard ticked, or the wave itself, triggers it). */
    void advance() {
        if (pendingTick) {
            pendingTick = false;
            sim.tick();
        }
    }

    /** Moves a shard to its piece: centre, rotation, scaled offset; removes it when its piece is over. */
    void place(ShardInstance shard, Ref ref) {
        shard.tickStatic();
        position(shard, ref);
    }

    private void position(ShardInstance shard, Ref ref) {
        FxSimulation.Piece p = ref.piece();
        if (sim.gone(p)) {
            shard.setLifetime(shard.age());
            return;
        }
        mosaic(shard, ref);
        float[] edge = sim.edgeOffset(1f);
        double x = p.x + edge[0];
        double y = p.y + edge[1];
        double z = p.z;
        rotation(p.angle, p.tilt, p.tiltAxis, rot);
        offset.set(right).mul(ref.dx() * p.scale).add(up.x * ref.dy() * p.scale, up.y * ref.dy() * p.scale,
                up.z * ref.dy() * p.scale);
        rot.transform(offset);
        shard.pos().set(origin.x + right.x * x + up.x * y + normal.x * z + offset.x,
                origin.y + right.y * x + up.y * y + normal.y * z + offset.y,
                origin.z + right.z * x + up.z * y + normal.z * z + offset.z);
        shard.rot().set(rot);
    }

    /**
     * Coarse pixels: the shard's texture corners move towards the texture point of its piece's centre (found from the
     * shard's own corners, which map GUI fractions to the captured image linearly).
     */
    private void mosaic(ShardInstance shard, Ref ref) {
        float m = sim.mosaic(1f);
        float[] uv = shard.uv();
        if (m <= 0f) {
            if (uv[0] != ref.uv()[0] || uv[1] != ref.uv()[1]) {
                System.arraycopy(ref.uv(), 0, uv, 0, 6);
            }
            return;
        }
        float[] g = ref.guiUv();
        float[] o = ref.uv();
        float[] w = barycentric(g, ref.piece().u, ref.piece().v);
        float cu = w[0] * o[0] + w[1] * o[2] + w[2] * o[4];
        float cv = w[0] * o[1] + w[1] * o[3] + w[2] * o[5];
        for (int c = 0; c < 3; c++) {
            uv[c * 2] = o[c * 2] + (cu - o[c * 2]) * m;
            uv[c * 2 + 1] = o[c * 2 + 1] + (cv - o[c * 2 + 1]) * m;
        }
    }

    /** Weights of the triangle's corners (u0 v0 u1 v1 u2 v2) for the point (u, v); may lie outside it. */
    static float[] barycentric(float[] t, float u, float v) {
        float d = (t[3] - t[5]) * (t[0] - t[4]) + (t[4] - t[2]) * (t[1] - t[5]);
        if (Math.abs(d) < 1e-12f) {
            return new float[]{1 / 3f, 1 / 3f, 1 / 3f};
        }
        float a = ((t[3] - t[5]) * (u - t[4]) + (t[4] - t[2]) * (v - t[5])) / d;
        float b = ((t[5] - t[1]) * (u - t[4]) + (t[0] - t[4]) * (v - t[5])) / d;
        return new float[]{a, b, 1 - a - b};
    }

    /** Spin about the plane normal, then a tumble about an in-plane axis. */
    private void rotation(float angle, float tilt, float tiltAxis, Quaternionf out) {
        out.identity().rotateAxis(angle, normal.x, normal.y, normal.z);
        if (tilt != 0f) {
            float ax = right.x * (float) Math.cos(tiltAxis) + up.x * (float) Math.sin(tiltAxis);
            float ay = right.y * (float) Math.cos(tiltAxis) + up.y * (float) Math.sin(tiltAxis);
            float az = right.z * (float) Math.cos(tiltAxis) + up.z * (float) Math.sin(tiltAxis);
            float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            out.rotateAxis(tilt, ax / len, ay / len, az / len);
        }
    }

    @Override
    public boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params) {
        advance();
        pendingTick = true;
        boolean alive = sim.alive();
        if (!alive) {
            report();
            if (effect.reversed) {
                OpenAssemble.waveDone();
            }
        }
        return alive;
    }

    @Override
    public void hurry(int ticks) {
        sim.hurry(ticks);
    }

    @Override
    public void dispose() {
        report();
        sim.dispose();
        if (effect.reversed) {
            OpenAssemble.waveDone();
        }
    }

    private void report() {
        if (!reported) {
            reported = true;
            ShatterProfiler.value("fx." + def.id + (effect.reversed ? ".open" : "") + ".done", String.format(Locale.ROOT,
                    "%d ticks, %d vanilla particles, sprite peak %d (wave of close #%d)", sim.time(), sim.vanillaSpawned(),
                    sim.spritePeak(), closeNumber));
        }
    }

    // --- plane ↔ world ---

    private Vec3 toWorld(float x, float y, float z) {
        return new Vec3(origin.x + right.x * x + up.x * y + normal.x * z, origin.y + right.y * x + up.y * y + normal.y * z,
                origin.z + right.z * x + up.z * y + normal.z * z);
    }

    private float[] toPlane(Vec3 p) {
        double dx = p.x - origin.x;
        double dy = p.y - origin.y;
        double dz = p.z - origin.z;
        return new float[]{(float) (dx * right.x + dy * right.y + dz * right.z),
                (float) (dx * up.x + dy * up.y + dz * up.z), (float) (dx * normal.x + dy * normal.y + dz * normal.z)};
    }

    // --- FxSimulation.Host ---

    @Override
    public float[] point(FxDef.Point.Kind kind) {
        if (kind == FxDef.Point.Kind.SOURCE) {
            FlyTarget target = source.target();
            return target != null && !target.isEdge() && target.point() != null ? toPlane(target.point()) : null;
        }
        if (kind == FxDef.Point.Kind.PLAYER) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return null;
            }
            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getLookAngle();
            return toPlane(new Vec3(eye.x + look.x * 0.8, eye.y + look.y * 0.8 - 0.2, eye.z + look.z * 0.8));
        }
        return null;
    }

    @Override
    public float sampleMask(String texture, float u, float v) {
        FxTextures.Tex tex = effect.textures.get(texture);
        return tex == null ? Float.NaN : tex.sample(u, v);
    }

    @Override
    public float[] gravity() {
        return new float[]{-right.y, -up.y, -normal.y};
    }

    @Override
    public boolean hasSprite(String texture) {
        FxTextures.Tex tex = effect.textures.get(texture);
        return tex != null && tex.gpu() != null;
    }

    @Override
    public void spawnParticle(FxDef.Emitter emitter, float x, float y, float z, float vx, float vy, float vz, float size) {
        SimpleParticleType type = effect.particles.get(emitter.particle());
        if (type == null || !EffectParticles.allowed()) {
            return;
        }
        Vec3 at = toWorld(x, y, z);
        float scale = EffectParticles.sizeAt(at) * Math.clamp(size / 0.1f, 0.2f, 3f);
        double wx = right.x * vx + up.x * vy + normal.x * vz;
        double wy = right.y * vx + up.y * vy + normal.y * vz;
        double wz = right.z * vx + up.z * vy + normal.z * vz;
        EffectParticles.spawn(type, at, wx, wy, wz, scale);
    }

    @Override
    public void playSound(String sound, float volume, float pitch) {
        SoundEvent event = effect.sounds.get(sound);
        if (event != null) {
            BundledParams.Set own = effect.bundled ? BundledParams.of(def.id) : null;
            float ownVolume = own != null ? params.f(own.soundVolume()) : 1f;
            EffectSounds.play(event, volume * ownVolume * params.f(Params.SOUND_VOLUME), pitch);
        }
    }

    private boolean reserve(int checks) {
        if (recordingChecks >= 0) {
            if (recordingChecks < checks) {
                return false;
            }
            recordingChecks -= checks;
            return true;
        }
        return BlockCollision.reserve(checks);
    }

    @Override
    public int move(float[] s, float radius, float bounce, float friction) {
        if (!params.fullQuality()) {
            // Low quality: pieces move through blocks.
            return FxSimulation.Host.super.move(s, radius, bounce, friction);
        }
        ClientLevel level = Minecraft.getInstance().level;
        // Three axes, and usually one more lookup to settle on the face it hit.
        if (level == null || !reserve(4)) {
            if (level != null && recordingChecks < 0) {
                return SKIPPED;
            }
            // No world, or the recording's checks are used up: the piece moves on freely.
            return FxSimulation.Host.super.move(s, radius, bounce, friction);
        }
        collidePos.set(origin.x + right.x * s[0] + up.x * s[1] + normal.x * s[2],
                origin.y + right.y * s[0] + up.y * s[1] + normal.y * s[2],
                origin.z + right.z * s[0] + up.z * s[1] + normal.z * s[2]);
        collideVel.set(right.x * s[3] + up.x * s[4] + normal.x * s[5], right.y * s[3] + up.y * s[4] + normal.y * s[5],
                right.z * s[3] + up.z * s[4] + normal.z * s[5]);
        int hits = BlockCollision.move(level, collidePos, collideVel, radius, bounce, friction);
        double dx = collidePos.x - origin.x;
        double dy = collidePos.y - origin.y;
        double dz = collidePos.z - origin.z;
        s[0] = (float) (dx * right.x + dy * right.y + dz * right.z);
        s[1] = (float) (dx * up.x + dy * up.y + dz * up.z);
        s[2] = (float) (dx * normal.x + dy * normal.y + dz * normal.z);
        Vector3d v = collideVel;
        s[3] = (float) (v.x * right.x + v.y * right.y + v.z * right.z);
        s[4] = (float) (v.x * up.x + v.y * up.y + v.z * up.z);
        s[5] = (float) (v.x * normal.x + v.y * normal.y + v.z * normal.z);
        return (hits & BlockCollision.LANDED) != 0 ? LANDED : hits != 0 ? HIT : MOVED;
    }

    @Override
    public int inside(float x, float y, float z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !params.fullQuality()) {
            return 0;
        }
        if (!reserve(1)) {
            return recordingChecks < 0 ? -1 : 0;
        }
        Vec3 at = toWorld(x, y, z);
        return BlockCollision.collides(level, at.x, at.y, at.z) ? 1 : 0;
    }

    // --- WaveDecor ---

    @Override
    public int overlayCount() {
        return overlays.size();
    }

    private FxDef.Overlay overlay(int k) {
        return def.appearance.overlays().get(overlays.get(k));
    }

    @Override
    public Identifier overlayTexture(int k) {
        return effect.textures.get(overlay(k).texture()).id;
    }

    @Override
    public Blend overlayBlend(int k) {
        return switch (overlay(k).blend()) {
            case "additive" -> Blend.ADDITIVE;
            case "multiply" -> Blend.MULTIPLY;
            default -> Blend.NORMAL;
        };
    }

    @Override
    public boolean overlayUv(ShardInstance shard, int k, float partialTick, float[] out) {
        if (!(shard.effectState instanceof Ref ref)) {
            return false;
        }
        FxDef.Overlay o = overlay(k);
        float seconds = (sim.time() - 1 + partialTick) / 20f;
        for (int c = 0; c < 3; c++) {
            out[c * 2] = ref.guiUv()[c * 2] * o.repeatU() + o.scrollU() * seconds;
            out[c * 2 + 1] = ref.guiUv()[c * 2 + 1] * o.repeatV() + o.scrollV() * seconds;
        }
        return true;
    }

    @Override
    public int overlayColor(ShardInstance shard, int k, float partialTick) {
        if (!(shard.effectState instanceof Ref ref)) {
            return 0;
        }
        FxSimulation.Piece p = ref.piece();
        if ((sim.color(p, partialTick) >>> 24) == 0) {
            return 0;
        }
        FxDef.Overlay o = overlay(k);
        float strength = sim.overlay(p, overlays.get(k), partialTick);
        int tint = o.color().at(sim.progress(p, partialTick));
        float alpha = (tint >>> 24) / 255f * strength;
        return (Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24) | (tint & 0xFFFFFF);
    }

    @Override
    public float scale(ShardInstance shard, float partialTick) {
        return shard.effectState instanceof Ref ref ? sim.scale(ref.piece(), partialTick) : 1f;
    }

    @Override
    public void sprites(float partialTick, SpriteSink sink) {
        for (FxSimulation.Sprite s : sim.sprites()) {
            FxTextures.Tex tex = effect.textures.get(s.emitter.sprite());
            if (tex == null || tex.gpu() == null) {
                continue;
            }
            float t = s.progress(partialTick);
            float x = s.px + (s.x - s.px) * partialTick;
            float y = s.py + (s.y - s.py) * partialTick;
            float z = s.pz + (s.z - s.pz) * partialTick;
            Vec3 at = toWorld(x, y, z);
            int color = s.emitter.color().at(t);
            float alpha = (color >>> 24) / 255f * s.emitter.alpha().at(t, s.rnd);
            int argb = (Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24) | (color & 0xFFFFFF);
            sink.sprite(tex.id, s.emitter.emissive(), at.x, at.y, at.z, s.emitter.size().at(t, s.rnd) / 2f, argb);
        }
    }
}
