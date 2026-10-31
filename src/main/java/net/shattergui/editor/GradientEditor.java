package net.shattergui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.Gradient;
import net.shattergui.ui.Button;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A colour gradient: the bar shows it (over a checkerboard, for transparency) with its stops below. A click on the
 * bar adds a stop with the colour found there, dragging a stop moves it, the chosen stop is edited with the colour
 * picker underneath, "remove" deletes it (one stop always stays). Written as one colour or a list of {t, color}.
 */
final class GradientEditor extends Container {
    private record Stop(float t, int color) {
    }

    private static final int BAR_H = 12;
    private static final int MARK_H = 7;
    private final FxEditor editor;
    private final String path;
    private final int def;
    private final Button remove;
    private final ColorPicker picker;
    private int selected;
    private int dragging = -1;

    GradientEditor(FxEditor editor, String path, int def) {
        this.editor = editor;
        this.path = path;
        this.def = def;
        this.remove = add(Button.icon(Icon.TRASH, EditorUi.tr("gradient.remove"), this::removeSelected));
        this.picker = add(new ColorPicker(() -> {
            List<Stop> stops = stops();
            return stops.get(Math.clamp(selected, 0, stops.size() - 1)).color();
        }, color -> {
            List<Stop> stops = stops();
            int i = Math.clamp(selected, 0, stops.size() - 1);
            stops.set(i, new Stop(stops.get(i).t(), color));
            write(stops, path + ".color");
        }));
    }

    private List<Stop> stops() {
        List<Stop> out = new ArrayList<>();
        JsonElement e = editor.doc.get(path);
        try {
            if (e != null && e.isJsonPrimitive()) {
                Integer c = Gradient.parseColor(e.getAsString());
                out.add(new Stop(0, c == null ? def : c));
            } else if (e != null && e.isJsonArray()) {
                JsonArray array = e.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    JsonElement item = array.get(i);
                    float even = array.size() == 1 ? 0 : (float) i / (array.size() - 1);
                    if (item.isJsonPrimitive()) {
                        Integer c = Gradient.parseColor(item.getAsString());
                        out.add(new Stop(even, c == null ? def : c));
                    } else if (item.isJsonObject()) {
                        Integer c = Gradient.parseColor(ValueForms.str(item, "color", null));
                        out.add(new Stop(Math.clamp(ValueForms.num(item, "t", even), 0f, 1f), c == null ? def : c));
                    }
                }
            }
        } catch (RuntimeException ex) {
            out.clear();
        }
        if (out.isEmpty()) {
            out.add(new Stop(0, def));
        }
        out.sort((a, b) -> Float.compare(a.t(), b.t()));
        return out;
    }

    private void write(List<Stop> stops, String mergeKey) {
        List<Stop> sorted = new ArrayList<>(stops);
        sorted.sort((a, b) -> Float.compare(a.t(), b.t()));
        JsonElement out;
        if (sorted.size() == 1) {
            out = new JsonPrimitive(Gradient.format(sorted.getFirst().color()));
        } else {
            JsonArray array = new JsonArray();
            for (Stop s : sorted) {
                JsonObject o = new JsonObject();
                o.add("t", FxDoc.number(s.t()));
                o.addProperty("color", Gradient.format(s.color()));
                array.add(o);
            }
            out = array;
        }
        editor.doc.edit(mergeKey, r -> FxDoc.put(r, path, out));
    }

    @Override
    protected int measure(int width) {
        int cy = y + BAR_H + MARK_H + 3;
        remove.setBounds(x + width - 18, y, 18, BAR_H + MARK_H);
        picker.layout(x, cy, width);
        return cy + picker.h - y;
    }

    private int barW() {
        return Math.max(10, w - 22);
    }

    @Override
    protected void draw(Ui ui) {
        List<Stop> stops = stops();
        selected = Math.clamp(selected, 0, stops.size() - 1);
        remove.enabled = stops.size() > 1;
        int bw = barW();
        Gradient gradient = Gradient.fromJson(editor.doc.get(path), Gradient.solid(def));
        EditorUi.checker(ui, x, y, bw, BAR_H, 3);
        EditorUi.hGradient(ui, x, y, bw, BAR_H, i -> gradient.at(i / (float) Math.max(1, bw - 1)));
        Draw.roundOutline(ui, x - 1, y - 1, bw + 2, BAR_H + 2, 2, Theme.BORDER_STRONG);
        for (int i = 0; i < stops.size(); i++) {
            Stop s = stops.get(i);
            int sx = x + Math.round(s.t() * (bw - 1));
            boolean on = i == selected;
            Draw.rect(ui, sx - 3, y + BAR_H + 1, 7, MARK_H, on ? 0xFFFFFFFF : 0xFF707070);
            Draw.rect(ui, sx - 2, y + BAR_H + 2, 5, MARK_H - 2, 0xFF000000 | (s.color() & 0xFFFFFF));
            if (on) {
                Draw.rect(ui, sx, y - 2, 1, 2, 0xFFFFFFFF);
            }
        }
        super.draw(ui);
    }

    private int stopAt(List<Stop> stops, double mx) {
        int bw = barW();
        int best = -1;
        double bestD = 5;
        for (int i = 0; i < stops.size(); i++) {
            double d = Math.abs(mx - (x + stops.get(i).t() * (bw - 1)));
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0 || my > y + BAR_H + MARK_H + 1 || mx > x + barW()) {
            return false;
        }
        List<Stop> stops = stops();
        int hit = stopAt(stops, mx);
        if (hit >= 0 && my > y + BAR_H - 2) {
            selected = hit;
            dragging = hit;
            return true;
        }
        if (stops.size() >= FxLimits.MAX_GRADIENT_STOPS) {
            return true;
        }
        float t = (float) Math.clamp((mx - x) / Math.max(1, barW() - 1), 0, 1);
        int color = Gradient.fromJson(editor.doc.get(path), Gradient.solid(def)).at(t);
        stops.add(new Stop(t, color));
        stops.sort((a, b) -> Float.compare(a.t(), b.t()));
        write(stops, null);
        selected = stops.indexOf(new Stop(t, color));
        dragging = selected;
        return true;
    }

    @Override
    public void mouseDrag(double mx, double my) {
        if (dragging < 0) {
            return;
        }
        List<Stop> stops = stops();
        if (dragging >= stops.size()) {
            return;
        }
        float t = (float) Math.clamp((mx - x) / Math.max(1, barW() - 1), 0, 1);
        Stop moved = new Stop(t, stops.get(dragging).color());
        stops.set(dragging, moved);
        stops.sort((a, b) -> Float.compare(a.t(), b.t()));
        write(stops, path + ".t");
        dragging = stops.indexOf(moved);
        selected = dragging;
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        dragging = -1;
        editor.doc.breakMerge();
    }

    private void removeSelected() {
        List<Stop> stops = stops();
        if (stops.size() <= 1) {
            return;
        }
        stops.remove(Math.clamp(selected, 0, stops.size() - 1));
        write(stops, null);
        selected = Math.max(0, selected - 1);
    }
}
