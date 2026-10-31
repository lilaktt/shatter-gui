package net.shattergui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.shattergui.fx.Easing;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxProblems;
import net.shattergui.fx.FxSchema;
import net.shattergui.ui.Button;
import net.shattergui.ui.Column;
import net.shattergui.ui.Container;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Dropdown;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Hint;
import net.shattergui.ui.Label;
import net.shattergui.ui.Section;
import net.shattergui.ui.Segmented;
import net.shattergui.ui.Row;
import net.shattergui.ui.ScrollView;
import net.shattergui.ui.Slider;
import net.shattergui.ui.TextField;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Toggle;
import net.shattergui.ui.Ui;
import net.shattergui.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The right panel: every setting of what is selected, with human names and units, an exact-value box on each slider,
 * "back to default", curves (presets, Bézier handles, keyframes), colour gradients, targets, textures, particles and
 * sounds with search. Rebuilt when the selection or the shape of the effect changes; in between the controls read
 * the effect live, so undo, the timeline and the preview stay in step.
 */
final class Inspector extends Container {
    private static final JsonElement ALPHA_DEFAULT = JsonParser.parseString("{\"keys\": [[0, 1], [0.75, 1], [1, 0]]}");
    private static final JsonElement EMITTER_ALPHA_DEFAULT = JsonParser.parseString("{\"keys\": [[0, 1], [0.7, 1], [1, 0]]}");
    private static final JsonElement PATH_PROGRESS_DEFAULT = JsonParser.parseString("{\"curve\": \"smooth\", \"from\": 0, \"to\": 1}");
    private static List<String> particles;
    private static List<String> sounds;

    private final FxEditor editor;
    private final Column content = new Column(0, 6);
    /** Where the builders put rows: the current card. */
    private Container target = content;
    private final ScrollView scroll = new ScrollView(content);

    Inspector(FxEditor editor) {
        this.editor = editor;
        add(scroll);
    }

    @Override
    protected void arrange() {
        scroll.setBounds(x, y + 18, w, h - 18);
    }

    @Override
    protected void draw(Ui ui) {
        Selection s = editor.selection();
        Component title = switch (s.kind()) {
            case EFFECT -> EditorUi.tr("part.effect");
            case APPEARANCE -> EditorUi.tr("part.appearance");
            case EDGE -> EditorUi.tr("part.edge");
            default -> LayerPanel.itemName(editor.doc, s.kind(), s.index());
        };
        Icon icon = switch (s.kind()) {
            case EFFECT -> Icon.GRID;
            case APPEARANCE -> Icon.PALETTE;
            case EDGE -> Icon.EDGE;
            default -> LayerPanel.itemIcon(editor.doc, s.kind(), s.index());
        };
        Draw.icon(ui, icon, x + 2, y + 4, 1, Theme.accentHover());
        List<FxProblems.Problem> problems = editor.problemsAt(s.path(), false);
        long errors = problems.stream().filter(FxProblems.Problem::error).count();
        int right = x + w;
        if (!problems.isEmpty()) {
            Component count = EditorUi.tr("inspector.problems", errors, problems.size() - errors);
            int cw = ui.font.width(count);
            ui.text(count, right - cw - 2, y + 5, errors > 0 ? 0xFFFCA5A5 : 0xFFFCD34D);
            right -= cw + 6;
        }
        ui.textFit(title, x + 15, y + 5, right - x - 17, Theme.TEXT);
        Draw.rect(ui, x, y + 16, w, 1, Theme.BORDER);
        super.draw(ui);
    }

    /** Builds the controls for the current selection. */
    void rebuild() {
        content.clear();
        target = content;
        Selection s = editor.selection();
        // Problems about the item as a whole (not a single field).
        for (FxProblems.Problem p : editor.problemsAt(s.path(), true)) {
            content.add(new Note(p));
        }
        try {
            switch (s.kind()) {
                case EFFECT -> buildEffect();
                case APPEARANCE -> buildAppearance();
                case EDGE -> buildEdge();
                case LAYER -> buildLayer(s.path());
                case EMITTER -> buildEmitter(s.path());
                case SOUND -> buildSound(s.path());
            }
        } catch (RuntimeException e) {
            // Odd data the controls cannot show: say so instead of failing.
            content.add(new Label(EditorUi.tr("inspector.cannot_show", String.valueOf(e.getMessage())), Label.Style.DIM));
        }
    }

    // --- pages ---

