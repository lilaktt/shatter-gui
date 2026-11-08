package net.shattergui.platform.impl;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.shattergui.ShatterMod;
import net.shattergui.platform.UiTexture;
import net.shattergui.platform.UiTextures;

import java.io.IOException;
import java.util.function.Consumer;

final class UiTextures26 implements UiTextures {
    private record Image26(NativeImage image) implements Image {
        @Override
        public int width() {
            return image.getWidth();
        }

        @Override
        public int height() {
            return image.getHeight();
        }

        @Override
        public int getPixel(int x, int y) {
            return image.getPixel(x, y);
        }

        @Override
        public void close() {
            image.close();
        }
    }

    private record Canvas26(NativeImage image) implements Canvas {
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

    @Override
    public UiTexture create(String path, String label, int width, int height, Consumer<Canvas> paint) {
        NativeImage image = new NativeImage(width, height, false);
        try {
            paint.accept(new Canvas26(image));
        } catch (RuntimeException e) {
            image.close();
            throw e;
        }
        DynamicTexture texture = new DynamicTexture(() -> label, image);
        Identifier id = Identifier.fromNamespaceAndPath(ShatterMod.MOD_ID, path);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new Texture26.Registered(texture, id);
    }

    @Override
    public Image decodePng(byte[] png) throws IOException {
        return new Image26(NativeImage.read(png));
    }

    @Override
    public UiTexture upload(String path, String label, Image image) {
        Identifier id = Identifier.fromNamespaceAndPath(ShatterMod.MOD_ID, path);
        DynamicTexture texture = new DynamicTexture(() -> label, ((Image26) image).image());
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new Texture26.Registered(texture, id);
    }

    @Override
    public void release(UiTexture texture) {
        Minecraft.getInstance().getTextureManager().release(((Texture26) texture).id());
    }
}
