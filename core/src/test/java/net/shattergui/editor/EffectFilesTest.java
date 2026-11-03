package net.shattergui.editor;

import net.shattergui.fx.FxPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saving (atomic, renames), export to a package, import back (checks, id and texture clashes), broken packages. */
class EffectFilesTest {
    @TempDir
    Path dir;

    private EffectFiles files() {
        return new EffectFiles(dir.resolve("effects"), dir.resolve("textures"), dir.resolve("exports"), dir.resolve("packages"));
    }

    /** A valid PNG of the given size (one grey row repeated), made without any image library. */
    static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, w);
        writeInt(ihdr, h);
        ihdr.write(new byte[]{8, 0, 0, 0, 0});
        chunk(out, "IHDR", ihdr.toByteArray());
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            raw.write(0);
            for (int x = 0; x < w; x++) {
                raw.write((x * 255) / Math.max(1, w - 1));
            }
        }
        ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(deflated, new Deflater())) {
            z.write(raw.toByteArray());
        }
        chunk(out, "IDAT", deflated.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v >>> 24);
        out.write(v >>> 16);
        out.write(v >>> 8);
        out.write(v);
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.write(t);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static final String MASKED = """
            {"schema": 2, "id": "masked", "name": "Masked", "layers": [{"module": "drag"}],
             "trigger": {"type": "mask", "texture": "spiral"},
             "emitters": [{"sprite": "spark", "rate": 1}]}
            """;

    @Test
    void saveRenameDelete() throws IOException {
        EffectFiles files = files();
        files.save("one", Templates.json("glass").replace("glass_fx", "one"), null);
        assertTrue(files.exists("one"));
        assertEquals(List.of("one"), files.ids());
        files.save("two", Templates.json("glass").replace("glass_fx", "two"), "one");
        assertFalse(files.exists("one"));
        assertEquals(List.of("two"), files.ids());
        // No temporary files are left behind.
        try (var list = Files.list(files.effects)) {
            assertEquals(1, list.count());
        }
        assertTrue(files.delete("two"));
        assertTrue(files.ids().isEmpty());
        // Schema-1 files in the folder are not schema-2 effects.
        Files.writeString(files.effects.resolve("old.json"), "{\"schema\": 1, \"id\": \"old\", \"base\": \"glass\"}");
        assertTrue(files.ids().isEmpty());
    }

    @Test
    void exportImportRoundTrip() throws IOException {
        EffectFiles files = files();
        Files.createDirectories(files.textures);
        byte[] spiral = png(64, 64);
        byte[] spark = png(16, 16);
        Files.write(files.textures.resolve("spiral.png"), spiral);
        Files.write(files.textures.resolve("spark.png"), spark);
        files.save("masked", MASKED, null);
        List<String> missing = new ArrayList<>();
        Path pkg = files.export("masked", MASKED, Set.of("spiral", "spark", "gone"), missing);
        assertEquals(List.of("gone"), missing);
        Map<String, byte[]> inside = FxPackage.read(pkg);
        assertTrue(inside.containsKey("effect.json"));
        assertArrayEquals(spiral, inside.get("textures/spiral.png"));
        // Delete the effect and a texture, import back.
        files.delete("masked");
        Files.delete(files.textures.resolve("spiral.png"));
        EffectFiles.ImportResult result = files.importPackage(pkg, id -> false);
        assertTrue(result.ok(), result.messages().toString());
        assertEquals("masked", result.id());
        assertTrue(files.exists("masked"));
        assertArrayEquals(spiral, Files.readAllBytes(files.textures.resolve("spiral.png")));
        assertEquals(List.of(pkg), files.importable());
        // Import again: the id is taken, it comes in under a free one.
        EffectFiles.ImportResult again = files.importPackage(pkg, id -> false);
        assertEquals("masked_2", again.id());
        assertTrue(files.read("masked_2").contains("\"masked_2\""));
    }

    @Test
    void textureNameClashIsRenamed() throws IOException {
        EffectFiles files = files();
        Files.createDirectories(files.textures);
        Files.write(files.textures.resolve("spiral.png"), png(64, 64));
        Files.write(files.textures.resolve("spark.png"), png(16, 16));
        Path pkg = files.export("masked", MASKED, Set.of("spiral", "spark"), new ArrayList<>());
        // A different picture now has the same name.
        Files.write(files.textures.resolve("spiral.png"), png(32, 32));
        EffectFiles.ImportResult result = files.importPackage(pkg, "masked"::equals);
        assertTrue(result.ok(), result.messages().toString());
        assertTrue(Files.exists(files.textures.resolve("spiral_2.png")));
        String json = files.read(result.id());
        assertTrue(json.contains("\"spiral_2\""), json);
        assertTrue(json.contains("\"spark\""), json);
    }

    @Test
    void brokenPackagesAreRefused() throws IOException {
        EffectFiles files = files();
        Files.createDirectories(files.exports);
        // Not a ZIP.
        Path junk = files.exports.resolve("junk.shatterfx");
        Files.write(junk, new byte[]{1, 2, 3, 4});
        assertFalse(files.importPackage(junk, id -> false).ok());
        // A broken effect inside.
        Path bad = files.exports.resolve("bad.shatterfx");
        FxPackage.write(bad, "{\"schema\": 2, \"id\": \"bad\", \"layers\": [{\"module\": \"nope\"}]}", Map.of());
        EffectFiles.ImportResult result = files.importPackage(bad, id -> false);
        assertFalse(result.ok());
        assertTrue(result.messages().stream().anyMatch(m -> m.contains("layers[0].module")), result.messages().toString());
        // A sprite too large.
        Path big = files.exports.resolve("big.shatterfx");
        FxPackage.write(big, MASKED, Map.of("spiral", png(64, 64), "spark", png(300, 20)));
        result = files.importPackage(big, id -> false);
        assertFalse(result.ok());
        assertTrue(result.messages().stream().anyMatch(m -> m.contains("spark") && m.contains("256")), result.messages().toString());
        // Not a PNG.
        Path fake = files.exports.resolve("fake.shatterfx");
        FxPackage.write(fake, MASKED, Map.of("spiral", "hello".getBytes(StandardCharsets.UTF_8)));
        assertFalse(files.importPackage(fake, id -> false).ok());
        // A path that leaves the package.
        Path evil = files.exports.resolve("evil.shatterfx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(evil))) {
            zip.putNextEntry(new ZipEntry("../effect.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertFalse(files.importPackage(evil, id -> false).ok());
        // Nothing was written by any of them.
        assertTrue(files.ids().isEmpty());
        assertTrue(files.textureNames().isEmpty());
    }

    @Test
    void atomicWriteReplaces() throws IOException {
        Path file = dir.resolve("a/b.json");
        FxPackage.writeAtomic(file, "one");
        FxPackage.writeAtomic(file, "two");
        assertEquals("two", Files.readString(file));
        try (var list = Files.list(file.getParent())) {
            assertEquals(1, list.count());
        }
    }
}
