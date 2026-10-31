package net.shattergui.ui.preview;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.shattergui.ShatterGuiClient;

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
    public record Image(AbstractTexture texture, float u0, float v0, float u1, float v1, boolean premultiplied) {
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
     * ({@link RealPreview}), otherwise the painted sample.
     */
    public static Image image(Kind kind) {
        Image real = RealPreview.image(kind);
        return real != null ? real : new Image(texture(kind), 0, 0, 1, 1, false);
    }

    public static final int WIDTH = SamplePainter.WIDTH;
    public static final int HEIGHT = SamplePainter.HEIGHT;
    private static final long RETRY_MILLIS = 5000;
    private static final Map<Kind, DynamicTexture> TEXTURES = new EnumMap<>(Kind.class);
    private static final Map<Kind, Long> FAILED = new EnumMap<>(Kind.class);
    private static final Map<Kind, Boolean> LOGGED = new EnumMap<>(Kind.class);
    private static DynamicTexture fallback;
    private static boolean fallbackFailed;

    private SampleImage() {
    }

    /** The chest. */
    public static AbstractTexture texture() {
        return texture(Kind.CHEST);
    }

    /** The sample's texture; a grey stand-in (or null: drawn untextured) while it cannot be made. */
    public static AbstractTexture texture(Kind kind) {
        DynamicTexture made = TEXTURES.get(kind);
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
                ShatterGuiClient.LOGGER.error("Could not make the {} sample for the previews; a plain stand-in is used", kind.id(), e);
            }
            return fallback();
        }
    }

    private static DynamicTexture create(Kind kind) {
        NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
        try {
            new SamplePainter(new ImageCanvas(image)).paint(kind);
        } catch (RuntimeException e) {
            image.close();
            throw e;
        }
        DynamicTexture texture = new DynamicTexture(() -> "shatter_gui editor sample " + kind.id(), image);
        Identifier id = Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID,
                kind == Kind.CHEST ? "editor/sample" : "editor/sample_" + kind.id());
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return texture;
    }

    /** A grey square; null if even that cannot be made (then previews draw the sample untextured). */
    private static AbstractTexture fallback() {
        if (fallback == null && !fallbackFailed) {
            try {
                NativeImage image = new NativeImage(4, 4, false);
                image.fillRect(0, 0, 4, 4, 0xFFC6C6C6);
                fallback = new DynamicTexture(() -> "shatter_gui editor sample fallback", image);
                Minecraft.getInstance().getTextureManager().register(
                        Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "editor/sample_fallback"), fallback);
            } catch (RuntimeException e) {
                fallbackFailed = true;
                ShatterGuiClient.LOGGER.error("Could not make the sample stand-in either", e);
            }
        }
        return fallback;
    }

    /** {@link SamplePainter} onto a game image. */
    private record ImageCanvas(NativeImage image) implements SamplePainter.Canvas {
        @Override
        public int width() {
            return image.getWidth();
        }

        @Override
        public int height() {
            return image.getHeight();
        }

        @Override
        public void set(int x, int y, int argb) {
            image.setPixel(x, y, argb);
        }
    }
}
