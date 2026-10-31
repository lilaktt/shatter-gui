package net.shattergui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.shattergui.ShatterGuiClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thematic mode: the close effect by kind of interface (a furnace burns, an enchanting table breaks into runes, …).
 * The rules are data ({@code assets/shatter_gui/themes.json}): each matches menu types and source blocks and names an
 * effect; the player can give a rule another effect, or none (then the general effect is used), in the Screens tab
 * ({@link ShatterConfig#themes}). Per-screen choices in the Screens tab still come first.
 */
public final class Themes {
    /** {@link ShatterConfig#themes} value: this rule uses the general effect. */
    public static final String GENERAL = "";

    /** One rule: its id (for the name and the player's choice), what it matches and its effect. */
    public record Rule(String id, List<String> match, String effect) {
    }

    private static List<Rule> rules;

    private Themes() {
    }

    /** The rules shipped with the mod, in order (read once). */
    public static List<Rule> rules() {
        if (rules == null) {
            rules = load();
        }
        return rules;
    }

    private static List<Rule> load() {
        try (InputStream in = Themes.class.getResourceAsStream("/assets/shatter_gui/themes.json")) {
            if (in == null) {
                ShatterGuiClient.LOGGER.warn("Thematic rules are missing from the mod, thematic mode does nothing");
                return List.of();
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            ShatterGuiClient.LOGGER.warn("Could not read the thematic rules: {}", e.toString());
            return List.of();
        }
    }

    /** Reads {"rules": [{"id", "match": [...], "effect"}]}; broken rules are skipped. */
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

    /** The effect a rule uses now: the player's choice if any (may be {@link #GENERAL}), else its own. */
    public static String effectOf(Rule rule, Map<String, String> choices) {
        return choices.getOrDefault(rule.id(), rule.effect());
    }

    /**
     * The first rule that matches the source block ({@code "block:<id>"}, may be null) or one of the screen's keys, or
     * null if none does.
     */
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

    /** The thematic effect for a screen, or null for the general one (no rule, or the rule says so). */
    public static String effectFor(List<Rule> rules, Map<String, String> choices, List<String> keys, String sourceKey) {
        Rule rule = match(rules, keys, sourceKey);
        if (rule == null) {
            return null;
        }
        String effect = effectOf(rule, choices);
        return effect == null || effect.isBlank() ? null : effect;
    }
}
