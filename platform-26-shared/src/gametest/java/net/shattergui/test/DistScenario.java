package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.platform.impl.Mc26;
import net.shattergui.shatter.ShardManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class DistScenario {
    private static final int OPEN_WAIT = 30;

    private DistScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Supplier<Screen> furnace) {
        ModContainer mod = FabricLoader.getInstance().getModContainer("shatter_gui").orElseThrow();
        List<Path> origin = mod.getOrigin().getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH
                ? mod.getOrigin().getPaths() : List.of();
        String expected = System.getProperty("shatter.test.distJar", "");
        String loadedFrom = origin.size() == 1 ? origin.getFirst().getFileName().toString() : origin.toString();
        log("Shatter GUI loaded from " + loadedFrom + " (" + mod.getOrigin().getKind() + ")");
        if (!expected.isEmpty()) {
            check.accept(loadedFrom.equals(expected), "dist: Shatter GUI must come from " + expected + ", not " + loadedFrom);
        }
        Optional<ModContainer> spatial = FabricLoader.getInstance().getModContainer("spatial_gui")
                .or(() -> FabricLoader.getInstance().getModContainer("spatial-gui"));
        String spatialVersion = spatial.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("not installed");
        log("Spatial GUI " + spatialVersion + " from " + spatial.map(c -> c.getOrigin().toString()).orElse("-"));
        check.accept(spatial.isPresent(), "dist: Spatial GUI must be loaded");

        ShatterConfig config = ShatterConfig.get();
        config.profiling = true;
        config.effect = "glass";
        config.applyTo = ShatterConfig.APPLY_ALL;
        config.spatialGuiIntegration = true;
        String before = context.computeOnClient(mc -> status());
        log("status before closing: " + before);
        check.accept(before.contains("bridge enabled"), "dist: the Spatial GUI bridge must be enabled, got: " + before);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            McTest.waitForChunksRender(singleplayer);
            context.waitTicks(20);
            SpatialScenarios spatialConfig = new SpatialScenarios(context, check);
            spatialConfig.setSpatial("firstPersonModeInventory", false);
            spatialConfig.setSpatial("firstPersonModeContainers", false);
            close(context, check, "dist_third_furnace", furnace);
            close(context, check, "dist_third_inventory", () -> new InventoryScreen(Minecraft.getInstance().player));
            spatialConfig.setSpatial("firstPersonModeInventory", true);
            spatialConfig.setSpatial("firstPersonModeContainers", true);
            close(context, check, "dist_first_furnace", furnace);
            spatialConfig.setSpatial("firstPersonModeInventory", false);
            spatialConfig.setSpatial("firstPersonModeContainers", false);
        }
        String after = context.computeOnClient(mc -> status());
        log("status after closing: " + after);
        check.accept(after.contains("bridge enabled"), "dist: the bridge must still be enabled after the closes, got: " + after);
        log("summary: jar=" + loadedFrom + " spatial=" + spatialVersion + " status=" + after);
    }

    private static void close(ClientGameTestContext context, BiConsumer<Boolean, String> check, String name,
                              Supplier<Screen> screen) {
        context.setScreen(screen);
        context.waitTicks(OPEN_WAIT);
        boolean hooked = context.computeOnClient(mc -> hookedScreen() == Mc26.screen());
        context.takeScreenshot(name + "_before");
        int closes = context.computeOnClient(mc -> ShatterProfiler.closeCount());
        context.runOnClient(mc -> Mc26.setScreen(null));
        context.waitTick();
        long elapsed = context.computeOnClient(mc -> closeStartElapsed());
        int shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
        boolean ours = context.computeOnClient(mc -> ShatterProfiler.closeCount()) > closes;
        context.waitTick();
        long elapsedLater = context.computeOnClient(mc -> closeStartElapsed());
        context.takeScreenshot(name + "_t2");
        context.waitTicks(8);
        context.takeScreenshot(name + "_t10");
        log(name + ": hooked=" + hooked + " ourClose=" + ours + " shards=" + shards + " spatialCloseStartElapsed=" + elapsed
                + "/" + elapsedLater);
        check.accept(hooked, name + ": Spatial GUI must show the screen on its plane");
        check.accept(ours && shards > 0, name + ": closing must play our effect (ourClose=" + ours + ", shards=" + shards + ")");
        check.accept(elapsed <= 0 && elapsedLater <= 0, name + ": Spatial GUI must not play its own closing plane "
                + "(closeStartElapsed=" + elapsed + "/" + elapsedLater + ")");
        context.waitTicks(100);
    }

    private static String status() {
        try {
            Class<?> compat = Class.forName("net.shattergui.compat.spatial.SpatialGuiCompat");
            try {
                Object status = compat.getMethod("status").invoke(null);
                return (String) status.getClass().getMethod("line").invoke(status);
            } catch (NoSuchMethodException e) {
                Field failed = compat.getDeclaredField("failed");
                failed.setAccessible(true);
                Class<?> detector = Class.forName("net.shattergui.compat.spatial.SpatialGuiDetector");
                boolean supported = (Boolean) detector.getMethod("isSupportedVersionInstalled").invoke(null);
                boolean ok = supported && !failed.getBoolean(null);
                return "Spatial GUI: (build without the status line), bridge " + (ok ? "enabled" : "disabled")
                        + ", supported=" + supported + " failed=" + failed.getBoolean(null);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "Spatial GUI: status unreadable (" + e + ")";
        }
    }

    private static Object spatialRenderer() throws ReflectiveOperationException {
        return Class.forName("org.tastytrash.spatialGUI.client.SpatialGUIClient").getMethod("renderer").invoke(null);
    }

    private static Object plane(Object renderer) throws ReflectiveOperationException {
        for (String getter : List.of("getWorldScreenRenderer", "getInventoryRenderer")) {
            try {
                return renderer.getClass().getMethod(getter).invoke(renderer);
            } catch (NoSuchMethodException e) {
            }
        }
        throw new NoSuchMethodException("no plane getter on " + renderer.getClass().getName());
    }

    private static Screen hookedScreen() {
        try {
            Object renderer = spatialRenderer();
            return renderer == null ? null : (Screen) renderer.getClass().getMethod("getHookedScreen").invoke(renderer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long closeStartElapsed() {
        try {
            Object renderer = spatialRenderer();
            if (renderer == null) {
                return 0L;
            }
            Object plane = plane(renderer);
            Method closing = plane.getClass().getMethod("isClosing");
            if (!(Boolean) closing.invoke(plane)) {
                return 0L;
            }
            Field field = plane.getClass().getDeclaredField("closeStartElapsed");
            field.setAccessible(true);
            return field.getLong(plane);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void log(String message) {
        ShatterGuiClient.LOGGER.info("[ShatterTest] dist: {}", message);
    }
}
