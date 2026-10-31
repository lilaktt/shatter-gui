package net.shattergui.ui.preview;

import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;

/**
 * Vortex: 14×9 shards come loose in a short wave from the centre, spiral around the point (spiral turns, pull,
 * shard spin) faster and closer, turn violet then white and shrink to nothing in the point (centre, the chest, or in
 * front of the player). Nothing follows.
 */
final class VortexSim extends Sim {
    private static final int VIOLET = 0xAA50FF;
    private final float flight;
    private final float turns;
    private final float pull;
    private final float spin;
    private final float tx;
    private final float ty;
    private float last;

    VortexSim(Stage stage, ParamValues params, long seed) {
        super(stage, params, seed);
        boolean calm = params.reduceMotion();
        float duration = params.i(Params.VORTEX_DURATION) * params.durationScale();
        flight = Math.max(1f, duration * 0.75f);
        turns = params.f(Params.VORTEX_TURNS) * (calm ? 0.5f : 1f);
        pull = params.f(Params.VORTEX_PULL);
        spin = params.f(Params.VORTEX_SPIN) * (calm ? 0.3f : 1f);
        String target = params.s(Params.VORTEX_TARGET);
        if ("source".equals(target) && stage.hasSource()) {
            tx = stage.sourceX();
            ty = stage.sourceY();
        } else if ("center".equals(target)) {
            tx = stage.centerX();
            ty = stage.centerY();
        } else {
            // In front of the player: below the centre, towards the viewer.
            tx = stage.w() / 2;
            ty = stage.sy() + stage.sh() * 0.85f;
        }
        float shardScale = (float) Math.sqrt(params.shardScale());
        int cols = Math.max(3, Math.round(14 * shardScale));
        int rows = Math.max(2, Math.round(9 * shardScale));
        float halfDiagonal = (float) Math.hypot(stage.sw() / 2, stage.sh() / 2);
        for (Piece p : shatter(cols, rows, 0.7f)) {
            float fromCenter = (float) Math.hypot(p.cx - stage.centerX(), p.cy - stage.centerY()) / halfDiagonal;
            p.delay = fromCenter * duration * 0.25f + random.nextFloat() * 1.5f;
            p.a = (float) Math.hypot(p.cx - tx, p.cy - ty);
            p.b = (float) Math.atan2(p.cy - ty, p.cx - tx);
            last = Math.max(last, p.delay + flight);
            pieces.add(p);
        }
    }

    @Override
    protected void step(float dt) {
        for (Piece p : pieces) {
            if (p.dead) {
                continue;
            }
            float since = time - p.delay;
            if (since <= 0) {
                continue;
            }
            float t = Math.min(1f, since / flight);
            float e = (float) Math.pow(t, 1.6);
            double angle = p.b + turns * Math.PI * 2 * e;
            double radius = p.a * Math.pow(1.0 - e, pull);
            p.cx = tx + (float) (Math.cos(angle) * radius);
            p.cy = ty + (float) (Math.sin(angle) * radius);
            p.angle += spin * (0.3f + e) * dt;
            int color = e < 0.6f ? net.shattergui.ui.Theme.mix(0xFFFFFFFF, 0xFF000000 | VIOLET, e / 0.6f)
                    : net.shattergui.ui.Theme.mix(0xFF000000 | VIOLET, 0xFFFFFFFF, (e - 0.6f) / 0.4f);
            p.tint = color & 0xFFFFFF;
            p.overlay = (Math.round(255 * e * e * 0.7f * 0.6f) << 24) | (color & 0xFFFFFF);
            p.scale = 1f - e;
            p.alpha = 1f - smoothstep(0.85f, 1f, e);
            if (t >= 1f) {
                p.dead = true;
            }
        }
    }

    @Override
    public boolean done() {
        return time > last + 0.5f;
    }
}
