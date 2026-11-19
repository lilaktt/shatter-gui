package net.shattergui.capture;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.shattergui.ShatterGuiClient;

import java.util.ArrayList;
import java.util.List;

public final class CapturePool {
    public static final int MAX_SLOTS = 8;
    private static final int MAX_IDLE_WITH_MEMORY = 1;

    public static final class Slot extends AbstractTexture {
        private final Identifier id;
        private boolean inUse;

        private Slot(Identifier id) {
            this.id = id;
        }

        public Identifier id() {
            return id;
        }

        public int width() {
            return texture.getWidth(0);
        }

        public int height() {
            return texture.getHeight(0);
        }

        private void ensure(int width, int height, TextureFormat format) {
            if (texture != null && texture.getWidth(0) == width && texture.getHeight(0) == height
                    && texture.getFormat() == format) {
                return;
            }
            freeMemory();
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "shatter_gui " + id,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, format, width, height, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }

        private boolean hasMemory() {
            return texture != null;
        }

        private void freeMemory() {
            if (textureView != null) {
                textureView.close();
                textureView = null;
            }
            if (texture != null) {
                texture.close();
                texture = null;
            }
        }

        @Override
        public void close() {
            freeMemory();
        }
    }

    private static final List<Slot> SLOTS = new ArrayList<>();
    private static GpuTexture backup;

    private CapturePool() {
    }

    public static Slot acquire(int width, int height, TextureFormat format) {
        Slot slot = null;
        for (Slot candidate : SLOTS) {
            if (!candidate.inUse && (slot == null || candidate.hasMemory() && !slot.hasMemory())) {
                slot = candidate;
            }
        }
        if (slot == null) {
            if (SLOTS.size() >= MAX_SLOTS) {
                return null;
            }
            slot = new Slot(Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "capture/" + SLOTS.size()));
            Minecraft.getInstance().getTextureManager().register(slot.id(), slot);
            SLOTS.add(slot);
        }
        slot.ensure(width, height, format);
        slot.inUse = true;
        return slot;
    }

    public static void release(Slot slot) {
        slot.inUse = false;
        int idleWithMemory = 0;
        for (Slot candidate : SLOTS) {
            if (!candidate.inUse && candidate.hasMemory() && ++idleWithMemory > MAX_IDLE_WITH_MEMORY) {
                candidate.freeMemory();
            }
        }
    }

    public static GpuTexture backup(int width, int height, TextureFormat format) {
        if (backup == null || backup.getWidth(0) != width || backup.getHeight(0) != height || backup.getFormat() != format) {
            if (backup != null) {
                backup.close();
            }
            backup = RenderSystem.getDevice().createTexture("shatter_gui backup",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC, format, width, height, 1, 1);
        }
        return backup;
    }

    public static int texturesWithMemory() {
        int count = backup != null ? 1 : 0;
        for (Slot slot : SLOTS) {
            if (slot.hasMemory()) {
                count++;
            }
        }
        return count;
    }
}
