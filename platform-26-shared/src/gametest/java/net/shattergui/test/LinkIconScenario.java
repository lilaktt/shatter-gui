package net.shattergui.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.debug.RecentWarnings;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.links.LinkButton;
import net.shattergui.ui.links.LinkIcons;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

final class LinkIconScenario {
    private static final int EXACT = 3;
    private static final int READABLE = 60;
    private static final LinkButton.Brand[] PICTURED = {LinkButton.Brand.MODRINTH, LinkButton.Brand.CURSEFORGE};

    private LinkIconScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        int scaleBefore = context.computeOnClient(mc -> mc.options.guiScale().get());
        try {
            for (LinkButton.Brand brand : LinkButton.Brand.values()) {
                boolean pictured = context.computeOnClient(mc -> LinkIcons.hasPicture(brand));
                boolean expected = brand == LinkButton.Brand.MODRINTH || brand == LinkButton.Brand.CURSEFORGE;
                check.accept(pictured == expected, name + ": " + brand + " picture loaded=" + pictured + ", expected " + expected);
            }
            for (int scale = 1; scale <= 4; scale++) {
                EditorScenario.setScale(context, scale);
                open(context);
                FullFrame.request(context, name + "_about_scale" + scale, true);
                FullFrame.Image frame = FullFrame.get(context, name + "_about_scale" + scale);
                int buttons = context.computeOnClient(mc -> LinksScenario.buttons().size());
                check.accept(buttons == 4, name + ": About has the four page buttons and Copy diagnostics (" + buttons + " page buttons)");
                for (LinkButton.Brand brand : PICTURED) {
                    crisp(context, check, name, frame, brand, scale);
                }
            }
            EditorScenario.setScale(context, 2);
            open(context);
            states(context, check, name);
            broken(context, check, name);
        } finally {
            context.runOnClient(mc -> LinkIcons.reloadForTests(null));
            context.getInput().setCursorPos(2, 2);
            EditorScenario.setScale(context, scaleBefore);
            context.runOnClient(mc -> Mc26.setScreen(null));
            context.waitTicks(3);
        }
    }

    private static void open(ClientGameTestContext context) {
        context.getInput().setCursorPos(2, 2);
        context.setScreen(() -> new ShatterConfigScreen(null));
        context.waitTicks(3);
        context.runOnClient(mc -> EditorScenario.editor(mc).selectTab(ShatterConfigScreen.Tab.GENERAL));
        LinksScenario.revealAbout(context);
        context.runOnClient(mc -> {
            LinkButton b = LinksScenario.button(LinkButton.Brand.MODRINTH);
            for (net.shattergui.ui.Widget p = b.parent(); p != null; p = p.parent()) {
                if (p instanceof net.shattergui.ui.ScrollView view) {
                    view.scrollToEnd();
                }
            }
            ((UiScreen) Mc26.screen()).setFocus(null);
        });
        context.waitTicks(15);
    }

    private static int[] source(LinkButton.Brand brand) {
        LinkIcons.Found found = LinkIcons.find(brand, path -> LinkIconScenario.class.getClassLoader().getResourceAsStream(path));
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(found.png()));
            return image.getRGB(0, 0, 8, 8, null, 0, 8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void crisp(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name,
                              FullFrame.Image frame, LinkButton.Brand brand, int scale) {
        int[] at = context.computeOnClient(mc -> {
            LinkButton b = LinksScenario.button(brand);
            return new int[]{b.iconX(), b.iconY(), b.y, b.h};
        });
        int[] texels = source(brand);
        int worst = 0;
        int checked = 0;
        for (int ty = 0; ty < 8; ty++) {
            for (int tx = 0; tx < 8; tx++) {
                int want = texels[ty * 8 + tx];
                if (want >>> 24 != 255) {
                    continue;
                }
                for (int py = 0; py < scale; py++) {
                    for (int px = 0; px < scale; px++) {
                        int got = frame.at((at[0] + tx) * scale + px, (at[1] + ty) * scale + py);
                        worst = Math.max(worst, distance(got, want));
                        checked++;
                    }
                }
            }
        }
        int textTop = at[2] + (at[3] - 8) / 2;
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} at GUI scale {}: icon at {},{} (text row {}), {} pixels checked, "
                + "largest difference from the author's colours {}", name, brand, scale, at[0], at[1], textTop, checked, worst);
        check.accept(at[1] == textTop, name + ": " + brand + " icon must sit on the text row at scale " + scale);
        check.accept(checked > 0 && worst <= EXACT, String.format(Locale.ROOT, "%s: %s at GUI scale %d must be the author's "
                + "pixels, 8 GUI px, sharp (largest difference %d over %d pixels)", name, brand, scale, worst, checked));
    }

    private static void states(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        int scale = context.computeOnClient(mc -> (int) mc.getWindow().getGuiScale());
        for (LinkButton.Brand brand : PICTURED) {
            int[] b = context.computeOnClient(mc -> {
                LinkButton button = LinksScenario.button(brand);
                return new int[]{button.x + button.w - 6, button.y + button.h / 2};
            });
            List<String> shots = new ArrayList<>();
            context.getInput().setCursorPos(2, 2);
            context.waitTicks(15);
            shots.add(shot(context, name, brand, "normal"));
            context.getInput().setCursorPos(b[0] * scale, b[1] * scale);
            context.waitTicks(15);
            shots.add(shot(context, name, brand, "hover"));
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(8);
            shots.add(shot(context, name, brand, "pressed"));
            context.runOnClient(mc -> ((UiScreen) Mc26.screen()).setFocus(null));
            context.getInput().setCursorPos(2, 2);
            context.waitTicks(2);
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            if (!context.computeOnClient(mc -> Mc26.screen() instanceof ShatterConfigScreen)) {
                open(context);
            }
            context.runOnClient(mc -> ((UiScreen) Mc26.screen()).setFocus(LinksScenario.button(brand)));
            context.waitTicks(15);
            shots.add(shot(context, name, brand, "focus"));
            context.runOnClient(mc -> ((UiScreen) Mc26.screen()).setFocus(null));
            for (String state : shots) {
                check.accept(state.startsWith("ok"), name + ": " + brand + " icon must read against its button: " + state);
            }
        }
    }

    private static String shot(ClientGameTestContext context, String name, LinkButton.Brand brand, String state) {
        String file = name + "_" + brand.file + "_" + state;
        int[] at = context.computeOnClient(mc -> {
            LinkButton b = LinksScenario.button(brand);
            return new int[]{b.iconX(), b.iconY(), (int) mc.getWindow().getGuiScale()};
        });
        FullFrame.request(context, file, true);
        FullFrame.Image frame = FullFrame.get(context, file);
        if (frame == null) {
            return "no frame for " + state;
        }
        int scale = at[2];
        int[] texels = source(brand);
        long sum = 0;
        int count = 0;
        for (int ty = 0; ty < 8; ty++) {
            for (int tx = 0; tx < 8; tx++) {
                if (texels[ty * 8 + tx] >>> 24 != 255) {
                    continue;
                }
                int icon = frame.at((at[0] + tx) * scale + scale / 2, (at[1] + ty) * scale + scale / 2);
                int around = nearestClear(frame, texels, at, scale, tx, ty);
                sum += distance(icon, around);
                count++;
            }
        }
        int mean = count == 0 ? 0 : (int) (sum / count);
        String text = String.format(Locale.ROOT, "%s mean colour distance icon/background %d", state, mean);
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {} {}", name, brand, text);
        return (mean >= READABLE ? "ok " : "too low ") + text;
    }

    private static int nearestClear(FullFrame.Image frame, int[] texels, int[] at, int scale, int tx, int ty) {
        for (int r = 1; r < 8; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int x = tx + dx;
                    int y = ty + dy;
                    boolean outside = x < 0 || y < 0 || x >= 8 || y >= 8;
                    if (outside || texels[y * 8 + x] >>> 24 == 0) {
                        int sx = Math.max(-1, Math.min(8, x));
                        int sy = Math.max(-1, Math.min(8, y));
                        return frame.at((at[0] + sx) * scale + scale / 2, (at[1] + sy) * scale + scale / 2);
                    }
                }
            }
        }
        return 0;
    }

    private static void broken(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        byte[] big = png(16);
        byte[] garbage = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        context.runOnClient(mc -> {
            RecentWarnings.clear();
            LinkIcons.reloadForTests(path -> path.endsWith("/modrith.png") || path.endsWith("/modrinth.png")
                    ? new ByteArrayInputStream(big) : path.endsWith("/curseforge.png") ? new ByteArrayInputStream(garbage)
                    : (InputStream) null);
        });
        open(context);
        FullFrame.request(context, name + "_broken_files", true);
        FullFrame.get(context, name + "_broken_files");
        boolean modrinth = context.computeOnClient(mc -> LinkIcons.hasPicture(LinkButton.Brand.MODRINTH));
        boolean curseforge = context.computeOnClient(mc -> LinkIcons.hasPicture(LinkButton.Brand.CURSEFORGE));
        List<String> lines = context.computeOnClient(mc -> RecentWarnings.all().stream().filter(l -> l.contains("Link icon")).toList());
        context.waitTicks(10);
        List<String> later = context.computeOnClient(mc -> RecentWarnings.all().stream().filter(l -> l.contains("Link icon")).toList());
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: broken files -> pictures {} {}, log {}", name, modrinth, curseforge, lines);
        check.accept(!modrinth && !curseforge, name + ": a 16x16 or damaged file must fall back to the built-in icon");
        check.accept(lines.size() == 2 && later.size() == 2 && lines.stream().anyMatch(l -> l.contains("16x16, must be 8x8"))
                && lines.stream().anyMatch(l -> l.contains("could not be read")), name
                + ": one log line per broken file, said once: " + later);
        check.accept(context.computeOnClient(mc -> Mc26.screen() instanceof ShatterConfigScreen), name + ": the settings screen must survive broken files");
    }

    private static byte[] png(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, 0xFFFFFFFF);
            }
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static int distance(int a, int b) {
        return Math.max(Math.abs(FullFrame.r(a) - FullFrame.r(b)), Math.max(Math.abs(FullFrame.g(a) - FullFrame.g(b)),
                Math.abs(FullFrame.b(a) - FullFrame.b(b))));
    }
}
