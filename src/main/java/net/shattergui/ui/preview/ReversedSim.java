package net.shattergui.ui.preview;

import net.shattergui.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A code effect's 2D stand-in played backwards, as {@link net.shattergui.effect.ReversedEffect} does in the world: the
 * forward run is recorded tick by tick (at most 40 ticks of it), then shown from its end to its start in the opening's
 * length ({@link net.shattergui.params.OpenTiming}), the pieces fading in over the first part. Particles are not replayed.
 */
final class ReversedSim extends Sim {
    private static final int WINDOW_TICKS = 40;
    private static final float FADE_IN = 0.3f;

    private final Sim forward;
    /** frames[tick][piece]: cx, cy, angle, flip, scale, alpha, tint, overlay, dead. */
    private final List<float[][]> frames = new ArrayList<>();
    private final int window;
    private final int length;

    ReversedSim(Sim forward) {
        super(forward.stage, forward.params, 0);
        this.forward = forward;
        frames.add(snapshot());
        for (int t = 1; t <= WINDOW_TICKS && !forward.done(); t++) {
            forward.advance(1f);
            frames.add(snapshot());
        }
        forward.particles.clear();
        window = frames.size() - 1;
        length = net.shattergui.params.OpenTiming.playback(window, false, forward.params);
        apply(0f);
    }

    private float[][] snapshot() {
        float[][] out = new float[forward.pieces.size()][];
        for (int i = 0; i < out.length; i++) {
            Piece p = forward.pieces.get(i);
            out[i] = new float[]{p.cx, p.cy, p.angle, p.flip, p.scale, p.alpha, Float.intBitsToFloat(p.tint),
                    Float.intBitsToFloat(p.overlay), p.dead ? 1 : 0};
        }
        return out;
    }

    /** Puts the forward pieces where the forward run was at {@code window·(1 − shown/length)}. */
    private void apply(float shown) {
        float t = Math.clamp(shown / length, 0f, 1f);
        float at = window * (1f - t);
        int i0 = Math.min(window, (int) Math.floor(at));
        int i1 = Math.min(window, i0 + 1);
        float f = at - i0;
        float fade = Math.min(1f, t / FADE_IN);
        fade = fade * fade * (3 - 2 * fade);
        float[][] a = frames.get(i0);
        float[][] b = frames.get(i1);
        for (int i = 0; i < a.length; i++) {
            Piece p = forward.pieces.get(i);
            float[] x = a[i];
            float[] y = b[i];
            p.cx = x[0] + (y[0] - x[0]) * f;
            p.cy = x[1] + (y[1] - x[1]) * f;
            p.angle = x[2] + (y[2] - x[2]) * f;
            p.flip = x[3] + (y[3] - x[3]) * f;
            p.scale = x[4] + (y[4] - x[4]) * f;
            p.alpha = (x[5] + (y[5] - x[5]) * f) * fade;
            p.tint = Float.floatToRawIntBits(f < 0.5f ? x[6] : y[6]);
            p.overlay = Float.floatToRawIntBits(f < 0.5f ? x[7] : y[7]);
            p.dead = x[8] > 0 && y[8] > 0;
        }
    }

    @Override
    protected void step(float dt) {
        apply(time);
    }

    @Override
    public boolean done() {
        return time >= length + 1;
    }

    @Override
    public void render(Ui ui, float ox, float oy) {
        forward.render(ui, ox, oy);
    }
}
