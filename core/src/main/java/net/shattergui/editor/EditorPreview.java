package net.shattergui.editor;

import net.shattergui.ShatterMod;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.game.FxTextures;
import net.shattergui.params.ParamValues;
import net.shattergui.platform.UiItem;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Ui;
import net.shattergui.ui.preview.FxSim;
import net.shattergui.ui.preview.SampleImage;
import net.shattergui.ui.preview.Sim;

import java.util.function.Function;
import java.util.function.Supplier;

public final class EditorPreview {
    private static final long BUDGET_NANOS = 6_000_000;
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
    private boolean opening;
    private SampleImage.Kind sample = SampleImage.Kind.CHEST;
    private long seed = 7;
    private String failure;
    private int peakSprites;

    EditorPreview(Supplier<ParamValues> values, Function<String, FxTextures.Tex> textures) {
        this.values = values;
        this.textures = textures;
    }

    void setDef(FxDef value) {
        if (value != def) {
            def = value;
            sim = null;
        }
    }

    FxDef def() {
        return def;
    }

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

    public float duration() {
        if (def == null) {
            return 40;
        }
        int natural = Math.max(2, Math.round(def.duration * values.get().durationScale()));
        return opening ? net.shattergui.params.OpenTiming.playback(natural,
                net.shattergui.params.OpenTiming.quick(def.id, false, def.duration), values.get()) : natural;
    }

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

    void render(Ui ui, int x, int y, int w, int h, boolean decorated) {
        if (w < 16 || h < 16) {
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
            ui.g.item(switch (sample) {
                case FURNACE -> UiItem.FURNACE;
                case CRAFTING -> UiItem.CRAFTING_TABLE;
                default -> UiItem.CHEST;
            }, 0, 0);
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
                    ShatterMod.LOGGER.warn("Effect editor preview failed", e);
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
