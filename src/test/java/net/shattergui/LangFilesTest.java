package net.shattergui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.shattergui.editor.Templates;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The translations: every language has every key of en_us (the source of truth; Minecraft falls back to en_us for a
 * missing one, but none should be missing), no keys en_us does not have, the same placeholders, and no key that the
 * code never uses. Names in the shipped effect files have a translation.
 */
class LangFilesTest {
    /** Every language the mod ships. en_us first. */
    static final List<String> LANGUAGES = List.of("en_us", "uk_ua", "de_de", "es_es", "fr_fr", "pt_br", "pl_pl", "zh_cn",
            "ru_ru", "ja_jp", "ko_kr", "it_it");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sd]");
    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path RESOURCES = Path.of("src/main/resources/assets/shatter_gui");

    static JsonObject lang(String code) throws IOException {
        try (InputStream in = LangFilesTest.class.getResourceAsStream("/assets/shatter_gui/lang/" + code + ".json")) {
            assertTrue(in != null, code + ".json is in the mod");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<String> placeholders(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        out.sort(String::compareTo);
        return out;
    }

    @Test
    void everyLanguageHasEveryKeyAndNoMore() throws IOException {
        JsonObject en = lang("en_us");
        for (String code : LANGUAGES) {
            JsonObject other = lang(code);
            Set<String> missing = new TreeSet<>(en.keySet());
            missing.removeAll(other.keySet());
            Set<String> extra = new TreeSet<>(other.keySet());
            extra.removeAll(en.keySet());
            assertTrue(missing.isEmpty(), code + " misses " + missing.size() + ": " + missing);
            assertTrue(extra.isEmpty(), code + " has keys en_us does not: " + extra);
            List<String> wrong = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : en.entrySet()) {
                String value = other.get(entry.getKey()).getAsString();
                if (!placeholders(entry.getValue().getAsString()).equals(placeholders(value))) {
                    wrong.add(entry.getKey());
                }
                if (value.isBlank()) {
                    wrong.add(entry.getKey() + " (empty)");
                }
            }
            assertTrue(wrong.isEmpty(), code + ": placeholders differ from en_us in " + wrong);
        }
    }

    /** All string literals of the mod's code. */
    private static Set<String> literals() throws IOException {
        Set<String> out = new HashSet<>();
        Pattern literal = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = literal.matcher(Files.readString(file));
                while (m.find()) {
                    out.add(m.group(1));
                }
            }
        }
        return out;
    }

    /**
     * A key is used if the code names it, or a prefix of it with at least three parts ("ui.shatter_gui.open." + id),
     * or names it without "ui.shatter_gui.fx." (the editor's tr), or without a .desc/.tooltip/.short ending; built-in
     * effects and settings are named from data (registry ids).
     */
    @Test
    void noUnusedKeys() throws IOException {
        Set<String> literals = literals();
        List<String> prefixes = new ArrayList<>();
        for (String l : literals) {
            if (l.endsWith(".") && l.chars().filter(c -> c == '.').count() >= 2 && l.matches("[a-z0-9_.\\-]+")) {
                prefixes.add(l);
                prefixes.add("ui.shatter_gui.fx." + l);
            }
        }
        List<String> unused = new ArrayList<>();
        for (String key : lang("en_us").keySet()) {
            // Key mapping categories are named by the game from the category's id (shatter_gui:main).
            if (key.startsWith("key.category.")) {
                continue;
            }
            if (!used(key, literals, prefixes)) {
                unused.add(key);
            }
        }
        assertTrue(unused.isEmpty(), "keys no code uses (" + unused.size() + "): " + unused);
    }

    private static boolean used(String key, Set<String> literals, List<String> prefixes) {
        String base = key.replaceAll("\\.(desc|tooltip|short)$", "");
        for (String k : List.of(key, base)) {
            if (literals.contains(k) || k.startsWith("ui.shatter_gui.fx.") && literals.contains(k.substring("ui.shatter_gui.fx.".length()))) {
                return true;
            }
            for (String p : prefixes) {
                if (k.startsWith(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void shippedNamesAreTranslated() throws IOException {
        JsonObject en = lang("en_us");
        List<String> missing = new ArrayList<>();
        for (String dir : List.of("editor/templates", "effects")) {
            try (Stream<Path> files = Files.list(RESOURCES.resolve(dir))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    collect(root, en, missing, file.getFileName().toString());
                    if (root.has("reduced")) {
                        collect(root.getAsJsonObject("reduced"), en, missing, file.getFileName() + " (reduced)");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "names without ui.shatter_gui.fx.label.*: " + missing);
    }

    private static void collect(JsonObject root, JsonObject en, List<String> missing, String file) {
        for (String list : List.of("layers", "emitters", "sounds")) {
            if (!root.has(list)) {
                continue;
            }
            for (JsonElement item : root.getAsJsonArray(list)) {
                if (item.isJsonObject() && item.getAsJsonObject().has("name")) {
                    String key = "ui.shatter_gui.fx.label." + Templates.labelSlug(item.getAsJsonObject().get("name").getAsString());
                    if (!en.has(key)) {
                        missing.add(file + ": " + key);
                    }
                }
            }
        }
    }
}
