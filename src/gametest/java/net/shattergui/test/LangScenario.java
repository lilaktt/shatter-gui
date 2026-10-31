package net.shattergui.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.shattergui.ShatterGuiClient;
import net.shattergui.ui.Ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * One language (German, Russian: long; Japanese: CJK) at GUI scales 1–4: every settings tab and every part of the
 * editor, recording which texts had to be shortened with "…". Texts of the mod's translations must fit; the player's
 * own data (effect ids, file names, screen class names) may be shortened. Screenshots: {@code lang_de_s<scale>_…}.
 */
final class LangScenario {
    private LangScenario() {
    }

    static void run(ClientGameTestContext context, BiConsumer<Boolean, String> check, Consumer<String> shot, String code) {
        String before = context.computeOnClient(mc -> mc.getLanguageManager().getSelected());
        List<Pattern> translations = patterns(code);
        try {
            setLanguage(context, code);
            context.runOnClient(mc -> Ui.trackTruncation(true));
            EditorShotsScenario.run(context, check, shot, "lang_" + code.substring(0, 2) + "_");
            Set<String> cut = context.computeOnClient(mc -> Ui.truncated());
            List<String> ours = new ArrayList<>();
            for (String text : cut) {
                if (translations.stream().anyMatch(p -> p.matcher(text).matches())) {
                    ours.add(text);
                }
            }
            ShatterGuiClient.LOGGER.info("[ShatterTest] lang {}: {} texts shortened, {} of them translations: {}", code,
                    cut.size(), ours.size(), ours);
            check.accept(ours.isEmpty(), "lang " + code + ": translated texts cut off with …: " + ours);
        } finally {
            context.runOnClient(mc -> Ui.trackTruncation(false));
            setLanguage(context, before);
        }
    }

    private static void setLanguage(ClientGameTestContext context, String code) {
        context.runOnClient(mc -> {
            mc.options.languageCode = code;
            mc.getLanguageManager().setSelected(code);
            mc.reloadResourcePacks();
        });
        context.waitFor(mc -> mc.getOverlay() == null, 1200);
        context.waitTicks(5);
    }

    /** Each translation as a pattern: its text with every %s as "anything". */
    private static List<Pattern> patterns(String code) {
        List<Pattern> out = new ArrayList<>();
        try (InputStream in = LangScenario.class.getResourceAsStream("/assets/shatter_gui/lang/" + code + ".json")) {
            JsonObject lang = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
                String value = e.getValue().getAsString().replace("%%", "%");
                StringBuilder regex = new StringBuilder();
                for (String part : value.split("%(\\d+\\$)?[sd]", -1)) {
                    if (!regex.isEmpty()) {
                        regex.append(".*");
                    }
                    regex.append(Pattern.quote(part));
                }
                // A text with only a placeholder ("%s") is data, not a translation.
                if (!value.replaceAll("%(\\d+\\$)?[sd]", "").isBlank()) {
                    out.add(Pattern.compile(regex.toString(), Pattern.DOTALL));
                }
            }
        } catch (IOException | RuntimeException e) {
            ShatterGuiClient.LOGGER.warn("[ShatterTest] could not read {}: {}", code, e.toString());
        }
        return out;
    }
}
