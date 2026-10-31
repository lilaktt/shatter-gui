package net.shattergui.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.shattergui.fx.FxSchema;
import net.shattergui.fx.Value;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

/**
 * A small graph of a parameter over its time window (left = start, right = end). Keyframes are dots: a click on the
 * empty graph adds one (a number or a preset curve becomes keyframes first), dragging moves one (never past its
 * neighbours), a right click removes one. A Bézier shows its two handles, dragged to shape it (drawn on a 0..1 scale;
 * its start and end values are set beside it). The value range follows the curve, frozen while dragging.
 */
final class CurveEditor extends net.shattergui.ui.Widget {
    private static final int SAMPLES = 48;
    private static final int GRAB = 5;
    private final FxEditor editor;
    private final String path;
    private final FxSchema.Field field;
    private final JsonElement def;
    private final boolean lane;
    private int dragKey = -1;
    private int dragHandle = -1;
    private float lo;
    private float hi = 1;
    private boolean frozen;
    private boolean refreshAfter;
    /** 0..1 of the window, or negative for none (the timeline sets it). */
    float playhead = -1;

    CurveEditor(FxEditor editor, String path, FxSchema.Field field, JsonElement def, boolean lane) {
        this.editor = editor;
        this.path = path;
        this.field = field;
        this.def = def;
        this.lane = lane;
        this.h = lane ? 34 : 46;
    }

    @Override
    protected int measure(int width) {
        return h;
    }

    private JsonElement json() {
        JsonElement e = editor.doc.get(path);
        return e == null || e.isJsonNull() ? def : e;
    }

    private int ix() {
        return x + 4;
    }

    private int iy() {
        return y + 3;
    }

    private int iw() {
        return Math.max(4, w - 8);
    }

    private int ih() {
        return Math.max(4, h - 6);
    }

    private float sx(float t) {
        return ix() + t * iw();
    }

    private float sy(float v) {
        return iy() + (1 - (v - lo) / Math.max(1e-6f, hi - lo)) * ih();
    }

    private float tAt(double mx) {
        return (float) Math.clamp((mx - ix()) / iw(), 0, 1);
    }

    private float vAt(double my) {
        float f = (float) (1 - (my - iy()) / ih());
        return Math.clamp(lo + f * (hi - lo), field.min(), field.max());
    }

