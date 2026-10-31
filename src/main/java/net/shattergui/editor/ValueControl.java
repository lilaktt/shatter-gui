package net.shattergui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
import net.shattergui.fx.Easing;
import net.shattergui.fx.FxSchema;
import net.shattergui.ui.Button;
import net.shattergui.ui.Column;
import net.shattergui.ui.Dropdown;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Row;
import net.shattergui.ui.Slider;

import java.util.ArrayList;
import java.util.List;

/**
 * The control for a number that may change over time: a choice of form (number, random range, preset curve, Bézier,
 * keyframes) and the controls of that form — sliders in human units with an exact-value box, the easing presets, the
 * curve editor with keyframes or Bézier handles — plus the spread between pieces and "show on the timeline".
 */
final class ValueControl extends Column {
    private final FxEditor editor;
    private final String path;
    private final FxSchema.Field field;
    private final JsonElement def;

    /**
     * @param def      the value used when the field is missing (a number or any curve form), may be null
     * @param window   path prefix of the layer/emitter whose time window the curve spans on the timeline (null: none)
     */
    ValueControl(FxEditor editor, String path, FxSchema.Field field, JsonElement def, String window) {
        super(0, 3);
        this.editor = editor;
        this.path = path;
        this.field = field;
        this.def = def;
        ValueForms.Form form = ValueForms.formOf(current());
        Row head = add(new Row(4));
        head.flex(new Dropdown<>(ValueControl::forms, () -> ValueForms.formOf(current()), this::switchTo), 1);
        if (window != null) {
            Button show = Button.icon(Icon.LAYERS, EditorUi.tr("value.timeline"), () -> editor.showCurve(path, field, def, window));
            show.tooltip(EditorUi.tr("value.timeline"), EditorUi.tr("value.timeline.desc"));
            head.fixed(show, 18);
        }
        switch (form) {
            case NUMBER -> add(slider(() -> number(), this::setNumber));
            case RANDOM -> {
                add(labelled("value.from", slider(() -> ValueForms.range(current(), field.def())[0], v -> setRange(0, v))));
                add(labelled("value.to", slider(() -> ValueForms.range(current(), field.def())[1], v -> setRange(1, v))));
            }
            case CURVE -> {
                add(new Dropdown<>(ValueControl::easings,
                        () -> {
                            Easing e = Easing.byName(ValueForms.str(current(), "curve", "linear"));
                            return e == null ? Easing.LINEAR : e;
                        },
                        e -> editor.doc.set(path + ".curve", e.configName())));
                add(new CurveEditor(editor, path, field, def, false));
                add(labelled("value.start", slider(() -> ValueForms.num(current(), "from", field.def()), v -> editor.doc.set(path + ".from", v))));
                add(labelled("value.end", slider(() -> ValueForms.num(current(), "to", field.def()), v -> editor.doc.set(path + ".to", v))));
            }
            case BEZIER -> {
                add(new CurveEditor(editor, path, field, def, false));
                add(labelled("value.start", slider(() -> ValueForms.num(current(), "from", field.def()), v -> editor.doc.set(path + ".from", v))));
                add(labelled("value.end", slider(() -> ValueForms.num(current(), "to", field.def()), v -> editor.doc.set(path + ".to", v))));
            }
            case KEYS -> {
                add(new CurveEditor(editor, path, field, def, false));
                add(new net.shattergui.ui.Hint(EditorUi.tr("value.keys_hint")));
                add(labelled("value.ease", new Dropdown<>(ValueControl::easings,
                        () -> {
                            Easing e = Easing.byName(ValueForms.str(current(), "ease", "linear"));
                            return e == null ? Easing.LINEAR : e;
                        },
                        e -> editor.doc.set(path + ".ease", e.configName()))));
            }
        }
        if (form != ValueForms.Form.NUMBER) {
            FxSchema.Field jitter = new FxSchema.Field("jitter", 0, 0, 1, FxSchema.Unit.FRACTION);
            Slider s = new Slider(0, 1, 0.01, () -> ValueForms.num(current(), "jitter", 0), v -> editor.doc.set(path + ".jitter", (float) v),
                    v -> EditorUi.format(jitter.unit(), v), t -> EditorUi.parse(jitter.unit(), t));
            add(labelled("value.jitter", s)).tooltip(EditorUi.tr("value.jitter"), EditorUi.tr("value.jitter.desc"));
        }
    }

