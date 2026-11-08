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

public final class LinkIcons {
    public static final String FOLDER = "assets/" + ShatterMod.MOD_ID + "/textures/gui/links/";

    private record Pictures(UiTexture small, UiTexture large) {
    }

    private static final Map<LinkButton.Brand, Pictures> LOADED = new EnumMap<>(LinkButton.Brand.class);

    private LinkIcons() {
    }

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

    public static Icon fallback(LinkButton.Brand brand) {
        return switch (brand) {
            case MODRINTH, CURSEFORGE -> Icon.EXTERNAL;
            case GITHUB -> Icon.BRACKETS;
            case BUG -> Icon.MARKER;
        };
    }

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

    private static UiTexture load(String name, String suffix) {
        try (InputStream in = LinkIcons.class.getClassLoader().getResourceAsStream(FOLDER + name + suffix + ".png")) {
            if (in == null) {
                return null;
            }
            UiTextures textures = Platform.get().textures();
            UiTextures.Image image = textures.decodePng(in.readAllBytes());
            String path = "gui/links/" + name + suffix;
            return textures.upload(path, "shatter_gui link icon " + path, image);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
