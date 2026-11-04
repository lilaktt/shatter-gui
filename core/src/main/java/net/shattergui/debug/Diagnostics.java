package net.shattergui.debug;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The text of "Copy diagnostics" (settings → About): what a bug report needs, in English whatever the game's language
 * (it is read by whoever answers the report). Versions of the mod, the game, the loader, Fabric API, Iris, Sodium and
 * Spatial GUI, the graphics card and its driver (the OpenGL renderer and version strings), the shader pack, the chosen
 * effects, the thematic mode and the mod's last warnings. Nothing that identifies the player: user names, file paths
 * and e-mail addresses are taken out of every value ({@link #sanitize}).
 */
public final class Diagnostics {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /** {@code C:\...}, {@code \\server\...} and {@code file:} URIs of them. */
    private static final Pattern WINDOWS_PATH =
            Pattern.compile("(?i)(file:/*)?(?:(?<![A-Z0-9])[A-Z]:|\\\\\\\\[^\\\\\\s]+)[\\\\/][^\\s\"'<>|]*");
    /** Absolute Unix paths ({@code /home/...}, {@code /Users/...}, any with at least two parts), not inside a word. */
    private static final Pattern UNIX_PATH = Pattern.compile("(?<![\\w.:/])(file:/*)?/(?:[^\\s/\"'<>]+/)+[^\\s\"'<>]*");

    private Diagnostics() {
    }

    /**
     * @param environment what the platform knows, in order (label → value): versions, graphics, shader pack
     * @param openEffect  the chosen open effect; {@code closeEffect}: the chosen close effect
     * @param thematic    whether the thematic mode is on
     * @param warnings    the mod's last warnings, oldest first
     */
    public static String build(Map<String, String> environment, String openEffect, String closeEffect, boolean thematic,
                               List<String> warnings) {
        Map<String, String> lines = new LinkedHashMap<>(environment);
        lines.put("Open effect", openEffect);
        lines.put("Close effect", closeEffect);
        lines.put("Thematic mode", thematic ? "on" : "off");
        StringBuilder out = new StringBuilder("Shatter GUI diagnostics\n");
        for (Map.Entry<String, String> line : lines.entrySet()) {
            out.append(line.getKey()).append(": ").append(sanitize(line.getValue())).append('\n');
        }
        out.append("Recent warnings:");
        if (warnings.isEmpty()) {
            out.append(" none\n");
        } else {
            out.append('\n');
            for (String warning : warnings) {
                out.append("  - ").append(sanitize(warning)).append('\n');
            }
        }
        return out.toString();
    }

    /** {@link #sanitize(String, String, String)} with this computer's home folder and user name. */
    public static String sanitize(String value) {
        return sanitize(value, System.getProperty("user.home"), System.getProperty("user.name"));
    }

    /**
     * {@code value} without e-mail addresses, file paths, the home folder and the user name ("&lt;email&gt;",
     * "&lt;path&gt;", "&lt;user&gt;"). Null becomes "unknown".
     */
    public static String sanitize(String value, String home, String user) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String out = EMAIL.matcher(value).replaceAll("<email>");
        out = WINDOWS_PATH.matcher(out).replaceAll("<path>");
        out = UNIX_PATH.matcher(out).replaceAll("<path>");
        // Whatever is left of the home folder (inside a quoted name, after a colon …).
        if (home != null && home.length() > 3) {
            out = out.replace(home, "<path>").replace(home.replace('\\', '/'), "<path>");
        }
        if (user != null && user.length() >= 3) {
            out = Pattern.compile(Pattern.quote(user), Pattern.CASE_INSENSITIVE).matcher(out).replaceAll("<user>");
        }
        return out.strip();
    }

    /** "1.2.3", or "not installed" for null. */
    public static String version(String version) {
        return version == null ? "not installed" : version;
    }
}
