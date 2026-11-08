package net.shattergui.fx.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.ShardTint;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.ShatterWave;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxSimulation;
import net.shattergui.fx.Fracture;
import net.shattergui.params.ParamValues;
import net.shattergui.platform.EffectCatalog;
import net.shattergui.shatter.ImageRect;
import net.shattergui.shatter.ShardInstance;
import net.shattergui.shatter.ShardPlane;
import net.shattergui.shatter.ShardSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class DataEffect implements ShatterEffect, EffectCatalog.Data, EffectCatalog.Built {
    public final FxDef def;
    public final FxTextures textures;
    public final String origin;
    public final boolean bundled;
    public final boolean reversed;
    final Map<String, SimpleParticleType> particles;
    final Map<String, SoundEvent> sounds;
    private DataWave pending;
    private DataEffect reversedView;

    private DataEffect(FxDef def, FxTextures textures, String origin, boolean bundled, boolean reversed,
                       Map<String, SimpleParticleType> particles, Map<String, SoundEvent> sounds) {
        this.def = def;
        this.textures = textures;
        this.origin = origin;
        this.bundled = bundled;
        this.reversed = reversed;
        this.particles = particles;
        this.sounds = sounds;
    }

    @Override
    public Boolean followCamera() {
        return def.followCamera;
    }

    public DataEffect reversed() {
        if (reversed) {
            return this;
        }
        if (reversedView == null) {
            reversedView = new DataEffect(def, textures, origin, bundled, true, particles, sounds);
        }
        return reversedView;
    }

    static DataEffect link(FxDef def, FxTextures textures, String origin, List<String> warnings) {
        return link(def, textures, origin, false, warnings);
    }

    static DataEffect link(FxDef def, FxTextures textures, String origin, boolean bundled, List<String> warnings) {
        DataEffect effect = new DataEffect(def, textures, origin, bundled, false, new HashMap<>(), new HashMap<>());
        for (FxDef.Emitter emitter : def.emitters) {
            if (emitter.particle() == null || effect.particles.containsKey(emitter.particle())) {
                continue;
            }
            Identifier id = Identifier.tryParse(emitter.particle());
            if (id != null && BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null) instanceof SimpleParticleType type) {
                effect.particles.put(emitter.particle(), type);
            } else {
                warnings.add("particle \"" + emitter.particle() + "\" is not a known particle without options, its emitter is skipped");
            }
        }
        for (FxDef.Sound sound : def.sounds) {
            Identifier id = Identifier.tryParse(sound.sound());
            if (id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                effect.sounds.put(sound.sound(), BuiltInRegistries.SOUND_EVENT.getValue(id));
            } else {
                warnings.add("sound \"" + sound.sound() + "\" is not a known sound, skipped");
            }
        }
        return effect;
    }

    @Override
    public String id() {
        return def.id;
    }

    @Override
    public boolean opens() {
        return reversed;
    }

    @Override
    public boolean countsTowardShardLimit() {
        return false;
    }

    @Override
    public List<ShardInstance> createShards(ShardSource source) {
        long start = ShatterProfiler.now();
        ShardPlane plane = source.plane();
        ImageRect b = source.bounds();
        double yc = (b.y0() + b.y1()) / 2;
        double xc = (b.x0() + b.x1()) / 2;
        double width = plane.toWorld(b.x0(), yc).distance(plane.toWorld(b.x1(), yc));
        double height = plane.toWorld(xc, b.y0()).distance(plane.toWorld(xc, b.y1()));
        float aspect = (float) (width / Math.max(1e-6, height));
        float guiPixelsW = (float) (b.width() * Minecraft.getInstance().getWindow().getGuiScaledWidth());
        Random random = new Random(source.random().nextLong());
        FxDef run = def.forReducedMotion(source.params().reduceMotion());
        List<Fracture.Piece> cut = Fracture.cut(run.fracture.withDensity(source.params().shardScale()), aspect, guiPixelsW,
                random);
        int triangles = cut.stream().mapToInt(Fracture.Piece::triangleCount).sum();
        ShatterProfiler.step(String.format(Locale.ROOT, "fx.%s.fracture (%s, %d pieces, %d triangles)", def.id,
                run.fracture.mode(), cut.size(), triangles), start);
        long simStart = ShatterProfiler.now();
        pending = new DataWave(this, run, source, cut, (float) width, (float) height, random);
        ShatterProfiler.step(String.format(Locale.ROOT, "fx.%s.setup (trigger %s, %d layers, %d emitters, %d textures, %d ticks)",
                def.id, def.trigger.type(), def.layers.size(), def.emitters.size(), textures.count(),
                pending.sim().duration), simStart);
        return pending.shards();
    }

    @Override
    public ShatterWave createWave(ShardSource source, List<ShardInstance> shards) {
        DataWave wave = pending;
        pending = null;
        return wave;
    }

    @Override
    public void tick(ShardInstance shard, ClientLevel level, ParamValues params) {
        if (shard.effectState instanceof DataWave.Ref ref) {
            ref.wave().advance();
            ref.wave().place(shard, ref);
        } else {
            shard.tickStatic();
        }
    }

    @Override
    public void tint(ShardInstance shard, float partialTick, ParamValues params, ShardTint out) {
        if (!(shard.effectState instanceof DataWave.Ref ref)) {
            return;
        }
        FxSimulation sim = ref.wave().sim();
        FxSimulation.Piece piece = ref.piece();
        out.set(sim.color(piece, partialTick));
        out.unlit = sim.def.appearance.fullbright();
        out.scale = sim.scale(piece, partialTick);
        if (reversed) {
            out.lightAge = Math.max(0f, sim.shownForward(partialTick));
        }
        float glow = sim.emissive(piece, partialTick);
        if (glow > 0f && (out.center >>> 24) != 0) {
            out.glow = glow * ((out.center >>> 24) / 255f);
            out.glowColor = sim.emissiveColor(piece, partialTick) & 0xFFFFFF;
        }
    }

    public static FxSimulation simulate(FxDef def, boolean bundled, boolean reversed, List<Fracture.Piece> cut, float width,
                                        float height, FxSimulation.Host host, Random random, ParamValues params) {
        return FxRun.simulate(def, bundled, reversed, cut, width, height, host, random, params);
    }

    @Override
    public FxDef def() {
        return def;
    }

    @Override
    public FxTextures textures() {
        return textures;
    }

    @Override
    public boolean bundled() {
        return bundled;
    }

    @Override
    public String origin() {
        return origin;
    }

    void release() {
        textures.release();
    }

    public List<String> loadedTextures() {
        List<String> names = new ArrayList<>();
        for (String name : def.textures.keySet()) {
            if (textures.get(name) != null) {
                names.add(name);
            }
        }
        return names;
    }
}
