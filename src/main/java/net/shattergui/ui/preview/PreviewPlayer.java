package net.shattergui.ui.preview;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Ui;
import net.shattergui.ui.UiClock;

import java.util.function.Supplier;

/**
 * Plays a {@link Scene} in a box: the sample opens (with the open effect), stays a moment, closes with the close
 * effect, then a short pause — and again if looping. Rebuilt whenever the scene's settings change ({@link Scene#signature})
 * and the player is set to {@link #autoRestart}. The big preview draws a small world (sky, ground, the chest the
 * interface came from); thumbnails only a dark backdrop.
 */
public final class PreviewPlayer {
    private enum Phase {
        OPEN, HOLD, CLOSE, REST, IDLE
    }

    private static final float FADE_IN = 4f;
    private final Supplier<Scene> scene;
    private final boolean decorated;
    private boolean loop;
    private boolean autoRestart = true;
    private float hold;
    private float rest;
    private Phase phase = Phase.OPEN;
    private float phaseTime;
    private Sim sim;
    private String signature = "";
    private int lastW;
    private int lastH;
    private long seed = 1;
    private Sim.Stage stage;
    private long lastFrame = -1;

    public PreviewPlayer(Supplier<Scene> scene, boolean decorated, boolean loop) {
        this.scene = scene;
        this.decorated = decorated;
        this.loop = loop;
        this.hold = decorated ? 16f : 22f;
        this.rest = decorated ? 14f : 5f;
    }

    public void loop(boolean value) {
        boolean was = loop;
        loop = value;
        if (value && !was && phase == Phase.IDLE) {
            restart();
        }
    }

    /** Restart automatically when the scene's settings change. */
    public void autoRestart(boolean value) {
        autoRestart = value;
    }

    public void restart() {
        seed++;
        phase = Phase.OPEN;
        phaseTime = 0;
        sim = null;
    }

    public boolean playing() {
        return phase != Phase.IDLE;
    }

    public void render(Ui ui, int x, int y, int w, int h) {
        Scene current = scene.get();
        if (current == null) {
            return;
        }
        String sig = current.signature();
        if (!sig.equals(signature)) {
            boolean first = signature.isEmpty();
            signature = sig;
            if (first || autoRestart) {
                restart();
            }
        }
        if (w != lastW || h != lastH || stage == null) {
            lastW = w;
            lastH = h;
            stage = stage(w, h, current);
            sim = null;
            if (phase == Phase.CLOSE || phase == Phase.OPEN) {
                phaseTime = 0;
            }
        }
        if (stage.hasSource() != current.hasSource()) {
            stage = stage(w, h, current);
        }
        // Shown in two places in one frame (a card and a drop-down): advance only once.
        if (lastFrame != UiClock.frameNumber()) {
            lastFrame = UiClock.frameNumber();
            advance(current, UiClock.dt() * 20f);
        }
        ui.pushClip(x, y, w, h);
        if (decorated) {
            backdrop(ui, x, y, w, h);
        } else {
            ui.g.fillGradient(x, y, x + w, y + h, 0xFF1E2130, 0xFF14161E);
        }
        if (current.hasSource() && (decorated || Scene.OPEN_FLY.equals(current.open()) || "fly".equals(current.base()))) {
            int size = decorated ? Math.max(8, Math.min(16, w / 14)) : Math.max(6, Math.min(12, h / 4));
            float s = size / 16f;
            ui.g.pose().pushMatrix();
            ui.g.pose().translate(x + stage.sourceX() - size / 2f, y + stage.sourceY() - size / 2f);
            ui.g.pose().scale(s, s);
            ui.g.item(new ItemStack(Items.CHEST), 0, 0);
            ui.g.pose().popMatrix();
        }
        switch (phase) {
            case OPEN -> {
                if (sim != null) {
                    sim.render(ui, x, y);
                } else {
                    drawSample(ui, x, y, Math.min(1f, phaseTime / FADE_IN));
                }
            }
            case HOLD -> drawSample(ui, x, y, 1f);
            case CLOSE -> {
                if (sim != null) {
                    sim.render(ui, x, y);
                }
            }
            default -> {
            }
        }
        ui.popClip();
    }

