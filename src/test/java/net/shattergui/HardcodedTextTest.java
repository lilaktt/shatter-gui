package net.shattergui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No text for the player is written into the interface code: the UI library, the effect editor and the settings
 * screen take every word from the language files. Allowed: translation keys, ids, JSON field names, formats without
 * words, and lines that only log, describe for the profile log or throw.
 */
class HardcodedTextTest {
    private static final List<Path> UI_CODE = List.of(Path.of("src/main/java/net/shattergui/ui"),
            Path.of("src/main/java/net/shattergui/editor"), Path.of("src/main/java/net/shattergui/config/screen"));
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    /** Something a person would read: two letters in a row of any script, not just an id or a key. */
    private static final Pattern WORDS = Pattern.compile("\\p{L}{2,}");
    /** Ids, keys and field paths: no spaces, and either lower case first or dots/underscores inside. */
    private static final Pattern ID_LIKE = Pattern.compile("([a-z0-9._][A-Za-z0-9_.\\-:/#%$]*|[A-Za-z0-9]+[._:][A-Za-z0-9_.\\-:/]*)?");
    /** Class names (screen types matched by name), JSON snippets and paths. */
    private static final Pattern CODE_LIKE = Pattern.compile("[A-Z][A-Za-z]*Screen|.*[\\[\\]{}].*|\\s*[\\[\\](){}.,:]+\\s*");
    /** Lines that are not shown to the player. */
    private static final Pattern NOT_SHOWN = Pattern.compile(
            "LOGGER\\.|Exception\\(|ShatterProfiler|describe|assert|getResource|@Inject|@Mixin|addProperty|\\.has\\(|"
                    + "\\.get\\(\"|\\.str\\(|\\.num\\(|getAs|Identifier|FxDoc\\.|doc\\.|\\.path|case \"|equals\\(\"|"
                    + "\\.json|\\.png|\\.shatterfx|startsWith|endsWith|replace|matches|split\\(|String\\.format\\(Locale\\.ROOT|"
                    + "guard\\(|reportScreen|UiFailures\\.report|DynamicTexture|FxProblems|problems\\.(error|warn)|// not shown");

    @Test
    void interfaceTextComesFromTheLanguageFiles() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path dir : UI_CODE) {
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    List<String> lines = Files.readAllLines(file);
                    boolean comment = false;
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.startsWith("/*")) {
                            comment = true;
                        }
                        if (comment) {
                            comment = !line.contains("*/");
                            continue;
                        }
                        if (line.startsWith("//") || line.startsWith("*") || NOT_SHOWN.matcher(line).find()) {
                            continue;
                        }
                        Matcher m = LITERAL.matcher(line.replaceAll("//.*$", ""));
                        while (m.find()) {
                            String text = m.group(1);
                            if (WORDS.matcher(text).find() && !ID_LIKE.matcher(text).matches()
                                    && !CODE_LIKE.matcher(text).matches()) {
                                found.add(dir.relativize(file) + ":" + (i + 1) + " \"" + text + "\"");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "text in the interface code instead of a translation (" + found.size() + "):\n"
                + String.join("\n", found));
    }
}
