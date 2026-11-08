package net.shattergui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.shattergui.ShatterMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Themes {
    public static final String GENERAL = "";

    public record Rule(String id, List<String> match, String effect) {
    }

    private static List<Rule> rules;

    private Themes() {
    }

    public static List<Rule> rules() {
        if (rules == null) {
            rules = load();
        }
        return rules;
    }

    private static List<Rule> load() {
        try (InputStream in = Themes.class.getResourceAsStream("/assets/shatter_gui/themes.json")) {
            if (in == null) {
                ShatterMod.LOGGER.warn("Thematic rules are missing from the mod, thematic mode does nothing");
                return List.of();
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            ShatterMod.LOGGER.warn("Could not read the thematic rules: {}", e.toString());
            return List.of();
        }
    }

    static List<Rule> parse(String json) {
        List<Rule> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray list = root.getAsJsonArray("rules");
        for (JsonElement element : list) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject o = element.getAsJsonObject();
            if (!o.has("id") || !o.has("match") || !o.has("effect") || !o.get("match").isJsonArray()) {
                continue;
            }
            List<String> match = new ArrayList<>();
            for (JsonElement m : o.getAsJsonArray("match")) {
                match.add(m.getAsString());
            }
            out.add(new Rule(o.get("id").getAsString(), List.copyOf(match), o.get("effect").getAsString()));
        }
        return List.copyOf(out);
    }

    public static String effectOf(Rule rule, Map<String, String> choices) {
        return choices.getOrDefault(rule.id(), rule.effect());
    }

    public static Rule match(List<Rule> rules, List<String> keys, String sourceKey) {
        for (Rule rule : rules) {
            if (sourceKey != null && rule.match().contains(sourceKey)) {
                return rule;
            }
            for (String key : keys) {
                if (rule.match().contains(key)) {
                    return rule;
                }
            }
        }
        return null;
    }

    public static String effectFor(List<Rule> rules, Map<String, String> choices, List<String> keys, String sourceKey) {
        Rule rule = match(rules, keys, sourceKey);
        if (rule == null) {
            return null;
        }
        String effect = effectOf(rule, choices);
        return effect == null || effect.isBlank() ? null : effect;
    }
}
