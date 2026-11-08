package net.shattergui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class BuildInfo {
    public static final String RESOURCE = "/shatter_gui_build.properties";
    private static final String UNKNOWN = "unknown";
    private static final Properties PROPERTIES = load();

    private BuildInfo() {
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | IllegalArgumentException e) {
            properties.clear();
        }
        return properties;
    }

    public static String commit() {
        return PROPERTIES.getProperty("commit", UNKNOWN);
    }

    public static String time() {
        return PROPERTIES.getProperty("time", UNKNOWN);
    }

    public static String minecraft() {
        return PROPERTIES.getProperty("minecraft", UNKNOWN);
    }

    public static String describe() {
        return commit() + ", " + time();
    }
}
