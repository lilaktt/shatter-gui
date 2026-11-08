package net.shattergui.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.ui.ConfirmDialog;
import net.shattergui.ui.Container;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;
import net.shattergui.ui.links.LinkButton;
import net.shattergui.links.LinkStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

final class LinksScenario {
    static final String MODRINTH = "https://modrinth.com/mod/shattergui";

    private LinksScenario() {
    }

    static LinkStatus.Http fake(Map<String, Object> answers, AtomicInteger calls) {
        return (url, agent, timeout) -> {
            calls.incrementAndGet();
            Object answer = answers.get(url);
            if (answer instanceof java.io.IOException e) {
                throw e;
            }
            return answer instanceof Integer code ? code : 200;
        };
    }

    static void everyPageUp() {
        LinkStatus.useForTests(fake(Map.of(), new AtomicInteger()));
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        AtomicInteger calls = new AtomicInteger();
        LinkStatus.useForTests(fake(Map.of(LinkStatus.Page.MODRINTH.api, 404, LinkStatus.Page.GITHUB.api, 200), calls));
        try {
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            context.runOnClient(mc -> EditorScenario.editor(mc).selectTab(ShatterConfigScreen.Tab.GENERAL));
            revealAbout(context);
            await();
            context.waitTicks(2);
            Map<LinkButton.Brand, LinkStatus.State> states = context.computeOnClient(mc -> {
                Map<LinkButton.Brand, LinkStatus.State> out = new java.util.EnumMap<>(LinkButton.Brand.class);
                for (LinkButton button : buttons()) {
                    out.put(button.brandOf(), button.state());
                }
                return out;
            });
            ShatterGuiClient.LOGGER.info("[ShatterTest] {}: states {}, {} requests", name, states, calls.get());
            check.accept(states.get(LinkButton.Brand.MODRINTH) == LinkStatus.State.NOT_PUBLISHED, name
                    + ": Modrinth answering 404 must be yellow (" + states + ")");
            check.accept(states.get(LinkButton.Brand.GITHUB) == LinkStatus.State.AVAILABLE
                    && states.get(LinkButton.Brand.BUG) == LinkStatus.State.AVAILABLE, name + ": GitHub answering 200 must be "
                    + "green, the bug report too (" + states + ")");
            check.accept(states.get(LinkButton.Brand.CURSEFORGE) == LinkStatus.State.NONE, name
                    + ": CurseForge is never checked, so it has no dot (" + states + ")");
            check.accept(calls.get() == 2, name + ": one request per page (" + calls.get() + ")");
            check.accept(context.computeOnClient(mc -> buttons().stream().allMatch(b -> b.tooltipTitle() != null
                    && b.tooltipText() != null)), name + ": every page button has a tooltip");
            check.accept(context.computeOnClient(mc -> {
                LinkButton yellow = button(LinkButton.Brand.MODRINTH);
                return yellow.tooltipText().getString().contains(
                        net.minecraft.network.chat.Component.translatable("ui.shatter_gui.about.status.not_published").getString());
            }), name + ": the yellow button's tooltip says the page is not published yet or in moderation");

            colours(context, check, name);

            context.runOnClient(mc -> button(LinkButton.Brand.MODRINTH).press());
            context.waitTicks(3);
            boolean explained = context.computeOnClient(mc -> Mc26.screen() instanceof ShatterConfigScreen screen
                    && screen.overlays().stream().anyMatch(o -> o instanceof ConfirmDialog));
            check.accept(explained, name + ": a yellow page must explain before opening");
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitTicks(3);
            check.accept(linkScreenShows(context, MODRINTH), name + ": after the explanation the link confirmation opens");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            context.runOnClient(mc -> button(LinkButton.Brand.GITHUB).press());
            context.waitTicks(3);
            check.accept(linkScreenShows(context, "https://github.com/lilaktt/shatter-gui"), name
                    + ": a green page opens the link confirmation at once");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);

            AtomicInteger offlineCalls = new AtomicInteger();
            LinkStatus.useForTests(fake(Map.of(LinkStatus.Page.MODRINTH.api, new java.net.UnknownHostException("api.modrinth.com"),
                    LinkStatus.Page.GITHUB.api, new java.net.http.HttpTimeoutException("timed out")), offlineCalls));
            context.waitTicks(3);
            await();
            context.waitTicks(2);
            LinkStatus.State offline = context.computeOnClient(mc -> button(LinkButton.Brand.MODRINTH).state());
            LinkStatus.State offlineGithub = context.computeOnClient(mc -> button(LinkButton.Brand.GITHUB).state());
            check.accept(offline == LinkStatus.State.UNKNOWN && offlineGithub == LinkStatus.State.UNKNOWN, name
                    + ": offline every checked page is grey (" + offline + ", " + offlineGithub + ")");
            context.runOnClient(mc -> button(LinkButton.Brand.MODRINTH).press());
            context.waitTicks(3);
            check.accept(linkScreenShows(context, MODRINTH), name + ": offline the click opens the link at once");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);

