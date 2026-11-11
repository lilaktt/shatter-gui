package net.shattergui.ui.links;

import net.shattergui.ShatterMod;
import net.shattergui.platform.Platform;
import net.shattergui.platform.UiTexture;
import net.shattergui.platform.UiTextures;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Theme;
import net.shattergui.ui.Ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class LinkIcons {
    public static final int SIZE = 8;
    public static final List<String> FOLDERS = List.of("assets/" + ShatterMod.MOD_ID + "/textures/gui/links/",
            "assets/" + ShatterMod.MOD_ID + "/gui/links/");
    public static final int COLOURED_SPREAD = 48;
    public static final float HOVER_LIGHTEN = 0.22f;
    private static final int BACKING = 0xFF15161B;

    public record Found(String path, byte[] png) {
    }

    public record Analysis(int width, int height, int opaque, int colours, boolean transparent, boolean coloured) {
    }

    private record Picture(UiTexture plain, UiTexture light, boolean coloured) {
    }

    private static final Map<LinkButton.Brand, Picture> LOADED = new EnumMap<>(LinkButton.Brand.class);
    private static final Picture NONE = new Picture(null, null, false);

    private LinkIcons() {
    }

    public static List<String> names(LinkButton.Brand brand) {
        return switch (brand) {
            case MODRINTH -> List.of("modrinth", "modrith");
            case CURSEFORGE -> List.of("curseforge");
            case GITHUB -> List.of("github");
            case BUG -> List.of("bug");
        };
    }

    public static Found find(LinkButton.Brand brand, Function<String, InputStream> resources) {
        for (String folder : FOLDERS) {
            for (String name : names(brand)) {
                String path = folder + name + ".png";
                try (InputStream in = resources.apply(path)) {
                    if (in != null) {
                        return new Found(path, in.readAllBytes());
                    }
                } catch (IOException e) {
                    return new Found(path, new byte[0]);
                }
            }
        }
        return null;
    }

    public static Analysis analyse(int width, int height, int[] argb) {
        int opaque = 0;
        boolean transparent = false;
        boolean coloured = false;
        java.util.Set<Integer> colours = new java.util.HashSet<>();
        for (int c : argb) {
            int a = c >>> 24;
            if (a < 255) {
                transparent = true;
            }
            if (a == 0) {
                continue;
            }
            opaque++;
            colours.add(c & 0xFFFFFF);
            int r = c >> 16 & 0xFF;
            int g = c >> 8 & 0xFF;
            int b = c & 0xFF;
            if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > COLOURED_SPREAD) {
                coloured = true;
            }
        }
        return new Analysis(width, height, opaque, colours.size(), transparent, coloured);
    }

    public static String problem(Analysis analysis) {
        if (analysis.width() != SIZE || analysis.height() != SIZE) {
            return String.format(Locale.ROOT, "is %dx%d, must be %dx%d", analysis.width(), analysis.height(), SIZE, SIZE);
        }
        if (analysis.opaque() == 0) {
            return String.format(Locale.ROOT, "has no visible pixel");
        }
        return null;
    }

    public static int[] lighter(int[] argb, float amount) {
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int a = c & 0xFF000000;
            int r = (c >> 16 & 0xFF) + Math.round((255 - (c >> 16 & 0xFF)) * amount);
            int g = (c >> 8 & 0xFF) + Math.round((255 - (c >> 8 & 0xFF)) * amount);
            int b = (c & 0xFF) + Math.round((255 - (c & 0xFF)) * amount);
            out[i] = a | r << 16 | g << 8 | b;
        }
        return out;
    }

    public static void draw(Ui ui, LinkButton.Brand brand, int x, int y, int textColor, float hover) {
        Picture picture = picture(brand);
        if (picture.plain == null) {
            Draw.icon(ui, fallback(brand), x, y, 1, Theme.mix(brand.color, textColor, hover));
            return;
        }
        if (!picture.coloured) {
            Draw.texture(ui, picture.plain, x, y, SIZE, SIZE, Theme.mix(brand.color, textColor, hover));
            return;
        }
        if (hover > 0f) {
            Draw.roundBox(ui, x - 1, y - 1, SIZE + 2, SIZE + 2, 2, Theme.fade(BACKING, hover), 0);
        }
        Draw.texture(ui, picture.plain, x, y, SIZE, SIZE, 0xFFFFFFFF);
        if (hover > 0f && picture.light != null) {
            Draw.texture(ui, picture.light, x, y, SIZE, SIZE, Theme.fade(0xFFFFFFFF, hover));
        }
    }

    public static Icon fallback(LinkButton.Brand brand) {
        return switch (brand) {
            case MODRINTH, CURSEFORGE -> Icon.EXTERNAL;
            case GITHUB -> Icon.BRACKETS;
            case BUG -> Icon.MARKER;
        };
    }

    public static boolean hasPicture(LinkButton.Brand brand) {
        return picture(brand).plain != null;
    }

    public static boolean colouredPicture(LinkButton.Brand brand) {
        return picture(brand).coloured;
    }

    public static void reloadForTests(Function<String, InputStream> resources) {
        LOADED.clear();
        source = resources == null ? LinkIcons::resource : resources;
    }

    private static Function<String, InputStream> source = LinkIcons::resource;

    private static InputStream resource(String path) {
        return LinkIcons.class.getClassLoader().getResourceAsStream(path);
    }

    private static Picture picture(LinkButton.Brand brand) {
        return LOADED.computeIfAbsent(brand, LinkIcons::load);
    }

    private static Picture load(LinkButton.Brand brand) {
        Found found = find(brand, source);
        if (found == null) {
            return NONE;
        }
        UiTextures textures;
        int[] argb;
        int width;
        int height;
        try {
            textures = Platform.get().textures();
            try (UiTextures.Image image = textures.decodePng(found.png())) {
                width = image.width();
                height = image.height();
                argb = new int[width * height];
                for (int py = 0; py < height; py++) {
                    for (int px = 0; px < width; px++) {
                        argb[py * width + px] = image.getPixel(px, py);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            ShatterMod.LOGGER.warn("Link icon {} could not be read ({}); using the built-in icon", found.path(), e.toString());
            return NONE;
        }
        Analysis analysis = analyse(width, height, argb);
        String problem = problem(analysis);
        if (problem != null) {
            ShatterMod.LOGGER.warn("Link icon {} {}; using the built-in icon", found.path(), problem);
            return NONE;
        }
        String id = "gui/links/" + brand.file;
        int[] pixels = argb;
        UiTexture plain = textures.create(id, "shatter_gui link icon " + id, SIZE, SIZE,
                canvas -> paint(canvas, pixels));
        UiTexture light = null;
        if (analysis.coloured()) {
            int[] bright = lighter(argb, HOVER_LIGHTEN);
            light = textures.create(id + "_light", String.format(Locale.ROOT, "shatter_gui link icon %s (hover)", id), SIZE, SIZE,
                    canvas -> paint(canvas, bright));
        }
        return new Picture(plain, light, analysis.coloured());
    }

    private static void paint(UiTextures.Canvas canvas, int[] argb) {
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                canvas.set(px, py, argb[py * SIZE + px]);
            }
        }
    }
}
