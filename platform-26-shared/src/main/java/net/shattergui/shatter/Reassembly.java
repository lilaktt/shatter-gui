package net.shattergui.shatter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.shattergui.ScreenRules;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.GlassEffect;
import net.shattergui.effect.ShardTint;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.ShatterWave;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.platform.impl.Mc26;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class Reassembly {
    private static final float LIGHT_EASE_TICKS = 10f;
    private static final float KEEP_ON_RESCATTER = 0.5f;
    private static ShardManager.Batch active;
    private static String lastDecision = "none yet";

    private Reassembly() {
    }

    private static final class Start {
        final Vector3d pos;
        final Quaternionf rot;
        final float alpha;
        final int light;
        final boolean gone;

        Start(Vector3d pos, Quaternionf rot, float alpha, int light, boolean gone) {
            this.pos = pos;
            this.rot = rot;
            this.alpha = alpha;
            this.light = light;
            this.gone = gone;
        }
    }

    public static String lastDecision() {
        return lastDecision;
    }

    public static boolean running() {
        return active != null && ShardManager.batches().contains(active);
    }

    public static boolean running(Screen screen) {
        return running() && active.owner == screen;
    }

    public static ShardManager.Batch activeBatch() {
        return running() ? active : null;
    }

    public static int[] counts() {
        ShardManager.Batch batch = activeBatch();
        return batch == null ? new int[]{-1, -1} : new int[]{batch.shards.size(), batch.all.size()};
    }

    public static float progress() {
        ShardManager.Batch batch = activeBatch();
        return batch != null && batch.wave instanceof Wave wave ? wave.eased() : -1f;
    }

    public static boolean landedNow() {
        ShardManager.Batch batch = activeBatch();
        return batch != null && landed(batch);
    }

    public static int[] closeCounts() {
        List<ShardManager.Batch> batches = ShardManager.batches();
        for (int i = batches.size() - 1; i >= 0; i--) {
            ShardManager.Batch batch = batches.get(i);
            if (!batch.effect.opens() && batch.all != null) {
                return new int[]{batch.shards.size(), batch.all.size()};
            }
        }
        return new int[]{-1, -1};
    }

    public static double jumpFromStart() {
        ShardManager.Batch batch = activeBatch();
        if (batch == null || !(batch.wave instanceof Wave)) {
            return -1;
        }
        Vec3 camera = Mc26.camera().position();
        CameraSpace space = place(batch, batch.space(camera), camera);
        Vector3f at = new Vector3f();
        double worst = 0;
        for (ShardInstance shard : batch.shards) {
            if (shard.effectState instanceof Start start && !start.gone) {
                space.point(shard.pos.x, shard.pos.y, shard.pos.z, at);
                worst = Math.max(worst, start.pos.distance(camera.x + at.x, camera.y + at.y, camera.z + at.z));
            }
        }
        return worst;
    }

    public static double closeOpacity() {
        List<ShardManager.Batch> batches = ShardManager.batches();
        for (int i = batches.size() - 1; i >= 0; i--) {
            ShardManager.Batch batch = batches.get(i);
            if (!batch.effect.opens() && batch.all != null) {
                return meanOpacity(batch);
            }
        }
        return -1;
    }

    public static String key(Screen screen) {
        return String.join("|", ScreenRules.keys(screen));
    }

    public static boolean tryStart(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || screen == null) {
            return false;
        }
        String key = key(screen);
        ShardManager.Batch candidate = null;
        List<ShardManager.Batch> batches = ShardManager.batches();
        for (int i = batches.size() - 1; i >= 0; i--) {
            ShardManager.Batch batch = batches.get(i);
            if (!batch.preview && !batch.effect.opens() && batch.all != null && batch.screenKey != null) {
                candidate = batch;
                break;
            }
        }
        if (candidate == null) {
            return decide(false, "no pieces of a closed screen in the world");
        }
        if (!key.equals(candidate.screenKey)) {
            return decide(false, "a different screen (" + candidate.screenKey + " -> " + key + ")");
        }
        if (!candidate.effect.reassemblesOnReopen(candidate.params)) {
            return decide(false, "the closing effect " + candidate.effect.id() + " does not reassemble");
        }
        if (candidate.hurried()) {
            return decide(false, "the pieces are already being cleared");
        }
        if (candidate.anchor == null) {
            return decide(false, "the plane of the close is unknown");
        }
        if (candidate.slot.width() != Mc26.mainTarget().width || candidate.slot.height() != Mc26.mainTarget().height) {
            return decide(false, "the window size changed");
        }
        boolean spatial = SpatialGuiCompat.isShowing(screen);
        if (spatial != candidate.anchor.onSpatialPlane()) {
            return decide(false, "closed " + (candidate.anchor.onSpatialPlane() ? "on" : "off") + " the Spatial GUI plane, opened "
                    + (spatial ? "on" : "off") + " it");
        }
        Supplier<PlaneBasis> now = spatial ? SpatialGuiCompat.planeNow() : PlaneBasis::flatNow;
        if (now == null) {
            return decide(false, "the Spatial GUI plane is not available");
        }
        int alive = candidate.shards.size();
        int total = candidate.all.size();
        double opacity = meanOpacity(candidate);
        String measured = String.format(Locale.ROOT, "%d of %d pieces alive, mean opacity %.2f", alive, total, opacity);
        if (!Reassemble.eligible(alive, total, opacity)) {
            return decide(false, measured + " (needs " + Math.round(Reassemble.MIN_ALIVE * 100) + "% and "
                    + Reassemble.MIN_OPACITY + ")");
        }
        ParamValues current = ParamValues.current();
        int duration = Reassemble.duration(current.f(Params.OPEN_SPEED), current.durationScale());
        Gather effect = new Gather(candidate.effect);
        Wave wave = new Wave(duration, EffectClock.now(), candidate.slot.width(), candidate.slot.height());
        effect.wave = wave;
        ShardTint tint = new ShardTint();
        java.util.Set<ShardInstance> live = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        live.addAll(candidate.shards);
        for (ShardInstance shard : candidate.all) {
            if (live.contains(shard)) {
                tint.reset();
                candidate.effect.tint(shard, 0f, candidate.params, tint);
                float alpha = (tint.center >>> 24) / 255f * candidate.fade(0f);
                shard.effectState = new Start(new Vector3d(shard.pos), new Quaternionf(shard.rot), alpha, shard.light, false);
            } else {
                shard.effectState = new Start(new Vector3d(shard.home), new Quaternionf(), 0f, ShardInstance.FULL_BRIGHT, true);
            }
            shard.restart(Integer.MAX_VALUE / 2);
        }
        ShardManager.OpenAnchor anchor = new ShardManager.OpenAnchor(candidate.anchor.captured(), now, spatial);
        if (candidate.wave != null) {
            candidate.wave.dispose();
        }
        active = ShardManager.replace(candidate, new ArrayList<>(candidate.all), effect, wave, anchor, screen);
        if (spatial) {
            SpatialGuiCompat.cancelOpenAnimation();
        }
        OpenAssemble.start(screen, spatial);
        OpenAssemble.waveStarted(duration);
        ShatterProfiler.beginOpen(ScreenRules.keys(screen).getFirst(), "reassemble", measured);
        ShatterProfiler.openValue("open.reassemble", measured + ", " + duration + " ticks"
                + (spatial ? ", onto the Spatial GUI plane" : ""));
        return decide(true, measured + ", " + duration + " ticks");
    }

    private static boolean decide(boolean started, String why) {
        lastDecision = (started ? "reassemble: " : "usual opening: ") + why;
        return started;
    }

    private static double meanOpacity(ShardManager.Batch batch) {
        if (batch.shards.isEmpty()) {
            return 0;
        }
        ShardTint tint = new ShardTint();
        double sum = 0;
        for (ShardInstance shard : batch.shards) {
            tint.reset();
            batch.effect.tint(shard, 0f, batch.params, tint);
            sum += (tint.center >>> 24) / 255.0;
        }
        return sum / batch.shards.size() * batch.fade(0f);
    }

    public static CameraSpace place(ShardManager.Batch batch, CameraSpace space, Vec3 camera) {
        if (!(batch.wave instanceof Wave wave)) {
            return space;
        }
        float e = wave.eased();
        Quaternionf rot = new Quaternionf();
        for (ShardInstance shard : batch.shards) {
            if (!(shard.effectState instanceof Start start)) {
                continue;
            }
            shard.pos.set(start.pos).lerp(shard.home, e);
            shard.prevPos.set(shard.pos);
            start.rot.slerp(new Quaternionf(), e, rot);
            shard.rot.set(rot);
            shard.prevRot.set(rot);
            shard.light = Reassemble.light(start.light, e);
        }
        return space.towardsWorld(camera, 1f - e);
    }

    public static boolean gathers(ShardManager.Batch batch) {
        return batch.wave instanceof Wave;
    }

    public static boolean landed(ShardManager.Batch batch) {
        return batch.wave instanceof Wave wave && wave.landed;
    }

    public static boolean rescatter(Screen screen, String why) {
        if (!running(screen)) {
            return false;
        }
        ShardManager.Batch batch = active;
        active = null;
        OpenAssemble.stop();
        if (!(batch.wave instanceof Wave wave) || !(batch.effect instanceof Gather gather)
                || !(gather.close.base() instanceof GlassEffect)) {
            ShardManager.drop(batch);
            ShatterProfiler.openValue("open.reassemble.dropped", why);
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = Mc26.camera().position();
        CameraSpace space = place(batch, batch.space(camera), camera);
        Quaternionf turn = space.rotation(new Quaternionf());
        float e = wave.eased();
        List<ShardInstance> keep = new ArrayList<>();
        Vector3f at = new Vector3f();
        Vector3d center = new Vector3d();
        for (ShardInstance shard : batch.shards) {
            if (!(shard.effectState instanceof Start start)) {
                continue;
            }
            float alpha = start.gone ? Reassemble.appear(e) : Reassemble.alpha(start.alpha, e);
            if (alpha * wave.fade() < KEEP_ON_RESCATTER) {
                continue;
            }
            space.point(shard.pos.x, shard.pos.y, shard.pos.z, at);
            shard.pos.set(camera.x + at.x, camera.y + at.y, camera.z + at.z);
            shard.prevPos.set(shard.pos);
            turn.mul(shard.rot, shard.rot);
            shard.prevRot.set(shard.rot);
            shard.effectState = null;
            center.add(shard.pos);
            keep.add(shard);
        }
        if (keep.isEmpty()) {
            ShardManager.drop(batch);
            return false;
        }
        center.div(keep.size());
        RandomSource random = RandomSource.create();
        Vector3f forward = new Vector3f(Mc26.camera().forwardVector());
        GlassEffect.scatterAgain(keep, center, forward, batch.params, random, mc.level);
        ShardManager.Batch scattered = ShardManager.replace(batch, keep, gather.close, null, batch.closeAnchor, null);
        ShatterProfiler.value("close.reassemble.rescatter", why + ": " + keep.size() + " pieces break again from where they are");
        return scattered != null;
    }

    public static void abort(String why) {
        if (running()) {
            ShardManager.Batch batch = active;
            active = null;
            OpenAssemble.stop();
            ShardManager.drop(batch);
            ShatterProfiler.openValue("open.reassemble.cleared", why);
        }
    }

    static final class Gather implements ShatterEffect {
        final ShatterEffect close;
        Wave wave;

        Gather(ShatterEffect close) {
            this.close = close;
        }

        @Override
        public String id() {
            return "reassemble";
        }

        @Override
        public List<ShardInstance> createShards(ShardSource source) {
            return List.of();
        }

        @Override
        public void tick(ShardInstance shard, ClientLevel level, ParamValues params) {
        }

        @Override
        public void tint(ShardInstance shard, float partialTick, ParamValues params, ShardTint out) {
            float e = wave.eased();
            float alpha = 0f;
            if (shard.effectState instanceof Start start) {
                alpha = start.gone ? Reassemble.appear(e) : Reassemble.alpha(start.alpha, e);
            }
            alpha *= wave.fade();
            out.set((Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24) | 0xFFFFFF);
            out.lightAge = LIGHT_EASE_TICKS * (1f - e);
        }

        @Override
        public boolean opens() {
            return true;
        }

        @Override
        public boolean countsTowardShardLimit() {
            return false;
        }

        @Override
        public Boolean followCamera() {
            return Boolean.TRUE;
        }
    }

    static final class Wave implements ShatterWave {
        final int duration;
        final double start;
        final int width;
        final int height;
        boolean landed;
        double landedAt;

        Wave(int duration, double start, int width, int height) {
            this.duration = duration;
            this.start = start;
            this.width = width;
            this.height = height;
        }

        float eased() {
            return Reassemble.ease((float) ((EffectClock.now() - start) / duration));
        }

        float fade() {
            return landed ? Reassemble.fade(EffectClock.now() - landedAt) : 1f;
        }

        @Override
        public boolean tick(ClientLevel level, List<ShardInstance> liveShards, ParamValues params) {
            if (Mc26.mainTarget().width != width || Mc26.mainTarget().height != height) {
                liveShards.clear();
                OpenAssemble.stop();
                ShatterProfiler.openValue("open.reassemble.cleared", "the window size changed");
                return false;
            }
            double now = EffectClock.now();
            if (!landed && now - start >= duration) {
                landed = true;
                landedAt = now;
                OpenAssemble.waveDone();
            }
            if (landed && now - landedAt >= Reassemble.FADE_TICKS) {
                liveShards.clear();
                return false;
            }
            return true;
        }
    }
}
