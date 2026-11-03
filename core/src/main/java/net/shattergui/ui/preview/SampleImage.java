package net.shattergui.ui.preview;

import net.shattergui.ShatterMod;
import net.shattergui.platform.Platform;
import net.shattergui.platform.UiTexture;
import net.shattergui.platform.UiTextures;

import java.util.EnumMap;
import java.util.Map;

/**
 * The sample interfaces the previews break, as textures: painted by {@link SamplePainter} on first use (the "simple"
 * preview, and the stand-in while the real screens are not drawn yet — see {@link #image}). Making one
 * never takes the game down: if it fails, a plain grey stand-in is used, the failure is logged once, and it is tried
 * again a few seconds later (a failure is not remembered for ever).
 */
public final class SampleImage {
    /** Which interface. */
    public enum Kind {
        CHEST, FURNACE, INVENTORY, CRAFTING;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * What a preview draws for a sample: a texture, the part of it that holds the interface (uv of its top-left and
     * bottom-right corners; v0 > v1 for a picture copied from the framebuffer, whose rows run bottom-up) and whether its
     * colours are premultiplied by alpha (a GUI drawn on a transparent target).
     */
    public record Image(UiTexture texture, float u0, float v0, float u1, float v1, boolean premultiplied) {
        /** The u to sample for a fraction across the interface (0 = left). */
        public float u(float x) {
            return u0 + (u1 - u0) * x;
        }

        /** The v to sample for a fraction down the interface (0 = top). */
        public float v(float y) {
            return v0 + (v1 - v0) * y;
        }
    }

    /**
     * The picture previews break: the real vanilla screen when the real preview is on and its picture is ready
     * ({@link net.shattergui.platform.ScreenCapture#realPreview}), otherwise the painted sample.
     */
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

    /** The chest. */
    public static UiTexture texture() {
        return texture(Kind.CHEST);
    }

    /** The sample's texture; a grey stand-in (or null: drawn untextured) while it cannot be made. */
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
                "shatter_gui editor sample " + kind.id(), WIDTH, HEIGHT, // not shown: a texture debug label
                canvas -> new SamplePainter(new ImageCanvas(canvas)).paint(kind));
    }

    /** A grey square; null if even that cannot be made (then previews draw the sample untextured). */
    private static UiTexture fallback() {
        if (fallback == null && !fallbackFailed) {
            try {
                fallback = Platform.get().textures().create("editor/sample_fallback",
                        "shatter_gui editor sample fallback", 4, 4, canvas -> { // not shown: a texture debug label
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

    /** {@link SamplePainter} onto a texture's picture. */
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
