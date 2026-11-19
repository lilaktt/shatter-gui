package net.shattergui.links;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProjectLinks {
    public static final String MODRINTH = "https://modrinth.com/mod/shattergui";
    public static final String CURSEFORGE = "https://www.curseforge.com/minecraft/mc-mods/shatter-gui";
    public static final String SOURCES = "https://github.com/lilaktt/shatter-gui";
    public static final String ISSUES = "https://github.com/lilaktt/shatter-gui/issues";
    public static final String MODRINTH_API = "https://api.modrinth.com/v2/project/shattergui";
    public static final String GITHUB_API = "https://api.github.com/repos/lilaktt/shatter-gui";

    private ProjectLinks() {
    }

    public static Map<String, String> contact() {
        Map<String, String> links = new LinkedHashMap<>();
        links.put("modrinth", MODRINTH);
        links.put("curseforge", CURSEFORGE);
        links.put("sources", SOURCES);
        links.put("issues", ISSUES);
        return links;
    }
}
