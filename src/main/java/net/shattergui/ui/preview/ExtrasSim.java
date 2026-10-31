package net.shattergui.ui.preview;

import net.shattergui.custom.CustomEffectDef;
import net.shattergui.ui.Ui;

/**
 * A custom effect: its base effect plus its extra particles (at the start, during, at the end), drawn as dots in a
 * colour that suggests the particle (flames orange, smoke grey, end rods white, …).
 */
final class ExtrasSim extends Sim {
    private final Sim base;
    private final CustomEffectDef def;
    private boolean started;
    private boolean ended;

    ExtrasSim(Sim base, CustomEffectDef def, long seed) {
        super(base.stage, base.params, seed);
        this.base = base;
        this.def = def;
    }

    @Override
    protected void step(float dt) {
        base.advance(dt);
        float scale = stage.sw() / 176f;
        if (!started) {
            started = true;
            burst(CustomEffectDef.Stage.START, scale);
        }
        for (CustomEffectDef.ParticleSpec spec : def.particles()) {
            if (spec.stage() == CustomEffectDef.Stage.DURING && !base.done()) {
                int n = count(spec.density() * params.particleScale() * 0.6f, dt);
                for (int i = 0; i < n; i++) {
                    Piece p = livePiece();
                    if (p != null) {
                        spawn(spec, p.cx, p.cy, scale);
                    }
                }
            }
        }
        if (!ended && base.done()) {
            ended = true;
            burst(CustomEffectDef.Stage.END, scale);
        }
    }

    private Piece livePiece() {
        if (base.pieces.isEmpty()) {
            return null;
        }
        for (int tries = 0; tries < 6; tries++) {
            Piece p = base.pieces.get(random.nextInt(base.pieces.size()));
            if (!p.dead && p.alpha > 0.1f) {
                return p;
            }
        }
        return null;
    }

    private void burst(CustomEffectDef.Stage when, float scale) {
        for (CustomEffectDef.ParticleSpec spec : def.particles()) {
            if (spec.stage() == when) {
                int n = Math.min(30, Math.round(spec.density() * 4 * params.particleScale()));
                for (int i = 0; i < n; i++) {
                    spawn(spec, stage.centerX() + (random.nextFloat() - 0.5f) * stage.sw() * 0.3f,
                            stage.centerY() + (random.nextFloat() - 0.5f) * stage.sh() * 0.3f, scale);
                }
            }
        }
    }

    private void spawn(CustomEffectDef.ParticleSpec spec, float x, float y, float scale) {
        int color = colorOf(spec.particle().getPath());
        Particle p = particle(x, y, (random.nextFloat() - 0.5f) * 0.6f * scale, -(0.2f + random.nextFloat() * 0.5f) * scale,
                (1.2f + random.nextFloat()) * scale + 0.5f, 10 + random.nextFloat() * 10, color,
                net.shattergui.ui.Theme.mix(0xFF000000 | color, 0xFF000000, 0.4f) & 0xFFFFFF, 0.9f);
        p.grow = path(spec).contains("smoke") || path(spec).contains("cloud") ? 0.1f * scale : -0.03f * scale;
    }

    private static String path(CustomEffectDef.ParticleSpec spec) {
        return spec.particle().getPath();
    }

    /** A colour that suggests the vanilla particle. */
    static int colorOf(String path) {
        if (path.contains("soul")) {
            return 0x5FE3E8;
        }
        if (path.contains("flame") || path.contains("lava") || path.contains("ember")) {
            return 0xFFA030;
        }
        if (path.contains("smoke") || path.contains("cloud") || path.contains("ash") || path.contains("poof")) {
            return 0x8C8884;
        }
        if (path.contains("end_rod") || path.contains("firework") || path.contains("snow") || path.contains("white")) {
            return 0xF4F0FF;
        }
        if (path.contains("heart") || path.contains("crimson") || path.contains("angry")) {
            return 0xE8333A;
        }
        if (path.contains("happy") || path.contains("composter") || path.contains("spore") && !path.contains("crimson")) {
            return 0x4EDB5A;
        }
        if (path.contains("portal") || path.contains("witch") || path.contains("dragon") || path.contains("reverse")) {
            return 0xB45CFF;
        }
        if (path.contains("bubble") || path.contains("splash") || path.contains("drip") || path.contains("rain")) {
            return 0x4F9BFF;
        }
        if (path.contains("glow") || path.contains("wax") || path.contains("electric")) {
            return 0xFFF27A;
        }
        int h = path.hashCode();
        return 0x808080 | (h & 0x7F7F7F);
    }

    @Override
    public void render(Ui ui, float ox, float oy) {
        base.render(ui, ox, oy);
        super.render(ui, ox, oy);
    }

    @Override
    public boolean done() {
        return base.done() && ended && particles.isEmpty();
    }
}
