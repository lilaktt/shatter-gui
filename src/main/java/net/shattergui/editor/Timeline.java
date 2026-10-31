package net.shattergui.editor;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
import net.shattergui.fx.FxSchema;
import net.shattergui.ui.Button;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The timeline under the preview: a ruler in seconds, one track per layer, emitter and sound, and the playhead.
 * A layer's or emitter's bar is its time window — drag the middle to move it, an end to change its start or length;
 * a sound is a diamond at its moment, dragged along. Layers timed by each piece's own life are drawn striped (their
 * window is relative to every piece). Clicking the ruler or empty space moves the playhead. Below, when a parameter
 * curve is shown ("show on the timeline" in the inspector), its keyframes sit over the item's window: click to add,
 * drag to move, right click to remove.
 */
final class Timeline extends Container {
    private static final int RULER = 12;
    private static final int ROW = 12;
    private static final int EDGE_GRAB = 3;
    private static final float MIN_WINDOW = 0.02f;

    private record Track(Selection selection, String path) {
    }

    private enum Drag {
        NONE, SCRUB, MOVE, START, END, SOUND
    }

    private final FxEditor editor;
    private final Button closeLane;
    private CurveEditor lane;
    private String lanePath;
    private FxSchema.Field laneField;
    private String laneWindow;
    private Component laneTitle = Component.empty();
    private float rowScroll;
    private Drag drag = Drag.NONE;
    private Track dragTrack;
    private float grab;
    private float startFrom;
    private float startTo;

    Timeline(FxEditor editor) {
        this.editor = editor;
        closeLane = add(Button.icon(Icon.CLOSE, EditorUi.tr("timeline.close_curve"), this::hideCurve));
        closeLane.visible = false;
    }

    /** Shows a parameter's curve in the lane, over the time window of {@code window} (null: the whole effect). */
    void showCurve(String path, FxSchema.Field field, JsonElement def, String window, Component title) {
        if (lane != null) {
            children().remove(lane);
        }
        lanePath = path;
        laneField = field;
        laneWindow = window;
        laneTitle = title;
        lane = add(new CurveEditor(editor, path, field, def, true));
        closeLane.visible = true;
    }

    void hideCurve() {
        if (lane != null) {
            children().remove(lane);
        }
        lane = null;
        lanePath = null;
        closeLane.visible = false;
    }

    String lanePath() {
        return lanePath;
    }

    private int labelW() {
        return Math.clamp(Math.round(w * 0.2f), 56, 130);
    }

    private int tx0() {
        return x + labelW() + 4;
    }

    private int tw() {
        return Math.max(10, w - labelW() - 8);
    }

    private float sx(float f) {
        return tx0() + f * tw();
    }

    private float fAt(double mx) {
        return (float) Math.clamp((mx - tx0()) / tw(), 0, 1);
    }

    private int laneH() {
        return lane == null ? 0 : Math.min(46, Math.max(24, h / 3));
    }

    private int rowsY() {
        return y + RULER + 1;
    }

    private int rowsH() {
        return Math.max(ROW, h - RULER - 1 - laneH());
    }

    private List<Track> tracks() {
        List<Track> out = new ArrayList<>();
        for (Selection.Kind kind : new Selection.Kind[]{Selection.Kind.LAYER, Selection.Kind.EMITTER, Selection.Kind.SOUND}) {
            int n = editor.doc.size(Selection.listOf(kind));
            for (int i = 0; i < n; i++) {
                Selection s = new Selection(kind, i);
                out.add(new Track(s, s.path()));
            }
        }
        return out;
    }

    @Override
    protected void arrange() {
        if (lane != null) {
            // The window of the item the curve belongs to.
            if (laneWindow != null && !editor.doc.has(laneWindow)) {
                // The layer or emitter is gone.
                hideCurve();
                return;
            }
            float from = laneWindow == null ? 0 : editor.doc.num(laneWindow + ".from", 0);
            float to = laneWindow == null ? 1 : editor.doc.num(laneWindow + ".to", 1);
            int lx = Math.round(sx(Math.min(from, to)));
            int lw = Math.max(16, Math.round(sx(Math.max(from, to))) - lx);
            int ly = y + h - laneH() + 10;
            lane.setBounds(lx, ly, lw, laneH() - 10);
            closeLane.setBounds(x + labelW() - 12, y + h - laneH(), 12, 10);
        }
    }

    // --- drawing ---

