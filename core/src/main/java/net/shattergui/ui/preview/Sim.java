package net.shattergui.ui.preview;

import net.shattergui.params.ParamValues;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class Sim {
    public record Stage(float w, float h, float sx, float sy, float sw, float sh, float sourceX, float sourceY,
                        float floor, boolean hasSource, boolean small, SampleImage.Kind sample) {
        public float blockPx() {
            return sw / 0.9f;
        }

        public float centerX() {
            return sx + sw / 2;
        }

        public float centerY() {
            return sy + sh / 2;
        }
    }

    protected static final class Piece {
        final float[] lx = new float[3];
        final float[] ly = new float[3];
        final float[] u = new float[3];
        final float[] v = new float[3];
        float cx;
        float cy;
        float vx;
        float vy;
        float angle;
        float spin;
        float flip;
        float flipSpeed;
        float scale = 1f;
        float alpha = 1f;
        int tint = 0xFFFFFF;
        int overlay;
        float age;
        float life = Float.MAX_VALUE;
        float delay;
        float a;
        float b;
        float c;
        float d;
        float wx;
        float wy;
        boolean dead;
        float[] sx;
        float[] sy;
    }

    protected static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float size;
        float grow;
        float age;
        float life;
        int color;
        int colorEnd;
        float alpha;
        float rise;
    }

    protected final Stage stage;
    protected final ParamValues params;
    protected final Random random;
    protected final List<Piece> pieces = new ArrayList<>();
    protected final List<Particle> particles = new ArrayList<>();
    protected float time;
    private float[] buffer = new float[0];
    private int[] colors = new int[0];

    protected Sim(Stage stage, ParamValues params, long seed) {
        this.stage = stage;
        this.params = params;
        this.random = new Random(seed);
    }

    public final void advance(float ticks) {
        while (ticks > 0) {
            float step = Math.min(ticks, 0.5f);
            time += step;
            step(step);
            for (int i = particles.size() - 1; i >= 0; i--) {
                Particle p = particles.get(i);
                p.age += step;
                if (p.age >= p.life) {
                    particles.remove(i);
                    continue;
                }
                p.x += p.vx * step;
                p.y += p.vy * step;
                p.vy -= p.rise * step;
                p.size += p.grow * step;
            }
            ticks -= step;
        }
    }

    protected abstract void step(float dt);

    public abstract boolean done();

    public float time() {
        return time;
    }

    protected List<Piece> shatter(int cols, int rows, float jitter) {
        float[][] gx = new float[cols + 1][rows + 1];
        float[][] gy = new float[cols + 1][rows + 1];
        for (int i = 0; i <= cols; i++) {
            for (int j = 0; j <= rows; j++) {
                float fx = (float) i / cols;
                float fy = (float) j / rows;
                if (i > 0 && i < cols) {
                    fx += (random.nextFloat() - 0.5f) * jitter / cols;
                }
                if (j > 0 && j < rows) {
                    fy += (random.nextFloat() - 0.5f) * jitter / rows;
                }
                gx[i][j] = fx;
                gy[i][j] = fy;
            }
        }
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                float[] ax = {gx[i][j], gx[i + 1][j], gx[i + 1][j + 1], gx[i][j + 1]};
                float[] ay = {gy[i][j], gy[i + 1][j], gy[i + 1][j + 1], gy[i][j + 1]};
                if (random.nextBoolean()) {
                    out.add(triangle(ax[0], ay[0], ax[1], ay[1], ax[2], ay[2]));
                    out.add(triangle(ax[0], ay[0], ax[2], ay[2], ax[3], ay[3]));
                } else {
                    out.add(triangle(ax[0], ay[0], ax[1], ay[1], ax[3], ay[3]));
                    out.add(triangle(ax[1], ay[1], ax[2], ay[2], ax[3], ay[3]));
                }
            }
        }
        return out;
    }

    protected List<Piece> squares(int cols, int rows) {
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                float x0 = (float) i / cols;
                float x1 = (float) (i + 1) / cols;
                float y0 = (float) j / rows;
                float y1 = (float) (j + 1) / rows;
                Piece p1 = triangle(x0, y0, x1, y0, x1, y1);
                Piece p2 = triangle(x0, y0, x1, y1, x0, y1);
                recenter(p1, (x0 + x1) / 2, (y0 + y1) / 2);
                recenter(p2, (x0 + x1) / 2, (y0 + y1) / 2);
                out.add(p1);
                out.add(p2);
            }
        }
        return out;
    }

    protected List<Piece> whole() {
        Piece p1 = triangle(0, 0, 1, 0, 1, 1);
        Piece p2 = triangle(0, 0, 1, 1, 0, 1);
        recenter(p1, 0.5f, 0.5f);
        recenter(p2, 0.5f, 0.5f);
        return List.of(p1, p2);
    }

    private Piece triangle(float x0, float y0, float x1, float y1, float x2, float y2) {
        Piece p = new Piece();
        float[] fx = {x0, x1, x2};
        float[] fy = {y0, y1, y2};
        float mx = (x0 + x1 + x2) / 3f;
        float my = (y0 + y1 + y2) / 3f;
        for (int k = 0; k < 3; k++) {
            p.u[k] = fx[k];
            p.v[k] = fy[k];
        }
        recenter(p, mx, my);
        return p;
    }

    private void recenter(Piece p, float fx, float fy) {
        p.cx = stage.sx + fx * stage.sw;
        p.cy = stage.sy + fy * stage.sh;
        for (int k = 0; k < 3; k++) {
            p.lx[k] = stage.sx + p.u[k] * stage.sw - p.cx;
            p.ly[k] = stage.sy + p.v[k] * stage.sh - p.cy;
        }
    }

    protected Particle particle(float x, float y, float vx, float vy, float size, float life, int color, int colorEnd,
                                float alpha) {
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.size = size;
        p.life = life;
        p.color = color;
        p.colorEnd = colorEnd;
        p.alpha = alpha;
        if (particles.size() < 220) {
            particles.add(p);
        }
        return p;
    }

    protected int count(float perTick, float dt) {
        float n = perTick * dt;
        int whole = (int) n;
        return whole + (random.nextFloat() < n - whole ? 1 : 0);
    }

    public void render(Ui ui, float ox, float oy) {
        int live = 0;
        for (Piece p : pieces) {
            if (!p.dead && p.alpha > 0.01f && p.scale > 0.01f) {
                live++;
            }
        }
        if (buffer.length < live * 12) {
            buffer = new float[live * 12 + 48];
            colors = new int[live * 3 + 12];
        }
        int n = 0;
        boolean overlays = false;
        SampleImage.Image image = SampleImage.image(stage.sample());
        for (Piece p : pieces) {
            if (p.dead || p.alpha <= 0.01f || p.scale <= 0.01f) {
                continue;
            }
            float cos = (float) Math.cos(p.angle);
            float sin = (float) Math.sin(p.angle);
            float fx = (float) Math.cos(p.flip);
            int color = (Math.round(Math.clamp(p.alpha, 0f, 1f) * 255) << 24) | (p.tint & 0xFFFFFF);
            for (int k = 0; k < 3; k++) {
                if (p.sx != null) {
                    buffer[n * 4] = ox + p.sx[k];
                    buffer[n * 4 + 1] = oy + p.sy[k];
                } else {
                    float lx = p.lx[k] * p.scale * fx;
                    float ly = p.ly[k] * p.scale;
                    buffer[n * 4] = ox + p.cx + lx * cos - ly * sin;
                    buffer[n * 4 + 1] = oy + p.cy + lx * sin + ly * cos;
                }
                buffer[n * 4 + 2] = image.u(p.u[k]);
                buffer[n * 4 + 3] = image.v(p.v[k]);
                colors[n] = color;
                n++;
            }
            if ((p.overlay >>> 24) != 0) {
                overlays = true;
            }
        }
        Draw.triangles(ui, image.texture(), image.premultiplied(), buffer, colors, n);
        if (overlays) {
            int m = 0;
            for (Piece p : pieces) {
                if (p.dead || p.alpha <= 0.01f || p.scale <= 0.01f) {
                    continue;
                }
                int o = p.overlay;
                for (int k = 0; k < 3; k++) {
                    if ((o >>> 24) != 0) {
                        colors[m] = Theme.fade(o, p.alpha);
                    } else {
                        colors[m] = 0;
                    }
                    m++;
                }
            }
            Draw.triangles(ui, null, buffer, colors, n);
        }
        for (Particle p : particles) {
            float t = p.age / p.life;
            int color = Theme.mix(p.color, p.colorEnd, t);
            float alpha = p.alpha * (t < 0.15f ? t / 0.15f : 1f - Math.max(0, (t - 0.5f) / 0.5f));
            int size = Math.max(1, Math.round(p.size));
            Draw.rect(ui, Math.round(ox + p.x - size / 2f), Math.round(oy + p.y - size / 2f), size, size,
                    Theme.fade(0xFF000000 | color, alpha));
        }
    }

    protected static float smoothstep(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    protected static float noise(float x, float y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;
        float u = xf * xf * (3 - 2 * xf);
        float v = yf * yf * (3 - 2 * yf);
        float a = hash(xi, yi, seed);
        float b = hash(xi + 1, yi, seed);
        float c = hash(xi, yi + 1, seed);
        float d = hash(xi + 1, yi + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }
}
