package net.shattergui.debug;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Diagnostics {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern WINDOWS_PATH =
            Pattern.compile("(?i)(file:/*)?(?:(?<![A-Z0-9])[A-Z]:|\\\\\\\\[^\\\\\\s]+)[\\\\/][^\\s\"'<>|]*");
    private static final Pattern UNIX_PATH = Pattern.compile("(?<![\\w.:/])(file:/*)?/(?:[^\\s/\"'<>]+/)+[^\\s\"'<>]*");

    private Diagnostics() {
    }

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

    public static String sanitize(String value) {
        return sanitize(value, System.getProperty("user.home"), System.getProperty("user.name"));
    }

    public static String sanitize(String value, String home, String user) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String out = EMAIL.matcher(value).replaceAll("<email>");
        out = WINDOWS_PATH.matcher(out).replaceAll("<path>");
        out = UNIX_PATH.matcher(out).replaceAll("<path>");
        if (home != null && home.length() > 3) {
            out = out.replace(home, "<path>").replace(home.replace('\\', '/'), "<path>");
        }
        if (user != null && user.length() >= 3) {
            out = Pattern.compile(Pattern.quote(user), Pattern.CASE_INSENSITIVE).matcher(out).replaceAll("<user>");
        }
        return out.strip();
    }

    public static String version(String version) {
        return version == null ? "not installed" : version;
    }
}
