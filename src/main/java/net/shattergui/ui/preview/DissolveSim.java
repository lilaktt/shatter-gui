package net.shattergui.ui.preview;

import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;

/**
 * Dissolve: squares of the configured size come loose in a noisy wave from an edge, a corner or the centre (origin,
 * duration): each glows cold white, drifts up and aside, shrinks and fades.
 */
final class DissolveSim extends Sim {
    private static final float GLOW = 2f;
    private static final float DRIFT = 12f;
    private float last;
    private final boolean calm;

    DissolveSim(Stage stage, ParamValues params, long seed) {
        super(stage, params, seed);
        calm = params.reduceMotion();
        float duration = params.i(Params.DISSOLVE_DURATION) * params.durationScale();
        int size = params.i(Params.DISSOLVE_PIXEL_SIZE);
        int cols = Math.clamp(Math.round(176f / size), 4, stage.small() ? 16 : 40);
        int rows = Math.clamp(Math.round(166f / size), 4, stage.small() ? 15 : 38);
        String origin = params.s(Params.DISSOLVE_ORIGIN);
        if ("random".equals(origin)) {
            origin = random.nextFloat() < 0.2f ? "center" : "edge";
        }
        // Edge or corner: a direction; centre: radial.
        float ox;
        float oy;
        boolean radial = "center".equals(origin);
        int side = random.nextInt(8);
        float[][] starts = {{0, 0.5f}, {1, 0.5f}, {0.5f, 0}, {0.5f, 1}, {0, 0}, {1, 0}, {0, 1}, {1, 1}};
        ox = radial ? 0.5f : starts[side][0];
        oy = radial ? 0.5f : starts[side][1];
        int noiseSeed = random.nextInt();
        java.util.List<Piece> made = squares(cols, rows);
        for (int i = 0; i < made.size(); i += 2) {
            Piece p = made.get(i);
            float fx = (p.cx - stage.sx()) / stage.sw();
            float fy = (p.cy - stage.sy()) / stage.sh();
            float dist;
            if (radial) {
                dist = (float) Math.hypot(fx - 0.5f, fy - 0.5f) / 0.7071f;
            } else if (side < 4) {
                dist = side < 2 ? Math.abs(fx - ox) : Math.abs(fy - oy);
            } else {
                dist = (Math.abs(fx - ox) + Math.abs(fy - oy)) / 2f;
            }
            float n = noise(fx * 6, fy * 6, noiseSeed);
            float release = dist * duration * 0.7f + n * duration * 0.3f + (random.nextFloat() - 0.5f) * 1.6f;
            float driftX = (random.nextFloat() - 0.5f) * 0.24f * stage.sw() * (calm ? 0.5f : 1f);
            float riseY = 0.25f * stage.sh() * (calm ? 0.5f : 1f);
            for (int k = 0; k < 2; k++) {
                Piece q = made.get(i + k);
                q.a = release;
                q.b = driftX;
                q.c = riseY;
                q.d = random.nextFloat() * 6.28f;
                pieces.add(q);
            }
            last = Math.max(last, release);
        }
        for (Piece p : pieces) {
            p.vx = p.cx;
            p.vy = p.cy;
        }
    }

    @Override
    protected void step(float dt) {
        for (Piece p : pieces) {
            if (p.dead) {
                continue;
            }
            float since = time - p.a;
            if (since < -GLOW) {
                continue;
            }
            float glow = since < 0 ? 1f - (-since / GLOW) : 1f - Math.min(1f, since / GLOW);
            p.overlay = (Math.round(140 * Math.max(0, glow)) << 24) | 0xCDE6FF;
            if (since <= 0) {
                continue;
            }
            float t = since / DRIFT;
            if (t >= 1f) {
                p.dead = true;
                continue;
            }
            float e = 1f - (1f - t) * (1f - t);
            float wobble = calm ? 0 : (float) Math.sin(p.d + since * 0.5f) * 0.02f * stage.sw();
            p.cx = p.vx + p.b * e + wobble;
            p.cy = p.vy - p.c * e;
            p.scale = 1f - 0.85f * e;
            p.alpha = t < 0.45f ? 1f : 1f - (t - 0.45f) / 0.55f;
        }
    }

    @Override
    public boolean done() {
        return time > last + DRIFT + 1;
    }
}
