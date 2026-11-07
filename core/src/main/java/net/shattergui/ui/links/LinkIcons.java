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
import java.util.Map;

/**
 * The small icons of the page buttons. The mod draws no platform logos itself: they are read from
 * {@code assets/shatter_gui/textures/gui/links/<name>.png} (16×16) and {@code <name>_32.png} (32×32, used from GUI scale 3),
 * so putting the files there (they belong to their owners) needs no code change. Without the files: simple icons of the
 * mod's own (an arrow out of a square, brackets, a marker) in the platform's colour.
 *
 * <p>Tinted on hover: a picture is multiplied towards the button's text colour as its fill turns the platform's colour
 * (a green logo on a green button would vanish); an own icon simply takes the text colour.
 */
public final class LinkIcons {
    /** Where the pictures are, in the mod's resources. */
    public static final String FOLDER = "assets/" + ShatterMod.MOD_ID + "/textures/gui/links/"; // not shown

    private record Pictures(UiTexture small, UiTexture large) {
    }

    private static final Map<LinkButton.Brand, Pictures> LOADED = new EnumMap<>(LinkButton.Brand.class);

    private LinkIcons() {
    }

    /** The icon for {@code brand} at {@code x, y} ({@link Icon#SIZE} square). {@code hover} 0–1: how filled the button is. */
    public static void draw(Ui ui, LinkButton.Brand brand, int x, int y, int textColor, float hover) {
        Pictures pictures = pictures(brand);
        UiTexture texture = pictures.large != null && (pictures.small == null || guiScale() >= 3)
                ? pictures.large : pictures.small;
        if (texture != null) {
            Draw.texture(ui, texture, x, y, Icon.SIZE, Icon.SIZE, Theme.mix(0xFFFFFFFF, textColor, hover));
            return;
        }
        Draw.icon(ui, fallback(brand), x, y, 1, Theme.mix(brand.color, textColor, hover));
    }

    /** The mod's own icon when there is no picture. */
    public static Icon fallback(LinkButton.Brand brand) {
        return switch (brand) {
            case MODRINTH, CURSEFORGE -> Icon.EXTERNAL;
            case GITHUB -> Icon.BRACKETS;
            case BUG -> Icon.MARKER;
        };
    }

    /** Whether a picture was found for {@code brand} (tests, diagnostics). */
    public static boolean hasPicture(LinkButton.Brand brand) {
        Pictures pictures = pictures(brand);
        return pictures.small != null || pictures.large != null;
    }

    private static double guiScale() {
        try {
            return Platform.get().desktop().guiScale();
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private static Pictures pictures(LinkButton.Brand brand) {
        return LOADED.computeIfAbsent(brand, b -> new Pictures(load(b.file, ""), load(b.file, "_32")));
    }

    /** A picture from the mod's resources, or null if there is none or it cannot be read (then the own icon is drawn). */
    private static UiTexture load(String name, String suffix) {
        try (InputStream in = LinkIcons.class.getClassLoader().getResourceAsStream(FOLDER + name + suffix + ".png")) {
            if (in == null) {
                return null;
            }
            UiTextures textures = Platform.get().textures();
            UiTextures.Image image = textures.decodePng(in.readAllBytes());
            String path = "gui/links/" + name + suffix;
            return textures.upload(path, "shatter_gui link icon " + path, image); // not shown
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
