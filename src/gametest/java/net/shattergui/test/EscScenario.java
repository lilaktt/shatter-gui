package net.shattergui.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.fly.FlyOpen;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.shatter.ShardManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Esc is never lost or delayed ({@code -PtestOnly=esc}; also with {@code -PspatialGui=1.8} and {@code -Pshaders}): the
 * real key closes a chest and the inventory within {@link #MAX_CLOSE_TICKS} — settled, right after opening (while the
 * opening effect plays), during the close effect of the screen before — and opens the pause menu over a running effect,
 * which shows within {@link #MAX_SHOW_TICKS} and closes again with Esc. With several open effects and with the thematic
 * mode on and off. The pause menu pauses the game: an opening must not wait for game ticks.
 */
final class EscScenario {
    /** Esc closes the screen in the key press itself; a few ticks of slack for the test's own input path. */
    static final int MAX_CLOSE_TICKS = 5;
    /** The longest opening is 10 ticks at the default speed (OpenTiming), plus slack. */
    static final int MAX_SHOW_TICKS = 15;
    private static final List<String> OPENS = List.of(ShatterConfig.OPEN_FLY_FROM_BLOCK,
            ShatterConfig.OPEN_DATA_PREFIX + "pop", ShatterConfig.OPEN_DATA_PREFIX + "glass", ShatterConfig.OPEN_NONE);

    private final ClientGameTestContext context;
    private final BiConsumer<Boolean, String> check;
    private final String tag;
    private final List<String> latencies = new ArrayList<>();
    private int cases;

    private EscScenario(ClientGameTestContext context, BiConsumer<Boolean, String> check, String tag) {
        this.context = context;
        this.check = check;
        this.tag = tag;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String tag) {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        boolean thematic = config.thematic;
        EscScenario s = new EscScenario(context, check, tag);
        try {
            BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
            BlockPos chest = feet.offset(0, 0, 2);
            singleplayer.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:chest[facing=north]",
                    chest.getX(), chest.getY(), chest.getZ()));
            FlyScenario.lookAt(context, singleplayer, feet, chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
            for (boolean theme : new boolean[]{false, true}) {
                config.thematic = theme;
                for (String o : theme ? List.of(ShatterConfig.OPEN_FLY_FROM_BLOCK, ShatterConfig.OPEN_DATA_PREFIX + "pop") : OPENS) {
                    config.openEffect = o;
                    config.effect = ShatterConfig.OPEN_FLY_FROM_BLOCK.equals(o) ? "fly" : "glass";
                    s.all((theme ? "thematic " : "") + o + "/" + config.effect);
                }
            }
        } finally {
            config.effect = effect;
            config.openEffect = open;
            config.thematic = thematic;
        }
        ShatterGuiClient.LOGGER.info("[ShatterTest] esc {}: {} cases; Esc → first frame of the close effect: {}", tag,
                s.cases, String.join(", ", s.latencies));
    }

    private void all(String set) {
        // Settled screens.
        openChest(set + " chest");
        context.waitTicks(20);
        esc(set + " chest, settled", true);
        openInventory();
        context.waitTicks(20);
        esc(set + " inventory, settled", true);
        // Esc during the opening effect (well under 0.5 s).
        openChest(set + " chest");
        context.waitTicks(2);
        esc(set + " chest, 2 ticks after opening", true);
        openInventory();
        context.waitTick();
        esc(set + " inventory, 1 tick after opening", true);
        // Esc while the previous screen's close effect plays.
        openChest(set + " chest");
        context.waitTicks(15);
        esc(set + " chest before the inventory", true);
        context.waitTick();
        openInventory();
        context.waitTicks(3);
        esc(set + " inventory during the chest's close effect", true);
        // Esc in the world while an effect plays: the pause menu (it pauses the game) shows and closes again.
        context.waitTick();
        pause(set + " pause menu during a close effect", MAX_SHOW_TICKS + 5);
        // The pause menu closed at once, while its opening still plays.
        waitClear();
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        check(context.computeOnClient(mc -> mc.screen instanceof PauseScreen), set + ": Esc in the world must open the pause menu");
        context.waitTicks(2);
        esc(set + " pause menu, 2 ticks after opening", true);
        waitClear();
    }

    /** Esc in the world: the pause menu must open and be drawn soon (not hidden by its opening), then close with Esc. */
    private void pause(String name, int settle) {
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        boolean opened = context.computeOnClient(mc -> mc.screen instanceof PauseScreen);
        check(opened, name + ": Esc in the world must open the pause menu at once");
        if (!opened) {
            return;
        }
        int ticks = 0;
        while (ticks <= MAX_SHOW_TICKS && context.computeOnClient(mc -> hidden(mc.screen))) {
            context.waitTick();
            ticks++;
        }
        boolean paused = context.computeOnClient(Minecraft::isPaused);
        check(ticks <= MAX_SHOW_TICKS, name + ": the pause menu must be shown within " + MAX_SHOW_TICKS
                + " ticks, still hidden by its opening after " + ticks + " (game paused: " + paused + ")");
        log(name, "shown after " + ticks + " ticks (game paused: " + paused + ")");
        context.waitTicks(Math.max(0, settle - ticks));
        esc(name + " → Esc", true);
    }

    /** True while the screen is still hidden or flying in, i.e. not yet the real screen. */
    private static boolean hidden(Screen screen) {
        return screen != null && (OpenAssemble.hides(screen) || FlyOpen.isAnimating(screen));
    }

    private void esc(String name, boolean effect) {
        cases++;
        String before = context.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        int closes = context.computeOnClient(mc -> ShatterProfiler.closeCount());
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        int ticks = 0;
        while (ticks <= MAX_CLOSE_TICKS && context.computeOnClient(mc -> mc.screen != null)) {
            context.waitTick();
            ticks++;
        }
        String after = context.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        check("none".equals(after) && ticks <= MAX_CLOSE_TICKS, name + ": Esc must close " + before + " within "
                + MAX_CLOSE_TICKS + " ticks, still " + after + " after " + ticks);
        if (effect) {
            check(context.computeOnClient(mc -> ShatterProfiler.closeCount()) > closes, name + ": the close effect must start");
            // The first frame of the effect, measured from the key press (it closes the screen in the same call).
            int frames = 0;
            while (frames < MAX_CLOSE_TICKS && context.computeOnClient(mc -> ShatterProfiler.lastCloseLatencyMs()) < 0) {
                context.waitTick();
                frames++;
            }
            double ms = context.computeOnClient(mc -> ShatterProfiler.lastCloseLatencyMs());
            check(ms >= 0, name + ": the close effect must be drawn within " + MAX_CLOSE_TICKS + " ticks");
            latencies.add(String.format(Locale.ROOT, "%.0f", ms));
            log(name, String.format(Locale.ROOT, "closed after %d ticks, effect drawn %.1f ms after Esc", ticks, ms));
        }
    }

    private void openChest(String name) {
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(mc -> mc.screen instanceof AbstractContainerScreen<?> && !(mc.screen instanceof InventoryScreen), 40);
        check(context.computeOnClient(mc -> mc.screen instanceof AbstractContainerScreen<?>), name + ": the chest must open");
    }

    private void openInventory() {
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitTick();
    }

    private void waitClear() {
        for (int i = 0; i < 600 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
    }

    private void check(boolean ok, String message) {
        check.accept(ok, "esc " + tag + ": " + message);
    }

    private void log(String name, String message) {
        ShatterGuiClient.LOGGER.info("[ShatterTest] esc {} {}: {}", tag, name, message);
    }
}
