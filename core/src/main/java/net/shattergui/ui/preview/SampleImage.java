package net.shattergui.ui.preview;

import net.shattergui.ShatterMod;
import net.shattergui.platform.Platform;
import net.shattergui.platform.UiTexture;
import net.shattergui.platform.UiTextures;

import java.util.EnumMap;
import java.util.Map;

public final class SampleImage {
    public enum Kind {
        CHEST, FURNACE, INVENTORY, CRAFTING;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Image(UiTexture texture, float u0, float v0, float u1, float v1, boolean premultiplied) {
        public float u(float x) {
            return u0 + (u1 - u0) * x;
        }

        public float v(float y) {
            return v0 + (v1 - v0) * y;
        }
    }

    public static Image image(Kind kind) {
        Image real = Platform.get().capture().realPreview(kind);
        return real != null ? real : new Image(texture(kind), 0, 0, 1, 1, false);
    }

    public static final int WIDTH = SamplePainter.WIDTH;
    public static final int HEIGHT = SamplePainter.HEIGHT;
    private static final long RETRY_MILLIS = 5000;
    private static final Map<Kind, UiTexture> TEXTURES = new EnumMap<>(Kind.class);
    private static final Map<Kind, Long> FAILED = new EnumMap<>(Kind.class);
    private static final Map<Kind, Boolean> LOGGED = new EnumMap<>(Kind.class);
    private static UiTexture fallback;
    private static boolean fallbackFailed;

    private SampleImage() {
    }

    public static UiTexture texture() {
        return texture(Kind.CHEST);
    }

    public static UiTexture texture(Kind kind) {
        UiTexture made = TEXTURES.get(kind);
        if (made != null) {
            return made;
        }
        long now = System.currentTimeMillis();
        Long failedAt = FAILED.get(kind);
        if (failedAt != null && now - failedAt < RETRY_MILLIS) {
            return fallback();
        }
        try {
            made = create(kind);
            TEXTURES.put(kind, made);
            FAILED.remove(kind);
            return made;
        } catch (RuntimeException e) {
            FAILED.put(kind, now);
            if (LOGGED.put(kind, true) == null) {
                ShatterMod.LOGGER.error("Could not make the {} sample for the previews; a plain stand-in is used", kind.id(), e);
            }
            return fallback();
        }
    }

    private static UiTexture create(Kind kind) {
        return Platform.get().textures().create(kind == Kind.CHEST ? "editor/sample" : "editor/sample_" + kind.id(),
                "shatter_gui editor sample " + kind.id(), WIDTH, HEIGHT,
                canvas -> new SamplePainter(new ImageCanvas(canvas)).paint(kind));
    }

    private static UiTexture fallback() {
        if (fallback == null && !fallbackFailed) {
            try {
                fallback = Platform.get().textures().create("editor/sample_fallback",
                        "shatter_gui editor sample fallback", 4, 4, canvas -> {
                            for (int y = 0; y < 4; y++) {
                                for (int x = 0; x < 4; x++) {
                                    canvas.set(x, y, 0xFFC6C6C6);
                                }
                            }
                        });
            } catch (RuntimeException e) {
                fallbackFailed = true;
                ShatterMod.LOGGER.error("Could not make the sample stand-in either", e);
            }
        }
        return fallback;
    }

    private record ImageCanvas(UiTextures.Canvas image) implements SamplePainter.Canvas {
        @Override
        public int width() {
            return image.width();
        }

        @Override
        public int height() {
            return image.height();
        }

        @Override
        public void set(int x, int y, int argb) {
            image.set(x, y, argb);
        }
    }
}
