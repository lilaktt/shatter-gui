package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.fx.FxLimits;
import net.shattergui.fx.FxPackage;
import net.shattergui.fx.game.FxTextures;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The PNGs of {@code config/shatter_gui/textures}, loaded for the editor's thumbnails and preview. The folder is looked
 * at again every few seconds (and on "Refresh"): new and changed files are loaded, removed ones freed. A file that
 * cannot be used gets a clear reason instead of a picture (not a PNG, too large, too big a file, a bad name, too
 * many). Everything is freed when the editor closes.
 */
final class TextureLibrary {
    /** One file: its picture (null if unusable) or the reason it is unusable. */
    record Entry(String name, long bytes, long modified, int width, int height, FxTextures.Tex tex, Component error) {
        boolean ok() {
            return tex != null;
        }
    }

    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final int MAX_FILES = FxLimits.MAX_LOADED_TEXTURES;
    private static final long RESCAN_MILLIS = 2000;

    private final Path dir;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long lastScan;
    private int version;

    TextureLibrary(Path dir) {
        this.dir = dir;
    }

    Path dir() {
        return dir;
    }

    /** Changes whenever a texture is added, changed or removed. */
    int version() {
        return version;
    }

    List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    Entry get(String name) {
        return entries.get(name);
    }

    /** The loaded picture for a name, or null (the preview's texture lookup). */
    FxTextures.Tex tex(String name) {
        Entry e = entries.get(name);
        return e == null ? null : e.tex();
    }

    void scanIfDue() {
        if (System.currentTimeMillis() - lastScan > RESCAN_MILLIS) {
            rescan();
        }
    }

    /** Loads new and changed files, frees removed ones. */
    void rescan() {
        lastScan = System.currentTimeMillis();
        Map<String, Path> files = new LinkedHashMap<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
                for (Path file : stream) {
                    String name = file.getFileName().toString();
                    files.put(name.substring(0, name.length() - 4), file);
                }
            } catch (IOException e) {
                return;
            }
        }
        boolean changed = false;
        for (String name : List.copyOf(entries.keySet())) {
            if (!files.containsKey(name)) {
                FxTextures.releaseLoose(entries.remove(name).tex());
                changed = true;
            }
        }
        List<String> names = new ArrayList<>(files.keySet());
        names.sort(String::compareTo);
        int loaded = (int) entries.values().stream().filter(Entry::ok).count();
        for (String name : names) {
            Path file = files.get(name);
            long size;
            long modified;
            try {
                size = Files.size(file);
                modified = Files.getLastModifiedTime(file).toMillis();
            } catch (IOException e) {
                continue;
            }
            Entry old = entries.get(name);
            if (old != null && old.bytes() == size && old.modified() == modified) {
                continue;
            }
            if (old != null) {
                FxTextures.releaseLoose(old.tex());
                if (old.ok()) {
                    loaded--;
                }
            }
            Entry entry = load(name, file, size, modified, loaded >= MAX_FILES);
            if (entry.ok()) {
                loaded++;
            }
            entries.put(name, entry);
            changed = true;
        }
        if (changed) {
            version++;
            // Keep the order by name.
            List<Map.Entry<String, Entry>> sorted = new ArrayList<>(entries.entrySet());
            sorted.sort(Map.Entry.comparingByKey());
            entries.clear();
            sorted.forEach(e -> entries.put(e.getKey(), e.getValue()));
        }
    }

    private static Entry load(String name, Path file, long size, long modified, boolean full) {
        if (!NAME.matcher(name).matches() || name.contains("..")) {
            return new Entry(name, size, modified, 0, 0, null, EditorUi.tr("textures.bad_name"));
        }
        if (full) {
            return new Entry(name, size, modified, 0, 0, null, EditorUi.tr("textures.too_many", MAX_FILES));
        }
        if (size > FxLimits.MAX_TEXTURE_BYTES) {
            return new Entry(name, size, modified, 0, 0, null, EditorUi.tr("textures.file_too_big", size / 1024,
                    FxLimits.MAX_TEXTURE_BYTES / 1024));
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            return new Entry(name, size, modified, 0, 0, null, EditorUi.tr("textures.unreadable", String.valueOf(e.getMessage())));
        }
        int[] dims = FxPackage.pngSize(bytes);
        if (dims == null) {
            return new Entry(name, size, modified, 0, 0, null, EditorUi.tr("textures.not_png"));
        }
        if (dims[0] < 1 || dims[1] < 1 || dims[0] > FxLimits.MAX_OVERLAY_SIZE || dims[1] > FxLimits.MAX_OVERLAY_SIZE) {
            return new Entry(name, size, modified, dims[0], dims[1], null, EditorUi.tr("textures.too_large", dims[0], dims[1],
                    FxLimits.MAX_OVERLAY_SIZE, FxLimits.MAX_OVERLAY_SIZE));
        }
        List<String> warnings = new ArrayList<>();
        FxTextures.Tex tex = FxTextures.decodeLoose(name, bytes, warnings);
        if (tex == null) {
            return new Entry(name, size, modified, dims[0], dims[1], null, EditorUi.tr("textures.broken",
                    warnings.isEmpty() ? "?" : warnings.getFirst()));
        }
        return new Entry(name, size, modified, tex.width, tex.height, tex, null);
    }

    void releaseAll() {
        for (Entry e : entries.values()) {
            FxTextures.releaseLoose(e.tex());
        }
        entries.clear();
        version++;
        lastScan = 0;
    }
}