    private void advance(Scene scene, float ticks) {
        phaseTime += ticks;
        switch (phase) {
            case OPEN -> {
                if (scene.open() == null) {
                    phase = Phase.HOLD;
                    phaseTime = 0;
                    return;
                }
                if (sim == null && !Scene.OPEN_FLY.equals(scene.open())) {
                    sim = Sims.opening(scene.open(), stage, scene.params(), seed);
                }
                if (sim != null && !Scene.OPEN_FLY.equals(scene.open())) {
                    sim.advance(ticks);
                    if (sim.done() || phaseTime > 20 * 20) {
                        next(Phase.HOLD);
                    }
                } else if (Scene.OPEN_FLY.equals(scene.open()) && scene.hasSource()) {
                    if (sim == null) {
                        sim = new FlySim(stage, scene.params(), seed, FlySim.Mode.OPEN);
                    }
                    sim.advance(ticks);
                    if (sim.done()) {
                        next(Phase.HOLD);
                    }
                } else if (phaseTime >= FADE_IN) {
                    next(Phase.HOLD);
                }
            }
            case HOLD -> {
                if (phaseTime >= hold) {
                    if (scene.base() == null) {
                        next(Phase.REST);
                    } else {
                        next(Phase.CLOSE);
                        sim = Sims.create(scene, stage, seed);
                    }
                }
            }
            case CLOSE -> {
                if (sim == null) {
                    sim = Sims.create(scene, stage, seed);
                }
                sim.advance(ticks);
                if (sim.done() || phaseTime > 20 * 20) {
                    next(Phase.REST);
                }
            }
            case REST -> {
                if (phaseTime >= rest) {
                    if (loop) {
                        restart();
                    } else {
                        next(Phase.IDLE);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void next(Phase value) {
        phase = value;
        phaseTime = 0;
        sim = null;
    }

    private void drawSample(Ui ui, float ox, float oy, float alpha) {
        float x0 = ox + stage.sx();
        float y0 = oy + stage.sy();
        float x1 = x0 + stage.sw();
        float y1 = y0 + stage.sh();
        SampleImage.Image image = SampleImage.image(stage.sample());
        float l = image.u(0);
        float r = image.u(1);
        float top = image.v(0);
        float bottom = image.v(1);
        float[] data = {x0, y0, l, top, x0, y1, l, bottom, x1, y1, r, bottom, x0, y0, l, top, x1, y1, r, bottom, x1, y0, r, top};
        int c = (Math.round(alpha * 255) << 24) | 0xFFFFFF;
        Draw.triangles(ui, image.texture(), image.premultiplied(), data, new int[]{c, c, c, c, c, c}, 6);
    }

    private void backdrop(Ui ui, int x, int y, int w, int h) {
        int horizon = y + Math.round(h * 0.62f);
        ui.g.fillGradient(x, y, x + w, horizon, 0xFF7FA6D9, 0xFFB9CCE6);
        ui.g.fillGradient(x, horizon, x + w, y + h, 0xFF4E7A3A, 0xFF2F4A24);
        // A few grass tiles for depth.
        for (int i = 0; i < 12; i++) {
            int gx = x + (i * 37 + 11) % Math.max(1, w);
            int gy = horizon + 3 + (i * 13) % Math.max(1, (y + h - horizon - 4));
            Draw.rect(ui, gx, gy, 3, 1, 0x30FFFFFF);
        }
        Draw.rect(ui, x, horizon, w, 1, 0x40FFFFFF);
    }

    /** Sample size and place for a box; the chest sits below it on the ground. */
    private static Sim.Stage stage(int w, int h, Scene scene) {
        float aspect = (float) SampleImage.WIDTH / SampleImage.HEIGHT;
        float sw = Math.min(w * 0.62f, h * 0.68f * aspect);
        float sh = sw / aspect;
        float sx = (w - sw) / 2f;
        float sy = (h - sh) * 0.32f;
        float sourceX = w / 2f;
        float sourceY = Math.min(h - 6f, sy + sh + (h - sy - sh) * 0.55f);
        return new Sim.Stage(w, h, sx, sy, sw, sh, sourceX, sourceY, h - 3f, scene.hasSource(), w < 120, SampleImage.Kind.CHEST);
    }

    /** The current phase as text (for tests and logs). */
    public String describe() {
        return phase + " " + Math.round(phaseTime) + "t" + (sim != null ? " " + sim.getClass().getSimpleName() : "")
                + (stage != null ? " " + lastW + "x" + lastH : " no-stage"); // not shown: tests and logs
    }
}
