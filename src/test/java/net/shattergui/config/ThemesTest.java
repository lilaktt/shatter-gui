package net.shattergui.config;

import net.shattergui.params.BundledParams;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Thematic mode's rules: the shipped table, its order and the player's changes. */
class ThemesTest {
    private static List<Themes.Rule> shipped() throws IOException {
        try (InputStream in = ThemesTest.class.getResourceAsStream("/assets/shatter_gui/themes.json")) {
            return Themes.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void shippedTableAsDesigned() throws IOException {
        List<Themes.Rule> rules = shipped();
        Map<String, String> none = Map.of();
        assertEquals("fly", Themes.effectFor(rules, none, List.of("minecraft:generic_9x3"), "block:minecraft:chest"));
        assertEquals("fly", Themes.effectFor(rules, none, List.of("minecraft:generic_9x3"), "block:minecraft:barrel"));
        assertEquals("portal", Themes.effectFor(rules, none, List.of("minecraft:generic_9x3"), "block:minecraft:ender_chest"),
                "the ender chest's block wins over its chest menu");
        for (String furnace : List.of("minecraft:furnace", "minecraft:smoker", "minecraft:blast_furnace")) {
            assertEquals("burn", Themes.effectFor(rules, none, List.of(furnace), null));
        }
        assertEquals("runes", Themes.effectFor(rules, none, List.of("minecraft:enchantment"), null));
        assertEquals("bubbles", Themes.effectFor(rules, none, List.of("minecraft:brewing_stand"), null));
        assertEquals("explosion", Themes.effectFor(rules, none, List.of("minecraft:anvil"), null));
        assertEquals("vortex", Themes.effectFor(rules, none, List.of("minecraft:beacon"), null));
        assertEquals("glass", Themes.effectFor(rules, none, List.of("minecraft:crafting"), null));
        assertEquals("confetti", Themes.effectFor(rules, none, List.of("minecraft:merchant"), null));
        assertNull(Themes.effectFor(rules, none, List.of("minecraft:inventory", "InventoryScreen"), null),
                "anything else: the player's general effect");
    }

    @Test
    void playerChoicesWin() throws IOException {
        List<Themes.Rule> rules = shipped();
        assertEquals("soul_fire", Themes.effectFor(rules, Map.of("furnaces", "soul_fire"), List.of("minecraft:furnace"), null));
        assertNull(Themes.effectFor(rules, Map.of("furnaces", Themes.GENERAL), List.of("minecraft:furnace"), null),
                "a rule set to the general effect gives none");
    }

    @Test
    void everyRuleNamesAnEffectOfTheMod() throws IOException {
        Set<String> known = new HashSet<>(List.of("glass", "burn", "fly", "dissolve", "vortex"));
        known.addAll(BundledParams.ids());
        List<String> ids = new ArrayList<>();
        for (Themes.Rule rule : shipped()) {
            assertTrue(known.contains(rule.effect()), rule.id() + " uses an unknown effect " + rule.effect());
            assertTrue(!ids.contains(rule.id()), "rule ids are unique");
            ids.add(rule.id());
        }
        assertEquals(15, ids.size());
    }

    @Test
    void brokenRulesAreSkipped() {
        List<Themes.Rule> rules = Themes.parse("{\"rules\": [{\"id\": \"a\"}, 5, {\"id\": \"b\", \"match\": [\"x\"], \"effect\": \"glass\"}]}");
        assertEquals(1, rules.size());
        assertEquals("b", rules.getFirst().id());
    }
}