    private JsonElement current() {
        JsonElement e = editor.doc.get(path);
        return e == null || e.isJsonNull() ? def : e;
    }

    private float number() {
        JsonElement e = current();
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return e.getAsFloat();
        }
        return ValueForms.read(e, def, field).at(0.5f, 0.5f);
    }

    private void setNumber(double v) {
        editor.doc.set(path, (float) v);
    }

    private void setRange(int i, double v) {
        float[] r = ValueForms.range(current(), field.def());
        r[i] = (float) v;
        JsonArray range = new JsonArray();
        range.add(FxDoc.number(r[0]));
        range.add(FxDoc.number(r[1]));
        editor.doc.edit(path + ".random", root -> {
            JsonElement target = FxDoc.get(root, path);
            if (target == null || !target.isJsonObject()) {
                return;
            }
            target.getAsJsonObject().remove("value");
            target.getAsJsonObject().add("random", range);
        });
    }

    private void switchTo(ValueForms.Form form) {
        if (form == ValueForms.formOf(current())) {
            return;
        }
        JsonElement converted = ValueForms.convert(editor.doc.get(path), def, field, form);
        editor.doc.edit(null, root -> FxDoc.put(root, path, converted));
        editor.refreshInspector();
    }

    private Slider slider(java.util.function.DoubleSupplier getter, java.util.function.DoubleConsumer setter) {
        return new Slider(field.min(), field.max(), EditorUi.step(field), getter, v -> setter.accept(
                field.whole() ? Math.round(v) : v),
                v -> EditorUi.format(field.unit(), v), t -> EditorUi.parse(field.unit(), t));
    }

    /** A small caption left of a control. */
    static Row labelled(String key, net.shattergui.ui.Widget control) {
        Row row = new Row(4);
        Component caption = EditorUi.tr(key);
        int width = Math.clamp(net.minecraft.client.Minecraft.getInstance().font.width(caption) + 4, 30, 72);
        row.fixed(new net.shattergui.ui.Label(caption, net.shattergui.ui.Label.Style.DIM), width);
        row.flex(control, 1);
        return row;
    }

    private static List<Dropdown.Option<ValueForms.Form>> forms() {
        List<Dropdown.Option<ValueForms.Form>> out = new ArrayList<>();
        for (ValueForms.Form form : ValueForms.Form.values()) {
            String key = "form." + form.name().toLowerCase(java.util.Locale.ROOT);
            out.add(new Dropdown.Option<>(form, EditorUi.tr(key), EditorUi.tr(key + ".desc"), null));
        }
        return out;
    }

    static List<Dropdown.Option<Easing>> easings() {
        List<Dropdown.Option<Easing>> out = new ArrayList<>();
        for (Easing easing : Easing.values()) {
            Easing e = easing;
            out.add(new Dropdown.Option<>(easing, EditorUi.tr("ease." + easing.configName()), null,
                    (ui, x, y, w, h) -> easingThumb(ui, e, x, y, w, h)));
        }
        return out;
    }

    /** A tiny picture of an easing for the presets list. */
    static void easingThumb(net.shattergui.ui.Ui ui, Easing easing, int x, int y, int w, int h) {
        net.shattergui.ui.Draw.rect(ui, x, y, w, h, 0xFF0F1014);
        int n = 16;
        float[] points = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            points[i * 2] = x + 1 + t * (w - 2);
            points[i * 2 + 1] = y + h - 2 - easing.apply(t) * (h - 4) * 0.8f - (h - 4) * 0.1f;
        }
        EditorUi.lines(ui, points, n + 1, 1f, net.shattergui.ui.Theme.accentHover());
    }

    static Component formName(ValueForms.Form form) {
        return EditorUi.tr("form." + form.name().toLowerCase(java.util.Locale.ROOT));
    }
}