    @Override
    protected void draw(Ui ui) {
        Draw.roundRect(ui, x, y, w, h, 4, 0xFF111217);
        float duration = editor.preview.duration();
        // Ruler: ticks every 0.25 s (or coarser), labels in seconds.
        Draw.rect(ui, x, y, w, RULER, 0xFF1A1C23);
        float seconds = duration / 20f;
        float step = seconds <= 2 ? 0.25f : seconds <= 6 ? 0.5f : seconds <= 12 ? 1f : 2f;
        for (float s = 0; s <= seconds + 1e-3f; s += step) {
            int tx = Math.round(sx(s / seconds));
            boolean major = Math.abs(s - Math.round(s)) < 1e-3f || step >= 1;
            Draw.rect(ui, tx, y + (major ? 6 : 9), 1, major ? 6 : 3, major ? 0x80FFFFFF : 0x40FFFFFF);
            if (major && tx + 20 < x + w) {
                ui.textSmall(String.format(Locale.ROOT, "%.0fs", s), tx + 2, y + 2, 20, Theme.TEXT_FAINT);
            }
        }
        ui.textSmall(EditorUi.tr("timeline.title").getString(), x + 4, y + 3, labelW() - 6, Theme.TEXT_DIM);
        List<Track> tracks = tracks();
        int maxScroll = Math.max(0, tracks.size() * ROW - rowsH());
        rowScroll = Math.clamp(rowScroll, 0, maxScroll);
        ui.pushClip(x, rowsY(), w, rowsH());
        if (tracks.isEmpty()) {
            ui.textFit(EditorUi.tr("timeline.empty"), tx0() + 4, rowsY() + 3, tw() - 8, Theme.TEXT_FAINT);
        }
        for (int i = 0; i < tracks.size(); i++) {
            int ry = Math.round(rowsY() + i * ROW - rowScroll);
            if (ry + ROW < rowsY() || ry > rowsY() + rowsH()) {
                continue;
            }
            drawTrack(ui, tracks.get(i), ry, i);
        }
        ui.popClip();
        if (maxScroll > 0) {
            int barH = Math.max(6, rowsH() * rowsH() / (tracks.size() * ROW));
            int barY = rowsY() + Math.round((rowsH() - barH) * (rowScroll / maxScroll));
            Draw.rect(ui, x + w - 3, barY, 2, barH, Theme.BORDER_STRONG);
        }
        // The curve lane.
        if (lane != null) {
            int ly = y + h - laneH();
            Draw.rect(ui, x, ly, w, 1, Theme.BORDER);
            ui.textSmall(laneTitle.getString(), x + 4, ly + 3, labelW() - 18, Theme.TEXT_DIM);
            ui.textSmall(EditorUi.tr("timeline.curve_hint").getString(), tx0(), ly + 2, tw(), Theme.TEXT_FAINT);
            lane.playhead = laneWindow == null || editor.doc.str(laneWindow + ".time", "local").equals("global")
                    || laneWindow.startsWith("emitters") ? windowFraction() : -1;
        }
        super.draw(ui);
        // Playhead over everything.
        float f = Math.clamp(editor.preview.time() / Math.max(1, duration), 0f, 1f);
        int px = Math.round(sx(f));
        Draw.rect(ui, px, y, 1, h, editor.preview.time() > duration ? 0x60FFFFFF : 0xE0FFFFFF);
        Draw.rect(ui, px - 2, y, 5, 3, Theme.accent());
    }

    /** The playhead inside the lane's window, 0..1 (outside: <0 or >1). */
    private float windowFraction() {
        float g = editor.preview.time() / Math.max(1, editor.preview.duration());
        if (laneWindow == null) {
            return g;
        }
        float from = editor.doc.num(laneWindow + ".from", 0);
        float to = editor.doc.num(laneWindow + ".to", 1);
        return to - from < 1e-4f ? -1 : (g - from) / (to - from);
    }

