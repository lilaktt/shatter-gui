package net.shattergui.ui.preview;

import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;

/**
 * Fly: the whole sample flies into the chest (shrinking to a speck, with a small puff), or — without a source — out
 * through an edge (shrinking and fading), or fades in place where edge flights are not allowed. Also the open effect
 * ({@link #open}): out of the chest, growing into place.
 */
final class FlySim extends Sim {
    enum Mode {
        INTO_SOURCE, THROUGH_EDGE, IN_PLACE, OPEN
    }

    private final Mode mode;
    private final float duration;
    private final float edgeX;
    private final float edgeY;
    private boolean puffed;

    FlySim(Stage stage, ParamValues params, long seed, Mode mode) {
        super(stage, params, seed);
        this.mode = mode;
        this.duration = Math.max(1f, params.i(Params.FLY_DURATION) * params.durationScale());
        String direction = params.s(Params.FLY_EDGE_DIRECTION);
        float[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        int pick = switch (direction) {
            case "top" -> 0;
            case "bottom" -> 1;
            case "left" -> 2;
            case "right" -> 3;
            default -> random.nextInt(8);
        };
        edgeX = dirs[pick][0];
        edgeY = dirs[pick][1];
        pieces.addAll(whole());
        apply(mode == Mode.OPEN ? 0f : 0f);
    }

    @Override
    protected void step(float dt) {
        apply(Math.min(1f, time / duration));
        if (mode == Mode.INTO_SOURCE && time >= duration && !puffed) {
            puffed = true;
            float scale = stage.sw() / 176f;
            int n = Math.round(8 * params.particleScale());
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Particle p = particle(stage.sourceX(), stage.sourceY(), (float) Math.cos(a) * 0.5f * scale,
                        (float) Math.sin(a) * 0.4f * scale - 0.2f * scale, 1.5f * scale + 0.5f, 10 + random.nextFloat() * 6,
                        0xF0F0F0, 0xB0B0B0, 0.8f);
                p.grow = 0.05f * scale;
            }
        }
    }

    private void apply(float t) {
        float cx0 = stage.centerX();
        float cy0 = stage.centerY();
        float cx;
        float cy;
        float scale;
        float alpha = 1f;
        switch (mode) {
            case OPEN -> {
                float e = 1f - (1f - t) * (1f - t);
                cx = stage.sourceX() + (cx0 - stage.sourceX()) * e;
                cy = stage.sourceY() + (cy0 - stage.sourceY()) * e;
                scale = 0.03f + 0.97f * e;
                alpha = Math.min(1f, t * 4f);
            }
            case INTO_SOURCE -> {
                float e = t * t;
                cx = cx0 + (stage.sourceX() - cx0) * e;
                cy = cy0 + (stage.sourceY() - cy0) * e;
                scale = 1f - 0.97f * e;
            }
            case THROUGH_EDGE -> {
                float e = t * t;
                cx = cx0 + edgeX * stage.w() * 0.9f * e;
                cy = cy0 + edgeY * stage.h() * 0.9f * e;
                scale = 1f - 0.4f * e;
                alpha = 1f - smoothstep(0.35f, 1f, t);
            }
            default -> {
                cx = cx0;
                cy = cy0;
                scale = 1f - 0.15f * t;
                alpha = 1f - t;
            }
        }
        for (Piece p : pieces) {
            p.cx = cx;
            p.cy = cy;
            p.scale = scale;
            p.alpha = alpha;
            p.dead = mode != Mode.OPEN && t >= 1f;
        }
    }

    @Override
    public boolean done() {
        return time >= duration && particles.isEmpty();
    }
}
