package net.shattergui.platform;

import java.io.IOException;
import java.util.function.Consumer;

/** GPU textures for the UI library: painted pictures and decoded PNGs, registered under the mod's namespace. */
public interface UiTextures {
    /** Pixels to paint, ARGB. */
    interface Canvas {
        int width();

        int height();

        void set(int x, int y, int argb);
    }

    /** A decoded picture on the CPU; {@link #upload} takes it over, otherwise close it. */
    interface Image extends AutoCloseable {
        int width();

        int height();

        /** ARGB. */
        int getPixel(int x, int y);

        @Override
        void close();
    }

    /**
     * Paints a new {@code width}×{@code height} picture (its pixels start undefined: paint all of them) and uploads it
     * as the texture {@code shatter_gui:<path>}. If painting throws, nothing is registered and the exception passes on.
     */
    UiTexture create(String path, String label, int width, int height, Consumer<Canvas> paint);

    /** Decodes a PNG file (the game's decoder). */
    Image decodePng(byte[] png) throws IOException;

    /** Uploads a decoded picture as the texture {@code shatter_gui:<path>}; the texture now owns the picture. */
    UiTexture upload(String path, String label, Image image);

    /** Frees a texture made here. */
    void release(UiTexture texture);
}
