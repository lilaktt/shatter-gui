package net.shattergui.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.shattergui.ShatterGuiClient;
import net.shattergui.ShatterKeys;
import net.shattergui.config.ShatterConfig;
import net.shattergui.config.screen.ShatterConfigScreen;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.ui.Button;
import net.shattergui.ui.ConfirmDialog;
import net.shattergui.ui.Container;
import net.shattergui.ui.UiScreen;
import net.shattergui.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class ChooseScenario {
    private ChooseScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String name) {
        ShatterConfig live = ShatterConfig.get();
        String effect = live.effect;
        String open = live.openEffect;
        List<String> favorites = new ArrayList<>(live.favorites);
        try {
            check.accept(context.computeOnClient(mc -> ShatterKeys.cycleKey().isUnbound()),
                    name + ": the cycle key is unbound by default");
            ShatterConfig old = ShatterConfig.parse("{\"effect\":\"burn\",\"openEffect\":\"fly_from_block\",\"overrides\":{}}");
            check.accept(old != null && "burn".equals(old.effect) && old.favorites.isEmpty()
                            && ShatterConfig.OPEN_FLY_FROM_BLOCK.equals(old.openEffect),
                    name + ": an old config without favourites reads as before");

            live.effect = "glass";
            live.openEffect = ShatterConfig.OPEN_NONE;
            live.favorites.clear();
            context.setScreen(() -> new ShatterConfigScreen(null));
            context.waitTicks(3);
            context.runOnClient(mc -> {
                ShatterConfigScreen s = (ShatterConfigScreen) Mc26.screen();
                s.selectTab(ShatterConfigScreen.Tab.EFFECTS);
                s.selectEffect("door");
            });
            context.waitTicks(10);
            shot.accept(name + "_door_selected");
            click(context, button("ui.shatter_gui.assign.open"));
            check.accept(work(context).openEffect.equals("open:door"), name + ": “For opening” sets open:door, got "
                    + work(context).openEffect);
            click(context, button("ui.shatter_gui.assign.close"));
            check.accept(work(context).effect.equals("door"), name + ": “For closing” sets door");
            context.waitTicks(5);
            shot.accept(name + "_door_badges");
            boolean badges = context.computeOnClient(mc -> find(Mc26.screen(), w -> w.getClass().getSimpleName().equals("Badges")
                    && w.tooltipTitle() != null).size() == 1);
            check.accept(badges, name + ": exactly one card shows badges (door)");
            click(context, button("ui.shatter_gui.assign.remove"));
            check.accept(work(context).openEffect.equals(ShatterConfig.OPEN_NONE) && work(context).effect.equals("glass"),
                    name + ": “Remove” puts opening to none and closing to glass");
            click(context, button("ui.shatter_gui.assign.both"));
            check.accept(work(context).openEffect.equals("open:door") && work(context).effect.equals("door"),
                    name + ": “For both”");

            context.runOnClient(mc -> ((ShatterConfigScreen) Mc26.screen()).selectEffect("fly"));
            context.waitTicks(3);
            click(context, button("ui.shatter_gui.assign.open"));
            check.accept(work(context).openEffect.equals(ShatterConfig.OPEN_FLY_FROM_BLOCK), name + ": fly opens out of the block");

            context.runOnClient(mc -> ((ShatterConfigScreen) Mc26.screen()).selectEffect("glass"));
            context.waitTicks(3);
            for (String mode : List.of("open", "close", "both")) {
                click(context, segment("ui.shatter_gui.preview.mode." + mode));
                String phase = context.computeOnClient(mc -> ((ShatterConfigScreen) Mc26.screen()).describePreview());
                for (int i = 0; i < 40 && "open".equals(mode) && !(phase.startsWith("OPEN") && phase.contains("ReversedSim")); i++) {
                    context.waitTick();
                    phase = context.computeOnClient(mc -> ((ShatterConfigScreen) Mc26.screen()).describePreview());
                }
                ShatterGuiClient.LOGGER.info("[ShatterTest] {}: preview {} -> {}", name, mode, phase);
                check.accept(!"open".equals(mode) || phase.startsWith("OPEN") && phase.contains("ReversedSim"),
                        name + ": the “Opening” preview plays glass backwards, got " + phase);
                check.accept(!"close".equals(mode) || phase.startsWith("CLOSE") || phase.startsWith("HOLD"),
                        name + ": the “Closing” preview does not open first, got " + phase);
                shot.accept(name + "_preview_" + mode);
            }

            Predicate<Widget> star = w -> w.getClass().getSimpleName().equals("FavoriteStar");
            for (int i = 0; i < 2; i++) {
                click(context, s -> find(s, star).getFirst());
            }
            check.accept(work(context).favorites.isEmpty(), name + ": the star toggles back off");
            click(context, s -> find(s, star).getFirst());
            String first = work(context).favorites.isEmpty() ? "none" : work(context).favorites.getFirst();
            check.accept(work(context).favorites.size() == 1, name + ": the star marks a favourite (" + first + ")");

            context.runOnClient(mc -> ((ShatterConfigScreen) Mc26.screen()).selectEffect("vortex"));
            context.waitTicks(3);
            Predicate<Widget> vortexCard = w -> w instanceof net.shattergui.ui.Card && w.tooltipTitle() != null
                    && w.tooltipTitle().getString().equals(Component.translatable("effect.shatter_gui.vortex").getString());
            context.runOnClient(mc -> {
                Widget card = find(Mc26.screen(), vortexCard).getFirst();
                for (Widget p = card.parent(); p != null; p = p.parent()) {
                    if (p instanceof net.shattergui.ui.ScrollView view) {
                        view.reveal(card);
                    }
                }
            });
            context.waitTicks(12);
            int[] at = context.computeOnClient(mc -> center(find(Mc26.screen(), vortexCard).getFirst()));
            context.runOnClient(mc -> {
                UiScreen s = (UiScreen) Mc26.screen();
                var info = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
                s.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(at[0], at[1], info), false);
                s.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(at[0], at[1], info));
            });
            context.waitTick();
            context.runOnClient(mc -> {
                UiScreen s = (UiScreen) Mc26.screen();
                var info = new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0);
                s.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(at[0], at[1], info), true);
                s.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(at[0], at[1], info));
            });
            context.waitTicks(3);
            boolean asked = context.computeOnClient(mc -> Mc26.screen() instanceof UiScreen s && s.topOverlay() instanceof ConfirmDialog);
            check.accept(asked, name + ": a double click asks first");
            shot.accept(name + "_double_click_question");
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitTicks(3);
            check.accept(work(context).openEffect.equals("open:vortex") && work(context).effect.equals("vortex"),
                    name + ": confirmed: vortex opens and closes, got " + work(context).openEffect + " / " + work(context).effect);
            context.runOnClient(mc -> Mc26.setScreen(null));
            context.waitTicks(3);

            context.runOnClient(mc -> {
                ShatterKeys.cycleKey().setKey(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_K));
                KeyMapping.resetMapping();
            });
            live.favorites.clear();
            live.effect = "glass";
            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitTicks(3);
            check.accept("glass".equals(live.effect), name + ": without favourites the key changes nothing");
            shot.accept(name + "_key_no_favorites");
            live.favorites.addAll(List.of("flip", "glass", "vortex"));
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                context.getInput().pressKey(InputConstants.KEY_K);
                context.waitTicks(3);
                seen.add(live.effect);
                if (i == 0) {
                    shot.accept(name + "_key_favorite_name");
                }
            }
            check.accept(seen.equals(List.of("vortex", "flip", "glass", "vortex")), name + ": the key cycles favourites "
                    + "in list order, got " + seen);
            context.runOnClient(mc -> {
                ShatterKeys.cycleKey().setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
            });
        } finally {
            live.effect = effect;
            live.openEffect = open;
            live.favorites.clear();
            live.favorites.addAll(favorites);
        }
    }

    private static ShatterConfig work(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            try {
                var f = ShatterConfigScreen.class.getDeclaredField("work");
                f.setAccessible(true);
                return (ShatterConfig) f.get(Mc26.screen());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private interface Finder {
        Widget find(net.minecraft.client.gui.screens.Screen screen);
    }

    private static Finder button(String key) {
        String text = Component.translatable(key).getString();
        return s -> find(s, w -> w instanceof Button b && b.text() != null && b.text().getString().equals(text)
                && b.tooltipText() != null).getFirst();
    }

    private static Finder segment(String key) {
        String text = Component.translatable(key).getString();
        return s -> find(s, w -> w instanceof Button b && b.text() != null && b.text().getString().equals(text)
                && w.parent() instanceof net.shattergui.ui.Segmented).getFirst();
    }

    private static void click(ClientGameTestContext context, Finder finder) {
        context.runOnClient(mc -> {
            Widget w = finder.find(Mc26.screen());
            for (Widget p = w.parent(); p != null; p = p.parent()) {
                if (p instanceof net.shattergui.ui.ScrollView view) {
                    view.reveal(w);
                }
            }
        });
        context.waitTicks(12);
        int[] at = context.computeOnClient(mc -> center(finder.find(Mc26.screen())));
        int scale = context.computeOnClient(mc -> (int) mc.getWindow().getGuiScale());
        context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(6);
    }

    private static int[] center(Widget w) {
        return new int[]{w.x + w.w / 2, w.y + w.h / 2};
    }

    static List<Widget> find(net.minecraft.client.gui.screens.Screen screen, Predicate<Widget> test) {
        List<Widget> out = new ArrayList<>();
        if (screen instanceof UiScreen s) {
            walk(s.rootWidget(), test, out);
        }
        return out;
    }

    private static void walk(Widget w, Predicate<Widget> test, List<Widget> out) {
        if (!w.visible) {
            return;
        }
        if (test.test(w)) {
            out.add(w);
        }
        if (w instanceof Container c) {
            for (Widget child : c.children()) {
                walk(child, test, out);
            }
        } else if (w instanceof net.shattergui.ui.ScrollView view) {
            walk(view.content(), test, out);
        }
    }
}
