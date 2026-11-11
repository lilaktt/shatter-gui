package net.shattergui.links;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectLinksTest {
    private static final List<Path> TEMPLATES = List.of(
            Path.of("platform-26-shared/src/main/resources/fabric.mod.json"),
            Path.of("platform-1.21.1/src/main/resources/fabric.mod.json"));
    private static final List<Path> BUILD_SCRIPTS = List.of(
            Path.of("gradle/platform-26x.gradle"), Path.of("platform-1.21.1/build.gradle"));
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");

    @Test
    void addressesFollowTheirPlatformPattern() {
        assertTrue(ProjectLinks.MODRINTH.matches("https://modrinth\\.com/mod/[a-z0-9-]+"), ProjectLinks.MODRINTH);
        assertTrue(ProjectLinks.CURSEFORGE.matches("https://www\\.curseforge\\.com/minecraft/mc-mods/[a-z0-9]+(-[a-z0-9]+)+"),
                ProjectLinks.CURSEFORGE);
        assertEquals("https://www.curseforge.com/minecraft/mc-mods/shatter-gui", ProjectLinks.CURSEFORGE);
        assertEquals("https://modrinth.com/mod/shattergui", ProjectLinks.MODRINTH);
        assertTrue(ProjectLinks.SOURCES.matches("https://github\\.com/[\\w.-]+/[\\w.-]+"), ProjectLinks.SOURCES);
        assertEquals(ProjectLinks.SOURCES + "/issues", ProjectLinks.ISSUES);
        assertTrue(ProjectLinks.MODRINTH_API.endsWith("/" + ProjectLinks.MODRINTH.substring(ProjectLinks.MODRINTH.lastIndexOf('/') + 1)));
        assertTrue(ProjectLinks.GITHUB_API.endsWith(ProjectLinks.SOURCES.substring("https://github.com".length())));
    }

    @Test
    void fabricModJsonGetsTheSameAddresses() throws IOException, IllegalAccessException {
        Map<String, String> gradle = asGradleReadsThem();
        for (Path template : TEMPLATES) {
            JsonObject json = JsonParser.parseString(expand(Files.readString(template, StandardCharsets.UTF_8), gradle))
                    .getAsJsonObject();
            JsonObject contact = json.getAsJsonObject("contact");
            for (Map.Entry<String, String> link : ProjectLinks.contact().entrySet()) {
                assertEquals(link.getValue(), contact.get(link.getKey()).getAsString(), template + " contact." + link.getKey());
            }
            assertEquals(ProjectLinks.MODRINTH, contact.get("homepage").getAsString(), template + " contact.homepage");
            JsonObject modMenu = json.getAsJsonObject("custom").getAsJsonObject("modmenu").getAsJsonObject("links");
            assertEquals(ProjectLinks.MODRINTH, modMenu.get("modmenu.modrinth").getAsString(), template + " modmenu.modrinth");
            assertEquals(ProjectLinks.CURSEFORGE, modMenu.get("modmenu.curseforge").getAsString(), template + " modmenu.curseforge");
        }
        Map<String, String> parsed = new java.util.LinkedHashMap<>();
        Matcher source = Pattern.compile("public static final String (\\w+) = \"([^\"]+)\";").matcher(Files.readString(
                Path.of("core/src/main/java/net/shattergui/links/ProjectLinks.java"), StandardCharsets.UTF_8));
        while (source.find()) {
            parsed.put(source.group(1).toLowerCase(Locale.ROOT), source.group(2));
        }
        assertEquals(gradle, parsed, "gradle/project-links.gradle reads ProjectLinks.java with this pattern");
        for (Path script : BUILD_SCRIPTS) {
            String text = Files.readString(script, StandardCharsets.UTF_8);
            assertTrue(text.contains("rootProject.projectLinks()"), script + " must fill fabric.mod.json from ProjectLinks");
        }
    }

    @Test
    void noOldAddressLeft() throws IOException {
        String old = "mc-mods/" + "shattergui";
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("."))) {
            files.filter(Files::isRegularFile).filter(ProjectLinksTest::isProjectText).forEach(file -> {
                try {
                    if (Files.readString(file, StandardCharsets.UTF_8).contains(old)) {
                        offenders.add(file.toString());
                    }
                } catch (IOException | java.io.UncheckedIOException e) {
                    offenders.add(file + " (unreadable: " + e.getMessage() + ")");
                }
            });
        }
        assertTrue(offenders.isEmpty(), "old CurseForge address in " + offenders);
    }

    private static boolean isProjectText(Path file) {
        String path = file.toString().replace('\\', '/');
        for (String skip : List.of("/build/", "/run/", "/.gradle/", "/.git/", "/libs/", "/dist/", "NOTES.md", "PROJECT_MAP.md")) {
            if (path.contains(skip)) {
                return false;
            }
        }
        String name = file.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".json") || name.endsWith(".md") || name.endsWith(".gradle")
                || name.endsWith(".properties") || name.endsWith(".txt");
    }

    private static Map<String, String> asGradleReadsThem() throws IllegalAccessException {
        Map<String, String> links = new java.util.LinkedHashMap<>();
        for (Field field : ProjectLinks.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isPublic(mods) && Modifier.isStatic(mods) && Modifier.isFinal(mods) && field.getType() == String.class) {
                links.put(field.getName().toLowerCase(Locale.ROOT), (String) field.get(null));
            }
        }
        return links;
    }

    private static String expand(String template, Map<String, String> links) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = links.getOrDefault(matcher.group(1), "unset-" + matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
