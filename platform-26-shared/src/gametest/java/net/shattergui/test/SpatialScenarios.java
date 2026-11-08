package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.ShatterKeys;
import net.shattergui.config.ShatterConfig;
import net.shattergui.params.Params;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardManager;
import org.tastytrash.spatialGUI.client.SpatialGUIClient;
import org.tastytrash.spatialGUI.render.InventoryRenderer;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class SpatialScenarios {
    private static final int OPEN_WAIT = 30;

    private final ClientGameTestContext context;
    private final BiConsumer<Boolean, String> check;
    private final Object spatialConfig;

    SpatialScenarios(ClientGameTestContext context, BiConsumer<Boolean, String> check) {
        this.context = context;
        this.check = check;
        this.spatialConfig = findSpatialConfig();
    }

    void run(Supplier<Screen> furnace) {
        ShatterConfig config = ShatterConfig.get();
        config.effect = "glass";
        config.spatialGuiIntegration = true;

        setSpatial("firstPersonModeInventory", false);
        setSpatial("firstPersonModeContainers", false);
        alignmentShots("third");
        closeWithSpatial("spatial_third_inventory", () -> new InventoryScreen(Minecraft.getInstance().player), OPEN_WAIT, true);
        closeWithSpatial("spatial_third_furnace", furnace, OPEN_WAIT, true);
        closeWithSpatial("spatial_third_during_open", () -> new InventoryScreen(Minecraft.getInstance().player), 2, true);
        BurnScenario.run(context, check, context::takeScreenshot, "spatial_third_burn",
                () -> new InventoryScreen(Minecraft.getInstance().player));

        setSpatial("firstPersonModeInventory", true);
        setSpatial("firstPersonModeContainers", true);
        config.spatialGuiIntegration = false;
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(OPEN_WAIT);
        context.takeScreenshot("spatial_first_reference_before");
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.waitTick();
        context.takeScreenshot("spatial_first_reference_own_close_t1");
        context.waitTicks(100);
        config.spatialGuiIntegration = true;
        alignmentShots("first");
        closeWithSpatial("spatial_first_inventory", () -> new InventoryScreen(Minecraft.getInstance().player), OPEN_WAIT, true);
        closeWithSpatial("spatial_first_during_open", () -> new InventoryScreen(Minecraft.getInstance().player), 2, true);

        setSpatial("curvedScreenEnabled", true);
        closeWithSpatial("spatial_first_curved", () -> new InventoryScreen(Minecraft.getInstance().player), OPEN_WAIT, true);
        setSpatial("curvedScreenEnabled", false);
        setSpatial("firstPersonModeInventory", false);
        setSpatial("firstPersonModeContainers", false);

        closeWithSpatial("spatial_pause_not_hooked", () -> new PauseScreen(true), 10, false);

        config.spatialGuiIntegration = false;
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(OPEN_WAIT);
        context.runOnClient(mc -> Mc26.setScreen(null));
        long elapsedLeft = context.computeOnClient(mc -> closeStartElapsed());
        boolean closing = context.computeOnClient(mc -> SpatialGUIClient.renderer().isClosing());
        log("integration_off", "Spatial closing=" + closing + " closeStartElapsed=" + elapsedLeft);
        check.accept(closing && elapsedLeft > 0, "with spatialGuiIntegration=false Spatial GUI must play its own close animation");
        context.waitTicks(100);
        config.spatialGuiIntegration = true;

        context.runOnClient(mc -> ShatterKeys.setEnabled(false));
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(OPEN_WAIT);
        int closes = context.computeOnClient(mc -> ShatterProfiler.closeCount());
        context.runOnClient(mc -> Mc26.setScreen(null));
        long offElapsed = context.computeOnClient(mc -> closeStartElapsed());
        boolean offClosing = context.computeOnClient(mc -> SpatialGUIClient.renderer().isClosing());
        context.waitTick();
        context.takeScreenshot("spatial_mod_off_close_t1");
        boolean untouched = context.computeOnClient(mc -> ShatterProfiler.closeCount()) == closes;
        log("mod_off", "Spatial closing=" + offClosing + " closeStartElapsed=" + offElapsed + " ourCloses=" + !untouched);
        check.accept(offClosing && offElapsed > 0 && untouched,
                "with the mod switched off Spatial GUI must play its own close animation and nothing of ours run");
        context.runOnClient(mc -> ShatterKeys.setEnabled(true));
        context.waitTicks(100);
    }

    private void alignmentShots(String mode) {
        ShatterConfig config = ShatterConfig.get();
        float burst = config.get(Params.GLASS_BURST);
        float gravity = config.get(Params.GLASS_GRAVITY);
        float spin = config.get(Params.GLASS_SPIN);
        config.set(Params.GLASS_BURST, 0);
        config.set(Params.GLASS_GRAVITY, 0);
        config.set(Params.GLASS_SPIN, 0);
        try {
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(OPEN_WAIT);
            context.takeScreenshot("spatial_" + mode + "_align_before");
            context.runOnClient(mc -> Mc26.setScreen(null));
            context.waitTick();
            context.takeScreenshot("spatial_" + mode + "_align_after");
            context.waitTicks(100);
        } finally {
            config.set(Params.GLASS_BURST, burst);
            config.set(Params.GLASS_GRAVITY, gravity);
            config.set(Params.GLASS_SPIN, spin);
        }
    }

    private void closeWithSpatial(String name, Supplier<Screen> screen, int openTicks, boolean expectSpatial) {
        context.setScreen(screen);
        context.waitTicks(openTicks);
        boolean hooked = context.computeOnClient(mc -> SpatialGUIClient.renderer().getHookedScreen() == Mc26.screen());
        if (expectSpatial) {
            context.takeScreenshot(name + "_before");
        }
        int closes = context.computeOnClient(mc -> ShatterProfiler.closeCount());
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.waitTick();
        long elapsedLeft = context.computeOnClient(mc -> closeStartElapsed());
        int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
        boolean shattered = context.computeOnClient(mc -> ShatterProfiler.closeCount()) > closes;
        context.waitTick();
        boolean closingAfter = context.computeOnClient(mc -> SpatialGUIClient.renderer().isClosing());
        if (expectSpatial) {
            context.takeScreenshot(name + "_t1");
            context.waitTicks(10);
            context.takeScreenshot(name + "_t11");
        }
        log(name, "hooked=" + hooked + " shattered=" + shattered + " shards=" + shards + " closeStartElapsed=" + elapsedLeft
                + " stillClosingAfter1Tick=" + closingAfter);
        check.accept(shattered && shards > 0, name + ": must shatter (shattered=" + shattered + ", shards=" + shards + ")");
        if (expectSpatial) {
            check.accept(hooked, name + ": Spatial GUI should have been showing the screen");
            check.accept(elapsedLeft == 0, name + ": Spatial GUI plane must be hidden at once (closeStartElapsed=" + elapsedLeft + ")");
        } else {
            check.accept(!hooked, name + ": Spatial GUI should not have been showing the screen");
        }
        context.waitTicks(100);
    }

    private static long closeStartElapsed() {
        try {
            InventoryRenderer inventory = SpatialGUIClient.renderer().getInventoryRenderer();
            Field field = InventoryRenderer.class.getDeclaredField("closeStartElapsed");
            field.setAccessible(true);
            return inventory.isClosing() ? field.getLong(inventory) : 0L;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    void setSpatial(String field, Object value) {
        context.runOnClient(mc -> {
            try {
                Class<?> type = spatialConfig.getClass();
                if (field.startsWith("firstPersonMode") && java.util.Arrays.stream(type.getFields()).noneMatch(f -> f.getName().equals(field))) {
                    type.getField("autoDetectCameraMode").set(spatialConfig, false);
                    java.lang.reflect.Field mode = type.getField("viewMode");
                    Object[] modes = mode.getType().getEnumConstants();
                    mode.set(spatialConfig, java.util.Arrays.stream(modes)
                            .filter(m -> m.toString().equals((Boolean) value ? "FIRST_PERSON" : "THIRD_PERSON")).findFirst().orElseThrow());
                    return;
                }
                type.getField(field).set(spatialConfig, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Spatial GUI config field " + field, e);
            }
        });
    }

    private static Object findSpatialConfig() {
        try {
            return Class.forName("org.tastytrash.spatialGUI.SpatialGUI").getField("config").get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
        }
        try {
            return Class.forName("org.tastytrash.spatialGUI.config.ConfigManager").getMethod("config").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Spatial GUI config not found", e);
        }
    }

    private static void log(String name, String message) {
        ShatterGuiClient.LOGGER.info("[ShatterTest] {}: {}", name, message);
    }
}