    private void buildEffect() {
        section("section.general", false);
        TextField id = new TextField(32).filter(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_');
        id.value(editor.doc.str("id", ""));
        id.onChange(v -> editor.doc.set("id", v));
        field("field.id", "id", id, null, null);
        TextField name = new TextField(48);
        name.value(editor.doc.str("name", ""));
        name.onChange(v -> editor.doc.set("name", v));
        name.hint(EditorUi.tr("field.name.hint"));
        field("field.name", "name", name, null, null);
        TextField description = new TextField(200);
        description.value(editor.doc.str("description", ""));
        description.onChange(v -> editor.doc.set("description", v));
        field("field.description", "description", description, null, null);
        number("field.duration", "duration", "effect.duration");

        section("section.fracture", false);
        String mode = editor.doc.str("fracture.mode", "grid");
        choice("field.fracture_mode", "fracture.mode", FxDef.Fracture.MODES, "fracture", "grid", true);
        switch (mode) {
            case "voronoi" -> number("field.count", "fracture.count", "fracture.voronoiCount");
            case "radial" -> {
                number("field.sectors", "fracture.count", "fracture.radialCount");
                number("field.rings", "fracture.rings", "fracture.rings");
                point("field.center", "fracture.center", false);
            }
            case "strips" -> {
                number("field.count", "fracture.count", "fracture.count");
                choice("field.strips_direction", "fracture.direction", List.of("horizontal", "vertical"), "direction", "horizontal", false);
                number("field.segments", "fracture.segments", "fracture.segments");
            }
            case "pixels" -> number("field.pixel_size", "fracture.size", "fracture.size");
            default -> {
                number("field.count", "fracture.count", "fracture.count");
                number("field.cols", "fracture.cols", "fracture.cols");
                number("field.rows", "fracture.rows", "fracture.rows");
                toggle("field.split", "fracture.split", false);
            }
        }
        if (!"pixels".equals(mode)) {
            number("field.jitter", "fracture.jitter", "fracture.jitter");
        }

        section("section.trigger", false);
        String type = editor.doc.str("trigger.type", "wave");
        choice("field.trigger_type", "trigger.type", FxDef.Trigger.TYPES, "trigger", "wave", true);
        switch (type) {
            case "wave" -> direction();
            case "radial" -> point("field.trigger_from", "trigger.from", false);
            case "noise" -> number("field.noise_scale", "trigger.scale", "trigger.scale");
            case "mask" -> {
                texture("field.trigger_texture", "trigger.texture", FxDef.TextureRole.MASK, true);
                toggle("field.invert", "trigger.invert", false);
            }
            default -> {
            }
        }
        number("field.spread", "trigger.spread", "trigger.spread");
        section("section.advanced", true);
        number("field.trigger_noise", "trigger.noise", "trigger.noise");
        easing("field.trigger_curve", "trigger.curve", Easing.LINEAR);
        region();
    }

    private void buildAppearance() {
        section("section.color", false);
        gradient("field.color", "appearance.color", 0xFFFFFFFF);
        choice("field.color_time", "appearance.time", List.of("local", "global"), "time", "local", false);
        value("field.alpha", "appearance.alpha", "appearance.alpha", ALPHA_DEFAULT, null);
        section("section.glow", false);
        value("field.emissive", "appearance.emissive", "appearance.emissive", null, null);
        gradient("field.emissive_color", "appearance.emissiveColor", 0xFFFFFFFF);
        toggle("field.fullbright", "appearance.fullbright", false);

        section("section.overlays", editor.doc.size("appearance.overlays") == 0);
        int overlays = editor.doc.size("appearance.overlays");
        for (int i = 0; i < overlays; i++) {
            String p = "appearance.overlays[" + i + "]";
            int index = i;
            Row head = target.add(new Row(4));
            head.flex(new Label(EditorUi.tr("overlay.title", i + 1), Label.Style.BODY), 1);
            Button remove = Button.icon(Icon.TRASH, EditorUi.tr("overlay.remove"), () -> {
                editor.doc.remove("appearance.overlays[" + index + "]");
                editor.refreshInspector();
            });
            head.fixed(remove, 18);
            texture("field.texture", p + ".texture", FxDef.TextureRole.OVERLAY, true);
            choice("field.blend", p + ".blend", FxDef.Overlay.BLENDS, "blend", "normal", false);
            value("field.opacity", p + ".opacity", "overlay.opacity", null, null);
            gradient("field.overlay_color", p + ".color", 0xFFFFFFFF);
            pairSliders("field.scroll", p + ".scroll", "overlay.scroll", 0);
            pairSliders("field.repeat", p + ".repeat", "overlay.repeat", 1);
        }
        if (overlays < FxLimits.MAX_OVERLAYS) {
            Button add = new Button(EditorUi.tr("overlay.add"), Icon.PLUS, Button.Kind.SECONDARY, () -> editor.addOverlay(null));
            add.tooltip(EditorUi.tr("overlay.add"), EditorUi.tr("overlay.add.desc"));
            Row row = target.add(new Row(4));
            row.fixed(add, add.naturalWidth());
        }

        boolean hasMask = editor.doc.has("appearance.mask");
        section("section.mask", !hasMask);
        Toggle maskOn = new Toggle(() -> editor.doc.has("appearance.mask"), on -> {
            if (on) {
                JsonObject mask = new JsonObject();
                mask.addProperty("texture", firstTexture());
                editor.doc.set("appearance.mask", mask);
            } else {
                editor.doc.remove("appearance.mask");
            }
            editor.refreshInspector();
        });
        field("field.mask_on", null, maskOn, null, null);
        if (hasMask) {
            texture("field.texture", "appearance.mask.texture", FxDef.TextureRole.MASK, true);
            value("field.threshold", "appearance.mask.threshold", "mask.threshold", null, null);
            number("field.softness", "appearance.mask.softness", "mask.softness");
            toggle("field.invert", "appearance.mask.invert", false);
        }
    }

    private void buildEdge() {
        section("section.edge", false);
        if (!editor.doc.has("modifiers.edge")) {
            target.add(new Hint(EditorUi.tr("edge.off")));
            Button on = new Button(EditorUi.tr("edge.enable"), Icon.PLUS, Button.Kind.PRIMARY, editor::toggleEdge);
            on.tooltip(EditorUi.tr("edge.enable"), EditorUi.tr("part.edge.desc"));
            Row row = target.add(new Row(4));
            row.fixed(on, on.naturalWidth());
            return;
        }
        target.add(new Hint(EditorUi.tr("part.edge.desc")));
        choice("field.edge_direction", "modifiers.edge.direction", FxDef.Edge.DIRECTIONS, "edge_dir", "random", false);
        toggle("field.edge_exit", "modifiers.edge.exit", true);
        number("field.exit_start", "modifiers.edge.exitStart", "edge.exitStart");
        toggle("field.edge_enter", "modifiers.edge.enter", false);
        number("field.enter_time", "modifiers.edge.enterTime", "edge.enterTime");
        number("field.distance", "modifiers.edge.distance", "edge.distance");
        easing("field.edge_curve", "modifiers.edge.curve", Easing.EASE_IN_CUBIC);
        Button off = new Button(EditorUi.tr("edge.disable"), Icon.TRASH, Button.Kind.DANGER, editor::toggleEdge);
        off.tooltip(EditorUi.tr("edge.disable"), EditorUi.tr("edge.disable.desc"));
        Row row = target.add(new Row(4));
        row.fixed(off, off.naturalWidth());
    }

    private void buildLayer(String p) {
        section("section.layer", false);
        FxDef.Module module = FxDef.Module.byName(editor.doc.str(p + ".module", "stay"));
        Dropdown<String> moduleBox = new Dropdown<>(Inspector::modules, () -> editor.doc.str(p + ".module", "stay"), v -> {
            editor.doc.edit(null, r -> {
                FxDoc.put(r, p + ".module", new JsonPrimitive(v));
                FxDoc.delete(r, p + ".params");
            });
            editor.refreshInspector();
        }).thumbs(14, 18);
        field("field.module", p + ".module", moduleBox, null, null);
        if (module != null) {
            target.add(new Hint(EditorUi.moduleDescription(module)));
        }
        section("section.target", false);
        target.add(new FieldRow(editor, EditorUi.tr("field.target"), EditorUi.tr("field.target.desc"), p + ".target",
                new TargetControl(editor, p + ".target"), () -> editor.doc.remove(p + ".target"), () -> editor.doc.has(p + ".target")));
        section("section.window", false);
        choice("field.time_base", p + ".time", List.of("local", "global"), "time", "local", false);
        number("field.from", p + ".from", "layer.from");
        number("field.to", p + ".to", "layer.to");
        if (module == null) {
            return;
        }
        section("section.params", false);
        for (FxSchema.Field f : FxSchema.MODULE_PARAMS.get(module)) {
            String param = FxSchema.paramName(f);
            value("param." + f.key(), p + ".params." + param, f.key(), module == FxDef.Module.FOLLOW_PATH ? PATH_PROGRESS_DEFAULT : null, p);
        }
        switch (module) {
            case LAUNCH -> {
                point("field.launch_from", p + ".params.from", true);
                toggle("field.once", p + ".params.once", true);
            }
            case ATTRACT -> point("field.attract_to", p + ".params.to", true);
            case GRAVITY -> toggle("field.world", p + ".params.world", true);
            case SPIN -> number("field.tumble_axis", p + ".params.axis", "spin.axis");
            case FOLLOW_PATH -> pathPoints(p + ".params.points");
            case COLLIDE -> choice("field.rest", p + ".params.rest", FxDef.Layer.RESTS, "rest", "lie", false);
            default -> {
            }
        }
        if (module == FxDef.Module.STAY) {
            target.add(new Hint(EditorUi.tr("field.no_params")));
        }
        advanced(p);
    }

    private void buildEmitter(String p) {
        boolean sprite = editor.doc.has(p + ".sprite");
        section("section.source", false);
        Segmented kind = target.add(new Segmented());
        kind.option(EditorUi.tr("emitter.vanilla"), EditorUi.tr("catalog.particles.desc"), () -> !editor.doc.has(p + ".sprite"), () -> {
            editor.doc.edit(null, r -> {
                FxDoc.delete(r, p + ".sprite");
                FxDoc.put(r, p + ".particle", new JsonPrimitive("minecraft:end_rod"));
            });
            editor.refreshInspector();
        });
        kind.option(EditorUi.tr("emitter.sprite"), EditorUi.tr("catalog.sprites.desc"), () -> editor.doc.has(p + ".sprite"), () -> {
            String texture = firstTexture();
            editor.doc.edit(null, r -> {
                FxDoc.delete(r, p + ".particle");
                FxDoc.put(r, p + ".sprite", new JsonPrimitive(texture));
            });
            editor.refreshInspector();
        });
        if (sprite) {
            texture("field.sprite", p + ".sprite", FxDef.TextureRole.SPRITE, true);
        } else {
            SearchList list = new SearchList(Inspector::particles, () -> editor.doc.str(p + ".particle", ""),
                    v -> editor.doc.set(p + ".particle", v), 6);
            target.add(new FieldRow(editor, EditorUi.tr("field.particle"), EditorUi.tr("field.particle.desc"), p + ".particle", list,
                    null, null));
        }
        section("section.spawn", false);
        number("field.emit_from", p + ".from", "emitter.from");
        number("field.emit_to", p + ".to", "emitter.to");
        value("field.rate", p + ".rate", "emitter.rate", null, p);
        number("field.burst", p + ".burst", "emitter.burst");
        String zone = editor.doc.str(p + ".zone", "surface");
        choice("field.zone", p + ".zone", FxDef.Emitter.ZONES, "zone", "surface", true);
        if ("point".equals(zone)) {
            point("field.at", p + ".at", false);
        } else if ("line".equals(zone)) {
            point("field.line_from", p + ".lineFrom", false);
            point("field.line_to", p + ".lineTo", false);
        }
        section("section.motion", false);
        velocity(p + ".velocity");
        value("field.emit_spread", p + ".spread", "emitter.spread", null, null);
        value("field.life", p + ".life", "emitter.life", null, null);
        number("field.gravity", p + ".gravity", "emitter.gravity");
        number("field.drag", p + ".drag", "emitter.drag");
        section("section.particle_look", false);
        value("field.size", p + ".size", "emitter.size", null, null);
        if (sprite) {
            gradient("field.particle_color", p + ".color", 0xFFFFFFFF);
            value("field.particle_alpha", p + ".alpha", "emitter.alpha", EMITTER_ALPHA_DEFAULT, null);
            toggle("field.emissive_sprite", p + ".emissive", true);
            number("field.max", p + ".max", "emitter.max");
        } else {
            target.add(new Hint(EditorUi.tr("emitter.vanilla_note")));
        }
        advanced(p);
    }

    private void buildSound(String p) {
        section("section.sound", false);
        SearchList list = new SearchList(Inspector::sounds, () -> editor.doc.str(p + ".sound", ""), v -> editor.doc.set(p + ".sound", v), 7);
        target.add(new FieldRow(editor, EditorUi.tr("field.sound"), EditorUi.tr("field.sound.desc"), p + ".sound", list, null, null));
        Button listen = new Button(EditorUi.tr("sound.listen"), Icon.PLAY, Button.Kind.SECONDARY, () -> listen(p));
        listen.tooltip(EditorUi.tr("sound.listen"), EditorUi.tr("sound.listen.desc"));
        Row row = target.add(new Row(4));
        row.fixed(listen, listen.naturalWidth());
        number("field.volume", p + ".volume", "sound.volume");
        number("field.pitch", p + ".pitch", "sound.pitch");
        section("section.moment", false);
        Dropdown<String> at = new Dropdown<>(() -> List.of(
                new Dropdown.Option<>("start", EditorUi.tr("moment.start")),
                new Dropdown.Option<>("end", EditorUi.tr("moment.end")),
                new Dropdown.Option<>("time", EditorUi.tr("moment.time"))),
                () -> momentKind(p), v -> {
            switch (v) {
                case "start" -> editor.doc.remove(p + ".at");
                case "end" -> editor.doc.set(p + ".at", "end");
                default -> editor.doc.set(p + ".at", 0.5f);
            }
            editor.refreshInspector();
        });
        field("field.moment", p + ".at", at, null, null);
        if ("time".equals(momentKind(p))) {
            FxSchema.Field f = FxSchema.field("sound.time");
            Slider s = slider(f, () -> editor.doc.num(p + ".at", 0), v -> editor.doc.set(p + ".at", (float) v));
            field("field.moment_time", p + ".at", s, null, null);
        }
        advanced(p);
    }

    private String momentKind(String p) {
        JsonElement at = editor.doc.get(p + ".at");
        if (at != null && at.isJsonPrimitive() && at.getAsJsonPrimitive().isNumber()) {
            return "time";
        }
        return "end".equals(editor.doc.str(p + ".at", "start")) ? "end" : "start";
    }

    private void listen(String p) {
        Identifier id = Identifier.tryParse(editor.doc.str(p + ".sound", ""));
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            editor.status(EditorUi.tr("sound.unknown"), false);
            return;
        }
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(id);
        float pitch = Math.clamp(editor.doc.num(p + ".pitch", 1), 0.5f, 2f);
        float volume = Math.clamp(editor.doc.num(p + ".volume", 1), 0f, 1f);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    // --- common parts of list items ---

    private void itemHead(String p) {
        TextField name = new TextField(FxParser.MAX_LABEL);
        name.value(editor.doc.str(p + ".name", ""));
        name.hint(LayerPanel.itemName(editor.doc, editor.selection().kind(), editor.selection().index()));
        name.onChange(v -> {
            if (v.isBlank()) {
                editor.doc.remove(p + ".name");
            } else {
                editor.doc.set(p + ".name", v);
            }
        });
        field("field.item_name", p + ".name", name, null, null);
        Toggle shown = new Toggle(() -> editor.doc.bool(p + ".enabled", true), on -> {
            if (on) {
                editor.doc.remove(p + ".enabled");
            } else {
                editor.doc.set(p + ".enabled", false);
            }
        });
        field("field.enabled", p + ".enabled", shown, null, null);
    }

    private void itemActions(String p) {
        Selection s = editor.selection();
        Row row = target.add(new Row(4));
        Button dup = new Button(EditorUi.tr("item.duplicate"), Icon.COPY, Button.Kind.SECONDARY, () -> editor.duplicate(s));
        dup.tooltip(EditorUi.tr("item.duplicate"), EditorUi.tr("item.duplicate.desc"));
        Button del = new Button(EditorUi.tr("item.delete"), Icon.TRASH, Button.Kind.DANGER, () -> editor.removeItem(s));
        del.tooltip(EditorUi.tr("item.delete"), EditorUi.tr("item.delete.desc"));
        row.fixed(dup, dup.naturalWidth());
        row.fixed(del, del.naturalWidth());
    }

    // --- field builders ---

    /** Starts a card; the rows after it go into it. Rare groups start folded. */
    private void section(String key, boolean folded) {
        Section section = new Section("inspector." + key, sectionIcon(key), EditorUi.tr(key), folded);
        String page = helpPage(key);
        if (page != null) {
            HelpDialog.attach(section, page);
        }
        content.add(section);
        target = section;
    }

    /** The help page a card's "?" opens (the cards whose idea takes more than a tooltip), or null. */
    private static String helpPage(String key) {
        return switch (key) {
            case "section.layer" -> "layers";
            case "section.target" -> "targets";
            case "section.window", "section.trigger" -> "timing";
            case "section.overlays", "section.mask" -> "textures";
            case "section.source", "section.spawn" -> "particles";
            case "section.fracture" -> "limits";
            default -> null;
        };
    }

    /** A field's help with its default value (a toggle's, a choice's). */
    private static Component withDefault(Component help, Component value) {
        return help.copy().append("\n").append(Component.translatable("ui.shatter_gui.default", value));
    }

    private static Icon sectionIcon(String key) {
        return switch (key) {
            case "section.general" -> Icon.GEAR;
            case "section.fracture" -> Icon.GRID;
            case "section.trigger" -> Icon.BOLT;
            case "section.color" -> Icon.PALETTE;
            case "section.glow" -> Icon.SPARKLES;
            case "section.overlays" -> Icon.LAYERS;
            case "section.mask" -> Icon.EYE;
            case "section.edge" -> Icon.EDGE;
            case "section.layer" -> Icon.LAYERS;
            case "section.target" -> Icon.STAR;
            case "section.window" -> Icon.REPLAY;
            case "section.params" -> Icon.GEAR;
            case "section.path" -> Icon.PATH;
            case "section.source" -> Icon.SPARKLES;
            case "section.spawn" -> Icon.PLUS;
            case "section.motion" -> Icon.WIND;
            case "section.particle_look" -> Icon.PALETTE;
            case "section.sound" -> Icon.NOTE;
            case "section.moment" -> Icon.REPLAY;
            default -> Icon.INFO;
        };
    }

    /** "Advanced", folded: the item's own name, whether it plays, duplicate and delete. */
    private void advanced(String p) {
        section("section.advanced", true);
        itemHead(p);
        itemActions(p);
    }

    private FieldRow field(String key, String path, Widget control, Runnable reset, BooleanSupplier changed) {
        return field(key, EditorUi.tr(key + ".desc"), path, control, reset, changed);
    }

    private FieldRow field(String key, Component help, String path, Widget control, Runnable reset, BooleanSupplier changed) {
        return target.add(new FieldRow(editor, EditorUi.tr(key), help, path, control, reset, changed));
    }

    private Slider slider(FxSchema.Field f, java.util.function.DoubleSupplier getter, java.util.function.DoubleConsumer setter) {
        return new Slider(f.min(), f.max(), EditorUi.step(f), getter, v -> setter.accept(f.whole() ? Math.round(v) : v),
                v -> EditorUi.format(f.unit(), v), t -> EditorUi.parse(f.unit(), t));
    }

    /** A plain number (never a curve). */
    private void number(String key, String path, String fieldKey) {
        FxSchema.Field f = FxSchema.field(fieldKey);
        Slider s = slider(f, () -> editor.doc.num(path, f.def()), v -> editor.doc.set(path, (float) v));
        field(key, EditorUi.withRange(EditorUi.tr(key + ".desc"), f, true), path, s, () -> editor.doc.remove(path),
                () -> editor.doc.has(path));
    }

    /** A number that may be a curve. */
    private void value(String key, String path, String fieldKey, JsonElement def, String window) {
        FxSchema.Field f = FxSchema.field(fieldKey);
        ValueControl control = new ValueControl(editor, path, f, def, window);
        target.add(new FieldRow(editor, EditorUi.tr(key), EditorUi.withRange(EditorUi.tr(key + ".desc"), f, def == null), path,
                control, () -> {
            editor.doc.remove(path);
            editor.refreshInspector();
        }, () -> editor.doc.has(path)));
    }

    private void toggle(String key, String path, boolean def) {
        Toggle t = new Toggle(() -> editor.doc.bool(path, def), v -> {
            if (v == def) {
                editor.doc.remove(path);
            } else {
                editor.doc.set(path, v);
            }
        });
        field(key, withDefault(EditorUi.tr(key + ".desc"), Component.translatable(def ? "options.on" : "options.off")), path, t,
                null, null);
    }

    /** One of several names; {@code rebuild}: the controls below depend on it. */
    private void choice(String key, String path, List<String> values, String prefix, String def, boolean rebuild) {
        Dropdown<String> box = new Dropdown<>(() -> {
            List<Dropdown.Option<String>> options = new ArrayList<>();
            for (String v : values) {
                options.add(new Dropdown.Option<>(v, EditorUi.tr(prefix + "." + v), EditorUi.tr(prefix + "." + v + ".desc"), null));
            }
            return options;
        }, () -> editor.doc.str(path, def), v -> {
            if (v.equals(editor.doc.str(path, def))) {
                return;
            }
            editor.doc.set(path, v);
            if (rebuild) {
                editor.refreshInspector();
            }
        });
        field(key, withDefault(EditorUi.tr(key + ".desc"), EditorUi.tr(prefix + "." + def)), path, box,
                () -> editor.doc.remove(path), () -> editor.doc.has(path));
    }

    private void easing(String key, String path, Easing def) {
        Dropdown<Easing> box = new Dropdown<>(ValueControl::easings, () -> {
            Easing e = Easing.byName(editor.doc.str(path, def.configName()));
            return e == null ? def : e;
        }, e -> editor.doc.set(path, e.configName())).thumbs(22, 18);
        field(key, path, box, () -> editor.doc.remove(path), () -> editor.doc.has(path));
    }

    private void gradient(String key, String path, int def) {
        field(key, path, new GradientEditor(editor, path, def), () -> editor.doc.remove(path), () -> editor.doc.has(path));
    }

    private void texture(String key, String path, FxDef.TextureRole role, boolean required) {
        int size = role == FxDef.TextureRole.SPRITE ? FxLimits.MAX_SPRITE_SIZE : FxLimits.MAX_OVERLAY_SIZE;
        Dropdown<String> box = new Dropdown<>(() -> textureOptions(path), () -> editor.doc.str(path, ""), v -> {
            if (v.isEmpty()) {
                editor.doc.remove(path);
            } else {
                editor.doc.set(path, v);
            }
        }).thumbs(18, 22);
        field(key, path, box, null, null).tooltip(EditorUi.tr(key), EditorUi.tr("field.texture.role." + role.name()
                .toLowerCase(Locale.ROOT), size, size));
    }

    private List<Dropdown.Option<String>> textureOptions(String path) {
        List<Dropdown.Option<String>> out = new ArrayList<>();
        out.add(new Dropdown.Option<>("", EditorUi.tr("texture.none")));
        String current = editor.doc.str(path, "");
        boolean listed = current.isEmpty();
        for (TextureLibrary.Entry e : editor.library.entries()) {
            if (!e.ok()) {
                continue;
            }
            listed |= e.name().equals(current);
            out.add(new Dropdown.Option<>(e.name(), Component.literal(e.name()), EditorUi.tr("textures.info", e.width(), e.height(),
                    Math.max(1, e.bytes() / 1024)), (ui, x, y, w, h) -> {
                EditorUi.checker(ui, x, y, w, h, 3);
                Draw.texture(ui, e.tex().gpu(), x, y, w, h, 0xFFFFFFFF);
            }));
        }
        if (!listed) {
            out.add(new Dropdown.Option<>(current, EditorUi.tr("texture.missing", current)));
        }
        return out;
    }

    private String firstTexture() {
        for (TextureLibrary.Entry e : editor.library.entries()) {
            if (e.ok()) {
                return e.name();
            }
        }
        return "texture";
    }

    /** "center", "source", "player" or [u, v]. */
    private void point(String key, String path, boolean allowFar) {
        target.add(new FieldRow(editor, EditorUi.tr(key), EditorUi.tr(key + ".desc"), path, new PointControl(path, allowFar),
                () -> {
                    editor.doc.remove(path);
                    editor.refreshInspector();
                }, () -> editor.doc.has(path)));
    }

    private final class PointControl extends Column {
        PointControl(String path, boolean allowFar) {
            super(0, 3);
            List<String> kinds = allowFar ? List.of("center", "source", "player", "fixed") : List.of("center", "fixed");
            add(new Dropdown<>(() -> {
                List<Dropdown.Option<String>> out = new ArrayList<>();
                for (String k : kinds) {
                    out.add(new Dropdown.Option<>(k, EditorUi.tr("point." + k), EditorUi.tr("point." + k + ".desc"), null));
                }
                return out;
            }, () -> kindOf(path), v -> {
                if (v.equals(kindOf(path))) {
                    return;
                }
                if ("fixed".equals(v)) {
                    JsonArray uv = new JsonArray();
                    uv.add(FxDoc.number(0.5));
                    uv.add(FxDoc.number(0.5));
                    editor.doc.set(path, uv);
                } else {
                    editor.doc.set(path, v);
                }
                editor.refreshInspector();
            }));
            if ("fixed".equals(kindOf(path))) {
                FxSchema.Field f = new FxSchema.Field("point", 0.5f, 0, 1, FxSchema.Unit.FRACTION);
                add(ValueControl.labelled("point.u", slider(f, () -> component(path, 0, 0.5f), v -> setComponent(path, 0, (float) v, 2,
                        0.5f))));
                add(ValueControl.labelled("point.v", slider(f, () -> component(path, 1, 0.5f), v -> setComponent(path, 1, (float) v, 2,
                        0.5f))));
            }
        }
    }

    private String kindOf(String path) {
        JsonElement e = editor.doc.get(path);
        if (e != null && e.isJsonArray()) {
            return "fixed";
        }
        return editor.doc.str(path, "center");
    }

    private float component(String path, int i, float def) {
        JsonElement e = editor.doc.get(path);
        try {
            return e.getAsJsonArray().get(i).getAsFloat();
        } catch (RuntimeException ex) {
            return def;
        }
    }

    /** Sets one number of a fixed-size list (creating the list with defaults first). */
    private void setComponent(String path, int i, float value, int size, float... defaults) {
        editor.doc.edit(path + "[" + i + "]", r -> {
            JsonElement e = FxDoc.get(r, path);
            JsonArray array = new JsonArray();
            for (int k = 0; k < size; k++) {
                float v;
                try {
                    v = e.getAsJsonArray().get(k).getAsFloat();
                } catch (RuntimeException ex) {
                    v = defaults.length == 0 ? 0 : defaults[Math.min(k, defaults.length - 1)];
                }
                array.add(FxDoc.number(k == i ? value : v));
            }
            FxDoc.put(r, path, array);
        });
    }

    private void pairSliders(String key, String path, String fieldKey, float def) {
        FxSchema.Field f = FxSchema.field(fieldKey);
        Column both = new Column(0, 3);
        both.add(ValueControl.labelled("axis.u", slider(f, () -> component(path, 0, def), v -> setComponent(path, 0, (float) v, 2, def))));
        both.add(ValueControl.labelled("axis.v", slider(f, () -> component(path, 1, def), v -> setComponent(path, 1, (float) v, 2, def))));
        field(key, path, both, () -> editor.doc.remove(path), () -> editor.doc.has(path));
    }

    private void velocity(String path) {
        FxSchema.Field f = FxSchema.field("emitter.velocity");
        float[] defaults = {0, 0.02f, 0};
        Column all = new Column(0, 3);
        String[] axes = {"axis.x", "axis.y", "axis.z"};
        for (int i = 0; i < 3; i++) {
            int k = i;
            all.add(ValueControl.labelled(axes[i], slider(f, () -> component(path, k, defaults[k]),
                    v -> setComponent(path, k, (float) v, 3, defaults))));
        }
        field("field.velocity", path, all, () -> editor.doc.remove(path), () -> editor.doc.has(path));
    }

    private void region() {
        String path = "fracture.region";
        Toggle on = new Toggle(() -> editor.doc.has(path), v -> {
            if (v) {
                JsonArray r = new JsonArray();
                for (float f : new float[]{0.2f, 0.2f, 0.8f, 0.8f}) {
                    r.add(FxDoc.number(f));
                }
                editor.doc.set(path, r);
            } else {
                editor.doc.remove(path);
            }
            editor.refreshInspector();
        });
        field("field.region", path, on, null, null);
        if (editor.doc.has(path)) {
            FxSchema.Field f = new FxSchema.Field("region", 0, 0, 1, FxSchema.Unit.FRACTION);
            float[] defaults = {0.2f, 0.2f, 0.8f, 0.8f};
            String[] keys = {"region.left", "region.top", "region.right", "region.bottom"};
            Column all = new Column(0, 3);
            for (int i = 0; i < 4; i++) {
                int k = i;
                all.add(ValueControl.labelled(keys[i], slider(f, () -> component(path, k, defaults[k]),
                        v -> setComponent(path, k, (float) v, 4, defaults))));
            }
            target.add(all);
        }
    }

    /** Wave direction: where the front moves to. */
    private void direction() {
        String path = "trigger.direction";
        String[] names = {"up", "down", "left", "right", "up_left", "up_right", "down_left", "down_right"};
        float[][] vectors = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        Dropdown<String> box = new Dropdown<>(() -> {
            List<Dropdown.Option<String>> out = new ArrayList<>();
            for (String n : names) {
                out.add(new Dropdown.Option<>(n, EditorUi.tr("wave." + n)));
            }
            return out;
        }, () -> {
            float u = component(path, 0, 0);
            float v = component(path, 1, -1);
            int best = 0;
            double bestDot = -2;
            double len = Math.max(1e-6, Math.hypot(u, v));
            for (int i = 0; i < vectors.length; i++) {
                double vl = Math.hypot(vectors[i][0], vectors[i][1]);
                double dot = (u * vectors[i][0] + v * vectors[i][1]) / (len * vl);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = i;
                }
            }
            return names[best];
        }, n -> {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(n)) {
                    JsonArray d = new JsonArray();
                    d.add(FxDoc.number(vectors[i][0]));
                    d.add(FxDoc.number(vectors[i][1]));
                    editor.doc.set(path, d);
                }
            }
        });
        field("field.wave_direction", path, box, () -> editor.doc.remove(path), () -> editor.doc.has(path));
    }

    /** follow-path points: up to 8 offsets, each x, y (and z) in interface widths. */
    private void pathPoints(String path) {
        section("section.path", false);
        int n = Math.max(0, editor.doc.size(path));
        FxSchema.Field f = new FxSchema.Field("path", 0, -1, 1, FxSchema.Unit.FACTOR);
        for (int i = 0; i < n; i++) {
            String p = path + "[" + i + "]";
            int index = i;
            Row head = target.add(new Row(4));
            head.flex(new Label(EditorUi.tr("path.point", i + 1), Label.Style.BODY), 1);
            Button remove = Button.icon(Icon.TRASH, EditorUi.tr("path.remove"), () -> {
                editor.doc.remove(path + "[" + index + "]");
                editor.refreshInspector();
            });
            remove.enabled = n > 1;
            head.fixed(remove, 18);
            Column xyz = new Column(0, 3);
            String[] axes = {"axis.x", "axis.y", "axis.z"};
            for (int k = 0; k < 3; k++) {
                int axis = k;
                xyz.add(ValueControl.labelled(axes[k], slider(f, () -> component(p, axis, 0), v -> setComponent(p, axis, (float) v, 3, 0))));
            }
            target.add(new FieldRow(editor, EditorUi.tr("path.offset"), EditorUi.tr("path.offset.desc"), p, xyz, null, null));
        }
        if (n < FxLimits.MAX_PATH_POINTS) {
            Button add = new Button(EditorUi.tr("path.add"), Icon.PLUS, Button.Kind.SECONDARY, () -> {
                JsonArray point = new JsonArray();
                point.add(FxDoc.number(0.1 * (n + 1)));
                point.add(FxDoc.number(-0.1));
                editor.doc.append(path, point);
                editor.refreshInspector();
            });
            add.tooltip(EditorUi.tr("path.add"), EditorUi.tr("path.add.desc"));
            Row row = target.add(new Row(4));
            row.fixed(add, add.naturalWidth());
        }
    }

    /** A segmented choice button. */
    private static Button segment(Component text, BooleanSupplier on, Runnable action) {
        return new Button(text, null, Button.Kind.GHOST, action) {
            @Override
            protected void draw(Ui ui) {
                selected(on.getAsBoolean());
                super.draw(ui);
            }
        };
    }

    // --- registries ---

    private static List<Dropdown.Option<String>> modules() {
        List<Dropdown.Option<String>> out = new ArrayList<>();
        for (FxDef.Module m : FxDef.Module.values()) {
            out.add(new Dropdown.Option<>(FxSchema.moduleName(m), EditorUi.moduleName(m), EditorUi.moduleDescription(m),
                    (ui, x, y, w, h) -> {
                        Draw.roundRect(ui, x, y, w, h, 3, Theme.fade(EditorUi.color(m), 0.3f));
                        Draw.icon(ui, EditorUi.icon(m), x + (w - 9) / 2, y + (h - 9) / 2, 1, 0xFFFFFFFF);
                    }));
        }
        return out;
    }

    static List<String> particles() {
        if (particles == null) {
            List<String> out = new ArrayList<>();
            for (var entry : BuiltInRegistries.PARTICLE_TYPE.entrySet()) {
                if (entry.getValue() instanceof SimpleParticleType) {
                    out.add(entry.getKey().identifier().toString());
                }
            }
            out.sort(String::compareTo);
            particles = List.copyOf(out);
        }
        return particles;
    }

    static List<String> sounds() {
        if (sounds == null) {
            List<String> out = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
                out.add(id.toString());
            }
            out.sort(String::compareTo);
            sounds = List.copyOf(out);
        }
        return sounds;
    }

    /** A problem about the item as a whole. */
    private static final class Note extends Widget {
        private final FxProblems.Problem problem;

        Note(FxProblems.Problem problem) {
            this.problem = problem;
        }

        @Override
        protected int measure(int width) {
            return Minecraft.getInstance().font.split(Component.literal(problem.message()), Math.max(20, width - 14)).size()
                    * Theme.LINE + 4;
        }

        @Override
        protected void draw(Ui ui) {
            int color = problem.error() ? Theme.DANGER : Theme.WARNING;
            Draw.roundRect(ui, x, y, w, h, 3, Theme.fade(color, 0.15f));
            Draw.icon(ui, Icon.WARNING, x + 2, y + 2, 1, color);
            ui.textWrapped(Component.literal(problem.message()), x + 13, y + 2, w - 14, problem.error() ? 0xFFFCA5A5 : 0xFFFCD34D);
        }
    }

    /** Placeholder-safe supplier of the current selection's title (kept for tests). */
    Supplier<Component> title() {
        return () -> LayerPanel.itemName(editor.doc, editor.selection().kind(), editor.selection().index());
    }
}
