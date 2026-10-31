package net.shattergui.editor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxProblems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the editor checks on top of the parser, in the player's language: the id (taken by a built-in effect or by
 * another file), textures (not in the folder, unusable, too large for a sprite, too much together), and particles and
 * sounds the game does not know.
 */
final class EditorChecks {
    /** A texture reference: where it is written and what it is for. */
    record TextureRef(String path, String name, boolean sprite) {
    }

    private EditorChecks() {
    }

    static List<FxProblems.Problem> check(FxEditor editor) {
        List<FxProblems.Problem> out = new ArrayList<>();
        FxDoc doc = editor.doc;
        String id = doc.str("id", "");
        if (FxParser.ID.matcher(id).matches()) {
            if (ShatterEffects.isReserved(id)) {
                out.add(error("id", EditorUi.tr("check.id_builtin", id).getString()));
            } else if (!id.equals(editor.fileId()) && editor.files.exists(id)) {
                out.add(error("id", EditorUi.tr("check.id_taken", id).getString()));
            }
        }
        // Textures.
        Map<String, Long> used = new LinkedHashMap<>();
        for (TextureRef ref : textureRefs(doc)) {
            if (ref.name().contains(":")) {
                continue;
            }
            TextureLibrary.Entry entry = editor.library.get(ref.name());
            if (entry == null) {
                out.add(warn(ref.path(), EditorUi.tr("check.texture_missing", ref.name()).getString()));
                continue;
            }
            if (!entry.ok()) {
                out.add(error(ref.path(), entry.error().getString()));
                continue;
            }
            if (ref.sprite() && (entry.width() > FxLimits.MAX_SPRITE_SIZE || entry.height() > FxLimits.MAX_SPRITE_SIZE)) {
                out.add(error(ref.path(), EditorUi.tr("check.sprite_too_large", entry.width(), entry.height(),
                        FxLimits.MAX_SPRITE_SIZE).getString()));
            }
            used.put(ref.name(), entry.bytes());
        }
        long bytes = used.values().stream().mapToLong(Long::longValue).sum();
        if (bytes > FxLimits.MAX_TEXTURE_BYTES) {
            out.add(error("", EditorUi.tr("check.textures_bytes", bytes / 1024, FxLimits.MAX_TEXTURE_BYTES / 1024).getString()));
        }
        // Particles and sounds the game does not know.
        List<String> particles = Inspector.particles();
        for (int i = 0; i < doc.size("emitters"); i++) {
            String particle = doc.str("emitters[" + i + "].particle", null);
            if (particle != null) {
                String full = particle.contains(":") ? particle : "minecraft:" + particle;
                if (!particles.contains(full)) {
                    out.add(warn("emitters[" + i + "].particle", EditorUi.tr("check.particle_unknown", particle).getString()));
                }
            }
        }
        for (int i = 0; i < doc.size("sounds"); i++) {
            String sound = doc.str("sounds[" + i + "].sound", null);
            if (sound != null) {
                Identifier sid = Identifier.tryParse(sound.contains(":") ? sound : "minecraft:" + sound);
                if (sid == null || !BuiltInRegistries.SOUND_EVENT.containsKey(sid)) {
                    out.add(warn("sounds[" + i + "].sound", EditorUi.tr("check.sound_unknown", sound).getString()));
                }
            }
        }
        return out;
    }

    /** Every texture the effect refers to, with the path of the reference. */
    static List<TextureRef> textureRefs(FxDoc doc) {
        List<TextureRef> out = new ArrayList<>();
        if ("mask".equals(doc.str("trigger.type", "wave"))) {
            add(out, doc, "trigger.texture", false);
        }
        for (int i = 0; i < doc.size("appearance.overlays"); i++) {
            add(out, doc, "appearance.overlays[" + i + "].texture", false);
        }
        add(out, doc, "appearance.mask.texture", false);
        for (int i = 0; i < doc.size("emitters"); i++) {
            add(out, doc, "emitters[" + i + "].sprite", true);
        }
        return out;
    }

    private static void add(List<TextureRef> out, FxDoc doc, String path, boolean sprite) {
        String name = doc.str(path, null);
        if (name != null && !name.isBlank()) {
            out.add(new TextureRef(path, name.endsWith(".png") ? name.substring(0, name.length() - 4) : name, sprite));
        }
    }

    private static FxProblems.Problem error(String path, String message) {
        return new FxProblems.Problem(true, path, message);
    }

    private static FxProblems.Problem warn(String path, String message) {
        return new FxProblems.Problem(false, path, message);
    }
}
