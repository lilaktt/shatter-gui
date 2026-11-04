package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.screen.ShatterConfigScreen;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * "Copy diagnostics" (settings → About, {@code -PtestOnly=diagnostics}): the clipboard gets every field a bug report
 * needs, the mod's last warning (a probe logged here) and nothing of the player's home folder or user name. The mod's
 * log keeps reaching the game's log (the warnings are collected beside it).
 */
final class DiagnosticsScenario {
    private static final List<String> FIELDS = List.of("Shatter GUI:", "Minecraft:", "Fabric Loader:", "Fabric API:",
            "Iris:", "Sodium:", "Spatial GUI:", "GPU:", "Driver (OpenGL):", "GPU vendor:", "Shader pack:", "Open effect:",
            "Close effect:", "Thematic mode:", "Recent warnings:");

    private DiagnosticsScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check) {
        String home = System.getProperty("user.home");
        ShatterGuiClient.LOGGER.warn("[ShatterTest] diagnostics probe, file at {}\\probe.json", home);
        String old = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
        context.setScreen(() -> new ShatterConfigScreen(null));
        context.waitTicks(3);
        String text = context.computeOnClient(mc -> ((ShatterConfigScreen) mc.screen).copyDiagnostics());
        String clipboard = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
        context.setScreen(() -> null);
        context.runOnClient(mc -> mc.keyboardHandler.setClipboard(old == null ? "" : old));
        ShatterGuiClient.LOGGER.info("[ShatterTest] diagnostics:\n{}", text);
        check.accept(text.equals(clipboard), "diagnostics: the text must be on the clipboard");
        for (String field : FIELDS) {
            check.accept(text.contains(field), "diagnostics: missing " + field);
        }
        check.accept(text.contains("diagnostics probe"), "diagnostics: the mod's last warning must be listed");
        check.accept(!text.contains(home) && !text.contains(home.replace('\\', '/')),
                "diagnostics: the home folder must not be in the text");
        String user = System.getProperty("user.name");
        check.accept(user == null || user.length() < 3 || !text.toLowerCase().contains(user.toLowerCase()),
                "diagnostics: the user name must not be in the text");
    }
}
