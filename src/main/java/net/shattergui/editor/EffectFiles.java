package net.shattergui.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxPackage;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxProblems;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The editor's files: effects in {@code effects/<id>.json}, textures in {@code textures/<name>.png}, exported packages
 * in {@code exports/}. Every write is atomic ({@link FxPackage#writeAtomic}). Importing a package checks it like the
 * loader does (the parser, PNG headers, size limits) before anything is written; a texture whose name is taken by a
 * different picture is saved under a free name and the effect's references are changed to it.
 */
public final class EffectFiles {
    /** What an import did: the effect id it was saved as (null if nothing was saved) and what to tell the player. */
    public record ImportResult(String id, List<Note> notes) {
        public boolean ok() {
            return id != null;
        }

        /** The notes in plain (English) words, for the log and tests. */
        public List<String> messages() {
            return notes.stream().map(Note::toString).toList();
        }

        /** The first error, or null. */
        public Note firstError() {
            return notes.stream().filter(Note::error).findFirst().orElse(null);
        }
    }

    /**
     * One thing to tell the player: translated by {@code ui.shatter_gui.fx.import.note.<key>} with {@code args}. A
     * problem the format reader found ({@code "problem"}) is passed on as the reader wrote it.
     */
    public record Note(boolean error, String key, Object... args) {
        @Override
        public String toString() {
            return (error ? "error: " : "warning: ") + key + " " + Arrays.toString(args); // not shown: log and tests
        }
    }

    private static final Pattern TEXTURE_FILE = Pattern.compile("[a-z0-9_.-]{1,64}");

    public final Path effects;
    public final Path textures;
    public final Path exports;
    public final Path packages;

    public EffectFiles(Path effects, Path textures, Path exports, Path packages) {
        this.effects = effects;
        this.textures = textures;
        this.exports = exports;
        this.packages = packages;
    }

    public Path file(String id) {
        return effects.resolve(id + ".json");
    }

    /** Ids of the schema-2 effects in the effects folder (by file name), sorted. */
    public List<String> ids() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(effects)) {
            return out;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(effects, "*.json")) {
            for (Path file : files) {
                try {
                    if (Files.size(file) > FxLimits.MAX_JSON_BYTES) {
                        continue;
                    }
                    if (FxParser.schemaOf(Files.readString(file, StandardCharsets.UTF_8)) == FxLimits.SCHEMA) {
                        String name = file.getFileName().toString();
                        out.add(name.substring(0, name.length() - 5));
                    }
                } catch (IOException | RuntimeException e) {
                    // Unreadable files are reported by the loader.
                }
            }
        } catch (IOException e) {
            return out;
        }
        out.sort(String::compareTo);
        return out;
    }

    /**
     * What went wrong, for a note: the exception itself when it can be translated ({@link net.shattergui.fx.FxIOException},
     * turned into the player's language where it is shown), else its message.
     */
    static Object reason(IOException e) {
        return e instanceof net.shattergui.fx.FxIOException ? e : String.valueOf(e.getMessage());
    }

    public boolean exists(String id) {
        return Files.isRegularFile(file(id));
    }

    public String read(String id) throws IOException {
        Path file = file(id);
        if (Files.size(file) > FxLimits.MAX_JSON_BYTES) {
            throw new net.shattergui.fx.FxIOException("json_too_big", "over " + FxLimits.MAX_JSON_BYTES / 1024 + " KB",
                    FxLimits.MAX_JSON_BYTES / 1024);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * Saves an effect as {@code effects/<id>.json} (atomically). If it was saved before under another id
     * ({@code oldId}), that file is removed after the new one is written (a rename).
     */
    public void save(String id, String json, String oldId) throws IOException {
        if (json.getBytes(StandardCharsets.UTF_8).length > FxLimits.MAX_JSON_BYTES) {
            throw new net.shattergui.fx.FxIOException("json_too_big", "the effect is over " + FxLimits.MAX_JSON_BYTES / 1024 + " KB",
                    FxLimits.MAX_JSON_BYTES / 1024);
        }
        FxPackage.writeAtomic(file(id), json);
        if (oldId != null && !oldId.equals(id)) {
            Files.deleteIfExists(file(oldId));
        }
    }

    public boolean delete(String id) throws IOException {
        return Files.deleteIfExists(file(id));
    }

    /** Names (without ".png") of the PNG files in the textures folder, sorted. */
    public List<String> textureNames() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(textures)) {
            return out;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(textures, "*.png")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                out.add(name.substring(0, name.length() - 4));
            }
        } catch (IOException e) {
            return out;
        }
        out.sort(String::compareTo);
        return out;
    }

    /**
     * Writes {@code exports/<id>.shatterfx} with the effect and those of its textures found in the textures folder.
     * Returns the file; {@code missing} receives the names of textures that were not there.
     */
    public Path export(String id, String json, Set<String> textureNames, List<String> missing) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        for (String name : textureNames) {
            Path file = textures.resolve(name + ".png").normalize();
            if (name.contains(":") || !file.startsWith(textures.normalize()) || !Files.isRegularFile(file)) {
                missing.add(name);
                continue;
            }
            byte[] bytes = Files.readAllBytes(file);
            total += bytes.length;
            if (total > FxLimits.MAX_TEXTURE_BYTES) {
                throw new net.shattergui.fx.FxIOException("textures_too_big",
                        "the textures are over " + FxLimits.MAX_TEXTURE_BYTES / 1024 + " KB together", FxLimits.MAX_TEXTURE_BYTES / 1024); // not shown: the log text
            }
            files.put(name, bytes);
        }
        Path out = exports.resolve(id + ".shatterfx");
        FxPackage.write(out, json, files);
        return out;
    }

    /** Packages that can be imported: those in the exports and packages folders, newest first. */
    public List<Path> importable() {
        List<Path> out = new ArrayList<>();
        for (Path dir : new Path[]{exports, packages}) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.shatterfx")) {
                files.forEach(out::add);
            } catch (IOException e) {
                // Nothing to offer from this folder.
            }
        }
        out.sort((a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (IOException e) {
                return 0;
            }
        });
        return out;
    }

    /**
     * Imports a package into the effects and textures folders. Checked first (the parser, every PNG header against
     * its role's size limit, the 2 MB total); nothing is written if it fails. Taken ids get a free one
     * ({@code taken} also covers built-in effects); clashing textures get a free name.
     */
    public ImportResult importPackage(Path file, Predicate<String> taken) {
        List<Note> messages = new ArrayList<>();
        Map<String, byte[]> entries;
        try {
            entries = FxPackage.read(file);
        } catch (IOException e) {
            messages.add(new Note(true, "read_failed", reason(e)));
            return new ImportResult(null, messages);
        }
        byte[] effectBytes = entries.get(FxPackage.EFFECT);
        if (effectBytes == null) {
            messages.add(new Note(true, "no_effect"));
            return new ImportResult(null, messages);
        }
        String json = new String(effectBytes, StandardCharsets.UTF_8);
        FxParser.Result result = FxParser.parse(json);
        for (FxProblems.Problem problem : result.problems().all()) {
            messages.add(new Note(problem.error(), "problem", problem.toString()));
        }
        if (!result.ok()) {
            return new ImportResult(null, messages);
        }
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        // Textures: checked, then renamed when the name is taken by a different file.
        Map<String, byte[]> toWrite = new LinkedHashMap<>();
        Map<String, String> renames = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().startsWith(FxPackage.TEXTURES)) {
                continue;
            }
            String name = entry.getKey().substring(FxPackage.TEXTURES.length(), entry.getKey().length() - 4);
            byte[] bytes = entry.getValue();
            if (!TEXTURE_FILE.matcher(name).matches() || name.contains("..")) {
                messages.add(new Note(false, "texture_bad_name", name));
                continue;
            }
            var role = result.def().textures.get(name);
            if (role == null) {
                messages.add(new Note(false, "texture_unused", name));
                continue;
            }
            int[] size = FxPackage.pngSize(bytes);
            int limit = role == net.shattergui.fx.FxDef.TextureRole.SPRITE ? FxLimits.MAX_SPRITE_SIZE : FxLimits.MAX_OVERLAY_SIZE;
            if (size == null) {
                messages.add(new Note(true, "texture_not_png", name));
                return new ImportResult(null, messages);
            }
            if (size[0] < 1 || size[1] < 1 || size[0] > limit || size[1] > limit) {
                messages.add(new Note(true, "texture_too_big", name, size[0], size[1], limit, limit));
                return new ImportResult(null, messages);
            }
            total += bytes.length;
            if (total > FxLimits.MAX_TEXTURE_BYTES) {
                messages.add(new Note(true, "textures_total", FxLimits.MAX_TEXTURE_BYTES / 1024));
                return new ImportResult(null, messages);
            }
            String target = name;
            try {
                Path existing = textures.resolve(name + ".png");
                if (Files.isRegularFile(existing) && !Arrays.equals(Files.readAllBytes(existing), bytes)) {
                    for (int n = 2; Files.exists(textures.resolve(target + ".png")) || toWrite.containsKey(target); n++) {
                        target = name + "_" + n;
                    }
                    renames.put(name, target);
                    messages.add(new Note(false, "texture_renamed", name, target));
                }
            } catch (IOException e) {
                messages.add(new Note(true, "io", reason(e)));
                return new ImportResult(null, messages);
            }
            toWrite.put(target, bytes);
        }
        if (!renames.isEmpty()) {
            renameTextures(root, renames);
        }
        String id = result.def().id;
        if (taken.test(id) || exists(id)) {
            String free = freeId(id, s -> taken.test(s) || exists(s));
            messages.add(new Note(false, "id_taken", id, free));
            id = free;
            root.addProperty("id", id);
        }
        try {
            for (Map.Entry<String, byte[]> texture : toWrite.entrySet()) {
                Path target = textures.resolve(texture.getKey() + ".png");
                if (!Files.isRegularFile(target)) {
                    FxPackage.writeAtomic(target, texture.getValue());
                }
            }
            save(id, FxDoc.pretty(root), null);
        } catch (IOException e) {
            messages.add(new Note(true, "save_failed", reason(e)));
            return new ImportResult(null, messages);
        }
        return new ImportResult(id, messages);
    }

    /** Changes texture references ("texture", "sprite" fields) from old to new names, anywhere in the tree. */
    static void renameTextures(JsonElement element, Map<String, String> renames) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String key : List.copyOf(obj.keySet())) {
                JsonElement value = obj.get(key);
                if (("texture".equals(key) || "sprite".equals(key)) && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    String name = value.getAsString();
                    String plain = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
                    if (renames.containsKey(plain)) {
                        obj.add(key, new JsonPrimitive(renames.get(plain)));
                    }
                } else {
                    renameTextures(value, renames);
                }
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> renameTextures(e, renames));
        }
    }

    /** {@code wanted} made a valid id, with _2, _3, … until it is free. */
    public static String freeId(String wanted, Predicate<String> taken) {
        String base = (wanted == null ? "" : wanted).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.isEmpty()) {
            base = "effect";
        }
        base = base.length() > 28 ? base.substring(0, 28) : base;
        String id = base;
        for (int n = 2; taken.test(id); n++) {
            id = base + "_" + n;
        }
        return id;
    }
}
