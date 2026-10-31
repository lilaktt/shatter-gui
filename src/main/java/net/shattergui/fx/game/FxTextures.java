package net.shattergui.fx.game;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.shattergui.ShatterGuiClient;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxLimits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The textures of one schema-2 effect: PNGs checked before decoding (the PNG header gives the size, so an oversized
 * or broken file is refused without allocating it), size limits by role (sprites up to 256², overlays and masks up to
 * 512²), at most 16 per effect and 2 MB of files together, at most {@link FxLimits#MAX_LOADED_TEXTURES} loaded over all
 * effects. Sprites and overlays become GPU textures; masks keep a CPU copy of their brightness. Freed with the effect.
 */
public final class FxTextures {
    /** Where a texture's bytes come from (a package, the config folder, resource packs). */
    public interface Source {
        /** The file's bytes, or null if this source does not have it. */
        byte[] read(String name) throws IOException;
    }

    /** One loaded texture. */
    public static final class Tex {
        public final String name;
        public final Identifier id;
        public final int width;
        public final int height;
        private final DynamicTexture gpu;
        private final float[] brightness;

        Tex(String name, Identifier id, int width, int height, DynamicTexture gpu, float[] brightness) {
            this.name = name;
            this.id = id;
            this.width = width;
            this.height = height;
            this.gpu = gpu;
            this.brightness = brightness;
        }

        public DynamicTexture gpu() {
            return gpu;
        }

        /** Brightness 0..1 at (u right, v down), bilinear; NaN without a CPU copy. */
        public float sample(float u, float v) {
            if (brightness == null) {
                return Float.NaN;
            }
            float x = Math.clamp(u, 0f, 1f) * (width - 1);
            float y = Math.clamp(v, 0f, 1f) * (height - 1);
            int x0 = (int) x;
            int y0 = (int) y;
            int x1 = Math.min(width - 1, x0 + 1);
            int y1 = Math.min(height - 1, y0 + 1);
            float fx = x - x0;
            float fy = y - y0;
            float a = brightness[y0 * width + x0];
            float b = brightness[y0 * width + x1];
            float c = brightness[y1 * width + x0];
            float d = brightness[y1 * width + x1];
            return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
        }
    }

    private static int loadedTotal;

    private final Map<String, Tex> textures = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean released;

    private FxTextures() {
    }

    public Tex get(String name) {
        return textures.get(name);
    }

    public List<String> warnings() {
        return warnings;
    }

    public int count() {
        return textures.size();
    }

    public static int loadedTotal() {
        return loadedTotal;
    }

    /** Loads every texture the effect uses from the first source that has it. Missing or invalid ones are warnings. */
    public static FxTextures load(FxDef def, List<Source> sources) {
        FxTextures out = new FxTextures();
        long bytesTotal = 0;
        for (Map.Entry<String, FxDef.TextureRole> entry : def.textures.entrySet()) {
            String name = entry.getKey();
            FxDef.TextureRole role = entry.getValue();
            if (name.contains("..")) {
                out.warnings.add("texture \"" + name + "\": \"..\" is not allowed in names");
                continue;
            }
            byte[] bytes = null;
            try {
                for (Source source : sources) {
                    bytes = source.read(name);
                    if (bytes != null) {
                        break;
                    }
                }
            } catch (IOException e) {
                out.warnings.add("texture \"" + name + "\": could not be read (" + e.getMessage() + ")");
                continue;
            }
            if (bytes == null) {
                out.warnings.add("texture \"" + name + "\": not found (textures/" + name + ".png)");
                continue;
            }
            bytesTotal += bytes.length;
            if (bytesTotal > FxLimits.MAX_TEXTURE_BYTES) {
                out.warnings.add("texture \"" + name + "\": the effect's textures exceed " + FxLimits.MAX_TEXTURE_BYTES / 1024
                        + " KB together, skipped");
                continue;
            }
            if (loadedTotal >= FxLimits.MAX_LOADED_TEXTURES) {
                out.warnings.add("texture \"" + name + "\": already " + FxLimits.MAX_LOADED_TEXTURES
                        + " effect textures loaded, skipped");
                continue;
            }
            Tex tex = decode(def.id, name, role, bytes, out.warnings);
            if (tex != null) {
                out.textures.put(name, tex);
                loadedTotal++;
            }
        }
        return out;
    }

    /**
     * A texture for the editor's library and preview: usable in any role (GPU texture and brightness), up to the
     * overlay size, not counted towards the effects' own limit. Null (with the reason in {@code warnings}) if it is
     * not a usable PNG. Free it with {@link #releaseLoose}.
     */
    public static Tex decodeLoose(String name, byte[] bytes, List<String> warnings) {
        // "editor-lib" cannot be an effect id (no '-'), so these never replace an effect's textures.
        return decode("editor-lib", name, FxDef.TextureRole.OVERLAY, bytes, warnings);
    }

    public static void releaseLoose(Tex tex) {
        if (tex != null && tex.gpu != null) {
            Minecraft.getInstance().getTextureManager().release(tex.id);
        }
    }

    private static Tex decode(String effectId, String name, FxDef.TextureRole role, byte[] bytes, List<String> warnings) {
        int[] size = pngSize(bytes);
        if (size == null) {
            warnings.add("texture \"" + name + "\": not a PNG file");
            return null;
        }
        int limit = role == FxDef.TextureRole.SPRITE ? FxLimits.MAX_SPRITE_SIZE : FxLimits.MAX_OVERLAY_SIZE;
        if (size[0] < 1 || size[1] < 1 || size[0] > limit || size[1] > limit) {
            warnings.add("texture \"" + name + "\": " + size[0] + "×" + size[1] + " is too large for a "
                    + role.name().toLowerCase(Locale.ROOT) + " (at most " + limit + "×" + limit + ")");
            return null;
        }
        NativeImage image;
        try {
            image = NativeImage.read(bytes);
        } catch (IOException | RuntimeException e) {
            warnings.add("texture \"" + name + "\": could not be decoded (" + e.getMessage() + ")");
            return null;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        float[] brightness = null;
        // Masks and overlays keep a CPU copy of their brightness (a texture may serve as both).
        if (role != FxDef.TextureRole.SPRITE) {
            brightness = new float[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = image.getPixel(x, y);
                    float lum = (0.299f * ((argb >> 16) & 0xFF) + 0.587f * ((argb >> 8) & 0xFF) + 0.114f * (argb & 0xFF)) / 255f;
                    brightness[y * w + x] = lum * ((argb >>> 24) / 255f);
                }
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(ShatterGuiClient.MOD_ID, "fx/" + effectId + "/"
                + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_"));
        DynamicTexture gpu = null;
        if (role != FxDef.TextureRole.MASK) {
            gpu = new DynamicTexture(() -> "shatter_gui effect texture " + id, image);
            Minecraft.getInstance().getTextureManager().register(id, gpu);
        } else {
            image.close();
        }
        return new Tex(name, id, w, h, gpu, brightness);
    }

    /** Width and height from a PNG's IHDR, or null if it is not a PNG. */
    static int[] pngSize(byte[] b) {
        return net.shattergui.fx.FxPackage.pngSize(b);
    }

    /** Frees the GPU textures (reload, effect removed). */
    public void release() {
        if (released) {
            return;
        }
        released = true;
        for (Tex tex : textures.values()) {
            if (tex.gpu != null) {
                Minecraft.getInstance().getTextureManager().release(tex.id);
            }
        }
        loadedTotal = Math.max(0, loadedTotal - textures.size());
        textures.clear();
    }
}
