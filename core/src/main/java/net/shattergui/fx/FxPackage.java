package net.shattergui.fx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The {@code .shatterfx} package (a ZIP with {@code effect.json} and {@code textures/*.png}) and safe file writing.
 * Reading is bounded (entry count, unpacked size, no paths leaving the package); writing goes through a temporary file
 * that is renamed over the target, so a crash never leaves a half-written effect.
 */
public final class FxPackage {
    public static final String EFFECT = "effect.json";
    public static final String TEXTURES = "textures/";

    private FxPackage() {
    }

    /**
     * Reads a package safely: at most {@link FxLimits#MAX_PACKAGE_ENTRIES} entries and
     * {@link FxLimits#MAX_PACKAGE_BYTES} unpacked, only {@code effect.json} and {@code textures/*.png}, no paths that
     * leave the package. Other entries are skipped.
     */
    public static Map<String, byte[]> read(Path file) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.size() > FxLimits.MAX_PACKAGE_ENTRIES) {
                throw new FxIOException("too_many_files", "more than " + FxLimits.MAX_PACKAGE_ENTRIES + " files inside",
                        FxLimits.MAX_PACKAGE_ENTRIES);
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.startsWith("/") || name.contains("..") || name.contains(":")) {
                    throw new FxIOException("unsafe_path", "unsafe path inside: " + name, name);
                }
                boolean wanted = name.equals(EFFECT) || name.startsWith(TEXTURES) && name.endsWith(".png");
                if (!wanted) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    long left = FxLimits.MAX_PACKAGE_BYTES - total;
                    byte[] bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE, left + 1));
                    if (bytes.length > left) {
                        throw new FxIOException("unpacked_too_big",
                                "unpacked size over " + FxLimits.MAX_PACKAGE_BYTES / 1024 / 1024 + " MB", FxLimits.MAX_PACKAGE_BYTES / 1024 / 1024);
                    }
                    total += bytes.length;
                    out.put(name, bytes);
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new FxIOException("not_a_package", "not a valid package (" + e.getMessage() + ")", e);
        }
        return out;
    }

    /** Writes a package: the effect and its textures (name without ".png" → PNG bytes). Atomic. */
    public static void write(Path file, String json, Map<String, byte[]> textures) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(EFFECT));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, byte[]> texture : textures.entrySet()) {
                zip.putNextEntry(new ZipEntry(TEXTURES + texture.getKey() + ".png"));
                zip.write(texture.getValue());
                zip.closeEntry();
            }
        }
        writeAtomic(file, bytes.toByteArray());
    }

    /** Width and height from a PNG's IHDR, or null if it is not a PNG. */
    public static int[] pngSize(byte[] b) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (b == null || b.length < 24) {
            return null;
        }
        for (int i = 0; i < 8; i++) {
            if (b[i] != signature[i]) {
                return null;
            }
        }
        if (b[12] != 'I' || b[13] != 'H' || b[14] != 'D' || b[15] != 'R') {
            return null;
        }
        int w = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
        int h = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
        return new int[]{w, h};
    }

    /** Writes text (UTF-8) atomically. */
    public static void writeAtomic(Path file, String text) throws IOException {
        writeAtomic(file, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes bytes to a temporary file next to the target, then renames it over the target (atomically where the file
     * system allows, otherwise a plain replace). The target is either the old or the complete new file, never a part.
     */
    public static void writeAtomic(Path file, byte[] data) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "." + file.getFileName(), ".tmp");
        try {
            Files.write(tmp, data);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
