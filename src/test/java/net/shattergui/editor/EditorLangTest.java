package net.shattergui.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.shattergui.fx.Easing;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxSchema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every name the editor builds from data (modules, parameters, forms, easings, choices, templates) is translated. */
class EditorLangTest {
    private static JsonObject lang(String code) throws IOException {
        try (InputStream in = EditorLangTest.class.getResourceAsStream("/assets/shatter_gui/lang/" + code + ".json")) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<String> dynamicKeys() {
        List<String> keys = new ArrayList<>();
        String p = "ui.shatter_gui.fx.";
        for (FxDef.Module m : FxDef.Module.values()) {
            keys.add(p + "module." + FxSchema.moduleName(m));
            keys.add(p + "module." + FxSchema.moduleName(m) + ".desc");
            for (FxSchema.Field f : FxSchema.MODULE_PARAMS.get(m)) {
                keys.add(p + "param." + f.key());
                keys.add(p + "param." + f.key() + ".desc");
            }
        }
        for (ValueForms.Form f : ValueForms.Form.values()) {
            keys.add(p + "form." + f.name().toLowerCase(Locale.ROOT));
            keys.add(p + "form." + f.name().toLowerCase(Locale.ROOT) + ".desc");
        }
        for (Easing e : Easing.values()) {
            keys.add(p + "ease." + e.configName());
        }
        choices(keys, "fracture", FxDef.Fracture.MODES);
        choices(keys, "trigger", FxDef.Trigger.TYPES);
        choices(keys, "blend", FxDef.Overlay.BLENDS);
        choices(keys, "zone", FxDef.Emitter.ZONES);
        choices(keys, "edge_dir", FxDef.Edge.DIRECTIONS);
        choices(keys, "target", FxDef.Target.GROUPS);
        choices(keys, "time", List.of("local", "global"));
        choices(keys, "direction", List.of("horizontal", "vertical"));
        choices(keys, "point", List.of("center", "source", "player", "fixed"));
        choices(keys, "template", Templates.IDS);
        for (net.shattergui.ui.preview.SampleImage.Kind kind : net.shattergui.ui.preview.SampleImage.Kind.values()) {
            keys.add(p + "sample." + kind.id());
        }
        for (String w : List.of("up", "down", "left", "right", "up_left", "up_right", "down_left", "down_right")) {
            keys.add(p + "wave." + w);
        }
        for (String t : List.of("layers", "preview", "timeline", "inspector")) {
            keys.add(p + "tour." + t + ".title");
            keys.add(p + "tour." + t + ".text");
        }
        for (String kind : List.of("effect", "appearance", "edge")) {
            keys.add(p + "part." + kind);
            keys.add(p + "part." + kind + ".desc");
        }
        for (String g : List.of("layers", "emitters", "sounds")) {
            keys.add(p + "group." + g);
            keys.add(p + "group." + g + ".empty");
        }
        for (FxDef.TextureRole role : FxDef.TextureRole.values()) {
            keys.add(p + "field.texture.role." + role.name().toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private static void choices(List<String> keys, String prefix, List<String> values) {
        for (String v : values) {
            keys.add("ui.shatter_gui.fx." + prefix + "." + v);
            keys.add("ui.shatter_gui.fx." + prefix + "." + v + ".desc");
        }
    }

    /**
     * The tooltip text of every parameter (settings and module), module type, list value, tab and help page — each one
     * shown when its control is hovered or focused.
     */
    private static List<String> tooltipKeys() {
        List<String> keys = new ArrayList<>();
        String p = "ui.shatter_gui.fx.";
        for (FxDef.Module m : FxDef.Module.values()) {
            keys.add(p + "module." + FxSchema.moduleName(m) + ".desc");
            for (FxSchema.Field f : FxSchema.MODULE_PARAMS.get(m)) {
                keys.add(p + "param." + f.key() + ".desc");
            }
        }
        for (String group : net.shattergui.params.Params.groups()) {
            for (net.shattergui.params.Param<?> param : net.shattergui.params.Params.group(group)) {
                keys.add(param.langKey() + ".tooltip");
            }
        }
        for (List<String> values : List.of(FxDef.Fracture.MODES, FxDef.Trigger.TYPES, FxDef.Overlay.BLENDS,
                FxDef.Emitter.ZONES, FxDef.Edge.DIRECTIONS, FxDef.Target.GROUPS, FxDef.Layer.RESTS)) {
            for (String v : values) {
                keys.add(values == FxDef.Fracture.MODES ? p + "fracture." + v + ".desc"
                        : values == FxDef.Trigger.TYPES ? p + "trigger." + v + ".desc"
                        : values == FxDef.Overlay.BLENDS ? p + "blend." + v + ".desc"
                        : values == FxDef.Emitter.ZONES ? p + "zone." + v + ".desc"
                        : values == FxDef.Edge.DIRECTIONS ? p + "edge_dir." + v + ".desc"
                        : values == FxDef.Target.GROUPS ? p + "target." + v + ".desc" : p + "rest." + v + ".desc");
            }
        }
        for (String tab : List.of("general", "effects", "screens", "custom")) {
            keys.add("ui.shatter_gui.tab." + tab + ".desc");
        }
        // Choosing effects on the Effects tab: the buttons under the selected card, the star, the badges and the
        // preview's direction.
        for (String key : List.of("assign.open.tooltip", "assign.open.none.tooltip", "assign.close.tooltip",
                "assign.both.tooltip", "assign.remove.tooltip", "favorite.tooltip", "badge.tooltip",
                "preview.mode.open.desc", "preview.mode.close.desc", "preview.mode.both.desc", "fx.preview.opening.desc")) {
            keys.add("ui.shatter_gui." + key);
        }
        for (String tab : List.of("layers", "textures")) {
            keys.add(p + "left." + tab + ".desc");
        }
        for (String page : HelpDialog.PAGES) {
            keys.add(p + "help." + page + ".title");
            keys.add(p + "help." + page + ".text");
        }
        return keys;
    }

    @Test
    void everyControlHasAShortTooltip() throws IOException {
        for (String code : List.of("en_us", "uk_ua")) {
            JsonObject lang = lang(code);
            List<String> missing = new ArrayList<>();
            List<String> badLength = new ArrayList<>();
            for (String key : tooltipKeys()) {
                if (!lang.has(key)) {
                    missing.add(key);
                    continue;
                }
                int length = lang.get(key).getAsString().length();
                // Help pages are a few sentences; a tooltip one or two (plus the range line the code adds).
                int max = key.contains(".help.") ? 520 : 260;
                int min = key.endsWith(".title") ? 2 : 8;
                if (length < min || length > max) {
                    badLength.add(key + " (" + length + ")");
                }
            }
            assertTrue(missing.isEmpty(), code + " has no tooltip for " + missing);
            assertTrue(badLength.isEmpty(), code + " tooltips of odd length: " + badLength);
        }
    }

    @Test
    void everythingTranslated() throws IOException {
        for (String code : List.of("en_us", "uk_ua")) {
            JsonObject lang = lang(code);
            List<String> missing = dynamicKeys().stream().filter(k -> !lang.has(k)).toList();
            assertTrue(missing.isEmpty(), code + " misses " + missing);
        }
        // Both languages have the same editor keys.
        JsonObject en = lang("en_us");
        JsonObject uk = lang("uk_ua");
        for (String key : en.keySet()) {
            if (key.startsWith("ui.shatter_gui.fx.")) {
                assertTrue(uk.has(key), "uk_ua misses " + key);
            }
        }
    }
}
