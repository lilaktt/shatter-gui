package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.ui.Button;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Dropdown;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;
import net.shattergui.ui.Widget;
import net.shattergui.ui.preview.RealPreview;
import net.shattergui.ui.preview.SampleImage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The live preview in the middle: the effect on a sample interface (chest, furnace or inventory) and its transport —
 * play/pause (Space), a time bar to scrub (pauses), the time, speed 0.25×–2×, loop and "another break" (new random
 * pieces). With errors in the effect it keeps showing the last state that was valid and says so.
 */
final class PreviewPane extends Container {
    private final FxEditor editor;
    private final Button play;
    private final Scrubber scrubber = new Scrubber();
    private final Dropdown<Float> speed;
    private final Button loop;
    private final Dropdown<SampleImage.Kind> sample;
    private final Button reseed;
    private final Button opening;
    /** Quick switch: real vanilla screens ↔ the simple painted samples. */
    private final Button real;
    private final Box box = new Box();
    private int controlsY;

    PreviewPane(FxEditor editor) {
        this.editor = editor;
        add(box);
        play = add(new Button(null, null, Button.Kind.SECONDARY, editor::togglePlay) {
            @Override
            protected void draw(Ui ui) {
                tooltip(EditorUi.tr(editor.preview.playing() ? "preview.pause" : "preview.play"), EditorUi.tr("preview.play.desc"));
                super.draw(ui);
                // The icon follows the state.
                int ix = x + (w - 9) / 2;
                int iy = y + (h - 9) / 2;
                Draw.icon(ui, editor.preview.playing() ? Icon.PAUSE : Icon.PLAY, ix, iy, 1, Theme.TEXT);
            }
        });
        add(scrubber);
        scrubber.tooltip(EditorUi.tr("preview.scrub"), EditorUi.tr("preview.scrub.desc"));
        speed = add(new Dropdown<>(PreviewPane::speeds, () -> editor.preview.speed(), v -> editor.preview.setSpeed(v)));
        speed.tooltip(EditorUi.tr("preview.speed"), EditorUi.tr("preview.speed.desc"));
        loop = add(new Button(null, Icon.LOOP, Button.Kind.GHOST, () -> editor.preview.setLoop(!editor.preview.loop())) {
            @Override
            protected void draw(Ui ui) {
                selected(editor.preview.loop());
                super.draw(ui);
            }
        });
        loop.tooltip(EditorUi.tr("preview.loop"), EditorUi.tr("preview.loop.desc"));
        sample = add(new Dropdown<>(PreviewPane::samples, () -> editor.preview.sample(), editor::setSample));
        sample.tooltip(EditorUi.tr("preview.sample"), EditorUi.tr("preview.sample.desc"));
        reseed = add(Button.icon(Icon.REPLAY, EditorUi.tr("preview.reseed"), editor.preview::reseed));
        reseed.tooltip(EditorUi.tr("preview.reseed"), EditorUi.tr("preview.reseed.desc"));
        opening = add(new Button(null, Icon.IMPORT, Button.Kind.GHOST,
                () -> editor.preview.setOpening(!editor.preview.opening())) {
            @Override
            protected void draw(Ui ui) {
                selected(editor.preview.opening());
                super.draw(ui);
            }
        });
        opening.tooltip(EditorUi.tr("preview.opening"), EditorUi.tr("preview.opening.desc"));
        real = add(new Button(null, Icon.IMAGE, Button.Kind.GHOST, RealPreview::toggleQuick) {
            @Override
            protected void draw(Ui ui) {
                selected(RealPreview.enabled());
                tooltip(EditorUi.tr(RealPreview.enabled() ? "preview.real.on" : "preview.real.off"),
                        EditorUi.tr(RealPreview.available() ? "preview.real.desc" : "preview.real.no_world"));
                super.draw(ui);
            }
        });
    }

    private static List<Dropdown.Option<Float>> speeds() {
        List<Dropdown.Option<Float>> out = new ArrayList<>();
        for (float s : EditorPreview.SPEEDS) {
            out.add(new Dropdown.Option<>(s, Component.literal(String.format(Locale.ROOT, "%s×", s == (int) s ? Integer.toString((int) s)
                    : Float.toString(s)))));
        }
        return out;
    }

    private static List<Dropdown.Option<SampleImage.Kind>> samples() {
        List<Dropdown.Option<SampleImage.Kind>> out = new ArrayList<>();
        for (SampleImage.Kind kind : SampleImage.Kind.values()) {
            out.add(new Dropdown.Option<>(kind, EditorUi.tr("sample." + kind.id()), null,
                    (ui, x, y, w, h) -> {
                        SampleImage.Image image = SampleImage.image(kind);
                        Draw.texture(ui, image.texture(), x, y, w, h, 0xFFFFFFFF, image.u0(), image.v0(), image.u1(),
                                image.v1(), image.premultiplied());
                    }));
        }
        return out;
    }

