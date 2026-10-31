package net.shattergui.ui.preview;

import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;

/**
 * Glass: the sample breaks into the configured grid, shards burst from the centre (burst), tumble (spin), fall
 * (gravity), slow down (air drag), bounce on the floor (bounce) and fade (lifetime, fade start), like the real effect.
 */
final class GlassSim extends Sim {
    private final float gravity;
    private final float drag;
    private final float bounce;

    GlassSim(Stage stage, ParamValues params, long seed) {
        super(stage, params, seed);
        boolean calm = params.reduceMotion();
        float burst = params.f(Params.GLASS_BURST) * (calm ? 0.5f : 1f);
        float spin = params.f(Params.GLASS_SPIN) * (calm ? 0.3f : 1f);
        float shardScale = (float) Math.sqrt(params.shardScale());
        int cols = Math.clamp(Math.round(params.i(Params.GLASS_COLUMNS) * shardScale), 1, stage.small() ? 10 : 22);
        int rows = Math.clamp(Math.round(params.i(Params.GLASS_ROWS) * shardScale), 1, stage.small() ? 8 : 18);
        float px = stage.blockPx();
        gravity = params.f(Params.GLASS_GRAVITY) * px;
        drag = params.f(Params.GLASS_AIR_DRAG);
        bounce = params.f(Params.GLASS_BOUNCE);
        float lifetime = params.i(Params.GLASS_LIFETIME) * params.durationScale();
        float fadeStart = params.f(Params.GLASS_FADE_START);
        float halfDiagonal = (float) Math.hypot(stage.sw() / 2, stage.sh() / 2);
        for (Piece p : shatter(cols, rows, 0.8f)) {
            float dx = p.cx - stage.centerX();
            float dy = p.cy - stage.centerY();
            float dist = (float) Math.hypot(dx, dy);
            float nx = dist > 1e-3f ? dx / dist : 0;
            float ny = dist > 1e-3f ? dy / dist : 0;
            float radial = burst * (0.5f + dist / halfDiagonal) * 0.6f * px;
            float noise = burst * 0.15f * px;
            p.vx = nx * radial + (float) random.nextGaussian() * noise;
            p.vy = ny * radial - burst * 0.25f * px + (float) random.nextGaussian() * noise;
            p.spin = spin * (0.3f + random.nextFloat() * 0.7f) * (random.nextBoolean() ? 1 : -1);
            p.flipSpeed = spin * (0.3f + random.nextFloat() * 0.7f) * (random.nextBoolean() ? 1 : -1);
            p.life = Math.max(1f, lifetime * (0.8f + random.nextFloat() * 0.4f));
            p.a = fadeStart;
            // Away from the viewer along the view (the interface is about a block in front of the camera).
            p.b = burst * 0.5f;
            p.wx = p.cx - stage.centerX();
            p.wy = p.cy - stage.centerY();
            pieces.add(p);
        }
    }

    @Override
    protected void step(float dt) {
        float keep = (float) Math.pow(drag, dt);
        for (Piece p : pieces) {
            if (p.dead) {
                continue;
            }
            p.age += dt;
            if (p.age >= p.life) {
                p.dead = true;
                continue;
            }
            p.vy += gravity * dt;
            p.vx *= keep;
            p.vy *= keep;
            p.spin *= keep;
            p.flipSpeed *= keep;
            p.wx += p.vx * dt;
            p.wy += p.vy * dt;
            p.angle += p.spin * dt;
            p.flip += p.flipSpeed * dt;
            p.c += p.b * dt;
            p.b *= keep;
            // Perspective: farther away is smaller and moves less on screen.
            float depth = 1f + p.c;
            p.scale = 1f / depth;
            p.cx = stage.centerX() + p.wx / depth;
            p.cy = stage.centerY() + p.wy / depth;
            if (p.cy > stage.floor() && p.vy > 0) {
                p.wy = (stage.floor() - stage.centerY()) * depth;
                p.cy = stage.floor();
                p.vy = -p.vy * bounce;
                p.vx *= 0.7f;
                p.spin *= 0.7f;
                p.flipSpeed *= 0.5f;
            }
            float t = p.age / p.life;
            p.alpha = t < p.a ? 1f : 1f - smoothstep(p.a, 1f, t);
        }
    }

    @Override
    public boolean done() {
        for (Piece p : pieces) {
            if (!p.dead) {
                return false;
            }
        }
        return true;
    }
}