            AtomicInteger offCalls = new AtomicInteger();
            LinkStatus.useForTests(fake(Map.of(), offCalls));
            context.runOnClient(mc -> EditorScenario.editor(mc).working().linkStatusCheck = false);
            context.waitTicks(10);
            await();
            LinkStatus.State off = context.computeOnClient(mc -> button(LinkButton.Brand.MODRINTH).state());
            check.accept(offCalls.get() == 0 && off == LinkStatus.State.NONE, name + ": with the check off nothing is asked ("
                    + offCalls.get() + " requests, " + off + ")");
            context.runOnClient(mc -> EditorScenario.editor(mc).working().linkStatusCheck = true);
            context.runOnClient(mc -> Mc26.setScreen(null));
            context.waitTicks(3);
        } finally {
            everyPageUp();
        }
    }

    private static void colours(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name) {
        int scale = context.computeOnClient(mc -> (int) mc.getWindow().getGuiScale());
        int[] at = context.computeOnClient(mc -> {
            LinkButton b = button(LinkButton.Brand.MODRINTH);
            return new int[]{b.x + b.w / 2, b.y + b.h / 2, b.x, b.y, b.h};
        });
        context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
        context.waitTicks(15);
        float hovered = context.computeOnClient(mc -> button(LinkButton.Brand.MODRINTH).brandFill());
        FullFrame.request(context, name + "_hover_modrinth", true);
        FullFrame.Image hoverFrame = FullFrame.get(context, name + "_hover_modrinth");
        context.getInput().setCursorPos(2, 2);
        context.runOnClient(mc -> ((UiScreen) Mc26.screen()).setFocus(button(LinkButton.Brand.CURSEFORGE)));
        context.waitTicks(15);
        float focused = context.computeOnClient(mc -> button(LinkButton.Brand.CURSEFORGE).brandFill());
        int[] cf = context.computeOnClient(mc -> {
            LinkButton b = button(LinkButton.Brand.CURSEFORGE);
            return new int[]{b.x, b.y, b.h};
        });
        FullFrame.request(context, name + "_focus_curseforge", true);
        FullFrame.Image focusFrame = FullFrame.get(context, name + "_focus_curseforge");
        context.runOnClient(mc -> ((UiScreen) Mc26.screen()).setFocus(null));
        int hoverPixel = pixel(hoverFrame, at[2] + 3, at[3] + at[4] / 2, scale);
        int focusPixel = pixel(focusFrame, cf[0] + 3, cf[1] + cf[2] / 2, scale);
        ShatterGuiClient.LOGGER.info(String.format(Locale.ROOT, "[ShatterTest] %s: hover fill %.2f, pixel %06X; focus fill "
                + "%.2f, pixel %06X", name, hovered, hoverPixel & 0xFFFFFF, focused, focusPixel & 0xFFFFFF));
        check.accept(hovered > 0.95f, name + ": the mouse over Modrinth must fill it (" + hovered + ")");
        check.accept(focused > 0.95f, name + ": keyboard focus on CurseForge must fill it (" + focused + ")");
        check.accept(near(hoverPixel, LinkButton.Brand.MODRINTH.color, 24), String.format(Locale.ROOT,
                "%s: the hovered Modrinth button must show green on screen (%06X)", name, hoverPixel & 0xFFFFFF));
        check.accept(near(focusPixel, LinkButton.Brand.CURSEFORGE.color, 24), String.format(Locale.ROOT,
                "%s: the focused CurseForge button must show its red-orange on screen (%06X)", name, focusPixel & 0xFFFFFF));
    }

    private static boolean near(int a, int b, int tolerance) {
        return Math.abs(FullFrame.r(a) - FullFrame.r(b)) <= tolerance && Math.abs(FullFrame.g(a) - FullFrame.g(b)) <= tolerance
                && Math.abs(FullFrame.b(a) - FullFrame.b(b)) <= tolerance;
    }

    private static void revealAbout(ClientGameTestContext context) {
        for (int pass = 0; pass < 2; pass++) {
            context.runOnClient(mc -> {
                LinkButton b = button(LinkButton.Brand.MODRINTH);
                for (Widget p = b.parent(); p != null; p = p.parent()) {
                    if (p instanceof net.shattergui.ui.ScrollView view) {
                        view.reveal(b);
                    }
                }
            });
            context.waitTicks(12);
        }
        String where = context.computeOnClient(mc -> {
            LinkButton b = button(LinkButton.Brand.MODRINTH);
            return String.format(Locale.ROOT, "button %d,%d %dx%d, GUI %dx%d, scale %s", b.x, b.y, b.w, b.h,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), mc.getWindow().getGuiScale());
        });
        ShatterGuiClient.LOGGER.info("[ShatterTest] links: {}", where);
    }

    private static int pixel(FullFrame.Image image, int x, int y, int scale) {
        if (image == null) {
            return 0;
        }
        int px = x * scale;
        int py = y * scale;
        return px < 0 || py < 0 || px >= image.width() || py >= image.height() ? 0 : image.at(px, py);
    }

    private static void await() {
        try {
            LinkStatus.awaitForTests(Duration.ofSeconds(4).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean linkScreenShows(ClientGameTestContext context, String url) {
        return context.computeOnClient(mc -> Mc26.screen() instanceof net.minecraft.client.gui.screens.ConfirmLinkScreen
                && Mc26.screen().getNarrationMessage().getString().contains(url)
                || Mc26.screen() instanceof net.minecraft.client.gui.screens.ConfirmLinkScreen
                && Mc26.screen().children().toString().contains(url));
    }

    static List<LinkButton> buttons() {
        List<Widget> all = new ArrayList<>();
        walk(((UiScreen) Mc26.screen()).rootWidget(), all);
        List<LinkButton> out = new ArrayList<>();
        for (Widget w : all) {
            if (w instanceof LinkButton b) {
                out.add(b);
            }
        }
        return out;
    }

    static LinkButton button(LinkButton.Brand brand) {
        return buttons().stream().filter(b -> b.brandOf() == brand).findFirst().orElseThrow();
    }

    private static void walk(Widget w, List<Widget> out) {
        if (w == null || !w.visible) {
            return;
        }
        out.add(w);
        if (w instanceof net.shattergui.ui.ScrollView scroll) {
            walk(scroll.content(), out);
        }
        if (w instanceof Container c) {
            for (Widget child : c.children()) {
                walk(child, out);
            }
        }
    }
}
