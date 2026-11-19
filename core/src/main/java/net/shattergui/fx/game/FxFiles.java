package net.shattergui.fx.game;

import net.shattergui.fx.FxLimits;
import net.shattergui.platform.Platform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class FxFiles {
    public static final Path CONFIG = Platform.get().paths().configDir().resolve("shatter_gui");
    public static final Path EFFECTS = CONFIG.resolve("effects");
    public static final Path TEXTURES = CONFIG.resolve("textures");
    public static final Path PACKAGES = CONFIG.resolve("packages");
    public static final Path EXPORTS = CONFIG.resolve("exports");
    private static final int MAX_CODE_LENGTH = 32_768;

    private FxFiles() {
    }

    public static String codeOf(String json) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    public static String fromCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() > MAX_CODE_LENGTH) {
            return null;
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(code.trim())))) {
            byte[] json = in.readNBytes(FxLimits.MAX_JSON_BYTES + 1);
            return json.length > FxLimits.MAX_JSON_BYTES ? null : new String(json, StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }
}
