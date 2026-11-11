package net.shattergui.ui.links;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkIconsTest {
    private static final Map<String, String> AUTHOR_FILES = Map.of(
            "assets/shatter_gui/gui/links/modrith.png", "21d3463cc56862c7c22d48a0c6393a57d01ad0873fd78959bd044833d69e4bbb",
            "assets/shatter_gui/gui/links/curseforge.png", "6b35a0c5f9b08e25a42682060f00eba1cad8061f47695572395ca5ecf069c01f");

    private static InputStream resource(String path) {
        return LinkIconsTest.class.getClassLoader().getResourceAsStream(path);
    }

    private static int[] pixels(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static LinkIcons.Analysis analyse(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        return LinkIcons.analyse(image.getWidth(), image.getHeight(), pixels(png));
    }

    @Test
    void theAuthorsPicturesAreFoundUnchanged() throws IOException, NoSuchAlgorithmException {
        LinkIcons.Found modrinth = LinkIcons.find(LinkButton.Brand.MODRINTH, LinkIconsTest::resource);
        LinkIcons.Found curseforge = LinkIcons.find(LinkButton.Brand.CURSEFORGE, LinkIconsTest::resource);
        assertNotNull(modrinth, "modrinth.png (or the author's modrith.png)");
        assertNotNull(curseforge, "curseforge.png");
        assertEquals("assets/shatter_gui/gui/links/modrith.png", modrinth.path());
        assertEquals("assets/shatter_gui/gui/links/curseforge.png", curseforge.path());
        for (LinkIcons.Found found : new LinkIcons.Found[]{modrinth, curseforge}) {
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(found.png()));
            assertEquals(AUTHOR_FILES.get(found.path()), sha, found.path() + " must stay exactly as the author drew it");
        }
    }

    @Test
    void picturesAreEightByEightWithVisiblePixels() throws IOException {
        for (LinkButton.Brand brand : new LinkButton.Brand[]{LinkButton.Brand.MODRINTH, LinkButton.Brand.CURSEFORGE}) {
            LinkIcons.Analysis analysis = analyse(LinkIcons.find(brand, LinkIconsTest::resource).png());
            assertEquals(8, analysis.width(), brand + " width");
            assertEquals(8, analysis.height(), brand + " height");
            assertTrue(analysis.opaque() > 0, brand + " has visible pixels");
            assertTrue(analysis.transparent(), brand + " has a transparent background");
            assertNull(LinkIcons.problem(analysis), brand + " is accepted");
            assertTrue(analysis.coloured(), brand + " is drawn in its own colours, not tinted");
        }
    }

    @Test
    void missingPicturesFallBack() {
        assertNull(LinkIcons.find(LinkButton.Brand.GITHUB, LinkIconsTest::resource), "no github.png was drawn yet");
        assertNull(LinkIcons.find(LinkButton.Brand.BUG, LinkIconsTest::resource), "no bug.png was drawn yet");
    }

    @Test
    void theStandardFolderWinsAndNamesAreMatched() {
        byte[] standard = {1};
        byte[] author = {2};
        LinkIcons.Found found = LinkIcons.find(LinkButton.Brand.MODRINTH, path -> switch (path) {
            case "assets/shatter_gui/textures/gui/links/modrinth.png" -> new ByteArrayInputStream(standard);
            case "assets/shatter_gui/gui/links/modrith.png" -> new ByteArrayInputStream(author);
            default -> null;
        });
        assertArrayEquals(standard, found.png());
        LinkIcons.Found github = LinkIcons.find(LinkButton.Brand.GITHUB,
                path -> path.endsWith("/gui/links/github.png") ? new ByteArrayInputStream(author) : null);
        assertEquals("assets/shatter_gui/textures/gui/links/github.png", github.path());
    }

    @Test
    void wrongPicturesAreRefused() throws IOException {
        assertEquals("is 16x16, must be 8x8", LinkIcons.problem(analyse(png(16, 16, 0xFFFFFFFF))));
        assertEquals("is 8x9, must be 8x8", LinkIcons.problem(analyse(png(8, 9, 0xFFFFFFFF))));
        assertEquals("has no visible pixel", LinkIcons.problem(analyse(png(8, 8, 0x00FFFFFF))));
        assertNull(ImageIO.read(new ByteArrayInputStream(new byte[]{1, 2, 3})), "garbage is not a picture");
    }

    @Test
    void lightPicturesAreTintedColouredOnesAreNot() throws IOException {
        assertFalse(analyse(png(8, 8, 0xFFFFFFFF)).coloured(), "white");
        assertFalse(analyse(png(8, 8, 0xFFD0D4DA)).coloured(), "light grey");
        assertTrue(analyse(png(8, 8, 0xFF1BD96A)).coloured(), "Modrinth green");
    }

    @Test
    void hoverLightensButKeepsAlpha() {
        int[] out = LinkIcons.lighter(new int[]{0xFF000000, 0x80FF0000, 0x00000000}, 0.5f);
        assertEquals(0xFF808080, out[0]);
        assertEquals(0x80FF8080, out[1]);
        assertEquals(0x00808080, out[2]);
    }

    private static byte[] png(int w, int h, int argb) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
