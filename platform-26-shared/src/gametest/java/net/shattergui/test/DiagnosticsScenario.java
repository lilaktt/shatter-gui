package net.shattergui.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.shattergui.ShatterGuiClient;
import net.shattergui.config.screen.ShatterConfigScreen;

import java.util.List;
import java.util.function.BiConsumer;
import net.shattergui.platform.impl.Mc26;

/**
 * "Copy diagnostics" (settings → About, {@code -PtestOnly=diagnostics}): the clipboard gets every field a bug report
 * needs, the mod's last warning (a probe logged here) and nothing of the player's home folder or user name. The mod's
 * log keeps reaching the game's log (the warnings are collected beside it).
 */
final class DiagnosticsScenario {
    private static final List<String> FIELDS = List.of("Shatter GUI:", "Build:", "Minecraft:", "Fabric Loader:", "Fabric API:",
            "Iris:", "Sodium:", "Spatial GUI:", "GPU:", "Driver:", "GPU vendor:", "Shader pack:", "Open effect:",
            "Close effect:", "Thematic mode:", "Recent warnings:");

    private DiagnosticsScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check) {
        String home = System.getProperty("user.home");
        ShatterGuiClient.LOGGER.warn("[ShatterTest] diagnostics probe, file at {}\\probe.json", home);
        String old = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
        context.setScreen(() -> new ShatterConfigScreen(null));
        context.waitTicks(3);
        String text = context.computeOnClient(mc -> ((ShatterConfigScreen) Mc26.screen()).copyDiagnostics());
        String clipboard = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
        context.setScreen(() -> null);
        context.runOnClient(mc -> mc.keyboardHandler.setClipboard(old == null ? "" : old));
        ShatterGuiClient.LOGGER.info("[ShatterTest] diagnostics:\n{}", text);
        // Windows' clipboard ends lines with CRLF as SDL (26.3) hands it back.
        check.accept(text.equals(clipboard.replace("\r\n", "\n")), "diagnostics: the text must be on the clipboard ("
                + text.length() + " characters copied, " + clipboard.length() + " there)");
        for (String field : FIELDS) {
            check.accept(text.contains(field), "diagnostics: missing " + field);
        }
        check.accept(text.contains("Build: " + net.shattergui.BuildInfo.commit()) && !net.shattergui.BuildInfo.commit().equals("unknown"),
                "diagnostics: the build's commit must be listed, got " + net.shattergui.BuildInfo.describe());
        check.accept(text.contains("diagnostics probe"), "diagnostics: the mod's last warning must be listed");
        check.accept(!text.contains(home) && !text.contains(home.replace('\\', '/')),
                "diagnostics: the home folder must not be in the text");
        String user = System.getProperty("user.name");
        check.accept(user == null || user.length() < 3 || !text.toLowerCase().contains(user.toLowerCase()),
                "diagnostics: the user name must not be in the text");
    }
}