    @Override
    protected void arrange() {
        int row = 18;
        boolean twoRows = w < 300;
        int controlsH = twoRows ? row * 2 + 3 : row;
        controlsY = y + h - controlsH;
        box.setBounds(x, y, w, Math.max(20, controlsY - 3 - y));
        int cy = controlsY;
        play.setBounds(x, cy, 18, row);
        int sampleW = Math.min(86, Math.max(64, w / 5));
        if (twoRows) {
            scrubber.setBounds(x + 22, cy, w - 22, row);
            cy += row + 3;
            speed.setBounds(x, cy, 50, row);
            loop.setBounds(x + 54, cy, 18, row);
            reseed.setBounds(x + 76, cy, 18, row);
            opening.setBounds(x + 98, cy, 18, row);
            sample.setBounds(x + w - sampleW, cy, sampleW, row);
            real.setBounds(x + w - sampleW - 22, cy, 18, row);
        } else {
            int right = x + w;
            sample.setBounds(right - sampleW, cy, sampleW, row);
            right -= sampleW + 4;
            real.setBounds(right - 18, cy, 18, row);
            right -= 22;
            reseed.setBounds(right - 18, cy, 18, row);
            right -= 22;
            opening.setBounds(right - 18, cy, 18, row);
            right -= 22;
            loop.setBounds(right - 18, cy, 18, row);
            right -= 22;
            speed.setBounds(right - 50, cy, 50, row);
            right -= 54;
            scrubber.setBounds(x + 22, cy, right - x - 22, row);
        }
    }

    /** The preview picture with its banners. */
    private final class Box extends Widget {
        @Override
        protected void draw(Ui ui) {
            Draw.roundRect(ui, x - 1, y - 1, w + 2, h + 2, 4, Theme.BORDER_STRONG);
            editor.preview.render(ui, x, y, w, h, true);
            int errors = editor.errorCount();
            Component banner = null;
            int color = Theme.DANGER;
            if (editor.preview.failure() != null) {
                banner = EditorUi.tr("preview.failed", editor.preview.failure());
            } else if (errors > 0) {
                banner = EditorUi.tr(editor.preview.def() == null ? "preview.errors_none" : "preview.errors", errors);
            } else if (editor.preview.def() != null && editor.maskFallback()) {
                banner = EditorUi.tr("preview.mask_missing");
                color = Theme.WARNING;
            } else if (RealPreview.notice()) {
                // Shown for a few seconds after the real preview failed (the log has the details).
                banner = EditorUi.tr("preview.real.failed");
                color = Theme.WARNING;
            }
            if (banner != null) {
                int bh = ui.wrappedHeight(banner, w - 20) + 6;
                Draw.rect(ui, x, y, w, bh, Theme.fade(0xFF000000, 0.7f));
                Draw.rect(ui, x, y + bh - 1, w, 1, color);
                Draw.icon(ui, Icon.WARNING, x + 4, y + 3, 1, color);
                ui.textWrapped(banner, x + 16, y + 3, w - 20, Theme.TEXT);
            }
        }

        @Override
        public boolean mouseDown(double mx, double my, int button) {
            if (button == 0) {
                editor.togglePlay();
                return true;
            }
            return false;
        }
    }

    /** Time bar: the effect's length, the playhead; click or drag to scrub (pauses). */
    private final class Scrubber extends Widget {
        private boolean dragging;

        @Override
        protected void draw(Ui ui) {
            EditorPreview p = editor.preview;
            // The full time if it fits, else shorter forms (never cut off); the tooltip has it in full.
            String time = String.format(Locale.ROOT, "%.2f / %.2f s", p.time() / 20f, p.duration() / 20f);
            tooltip(EditorUi.tr("preview.scrub"), net.minecraft.network.chat.Component.literal(time + " · ").append(EditorUi.tr("preview.scrub.desc")));
            for (String shorter : new String[]{String.format(Locale.ROOT, "%.1f/%.1fs", p.time() / 20f, p.duration() / 20f),
                    String.format(Locale.ROOT, "%.1fs", p.time() / 20f)}) {
                if (ui.font.width(time) + 4 > w / 2) {
                    time = shorter;
                }
            }
            int labelW = Math.min(ui.font.width(time) + 4, w / 2);
            int tx = x + 2;
            int tw = Math.max(10, w - labelW - 6);
            int cy = y + h / 2;
            Draw.roundRect(ui, tx, cy - 2, tw, 4, 2, Theme.RAISED_HOVER);
            float d = p.duration() / p.length();
            Draw.rect(ui, tx + Math.round(tw * d), cy - 4, 1, 8, Theme.TEXT_FAINT);
            float f = p.time() / p.length();
            Draw.roundRect(ui, tx, cy - 2, Math.round(tw * Math.clamp(f, 0f, 1f)), 4, 2, Theme.accent());
            int kx = tx + Math.round(tw * Math.clamp(f, 0f, 1f));
            Draw.circle(ui, kx, cy, dragging || ui.isHovered(this) ? 5 : 4, 0xFFF5F6FA);
            ui.textFit(Component.literal(time), x + w - labelW, y + (h - 8) / 2, labelW, Theme.TEXT_DIM);
        }

        private void seek(double mx) {
            int labelW = Math.min(net.minecraft.client.Minecraft.getInstance().font.width("00.00 / 00.00 s") + 4, w / 2);
            int tw = Math.max(10, w - labelW - 6);
            float f = (float) Math.clamp((mx - x - 2) / tw, 0, 1);
            editor.preview.setPlaying(false);
            editor.preview.seek(f * editor.preview.length());
        }

        @Override
        public boolean mouseDown(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            dragging = true;
            seek(mx);
            return true;
        }

        @Override
        public void mouseDrag(double mx, double my) {
            if (dragging) {
                seek(mx);
            }
        }

        @Override
        public void mouseUp(double mx, double my, int button) {
            dragging = false;
        }
    }
}
