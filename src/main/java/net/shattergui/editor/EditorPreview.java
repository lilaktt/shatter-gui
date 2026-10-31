package net.shattergui.editor;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.shattergui.ShatterGuiClient;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.game.FxTextures;
import net.shattergui.params.ParamValues;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Ui;
import net.shattergui.ui.preview.FxSim;
import net.shattergui.ui.preview.SampleImage;
import net.shattergui.ui.preview.Sim;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Plays the effect being edited on a sample interface, with a clock the editor controls: play, pause, seek, speed and
 * loop. The same engine as in the world ({@link FxSim}), with a fixed seed so the same moment always looks the same.
 * Seeking back rebuilds the run and catches up to the moment within a small time budget per frame (never a freeze;
 * a long effect may take a few frames to arrive).
 */
public final class EditorPreview {
    private static final long BUDGET_NANOS = 6_000_000;
    /** Ticks shown after the effect's end (particles finishing). */
    static final int TAIL = 24;
    static final float[] SPEEDS = {0.25f, 0.5f, 1f, 1.5f, 2f};

    private final Supplier<ParamValues> values;
    private final Function<String, FxTextures.Tex> textures;
    private FxDef def;
    private FxSim sim;
    private Sim.Stage stage;
    private int stageW;
    private int stageH;
    private float time;
    private boolean playing = true;
    private boolean loop = true;
    private float speed = 1f;
    /** Plays the effect backwards, as it opens an interface (with the opening's own, shorter length). */
    private boolean opening;
    private SampleImage.Kind sample = SampleImage.Kind.CHEST;
    private long seed = 7;
    private String failure;
    private int peakSprites;

    EditorPreview(Supplier<ParamValues> values, Function<String, FxTextures.Tex> textures) {
        this.values = values;
        this.textures = textures;
    }

    // --- state ---

    void setDef(FxDef value) {
        if (value != def) {
            def = value;
            sim = null;
        }
    }

    FxDef def() {
        return def;
    }

    /** Throws the run away (a texture changed). */
    void invalidate() {
        sim = null;
    }

    public boolean playing() {
        return playing;
    }

    public void setPlaying(boolean value) {
        if (value && time >= length()) {
            time = 0;
        }
        playing = value;
    }

    boolean loop() {
        return loop;
    }

    void setLoop(boolean value) {
        loop = value;
    }

    float speed() {
        return speed;
    }

    public boolean opening() {
        return opening;
    }

    public void setOpening(boolean value) {
        if (value != opening) {
            opening = value;
            sim = null;
            time = 0;
        }
    }

    void setSpeed(float value) {
        speed = Math.clamp(value, 0.25f, 2f);
    }

    public SampleImage.Kind sample() {
        return sample;
    }

    void setSample(SampleImage.Kind value) {
        if (value != sample) {
            sample = value;
            stage = null;
            sim = null;
        }
    }

    /** A new random run (other piece shapes). */
    void reseed() {
        seed++;
        sim = null;
    }

    public float time() {
        return time;
    }

    public void seek(float ticks) {
        time = Math.clamp(ticks, 0, length());
    }

    /**
     * The effect's own duration with the player's speed settings (ticks); backwards the opening's length, which
     * openSpeed shortens ({@link net.shattergui.params.OpenTiming}).
     */
    public float duration() {
        if (def == null) {
            return 40;
        }
        int natural = Math.max(2, Math.round(def.duration * values.get().durationScale()));
        return opening ? net.shattergui.params.OpenTiming.playback(natural,
                net.shattergui.params.OpenTiming.quick(def.id, false, def.duration), values.get()) : natural;
    }

    /** Duration plus the tail in which particles finish (an opening has none: the interface is then shown). */
    public float length() {
        return duration() + (opening ? 4 : TAIL);
    }

    String failure() {
        return failure;
    }

    FxSim sim() {
        return sim;
    }

    public int peakSprites() {
        return peakSprites;
    }

    /** Advances the clock by real time (seconds). */
    void frame(float seconds) {
        if (!playing || def == null) {
            return;
        }
        time += seconds * 20f * speed;
        if (time >= length()) {
            if (loop) {
                time = 0;
                peakSprites = 0;
            } else {
                time = length();
                playing = false;
            }
        }
    }

    // --- drawing ---

