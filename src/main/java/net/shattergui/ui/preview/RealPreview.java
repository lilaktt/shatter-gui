package net.shattergui.ui.preview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.shattergui.ShatterGuiClient;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.mixin.ScreenAccessor;
import net.shattergui.shatter.ImageRect;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The editor's "real" preview: pictures of the real vanilla screens (inventory, chest, furnace, crafting table) for
 * the previews to break instead of the painted samples.
 *
 * <p>The screens are made on the client with stand-in menus (no packets, nothing opened on the server), laid out with
 * the protected {@code Screen#init()} (not {@code init(int, int)}, which Fabric's screen events and Spatial GUI hook),
 * never shown ({@code Minecraft#setScreen} is not called; a call they make themselves is blocked, see
 * {@link #blocksSetScreen}) and never closed ({@code removed()} would hand back crafting grids). So they cannot start
 * the mod's open/close effects.
 *
 * <p>Drawing uses the close capture's own path ({@code GameRendererMixin}): in one frame the screen is extracted
 * instead of the GUI, drawn by the game's {@code GuiRenderer} onto the cleared main target and copied (GPU only) into
 * this kind's texture. So that this frame does not flash, the frame before it was copied aside ({@link #hold}) and is
 * put back at once: the editor shows the previous frame twice. One screen per two frames, only while a preview asks.
 *
 * <p>Any error while making or drawing a screen: one log line, the simple samples from then on (the setting shows
 * "simple"), and one small notice in the editor ({@link #notice}).
 */
public final class RealPreview {
    /** The samples' width to height: real pictures are widened to it. */
    private static final float ASPECT = (float) SampleImage.WIDTH / SampleImage.HEIGHT;
    /** A preview that asked within this time keeps the queue going (it is being drawn). */
    private static final long ASKED_NANOS = 500_000_000L;
    /** How long the editor shows the "switched to simple" notice. */
    private static final long NOTICE_NANOS = 8_000_000_000L;

    /** One kind's picture: the interface cropped out of a captured frame, kept on the GPU. */
    private static final class Picture extends AbstractTexture {
        final Identifier id;
        boolean ready;
        int windowW;
        int windowH;
        double guiScale;
        /** Where the interface is in the frame (fractions, top-left origin). */
        ImageRect region = ImageRect.FULL;

        Picture(Identifier id) {
            this.id = id;
        }

        void ensure(int width, int height, TextureFormat format) {
            if (texture != null && texture.getWidth(0) == width && texture.getHeight(0) == height
                    && texture.getFormat() == format) {
                return;
            }
            free();
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "shatter_gui " + id, // not shown: a GPU debug label
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, format, width, height, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        void free() {
            ready = false;
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
            free();
        }
    }

    private static final Map<SampleImage.Kind, Picture> PICTURES = new EnumMap<>(SampleImage.Kind.class);
    /** Kinds that cannot be shown for real right now (the inventory in creative mode): the sample stands in. */
    private static final Set<SampleImage.Kind> UNAVAILABLE = EnumSet.noneOf(SampleImage.Kind.class);
    private static final ArrayDeque<SampleImage.Kind> QUEUE = new ArrayDeque<>();

    /** The saved setting (or the settings screen's unsaved one). */
    private static boolean configured = true;
    /** The preview pane's quick switch for this session, or null to follow {@link #configured}. */
    private static Boolean override;
    private static boolean failed;
    private static long failedAt;

    private static long lastAsked;
    private static GpuTexture hold;
    private static boolean holdValid;
    /** This frame draws a preview screen: what for (null: it failed, only put the held frame back). */
    private static boolean armed;
    private static SampleImage.Kind armedKind;
    private static ImageRect armedRegion;
    private static boolean building;
    private static Screen blocked;
    /** Tests: the next screen made throws, as a broken screen from another mod would. */
    private static boolean breakNext;

    private RealPreview() {
    }

    // --- mode ---

    /** Real pictures are wanted (setting or quick switch) and nothing has failed this session. */
    public static boolean enabled() {
        return !failed && (override != null ? override : configured);
    }

    /** The settings screen's value (saved or not); the quick switch then follows it again. */
    public static void setConfigured(boolean value) {
        configured = value;
        override = null;
    }

    /** The preview pane's switch: real ↔ simple for this session (a failure is tried again when switched on). */
    public static void toggleQuick() {
        boolean now = !enabled();
        override = now;
        if (now) {
            failed = false;
            UNAVAILABLE.clear();
        }
    }

    /** A world is loaded (the real screens need the player). */
    public static boolean available() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null;
    }

    /** Switched to simple by an error within the last few seconds: the editor shows one small notice. */
    public static boolean notice() {
        return failed && System.nanoTime() - failedAt < NOTICE_NANOS;
    }

    /**
     * The real picture of {@code kind}, or null (simple mode, no world, not drawn yet — then it is queued). A picture
     * from another window size or GUI scale is drawn again.
     */
    public static SampleImage.Image image(SampleImage.Kind kind) {
        if (!enabled() || UNAVAILABLE.contains(kind) || !available()) {
            return null;
        }
        lastAsked = System.nanoTime();
        Picture p = PICTURES.get(kind);
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (p != null && p.ready && p.windowW == main.width && p.windowH == main.height
                && p.guiScale == mc.getWindow().getGuiScale()) {
            // Framebuffer rows run bottom-up: the interface's top is at v = 1 − y0.
            ImageRect r = p.region;
            return new SampleImage.Image(p, (float) r.x0(), (float) (1 - r.y0()), (float) r.x1(), (float) (1 - r.y1()), true);
        }
        if (!QUEUE.contains(kind)) {
            QUEUE.add(kind);
        }
        return null;
    }

    /** Frees every picture (the settings editor closed). Drawn again next time. */
    public static void release() {
        QUEUE.clear();
        for (Picture p : PICTURES.values()) {
            Minecraft.getInstance().getTextureManager().release(p.id);
        }
        PICTURES.clear();
        if (hold != null) {
            hold.close();
            hold = null;
        }
        holdValid = false;
        UNAVAILABLE.clear();
    }

    /** For test logs. */
    public static String describe() {
        return String.format(Locale.ROOT, "configured=%s override=%s failed=%s queue=%s unavailable=%s pictures=%s",
                configured, override, failed, QUEUE, UNAVAILABLE, PICTURES.entrySet().stream()
                .map(e -> e.getKey().id() + " " + e.getValue().region).toList());
    }

    /** GPU textures held (for leak checks). */
    public static int texturesInMemory() {
        int n = hold != null ? 1 : 0;
        for (Picture p : PICTURES.values()) {
            if (p.getTexture() != null) {
                n++;
            }
        }
        return n;
    }

    // --- making the screens ---

    /** While a preview screen is made or extracted: a {@code setScreen} it calls is blocked (MinecraftMixin). */
    public static boolean blocksSetScreen(Screen screen) {
        if (!building) {
            return false;
        }
        blocked = screen;
        return true;
    }

    /** Tests: makes the next screen fail while it is made (to check the fallback to the simple samples). */
    public static void breakNextForTests() {
        breakNext = true;
    }

    private static Screen create(SampleImage.Kind kind, LocalPlayer player) {
        if (breakNext) {
            breakNext = false;
            throw new IllegalStateException("broken on purpose by a test");
        }
        Inventory inventory = player.getInventory();
        return switch (kind) {
            case CHEST -> new ContainerScreen(ChestMenu.threeRows(0, inventory), inventory,
                    Component.translatable("container.chest"));
            case FURNACE -> new FurnaceScreen(new FurnaceMenu(0, inventory), inventory,
                    Component.translatable("container.furnace"));
            case CRAFTING -> new CraftingScreen(new CraftingMenu(0, inventory), inventory,
                    Component.translatable("container.crafting"));
            // The player's own inventory menu: always there on the client, nothing is opened.
            case INVENTORY -> new InventoryScreen(player);
        };
    }

    private static void fail(SampleImage.Kind kind, Throwable e) {
        failed = true;
        failedAt = System.nanoTime();
        override = false;
        QUEUE.clear();
        ShatterGuiClient.LOGGER.warn("Real preview of the {} failed; the editor uses the simple samples from now on",
                kind.id(), e);
    }

    // --- the frame (GameRendererMixin) ---

    /**
     * Head of {@code GameRenderer#extractGui}: if a picture is due and the previous frame was held, extracts the preview
     * screen instead of this frame's GUI and returns true (the caller skips the normal extraction).
     */
    public static boolean extract(GuiRenderState state, float partialTick) {
        if (!holdValid || QUEUE.isEmpty()) {
            return false;
        }
        holdValid = false;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (hold == null || hold.getWidth(0) != main.width || hold.getHeight(0) != main.height || ScreenCapture.busy()) {
            return false;
        }
        SampleImage.Kind kind = QUEUE.poll();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !enabled()) {
            QUEUE.clear();
            return false;
        }
        if (kind == SampleImage.Kind.INVENTORY && player.hasInfiniteMaterials()) {
            // In creative the inventory screen turns itself into the creative one, whose constructor changes the
            // player's open menu: never made here. The sample stands in.
            UNAVAILABLE.add(kind);
            ShatterGuiClient.LOGGER.info("Real preview: the inventory is not drawn in creative mode, the simple sample is used");
            return false;
        }
        Screen screen;
        building = true;
        blocked = null;
        try {
            screen = create(kind, player);
            screen.width = mc.getWindow().getGuiScaledWidth();
            screen.height = mc.getWindow().getGuiScaledHeight();
            ((ScreenAccessor) screen).shatterGui$init();
        } catch (Throwable e) {
            building = false;
            fail(kind, e);
            return false;
        }
        if (blocked != null) {
            building = false;
            UNAVAILABLE.add(kind);
            ShatterGuiClient.LOGGER.info("Real preview: {} tried to open {}; the simple sample is used for it", kind.id(),
                    blocked.getClass().getName());
            return false;
        }
        state.reset();
        armed = true;
        try {
            ScreenCapture.extractQuietly(screen, state, partialTick);
            if (blocked != null) {
                throw new IllegalStateException("the screen tried to open " + blocked.getClass().getName());
            }
            armedKind = kind;
            armedRegion = fit(ScreenCapture.contentBounds(state, screen), screen.width, screen.height);
        } catch (Throwable e) {
            // Nothing of it is drawn; the held frame is still put back, so the editor does not flash.
            state.reset();
            armedKind = null;
            fail(kind, e);
        } finally {
            building = false;
        }
        return true;
    }

    /**
     * The region around the interface, widened to the samples' aspect ratio (so the previews' stage does not stretch it)
     * and kept on the screen. Fractions of the screen.
     */
    static ImageRect fit(ImageRect content, int screenW, int screenH) {
        double x0 = content.x0() * screenW;
        double y0 = content.y0() * screenH;
        double w = Math.max(1, content.width() * screenW);
        double h = Math.max(1, content.height() * screenH);
        if (w / h > ASPECT) {
            double nh = w / ASPECT;
            y0 -= (nh - h) / 2;
            h = nh;
        } else {
            double nw = h * ASPECT;
            x0 -= (nw - w) / 2;
            w = nw;
        }
        w = Math.min(w, screenW);
        h = Math.min(h, screenH);
        x0 = Math.clamp(x0, 0, screenW - w);
        y0 = Math.clamp(y0, 0, screenH - h);
        return ImageRect.of(x0 / screenW, y0 / screenH, (x0 + w) / screenW, (y0 + h) / screenH);
    }

    /** Before {@code GuiRenderer#render}: a preview frame draws onto a transparent target. */
    public static void beforeGuiRender() {
        if (!armed) {
            return;
        }
        GpuTexture color = Minecraft.getInstance().getMainRenderTarget().getColorTexture();
        if (color != null) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(color, 0);
        }
    }

    /**
     * After {@code GuiRenderer#render}: a preview frame copies its interface into the kind's texture and puts the held
     * frame back; a normal frame is held when a picture is due.
     */
    public static void afterGuiRender() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        GpuTexture color = main.getColorTexture();
        if (color == null) {
            armed = false;
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (armed) {
            armed = false;
            if (armedKind != null) {
                try {
                    copyPicture(encoder, color, main, mc);
                } catch (Throwable e) {
                    fail(armedKind, e);
                }
                armedKind = null;
            }
            if (hold != null && hold.getWidth(0) == main.width && hold.getHeight(0) == main.height) {
                encoder.copyTextureToTexture(hold, color, 0, 0, 0, 0, 0, main.width, main.height);
            }
            return;
        }
        if (QUEUE.isEmpty() || System.nanoTime() - lastAsked > ASKED_NANOS || !enabled() || ScreenCapture.busy()) {
            if (System.nanoTime() - lastAsked > ASKED_NANOS) {
                // Nobody draws a preview any more (the editor is gone): stop.
                QUEUE.clear();
            }
            return;
        }
        if (hold == null || hold.getWidth(0) != main.width || hold.getHeight(0) != main.height
                || hold.getFormat() != color.getFormat()) {
            if (hold != null) {
                hold.close();
            }
            hold = RenderSystem.getDevice().createTexture("shatter_gui preview hold", // not shown: a GPU debug label
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC, color.getFormat(), main.width, main.height, 1, 1);
        }
        encoder.copyTextureToTexture(color, hold, 0, 0, 0, 0, 0, main.width, main.height);
        holdValid = true;
    }

    private static void copyPicture(CommandEncoder encoder, GpuTexture color, RenderTarget main, Minecraft mc) {
        Picture p = PICTURES.computeIfAbsent(armedKind, kind -> {
            Picture made = new Picture(Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "editor/real_" + kind.id()));
            mc.getTextureManager().register(made.id, made);
            return made;
        });
        // The whole frame, as the close capture does (the GL copy's last two numbers are end coordinates, not a size,
        // so a part of the frame cannot be moved to a texture's corner); the interface is picked out by uv.
        p.ensure(main.width, main.height, color.getFormat());
        encoder.copyTextureToTexture(color, p.getTexture(), 0, 0, 0, 0, 0, main.width, main.height);
        p.region = armedRegion;
        p.ready = true;
        p.windowW = main.width;
        p.windowH = main.height;
        p.guiScale = mc.getWindow().getGuiScale();
    }
}
