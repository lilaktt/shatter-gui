package net.shattergui.ui.preview;

import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxSimulation;
import net.shattergui.fx.Fracture;
import net.shattergui.fx.game.DataEffect;
import net.shattergui.fx.game.FxTextures;
import net.shattergui.params.ParamValues;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * A schema-2 effect in the editor's preview: the same {@link FxSimulation} as in the world (same pieces, modules,
 * masks, timing), drawn in 2D through a camera one block in front of the interface, as in the game: every corner is
 * turned in 3D (spin and tumble, {@link FxSimulation#rotate}) and divided by its depth, so a card turned about its
 * vertical axis narrows with perspective and a door swings on its hinges instead of just squeezing. Overlays as
 * textured triangles, own sprites as textured squares, vanilla particles as coloured dots.
 */
public final class FxSim extends Sim implements FxSimulation.Host {
    /** One drawn triangle: its sim piece and offset from the piece centre (blocks, plane axes), and its GUI uv. */
    private record Tri(FxSimulation.Piece piece, float dx, float dy, float[] guiUv, Piece drawn) {
    }

    private final FxDef def;
    private final Function<String, FxTextures.Tex> textures;
    private final FxSimulation sim;
    private final List<Tri> tris = new ArrayList<>();
    private final float px;
    private float carry;

    /**
     * @param def      the effect
     * @param textures its textures by name (a missing one is null: masks fall back to noise, overlays and sprites are
     *                 not drawn)
     */
    public FxSim(Stage stage, ParamValues params, long seed, FxDef def, Function<String, FxTextures.Tex> textures) {
        this(stage, params, seed, def, textures, false, false);
    }

    /**
     * @param bundled  a shipped effect: its own settings (length, strength, particles) apply
     * @param reversed played backwards (opening a screen)
     */
    public FxSim(Stage stage, ParamValues params, long seed, FxDef def, Function<String, FxTextures.Tex> textures,
                 boolean bundled, boolean reversed) {
        super(stage, params, seed);
        def = def.forReducedMotion(params.reduceMotion());
        this.def = def;
        this.textures = textures;
        this.px = stage.blockPx();
        float width = stage.sw() / px;
        float height = stage.sh() / px;
        Random fxRandom = new Random(seed * 31 + 7);
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, stage.sw() / stage.sh(), 176, fxRandom);
        this.sim = DataEffect.simulate(def, bundled, reversed, cut, width, height, this, fxRandom, params);
        sim.untracked();
        for (FxSimulation.Piece piece : sim.pieces()) {
            float[] t = piece.triangles;
            for (int i = 0; i < t.length; i += 6) {
                Piece drawn = triangleAt(t[i], t[i + 1], t[i + 2], t[i + 3], t[i + 4], t[i + 5]);
                float tu = (t[i] + t[i + 2] + t[i + 4]) / 3;
                float tv = (t[i + 1] + t[i + 3] + t[i + 5]) / 3;
                tris.add(new Tri(piece, (tu - piece.u) * width, -(tv - piece.v) * height,
                        new float[]{t[i], t[i + 1], t[i + 2], t[i + 3], t[i + 4], t[i + 5]}, drawn));
                pieces.add(drawn);
            }
        }
        place(0f);
    }

    /** A drawn triangle from GUI fractions (uv = GUI fractions of the sample). */
    private Piece triangleAt(float u0, float v0, float u1, float v1, float u2, float v2) {
        Piece p = new Piece();
        float[] us = {u0, u1, u2};
        float[] vs = {v0, v1, v2};
        float cu = (u0 + u1 + u2) / 3;
        float cv = (v0 + v1 + v2) / 3;
        for (int k = 0; k < 3; k++) {
            p.u[k] = us[k];
            p.v[k] = vs[k];
            p.lx[k] = (us[k] - cu) * stage.sw();
            p.ly[k] = (vs[k] - cv) * stage.sh();
        }
        return p;
    }

    @Override
    protected void step(float dt) {
        carry += dt;
        while (carry >= 1f) {
            carry -= 1f;
            sim.tick();
        }
    }

    /** Moves the drawn triangles to the simulation's state at the given partial tick. */
    private void place(float partial) {
        float[] edge = sim.edgeOffset(partial);
        float mosaic = sim.mosaic(partial);
        for (Tri tri : tris) {
            FxSimulation.Piece p = tri.piece();
            Piece d = tri.drawn();
            for (int c = 0; c < 3; c++) {
                // Coarse pixels: the texture corners move towards the piece's centre.
                d.u[c] = tri.guiUv()[c * 2] + (p.u - tri.guiUv()[c * 2]) * mosaic;
                d.v[c] = tri.guiUv()[c * 2 + 1] + (p.v - tri.guiUv()[c * 2 + 1]) * mosaic;
            }
            float x = p.px + (p.x - p.px) * partial + edge[0];
            float y = p.py + (p.y - p.py) * partial + edge[1];
            float z = p.pz + (p.z - p.pz) * partial;
            float angle = p.pAngle + (p.angle - p.pAngle) * partial;
            float tilt = p.pTilt + (p.tilt - p.pTilt) * partial;
            float scale = sim.scale(p, partial);
            if (d.sx == null) {
                d.sx = new float[3];
                d.sy = new float[3];
            }
            for (int c = 0; c < 3; c++) {
                // Corner relative to the piece centre (blocks, y up), turned in 3D, then seen from one block away.
                float cx = (tri.dx() + d.lx[c] / px) * scale;
                float cy = (tri.dy() - d.ly[c] / px) * scale;
                float[] v = FxSimulation.rotate(cx, cy, 0, p.tiltAxis, tilt, angle);
                float persp = 1f / Math.max(0.3f, 1f - (z + v[2]));
                d.sx[c] = stage.centerX() + (x + v[0]) * px * persp;
                d.sy[c] = stage.centerY() - (y + v[1]) * px * persp;
            }
            float persp = 1f / Math.max(0.3f, 1f - z);
            d.cx = (d.sx[0] + d.sx[1] + d.sx[2]) / 3;
            d.cy = (d.sy[0] + d.sy[1] + d.sy[2]) / 3;
            d.angle = -angle;
            d.flip = tilt;
            d.scale = scale * persp;
            int color = sim.color(p, partial);
            d.alpha = (color >>> 24) / 255f;
            d.tint = color & 0xFFFFFF;
            float glow = sim.emissive(p, partial);
            d.overlay = glow > 0.01f ? (Math.round(Math.min(1f, glow) * 0.6f * 255) << 24)
                    | (sim.emissiveColor(p, partial) & 0xFFFFFF) : 0;
            d.dead = d.alpha <= 0.004f;
        }
    }

    @Override
    public void render(Ui ui, float ox, float oy) {
        place(carry);
        super.render(ui, ox, oy);
        overlays(ui, ox, oy);
        sprites(ui, ox, oy);
    }

    private void overlays(Ui ui, float ox, float oy) {
        List<FxDef.Overlay> overlays = def.appearance.overlays();
        float seconds = (sim.time() - 1 + carry) / 20f;
        for (int k = 0; k < overlays.size(); k++) {
            FxDef.Overlay o = overlays.get(k);
            FxTextures.Tex tex = textures.apply(o.texture());
            if (tex == null || tex.gpu() == null) {
                continue;
            }
            float[] data = new float[tris.size() * 12];
            int[] colors = new int[tris.size() * 3];
            int n = 0;
            for (Tri tri : tris) {
                Piece d = tri.drawn();
                if (d.dead) {
                    continue;
                }
                float strength = sim.overlay(tri.piece(), k, carry) * d.alpha;
                // The preview has normal blending only: additive and multiply are approximated by opacity.
                strength *= "normal".equals(o.blend()) ? 1f : 0.6f;
                if (strength <= 0.01f) {
                    continue;
                }
                int tint = o.color().at(sim.progress(tri.piece(), carry));
                int color = (Math.round(Math.min(1f, strength * ((tint >>> 24) / 255f)) * 255) << 24) | (tint & 0xFFFFFF);
                for (int c = 0; c < 3; c++) {
                    data[n * 4] = ox + d.sx[c];
                    data[n * 4 + 1] = oy + d.sy[c];
                    data[n * 4 + 2] = tri.guiUv()[c * 2] * o.repeatU() + o.scrollU() * seconds;
                    data[n * 4 + 3] = tri.guiUv()[c * 2 + 1] * o.repeatV() + o.scrollV() * seconds;
                    colors[n] = color;
                    n++;
                }
            }
            Draw.triangles(ui, tex.gpu(), data, colors, n);
        }
    }

    private void sprites(Ui ui, float ox, float oy) {
        float[] edge = {0, 0};
        for (FxSimulation.Sprite s : sim.sprites()) {
            FxTextures.Tex tex = textures.apply(s.emitter.sprite());
            if (tex == null || tex.gpu() == null) {
                continue;
            }
            float t = s.progress(carry);
            float x = s.px + (s.x - s.px) * carry + edge[0];
            float y = s.py + (s.y - s.py) * carry + edge[1];
            float z = s.pz + (s.z - s.pz) * carry;
            float persp = 1f / Math.max(0.3f, 1f - z);
            float half = s.emitter.size().at(t, s.rnd) / 2f * px * persp;
            int color = s.emitter.color().at(t);
            float alpha = (color >>> 24) / 255f * s.emitter.alpha().at(t, s.rnd);
            if (alpha <= 0.01f || half < 0.3f) {
                continue;
            }
            float cx = ox + stage.centerX() + x * px * persp;
            float cy = oy + stage.centerY() - y * px * persp;
            Draw.texture(ui, tex.gpu(), cx - half, cy - half, half * 2, half * 2,
                    (Math.round(Math.min(1f, alpha) * 255) << 24) | (color & 0xFFFFFF));
        }
    }

    @Override
    public boolean done() {
        return !sim.alive() && particles.isEmpty();
    }

    /** The simulation (the editor reads its time, pieces and sprites). */
    public FxSimulation simulation() {
        return sim;
    }

    /** Vanilla particles shown as dots right now. */
    public int dotCount() {
        return particles.size();
    }

    /** Triangles drawn for the pieces. */
    public int triangleCount() {
        return tris.size();
    }

    // --- FxSimulation.Host ---

    @Override
    public float[] point(FxDef.Point.Kind kind) {
        if (kind == FxDef.Point.Kind.SOURCE && stage.hasSource()) {
            return new float[]{(stage.sourceX() - stage.centerX()) / px, (stage.centerY() - stage.sourceY()) / px, 0.3f};
        }
        if (kind == FxDef.Point.Kind.PLAYER) {
            return new float[]{0, -(stage.sh() * 0.45f) / px, 0.5f};
        }
        return null;
    }

    @Override
    public float sampleMask(String texture, float u, float v) {
        FxTextures.Tex tex = textures.apply(texture);
        return tex == null ? Float.NaN : tex.sample(u, v);
    }

    @Override
    public float[] gravity() {
        return new float[]{0, -1, 0};
    }

    @Override
    public boolean hasSprite(String texture) {
        FxTextures.Tex tex = textures.apply(texture);
        return tex != null && tex.gpu() != null;
    }

    @Override
    public void spawnParticle(FxDef.Emitter emitter, float x, float y, float z, float vx, float vy, float vz, float size) {
        float persp = 1f / Math.max(0.3f, 1f - z);
        int color = ExtrasSim.colorOf(emitter.particle().substring(emitter.particle().indexOf(':') + 1));
        Particle p = particle(stage.centerX() + x * px * persp, stage.centerY() - y * px * persp, vx * px, -vy * px,
                Math.max(1f, size * px * 0.6f), 12 + random.nextFloat() * 8, color,
                net.shattergui.ui.Theme.mix(0xFF000000 | color, 0xFF000000, 0.4f) & 0xFFFFFF, 0.9f);
        p.rise = 0.01f * stage.sw() / 176f;
    }

    @Override
    public void playSound(String sound, float volume, float pitch) {
        // The editor's preview is silent.
    }

    /** Colliding pieces land on the preview's floor (the only "block" here), with the same bounce and friction. */
    @Override
    public int move(float[] s, float radius, float bounce, float friction) {
        s[0] += s[3];
        s[1] += s[4];
        s[2] += s[5];
        float floor = (stage.centerY() - stage.floor()) / px + radius;
        if (s[1] >= floor || s[4] >= 0) {
            return MOVED;
        }
        s[1] = floor;
        s[4] = -s[4] * bounce;
        s[3] *= 1 - friction;
        s[5] *= 1 - friction;
        if (Math.abs(s[4]) < 0.02f) {
            s[4] = 0;
        }
        return LANDED;
    }
}
