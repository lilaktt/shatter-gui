package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.fly.OpenAssemble;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class MatrixScenario {
    static final List<String> STEPS = List.of("startup", "glass_chest", "glass_inventory", "open_effect", "fly_container",
            "esc", "ontop", "crawler_main");

    private final ClientGameTestContext context;
    private final TestSingleplayerContext singleplayer;
    private final BiConsumer<Boolean, String> check;
    private final String mode;

    private MatrixScenario(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                           BiConsumer<Boolean, String> check, String mode) {
        this.context = context;
        this.singleplayer = singleplayer;
        this.check = check;
        this.mode = mode;
    }

    static void startup(ClientGameTestContext context, List<String> failures) {
        Map<String, String> info = new LinkedHashMap<>();
        context.runOnClient(mc -> {
            Mc26.gpuInfo(info);
            info.put("Minecraft", net.minecraft.SharedConstants.getCurrentVersion().name());
        });
        info.forEach((k, v) -> write("info\t" + k + "\t" + clean(v)));
        write("startup\t" + (failures.isEmpty() ? "OK\t" : "FAIL\t" + clean(String.join("; ", failures))));
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, BiConsumer<Boolean, String> check,
                    String mode) {
        new MatrixScenario(context, singleplayer, check, mode).steps();
    }

    private void steps() {
        ShatterConfig config = ShatterConfig.get();
        String effect = config.effect;
        String open = config.openEffect;
        try {
            step("glass_chest", this::glassChest);
            step("glass_inventory", this::glassInventory);
            step("open_effect", this::openEffect);
            step("fly_container", c -> FlyScenario.run(context, singleplayer, c, context::takeScreenshot, "matrix_fly"));
            step("esc", c -> EscScenario.run(context, singleplayer, c, mode));
            step("ontop", c -> OnTopScenario.run(context, singleplayer, c, mode));
            step("crawler_main", c -> UiCrawlerScenario.runMain(context, c, context::takeScreenshot, "matrix_crawler"));
        } finally {
            config.effect = effect;
            config.openEffect = open;
        }
    }

    private void step(String id, Consumer<BiConsumer<Boolean, String>> body) {
        List<String> failed = new ArrayList<>();
        BiConsumer<Boolean, String> local = (ok, message) -> {
            if (!ok) {
                failed.add(message);
            }
            check.accept(ok, message);
        };
        try {
            ShatterGuiClient.LOGGER.info("[ShatterTest] matrix: {}", id);
            body.accept(local);
        } catch (Throwable t) {
            failed.add(t.toString());
            check.accept(false, "matrix " + id + ": " + t);
        }
        write(id + "\t" + (failed.isEmpty() ? "OK\t" : "FAIL\t" + clean(String.join("; ", failed))));
        try {
            context.runOnClient(mc -> {
                if (Mc26.screen() != null) {
                    Mc26.setScreen(null);
                }
            });
            waitForNoWaves();
        } catch (Throwable t) {
            ShatterGuiClient.LOGGER.warn("[ShatterTest] matrix: could not clean up after {}", id, t);
        }
    }

    private void waitForNoWaves() {
        for (int i = 0; i < 400 && context.computeOnClient(mc -> ShardManager.waveCount() > 0); i++) {
            context.waitTick();
        }
        context.waitTicks(3);
    }

    private void glassChest(BiConsumer<Boolean, String> c) {
        ShatterConfig config = ShatterConfig.get();
        config.effect = "glass";
        config.openEffect = ShatterConfig.OPEN_NONE;
        BlockPos chest = placeChest();
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(mc -> Mc26.screen() instanceof AbstractContainerScreen<?>, 60);
        context.waitTicks(5);
        c.accept(context.computeOnClient(mc -> Mc26.screen() instanceof AbstractContainerScreen<?>), "the chest must open");
        close(c, "matrix_glass_chest", "chest");
        removeBlock(chest);
    }

    private void glassInventory(BiConsumer<Boolean, String> c) {
        ShatterConfig config = ShatterConfig.get();
        config.effect = "glass";
        config.openEffect = ShatterConfig.OPEN_NONE;
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);
        close(c, "matrix_glass_inventory", "inventory");
    }

    private void close(BiConsumer<Boolean, String> c, String shot, String what) {
        int closes = context.computeOnClient(mc -> ShatterProfiler.closeCount());
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.waitTicks(3);
        c.accept(context.computeOnClient(mc -> ShatterProfiler.closeCount()) > closes, "closing the " + what + " must start the effect");
        c.accept(context.computeOnClient(mc -> ShardManager.liveShardCount()) > 0, "closing the " + what + " must make pieces");
        context.takeScreenshot(shot);
        waitForNoWaves();
        c.accept(context.computeOnClient(mc -> ShardManager.liveShardCount()) == 0, "the pieces of the " + what + " must go away");
    }

    private void openEffect(BiConsumer<Boolean, String> c) {
        ShatterConfig config = ShatterConfig.get();
        config.effect = "glass";
        config.openEffect = ShatterConfig.OPEN_DATA_PREFIX + "glass";
        int opens = context.computeOnClient(mc -> ShatterProfiler.openCount());
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(3);
        c.accept(context.computeOnClient(mc -> ShatterProfiler.openCount()) > opens, "the inventory must open with an effect");
        c.accept(context.computeOnClient(mc -> OpenAssemble.hides(Mc26.screen())), "the screen must stay hidden while it gathers");
        context.takeScreenshot("matrix_open_effect");
        boolean shown = false;
        for (int i = 0; i < 200 && !shown; i++) {
            context.waitTick();
            shown = context.computeOnClient(mc -> Mc26.screen() != null && !OpenAssemble.hides(Mc26.screen()));
        }
        c.accept(shown, "the real screen must show after the opening");
        context.waitTicks(2);
        context.takeScreenshot("matrix_open_effect_end");
        config.openEffect = ShatterConfig.OPEN_NONE;
    }

    private BlockPos placeChest() {
        BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());
        BlockPos chest = feet.offset(0, 0, 2);
        singleplayer.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:chest[facing=north]",
                chest.getX(), chest.getY(), chest.getZ()));
        FlyScenario.lookAt(context, singleplayer, feet, chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
        context.waitTicks(10);
        return chest;
    }

    private void removeBlock(BlockPos pos) {
        singleplayer.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air",
                pos.getX(), pos.getY(), pos.getZ()));
        context.waitTicks(3);
    }

    private static String clean(String text) {
        String line = text == null ? "" : text.replaceAll("[\\t\\r\\n]+", " ");
        return net.shattergui.debug.Diagnostics.sanitize(line);
    }

    private static synchronized void write(String line) {
        Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("matrix-results.tsv");
        try {
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            ShatterGuiClient.LOGGER.error("[ShatterTest] could not write the matrix results", e);
        }
    }
}
