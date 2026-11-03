package net.shattergui.capture;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.shattergui.ShatterGuiClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Render targets that hold captured screens. Everything stays on the GPU: the closing screen is drawn straight into a
 * slot's {@link TextureTarget} and the shards sample that target's colour texture, so there is neither a read-back to
 * the CPU nor a copy (26.1 copies the main target into a slot with {@code copyTextureToTexture}). Slots are registered
 * in the {@code TextureManager} once and reused; a slot's target is only recreated when the window size changes.
 */
public final class CapturePool {
    /** Simultaneous captures (overlapping shard waves). Past this the oldest wave is dropped. */
    public static final int MAX_SLOTS = 8;
    /** Free slots that keep their GPU memory for reuse; the rest give the memory back. */
    private static final int MAX_IDLE_WITH_MEMORY = 1;

    /** A captured screen, sampled by the shards of one wave: the texture is the target's colour attachment. */
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

        /** Where the screen is drawn: bound for writing, cleared to transparent black. */
        public TextureTarget target() {
            return target;
        }

        @Override
        public int getId() {
            return target != null ? target.getColorTextureId() : -1;
        }

        @Override
        public void load(ResourceManager manager) {
            // Nothing to load: the content is drawn on the GPU.
        }

        /** The colour texture belongs to the target, which frees it ({@link #freeMemory}). */
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

    /** A free slot holding a target of the given size, or null if all {@link #MAX_SLOTS} are in use. */
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

    /** Number of slots currently holding GPU memory (for leak checks; 1.21.1 needs no backup of the frame). */
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