    private void range(ValueForms.Form form, Value value, float[][] keys) {
        if (frozen) {
            return;
        }
        if (form == ValueForms.Form.BEZIER) {
            lo = -0.25f;
            hi = 1.25f;
            return;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i <= SAMPLES; i++) {
            float t = i / (float) SAMPLES;
            for (float r : form == ValueForms.Form.RANDOM ? new float[]{0, 1} : new float[]{0.5f}) {
                float v = value.at(t, r);
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        for (float[] k : keys) {
            min = Math.min(min, k[1]);
            max = Math.max(max, k[1]);
        }
        float span = field.max() - field.min();
        if (max - min < span * 0.1f) {
            float c = (max + min) / 2;
            min = c - span * 0.05f;
            max = c + span * 0.05f;
        }
        float pad = (max - min) * 0.12f;
        lo = Math.max(field.min(), min - pad);
        hi = Math.min(field.max(), max + pad);
        if (hi - lo < 1e-5f) {
            hi = lo + 1;
        }
    }

    @Override
    protected void draw(Ui ui) {
        JsonElement json = json();
        ValueForms.Form form = ValueForms.formOf(json);
        Value value = ValueForms.read(json, def, field);
        float[][] keys = ValueForms.keys(json);
        range(form, value, keys);
        if (lane) {
            Draw.rect(ui, x, y, w, h, 0x40000000);
        } else {
            Draw.roundBox(ui, x, y, w, h, Theme.RADIUS_CONTROL, Theme.FIELD, Theme.BORDER);
        }
        // Grid: quarters of time, zero line.
        for (int i = 1; i < 4; i++) {
            Draw.rect(ui, Math.round(sx(i / 4f)), iy(), 1, ih(), 0x18FFFFFF);
        }
        if (form != ValueForms.Form.BEZIER && lo < 0 && hi > 0) {
            Draw.rect(ui, ix(), Math.round(sy(0)), iw(), 1, 0x30FFFFFF);
        }
        int color = Theme.accentHover();
        float[] points = new float[(SAMPLES + 1) * 2];
        if (form == ValueForms.Form.BEZIER) {
            float[] p = ValueForms.bezier(json);
            for (int i = 0; i <= SAMPLES; i++) {
                float t = i / (float) SAMPLES;
                points[i * 2] = sx(t);
                points[i * 2 + 1] = sy(bezierAt(p, t));
            }
            EditorUi.lines(ui, points, SAMPLES + 1, 1.5f, color);
            float[][] handles = {{p[0], p[1]}, {p[2], p[3]}};
            float[][] anchors = {{0, 0}, {1, 1}};
            for (int k = 0; k < 2; k++) {
                EditorUi.lines(ui, new float[]{sx(anchors[k][0]), sy(anchors[k][1]), sx(handles[k][0]), sy(handles[k][1])}, 2, 1f,
                        0x90FFFFFF);
                int hx = Math.round(sx(handles[k][0]));
                int hy = Math.round(sy(Math.clamp(handles[k][1], lo, hi)));
                Draw.rect(ui, hx - 3, hy - 3, 6, 6, dragHandle == k ? 0xFFFFFFFF : Theme.accent());
                Draw.rect(ui, hx - 2, hy - 2, 4, 4, 0xFF15161C);
            }
        } else {
            for (float r : form == ValueForms.Form.RANDOM ? new float[]{0, 1} : new float[]{0.5f}) {
                for (int i = 0; i <= SAMPLES; i++) {
                    float t = i / (float) SAMPLES;
                    points[i * 2] = sx(t);
                    points[i * 2 + 1] = sy(value.at(t, r));
                }
                EditorUi.lines(ui, points, SAMPLES + 1, 1.5f, form == ValueForms.Form.RANDOM ? Theme.fade(color, 0.7f) : color);
            }
            for (int i = 0; i < keys.length; i++) {
                int kx = Math.round(sx(keys[i][0]));
                int ky = Math.round(sy(keys[i][1]));
                boolean active = i == dragKey;
                Draw.circle(ui, kx, ky, active ? 4 : 3, active ? 0xFFFFFFFF : Theme.accent());
                Draw.circle(ui, kx, ky, active ? 2 : 1, 0xFF15161C);
            }
        }
        if (playhead >= 0 && playhead <= 1) {
            Draw.rect(ui, Math.round(sx(playhead)), y + 1, 1, h - 2, 0xC0FFFFFF);
        }
        if (!lane || h >= 30) {
            String top = form == ValueForms.Form.BEZIER ? "1" : EditorUi.format(field.unit(), hi);
            String bottom = form == ValueForms.Form.BEZIER ? "0" : EditorUi.format(field.unit(), lo);
            ui.textSmall(top, ix() + 1, iy(), iw() / 2, 0x90FFFFFF);
            ui.textSmall(bottom, ix() + 1, iy() + ih() - 6, iw() / 2, 0x90FFFFFF);
        }
        if (ui.isHovered(this) && dragKey < 0 && dragHandle < 0) {
            int hx = ui.mouseX;
            if (hx >= ix() && hx <= ix() + iw()) {
                Draw.rect(ui, hx, iy(), 1, ih(), 0x30FFFFFF);
            }
        }
    }

    private static float bezierAt(float[] p, float t) {
        // Same maths as the engine: bisection on x(s), then y(s).
        float lo = 0;
        float hi = 1;
        float s = t;
        for (int i = 0; i < 20; i++) {
            s = (lo + hi) / 2;
            float x = cubic(p[0], p[2], s);
            if (x < t) {
                lo = s;
            } else {
                hi = s;
            }
        }
        return cubic(p[1], p[3], s);
    }

    private static float cubic(float a, float b, float s) {
        float u = 1 - s;
        return 3 * u * u * s * a + 3 * u * s * s * b + s * s * s;
    }

    // --- input ---

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        JsonElement json = json();
        ValueForms.Form form = ValueForms.formOf(json);
        frozen = true;
        if (form == ValueForms.Form.BEZIER) {
            float[] p = ValueForms.bezier(json);
            for (int k = 0; k < 2; k++) {
                if (Math.abs(mx - sx(p[k * 2])) <= GRAB && Math.abs(my - sy(Math.clamp(p[k * 2 + 1], lo, hi))) <= GRAB) {
                    dragHandle = k;
                    return true;
                }
            }
            frozen = false;
            return true;
        }
        float[][] keys = ValueForms.keys(json);
        if (form == ValueForms.Form.KEYS) {
            int near = nearestKey(keys, mx, my);
            if (near >= 0 && button == 1) {
                if (keys.length > 1) {
                    float[][] fewer = new float[keys.length - 1][];
                    for (int i = 0, j = 0; i < keys.length; i++) {
                        if (i != near) {
                            fewer[j++] = keys[i];
                        }
                    }
                    writeKeys(fewer);
                    editor.doc.breakMerge();
                }
                frozen = false;
                return true;
            }
            if (near >= 0) {
                dragKey = near;
                return true;
            }
        }
        if (button != 0) {
            frozen = false;
            return false;
        }
        if (form != ValueForms.Form.KEYS) {
            // A number or preset curve becomes keyframes, then the new key goes in.
            JsonElement converted = ValueForms.convert(editor.doc.get(path), def, field, ValueForms.Form.KEYS);
            editor.doc.edit(null, r -> FxDoc.put(r, path, converted));
            keys = ValueForms.keys(converted);
            // The form controls above change: rebuilt when the drag ends.
            refreshAfter = true;
        }
        if (keys.length >= net.shattergui.fx.FxLimits.MAX_KEYS) {
            frozen = false;
            return true;
        }
        float t = tAt(mx);
        float v = vAt(my);
        float[][] more = new float[keys.length + 1][];
        int insert = 0;
        while (insert < keys.length && keys[insert][0] <= t) {
            insert++;
        }
        for (int i = 0, j = 0; i < more.length; i++) {
            more[i] = i == insert ? new float[]{t, v} : keys[j++];
        }
        writeKeys(more);
        dragKey = insert;
        return true;
    }

    private int nearestKey(float[][] keys, double mx, double my) {
        int best = -1;
        double bestD = GRAB * GRAB * 2.0;
        for (int i = 0; i < keys.length; i++) {
            double dx = mx - sx(keys[i][0]);
            double dy = my - sy(keys[i][1]);
            double d = dx * dx + dy * dy;
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public void mouseDrag(double mx, double my) {
        JsonElement json = json();
        if (dragHandle >= 0 && ValueForms.formOf(json) == ValueForms.Form.BEZIER) {
            float[] p = ValueForms.bezier(json);
            p[dragHandle * 2] = Math.clamp(tAt(mx), 0f, 1f);
            float f = (float) (1 - (my - iy()) / ih());
            p[dragHandle * 2 + 1] = Math.clamp(lo + f * (hi - lo), -1f, 2f);
            editor.doc.edit(path + ".bezier", r -> {
                JsonElement target = FxDoc.get(r, path);
                if (target != null && target.isJsonObject()) {
                    target.getAsJsonObject().add("bezier", ValueForms.bezierJson(p));
                }
            });
            return;
        }
        if (dragKey < 0) {
            return;
        }
        float[][] keys = ValueForms.keys(json);
        if (dragKey >= keys.length) {
            return;
        }
        float min = dragKey > 0 ? keys[dragKey - 1][0] : 0;
        float max = dragKey < keys.length - 1 ? keys[dragKey + 1][0] : 1;
        keys[dragKey] = new float[]{Math.clamp(tAt(mx), min, max), vAt(my)};
        writeKeys(keys);
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        dragKey = -1;
        dragHandle = -1;
        frozen = false;
        editor.doc.breakMerge();
        if (refreshAfter) {
            refreshAfter = false;
            editor.refreshInspector();
        }
    }

    private void writeKeys(float[][] keys) {
        editor.doc.edit(path + ".keys", r -> {
            JsonElement target = FxDoc.get(r, path);
            if (target == null || !target.isJsonObject()) {
                JsonObject o = new JsonObject();
                FxDoc.put(r, path, o);
                target = o;
            }
            target.getAsJsonObject().add("keys", ValueForms.keysJson(keys));
        });
    }
}