    void render(Ui ui, int x, int y, int w, int h, boolean decorated) {
        if (w < 16 || h < 16) {
            // Not laid out yet (or squeezed to nothing): a stage this small would make no sense.
            return;
        }
        if (stage == null || w != stageW || h != stageH) {
            stageW = w;
            stageH = h;
            stage = stage(w, h, sample);
            sim = null;
        }
        ui.pushClip(x, y, w, h);
        if (decorated) {
            backdrop(ui, x, y, w, h);
        } else {
            ui.g.fillGradient(x, y, x + w, y + h, 0xFF1E2130, 0xFF14161E);
        }
        if (stage.hasSource() && decorated) {
            int size = Math.max(8, Math.min(16, w / 14));
            float s = size / 16f;
            ui.g.pose().pushMatrix();
            ui.g.pose().translate(x + stage.sourceX() - size / 2f, y + stage.sourceY() - size / 2f);
            ui.g.pose().scale(s, s);
            ui.g.item(new ItemStack(switch (sample) {
                case FURNACE -> Items.FURNACE;
                case CRAFTING -> Items.CRAFTING_TABLE;
                default -> Items.CHEST;
            }), 0, 0);
            ui.g.pose().popMatrix();
        }
        if (def == null) {
            drawSample(ui, x, y);
        } else {
            try {
                catchUp();
                if (sim != null) {
                    sim.render(ui, x, y);
                    peakSprites = Math.max(peakSprites, sim.simulation().sprites().size() + sim.dotCount());
                }
                failure = null;
            } catch (RuntimeException e) {
                if (failure == null) {
                    ShatterGuiClient.LOGGER.warn("Effect editor preview failed", e);
                }
                failure = String.valueOf(e.getMessage());
                sim = null;
                drawSample(ui, x, y);
            }
        }
        ui.popClip();
    }

    private void catchUp() {
        if (sim != null && sim.time() > time + 0.01f) {
            sim = null;
        }
        if (sim == null) {
            sim = new FxSim(stage, values.get(), seed, def, textures, false, opening);
        }
        long start = System.nanoTime();
        while (sim.time() < time - 1e-3f) {
            sim.advance(Math.min(2f, time - sim.time()));
            if (System.nanoTime() - start > BUDGET_NANOS) {
                break;
            }
        }
    }

    private void drawSample(Ui ui, float ox, float oy) {
        float x0 = ox + stage.sx();
        float y0 = oy + stage.sy();
        SampleImage.Image image = SampleImage.image(sample);
        Draw.texture(ui, image.texture(), x0, y0, stage.sw(), stage.sh(), 0xFFFFFFFF, image.u0(), image.v0(), image.u1(),
                image.v1(), image.premultiplied());
    }

    private static void backdrop(Ui ui, int x, int y, int w, int h) {
        int horizon = y + Math.round(h * 0.62f);
        ui.g.fillGradient(x, y, x + w, horizon, 0xFF7FA6D9, 0xFFB9CCE6);
        ui.g.fillGradient(x, horizon, x + w, y + h, 0xFF4E7A3A, 0xFF2F4A24);
        for (int i = 0; i < 12; i++) {
            int gx = x + (i * 37 + 11) % Math.max(1, w);
            int gy = horizon + 3 + (i * 13) % Math.max(1, (y + h - horizon - 4));
            Draw.rect(ui, gx, gy, 3, 1, 0x30FFFFFF);
        }
        Draw.rect(ui, x, horizon, w, 1, 0x40FFFFFF);
    }

    /** Sample size and place for a box; the block it was opened from sits below it (not for the inventory). */
    static Sim.Stage stage(int w, int h, SampleImage.Kind sample) {
        float aspect = (float) SampleImage.WIDTH / SampleImage.HEIGHT;
        float sw = Math.min(w * 0.62f, h * 0.68f * aspect);
        float sh = sw / aspect;
        float sx = (w - sw) / 2f;
        float sy = (h - sh) * 0.32f;
        float sourceX = w / 2f;
        float sourceY = Math.min(h - 6f, sy + sh + (h - sy - sh) * 0.55f);
        return new Sim.Stage(w, h, sx, sy, sw, sh, sourceX, sourceY, h - 3f, sample != SampleImage.Kind.INVENTORY,
                w < 120, sample);
    }
}
