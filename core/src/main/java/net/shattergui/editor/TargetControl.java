package net.shattergui.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxSchema;
import net.shattergui.ui.Button;
import net.shattergui.ui.Column;
import net.shattergui.ui.Segmented;
import net.shattergui.ui.Row;
import net.shattergui.ui.Slider;
import net.shattergui.ui.Toggle;
import net.shattergui.ui.Ui;

/**
 * Which pieces a layer moves: all of them, a part of the front (by when they start), those near the edge, a random
 * share, the region or everything outside it — as buttons, with the group's own settings below and "the opposite".
 */
final class TargetControl extends Column {
    private final FxEditor editor;
    private final String path;

    TargetControl(FxEditor editor, String path) {
        super(0, 3);
        this.editor = editor;
        this.path = path;
        Segmented groups = add(new Segmented());
        for (String group : FxDef.Target.GROUPS) {
            groups.option(EditorUi.tr("target." + group), EditorUi.tr("target." + group + ".desc"), () -> group.equals(group()),
                    () -> setGroup(group));
        }
        switch (group()) {
            case "wave" -> {
                add(ValueControl.labelled("value.from", slider("target.from", "from")));
                add(ValueControl.labelled("value.to", slider("target.to", "to")));
            }
            case "edge" -> add(ValueControl.labelled("target.width", slider("target.width", "width")));
            case "random" -> {
                add(ValueControl.labelled("target.chance", slider("target.chance", "chance")));
                add(ValueControl.labelled("target.seed", slider("target.seed", "seed")));
            }
            default -> {
            }
        }
        if (!"all".equals(group())) {
            Row invert = add(new Row(4));
            invert.flex(new net.shattergui.ui.Label(EditorUi.tr("target.invert"), net.shattergui.ui.Label.Style.DIM), 1);
            invert.fixed(new Toggle(() -> bool("invert"), v -> setField("invert", v)), Toggle.WIDTH);
        }
    }

    private String group() {
        JsonElement e = editor.doc.get(path);
        if (e == null || e.isJsonNull()) {
            return "all";
        }
        if (e.isJsonPrimitive()) {
            String g = e.getAsString();
            return FxDef.Target.GROUPS.contains(g) ? g : "all";
        }
        String g = ValueForms.str(e, "group", "all");
        return FxDef.Target.GROUPS.contains(g) ? g : "all";
    }

    private void setGroup(String group) {
        if (group.equals(group())) {
            return;
        }
        if ("all".equals(group)) {
            editor.doc.remove(path);
        } else {
            JsonObject o = new JsonObject();
            o.addProperty("group", group);
            editor.doc.set(path, o);
        }
        editor.refreshInspector();
    }

    private boolean bool(String key) {
        JsonElement e = editor.doc.get(path);
        try {
            return e != null && e.isJsonObject() && e.getAsJsonObject().has(key) && e.getAsJsonObject().get(key).getAsBoolean();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Writes a field of the target object (a plain-string target becomes an object first). */
    private void setField(String key, Object value) {
        String group = group();
        editor.doc.edit(path + "." + key, r -> {
            JsonElement e = FxDoc.get(r, path);
            JsonObject o;
            if (e != null && e.isJsonObject()) {
                o = e.getAsJsonObject();
            } else {
                o = new JsonObject();
                o.addProperty("group", group);
                FxDoc.put(r, path, o);
            }
            if (value instanceof Boolean b) {
                if (b) {
                    o.addProperty(key, true);
                } else {
                    o.remove(key);
                }
            } else {
                o.add(key, FxDoc.number(((Number) value).doubleValue()));
            }
        });
    }

    private Slider slider(String fieldKey, String key) {
        FxSchema.Field f = FxSchema.field(fieldKey);
        return new Slider(f.min(), f.max(), EditorUi.step(f), () -> ValueForms.num(editor.doc.get(path), key, f.def()),
                v -> setField(key, f.whole() ? Math.round(v) : v), v -> EditorUi.format(f.unit(), v), t -> EditorUi.parse(f.unit(), t));
    }
}
