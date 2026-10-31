package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.ShatterConfig;
import net.shattergui.shatter.ShardManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Screenshots with Iris ({@code ./gradlew runClientGameTest -Pshaders [-PshaderPack=C:/path/Pack.zip]}). Without a pack
 * the shaders stay off, which gives the "Iris installed, no shaders" reference. The visual result is checked by looking
 * at the screenshots in the run's screenshots folder; here it only asserts that shards exist.
 *
 * <p>Screenshots use vanilla {@link Screenshot#grab} on the last drawn frame: the game test's own screenshot renders an
 * extra frame outside the normal loop, which Sodium rejects ("Global terrain uniforms have not been updated").
 */
final class ShaderScenarios {
    private static final String PACK_PROPERTY = "shatter.test.shaderPack";

    private final ClientGameTestContext context;
    private final BiConsumer<Boolean, String> check;

    ShaderScenarios(ClientGameTestContext context, BiConsumer<Boolean, String> check) {
        this.context = context;
        this.check = check;
    }

    /** Before the world loads: installs the pack given by -PshaderPack (the run directory is wiped every run). */
    static void selectPack(ClientGameTestContext context) {
        String source = System.getProperty(PACK_PROPERTY);
        context.runOnClient(mc -> {
            try {
                if (source != null) {
                    Path zip = Path.of(source);
                    Files.createDirectories(Iris.getShaderpacksDirectory());
                    Files.copy(zip, Iris.getShaderpacksDirectory().resolve(zip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    Iris.getIrisConfig().setShaderPackName(zip.getFileName().toString());
                }
                Iris.getIrisConfig().setShadersEnabled(source != null);
                Iris.getIrisConfig().save();
                Iris.reload();
            } catch (Exception e) {
                throw new IllegalStateException("Could not set up the shader pack " + source, e);
            }
        });
    }

    /** Short pack name for screenshot names, or "off". */
    static String packTag() {
        String source = System.getProperty(PACK_PROPERTY);
        if (source == null) {
            return "off";
        }
        String name = Path.of(source).getFileName().toString();
        int cut = name.indexOf('_');
        return (cut > 0 ? name.substring(0, cut) : name).replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    void run(String tag, Supplier<Screen> furnace, TestSingleplayerContext singleplayer) {
        // Shader packs compile and warm up after the world loads.
        context.waitTicks(100);
        boolean packInUse = context.computeOnClient(mc -> IrisApi.getInstance().isShaderPackInUse());
        String packName = context.computeOnClient(mc -> String.valueOf(Iris.getCurrentPackName()));
        ShatterGuiClient.LOGGER.info("[ShatterTest] shaders: tag={} pack={} inUse={}", tag, packName, packInUse);
        check.accept(packInUse == !"off".equals(tag), "shader pack in use must match the run (" + tag + ", inUse=" + packInUse + ")");
        shot(tag + "_world");

        ShatterConfig config = ShatterConfig.get();
        config.effect = "glass";
        shots(tag + "_glass", () -> new InventoryScreen(Minecraft.getInstance().player), new int[]{1, 2, 3, 10, 40, 52});
        BurnScenario.run(context, check, this::shot, tag + "_burn", furnace);
        FlyScenario.run(context, singleplayer, check, this::shot, tag + "_fly");
        EffectScenarios effects = new EffectScenarios(context, check, this::shot);
        effects.edgeFlights(tag + "_edge");
        effects.dissolve(tag + "_dissolve", furnace);
        VortexScenario.run(context, singleplayer, check, this::shot, tag + "_vortex", furnace);
        EditorScenario.run(context, check, this::shot, tag + "_editor");
        DataEffectsScenario.run(context, check, this::shot, tag + "_fx", furnace);
        FxEditorScenario.run(context, check, this::shot, tag + "_fx_editor", furnace);

        if (FabricLoader.getInstance().isModLoaded("spatial-gui")) {
            shots(tag + "_spatial_third", () -> new InventoryScreen(Minecraft.getInstance().player), new int[]{1, 11});
        }
    }

    /** Screenshot of the open screen, then at each given tick after closing. */
    private void shots(String name, Supplier<Screen> screen, int[] ticks) {
        context.setScreen(screen);
        context.waitTicks(30);
        shot(name + "_before");
        context.runOnClient(mc -> mc.setScreen(null));
        int waited = 0;
        int shards = -1;
        for (int tick : ticks) {
            context.waitTicks(tick - waited);
            waited = tick;
            if (shards < 0) {
                shards = context.computeOnClient(mc -> ShardManager.liveShardCount());
            }
            shot(name + "_t" + tick);
        }
        check.accept(shards > 0, name + ": shards must exist with shaders (shards=" + shards + ")");
        context.waitTicks(100);
    }

    private void shot(String name) {
        context.runOnClient(mc -> Screenshot.grab(mc.gameDirectory, "shader_" + name + ".png", mc.getMainRenderTarget(), 1,
                message -> { }));
        context.waitTicks(2);
    }
}
