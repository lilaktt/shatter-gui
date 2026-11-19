package net.shattergui.platform;

import java.io.IOException;
import java.util.function.Consumer;

public interface UiTextures {
    interface Canvas {
        int width();

        int height();

        void set(int x, int y, int argb);
    }

    interface Image extends AutoCloseable {
        int width();

        int height();

        int getPixel(int x, int y);

        @Override
        void close();
    }

    UiTexture create(String path, String label, int width, int height, Consumer<Canvas> paint);

    Image decodePng(byte[] png) throws IOException;

    UiTexture upload(String path, String label, Image image);

    void release(UiTexture texture);
}