    private void drawTrack(Ui ui, Track track, int ry, int index) {
        Selection s = track.selection();
        boolean on = s.equals(editor.selection());
        boolean enabled = editor.doc.bool(track.path() + ".enabled", true);
        if (on) {
            Draw.rect(ui, x, ry, w, ROW, Theme.accentAlpha(0x26));
        } else if (index % 2 == 1) {
            Draw.rect(ui, x, ry, w, ROW, 0x0CFFFFFF);
        }
        int color = LayerPanel.itemColor(editor.doc, s.kind(), s.index());
        if (!enabled) {
            color = Theme.fade(color, 0.3f);
        }
        Draw.icon(ui, LayerPanel.itemIcon(editor.doc, s.kind(), s.index()), x + 3, ry + (ROW - 9) / 2, 1, color);
        ui.textFit(LayerPanel.itemName(editor.doc, s.kind(), s.index()), x + 15, ry + 2, labelW() - 17,
                enabled ? (on ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_FAINT);
        if (s.kind() == Selection.Kind.SOUND) {
            float f = soundTime(track.path());
            int cx = Math.round(sx(f));
            int cy = ry + ROW / 2;
            for (int d = 0; d <= 4; d++) {
                Draw.rect(ui, cx - (4 - d), cy - d, (4 - d) * 2 + 1, 1, color);
                Draw.rect(ui, cx - (4 - d), cy + d, (4 - d) * 2 + 1, 1, color);
            }
            return;
        }
        float from = editor.doc.num(track.path() + ".from", 0);
        float to = editor.doc.num(track.path() + ".to", 1);
        int bx = Math.round(sx(from));
        int bw = Math.max(3, Math.round(sx(to)) - bx);
        boolean local = s.kind() == Selection.Kind.LAYER && !"global".equals(editor.doc.str(track.path() + ".time", "local"));
        Draw.roundRect(ui, bx, ry + 2, bw, ROW - 4, 2, Theme.fade(color, on ? 0.95f : 0.7f));
        if (local) {
            // Stripes: the window is relative to each piece's own life.
            ui.pushClip(bx, ry + 2, bw, ROW - 4);
            for (int sx = bx - ROW; sx < bx + bw; sx += 5) {
                EditorUi.lines(ui, new float[]{sx, ry + ROW - 2, sx + ROW - 4, ry + 2}, 2, 1.5f, 0x40000000);
            }
            ui.popClip();
        }
        if (on) {
            Draw.rect(ui, bx, ry + 2, 2, ROW - 4, 0xFFFFFFFF);
            Draw.rect(ui, bx + bw - 2, ry + 2, 2, ROW - 4, 0xFFFFFFFF);
        }
        if (s.kind() == Selection.Kind.EMITTER && editor.doc.num(track.path() + ".burst", 0) > 0) {
            Draw.rect(ui, bx, ry, 1, ROW, 0xFFFFFFFF);
        }
    }

    private float soundTime(String path) {
        JsonElement at = editor.doc.get(path + ".at");
        if (at != null && at.isJsonPrimitive() && at.getAsJsonPrimitive().isNumber()) {
            return Math.clamp(at.getAsFloat(), 0f, 1f);
        }
        return "end".equals(editor.doc.str(path + ".at", "start")) ? 1f : 0f;
    }

    // --- input ---

    private Track trackAt(double my) {
        if (my < rowsY() || my >= rowsY() + rowsH()) {
            return null;
        }
        int index = (int) Math.floor((my - rowsY() + rowScroll) / ROW);
        List<Track> tracks = tracks();
        return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (my < rowsY()) {
            startScrub(mx);
            return true;
        }
        Track track = trackAt(my);
        if (track == null) {
            if (my < rowsY() + rowsH()) {
                startScrub(mx);
            }
            return true;
        }
        editor.select(track.selection());
        if (mx < tx0()) {
            return true;
        }
        if (track.selection().kind() == Selection.Kind.SOUND) {
            if (Math.abs(mx - sx(soundTime(track.path()))) <= 5) {
                drag = Drag.SOUND;
                dragTrack = track;
            } else {
                startScrub(mx);
            }
            return true;
        }
        float from = editor.doc.num(track.path() + ".from", 0);
        float to = editor.doc.num(track.path() + ".to", 1);
        double left = sx(from);
        double right = sx(to);
        if (Math.abs(mx - left) <= EDGE_GRAB) {
            drag = Drag.START;
        } else if (Math.abs(mx - right) <= EDGE_GRAB) {
            drag = Drag.END;
        } else if (mx > left && mx < right) {
            drag = Drag.MOVE;
            grab = fAt(mx) - from;
        } else {
            startScrub(mx);
            return true;
        }
        dragTrack = track;
        startFrom = from;
        startTo = to;
        return true;
    }

    private void startScrub(double mx) {
        drag = Drag.SCRUB;
        editor.preview.setPlaying(false);
        editor.preview.seek(fAt(mx) * editor.preview.duration());
    }

    @Override
    public void mouseDrag(double mx, double my) {
        float f = fAt(mx);
        switch (drag) {
            case SCRUB -> editor.preview.seek(f * editor.preview.duration());
            case SOUND -> editor.doc.set(dragTrack.path() + ".at", Math.round(f * 100) / 100f);
            case START -> setWindow(Math.min(f, startTo - MIN_WINDOW), startTo);
            case END -> setWindow(startFrom, Math.max(f, startFrom + MIN_WINDOW));
            case MOVE -> {
                float len = startTo - startFrom;
                float from = Math.clamp(f - grab, 0f, 1f - len);
                setWindow(from, from + len);
            }
            default -> {
            }
        }
    }

    private void setWindow(float from, float to) {
        String path = dragTrack.path();
        float a = Math.round(Math.clamp(from, 0f, 1f) * 100) / 100f;
        float b = Math.round(Math.clamp(to, 0f, 1f) * 100) / 100f;
        editor.doc.edit(path + ".window", r -> {
            FxDoc.put(r, path + ".from", FxDoc.number(a));
            FxDoc.put(r, path + ".to", FxDoc.number(b));
        });
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        drag = Drag.NONE;
        dragTrack = null;
        editor.doc.breakMerge();
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (my >= rowsY() && my < rowsY() + rowsH()) {
            rowScroll = (float) Math.max(0, rowScroll - amount * ROW * 2);
            return true;
        }
        return false;
    }
}
