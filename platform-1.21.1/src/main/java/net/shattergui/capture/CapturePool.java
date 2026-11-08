package net.shattergui.capture;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.shattergui.ShatterGuiClient;

import java.util.ArrayList;
import java.util.List;

public final class CapturePool {
    public static final int MAX_SLOTS = 8;
    private static final int MAX_IDLE_WITH_MEMORY = 1;

    public static final class Slot extends AbstractTexture {
        private final ResourceLocation id;
        private TextureTarget target;
        private boolean inUse;

        private Slot(ResourceLocation id) {
            this.id = id;
        }

        public ResourceLocation id() {
            return id;
        }

        public int width() {
            return target.width;
        }

        public int height() {
            return target.height;
        }

        public TextureTarget target() {
            return target;
        }

        @Override
        public int getId() {
            return target != null ? target.getColorTextureId() : -1;
        }

        @Override
        public void load(ResourceManager manager) {
        }

        @Override
        public void releaseId() {
        }

        private void ensure(int width, int height) {
            if (target != null && target.width == width && target.height == height) {
                return;
            }
            freeMemory();
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setClearColor(0f, 0f, 0f, 0f);
        }

        private boolean hasMemory() {
            return target != null;
        }

        private void freeMemory() {
            if (target != null) {
                target.destroyBuffers();
                target = null;
            }
        }

        @Override
        public void close() {
            freeMemory();
        }
    }

    private static final List<Slot> SLOTS = new ArrayList<>();

    private CapturePool() {
    }

    public static Slot acquire(int width, int height) {
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
            slot = new Slot(ResourceLocation.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "capture/" + SLOTS.size()));
            Minecraft.getInstance().getTextureManager().register(slot.id(), slot);
            SLOTS.add(slot);
        }
        slot.ensure(width, height);
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

    public static int texturesWithMemory() {
        int count = 0;
        for (Slot slot : SLOTS) {
            if (slot.hasMemory()) {
                count++;
            }
        }
        return count;
    }
}
