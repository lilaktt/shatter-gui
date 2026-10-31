package net.shattergui.fx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The schema-2 parser on good and broken input: clear errors with the field's path, clamping, unknown fields. */
class FxParserTest {
    private static final String MINIMAL = "{\"schema\": 2, \"id\": \"t\"}";

    /** A minimal effect with one extra top-level field (JSON fragment without braces). */
    private static String with(String fields) {
        return "{\"schema\": 2, \"id\": \"t\", " + fields + "}";
    }

    private static FxParser.Result parse(String json) {
        return FxParser.parse(json);
    }

    private static boolean hasError(FxParser.Result r, String path, String text) {
        return r.problems().errors().stream().anyMatch(p -> p.path().equals(path) && p.message().contains(text));
    }

    private static boolean hasWarning(FxParser.Result r, String path, String text) {
        return r.problems().warnings().stream().anyMatch(p -> p.path().equals(path) && p.message().contains(text));
    }

    static List<Path> examples() throws IOException {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(Path.of("docs", "examples"))) {
            for (Path dir : dirs) {
                Path json = dir.resolve("effect.json");
                if (Files.exists(json)) {
                    out.add(json);
                }
            }
        }
        return out;
    }

    @Test
    void examplesParseCleanly() throws IOException {
        List<Path> files = examples();
        assertEquals(4, files.size(), "four examples in docs/examples");
        for (Path file : files) {
            FxParser.Result r = parse(Files.readString(file, StandardCharsets.UTF_8));
            assertTrue(r.ok(), file + ": " + r.problems().all());
            assertTrue(r.problems().all().isEmpty(), file + " should parse without warnings: " + r.problems().all());
        }
    }

    @Test
    void minimalEffectHasDefaults() {
        FxParser.Result r = parse(MINIMAL);
        assertTrue(r.ok(), r.problems().all().toString());
        assertEquals(40, r.def().duration);
        assertEquals("grid", r.def().fracture.mode());
        assertEquals("wave", r.def().trigger.type());
        assertTrue(hasWarning(r, "layers", "no layers"));
    }

    @Test
    void notJson() {
        FxParser.Result r = parse("{\"schema\": 2, \"id\": ");
        assertFalse(r.ok());
        assertTrue(hasError(r, "", "not valid JSON"), r.problems().all().toString());
        assertFalse(parse("[1, 2]").ok());
        assertFalse(parse("").ok());
        assertFalse(parse("null").ok());
    }

    @Test
    void schemaAndId() {
        assertTrue(hasError(parse("{\"schema\": 1, \"id\": \"t\"}"), "schema", "must be 2"));
        assertTrue(hasError(parse("{\"schema\": \"2\", \"id\": \"t\"}"), "schema", "must be 2"));
        assertTrue(hasError(parse("{\"schema\": 2}"), "id", "required"));
        assertTrue(hasError(parse("{\"schema\": 2, \"id\": \"Bad Id!\"}"), "id", "a-z"));
        assertTrue(hasError(parse("{\"schema\": 2, \"id\": 5}"), "id", "must be a string"));
    }

    @Test
    void wrongTypesHavePaths() {
        assertTrue(hasError(parse(with("\"duration\": \"long\"")), "duration", "must be a number"));
        assertTrue(hasError(parse(with("\"fracture\": 3")), "fracture", "must be an object"));
        assertTrue(hasError(parse(with("\"fracture\": {\"mode\": \"cubes\"}")), "fracture.mode", "expected one of"));
        assertTrue(hasError(parse(with("\"layers\": {}")), "layers", "must be a list"));
        assertTrue(hasError(parse(with("\"layers\": [5]")), "layers[0]", "must be an object"));
        assertTrue(hasError(parse(with("\"appearance\": {\"fullbright\": 1}")), "appearance.fullbright", "true or false"));
    }

    @Test
    void outOfRangeIsClampedWithAWarning() {
        FxParser.Result r = parse(with("\"duration\": 99999, \"fracture\": {\"jitter\": 7}"));
        assertTrue(r.ok(), r.problems().all().toString());
        assertEquals(FxLimits.MAX_DURATION, r.def().duration);
        assertEquals(1f, r.def().fracture.jitter());
        assertTrue(hasWarning(r, "duration", "clamped"));
        assertTrue(hasWarning(r, "fracture.jitter", "clamped"));
        FxParser.Result nan = parse(with("\"layers\": [{\"module\": \"spin\", \"params\": {\"speed\": 1e400}}]"));
        assertFalse(nan.ok(), "an infinite number is an error: " + nan.problems().all());
    }

    @Test
    void unknownFieldsAreWarnings() {
        FxParser.Result r = parse(with("\"colour\": \"#fff\", \"layers\": [{\"module\": \"spin\", \"params\": {\"speeed\": 1}}]"));
        assertTrue(r.ok(), r.problems().all().toString());
        assertTrue(hasWarning(r, "colour", "unknown field"));
        assertTrue(hasWarning(r, "layers[0].params.speeed", "unknown field"));
    }

    @Test
    void layers() {
        assertTrue(hasError(parse(with("\"layers\": [{\"module\": \"teleport\"}]")), "layers[0].module", "expected one of"));
        assertTrue(hasError(parse(with("\"layers\": [{}]")), "layers[0].module", "required"));
        assertTrue(hasError(parse(with("\"layers\": [{\"module\": \"spin\", \"from\": 0.8, \"to\": 0.2}]")), "layers[0]", "before"));
        assertTrue(hasError(parse(with("\"layers\": [{\"module\": \"spin\", \"target\": {\"group\": \"corners\"}}]")),
                "layers[0].target.group", "expected one of"));
        assertTrue(hasError(parse(with("\"layers\": [{\"module\": \"follow-path\"}]")), "layers[0].params.points", "needs"));
        assertTrue(hasError(parse(with("\"layers\": [{\"module\": \"follow-path\", \"params\": {\"points\": [[1]]}}]")),
                "layers[0].params.points[0]", "exactly"));
        StringBuilder many = new StringBuilder("\"layers\": [");
        for (int i = 0; i < 20; i++) {
            many.append(i > 0 ? "," : "").append("{\"module\": \"drag\"}");
        }
        FxParser.Result r = parse(with(many + "]"));
        assertTrue(r.ok());
        assertEquals(FxLimits.MAX_LAYERS, r.def().layers.size());
        assertTrue(hasWarning(r, "layers", "more than"));
    }

    @Test
    void curves() {
        String base = "\"layers\": [{\"module\": \"spin\", \"params\": {\"speed\": %s}}]";
        assertTrue(hasError(parse(with(String.format(base, "{\"curve\": \"wobbly\", \"from\": 0, \"to\": 1}"))),
                "layers[0].params.speed.curve", "unknown easing"));
        assertTrue(hasError(parse(with(String.format(base, "{\"curve\": \"easeIn\"}"))),
                "layers[0].params.speed", "\"from\" and \"to\""));
        assertTrue(hasError(parse(with(String.format(base, "{\"bezier\": [1.5, 0, 0.5, 1], \"from\": 0, \"to\": 1}"))),
                "layers[0].params.speed.bezier", "between 0 and 1"));
        assertTrue(hasError(parse(with(String.format(base, "{\"bezier\": [0.1, 0.2], \"from\": 0, \"to\": 1}"))),
                "layers[0].params.speed.bezier", "exactly 4"));
        assertTrue(hasError(parse(with(String.format(base, "{\"keys\": [[0, 0], [0.8, 1], [0.5, 0]]}"))),
                "layers[0].params.speed.keys[2]", "go up"));
        assertTrue(hasError(parse(with(String.format(base, "{\"keys\": [[0, 0], [2, 1]]}"))),
                "layers[0].params.speed.keys[1]", "between 0 and 1"));
        assertTrue(hasError(parse(with(String.format(base, "{\"keys\": []}"))), "layers[0].params.speed.keys", "at least one"));
        assertTrue(hasError(parse(with(String.format(base, "{\"random\": [0]}"))), "layers[0].params.speed.random", "exactly 2"));
        assertTrue(hasError(parse(with(String.format(base, "{\"jitter\": 0.2}"))), "layers[0].params.speed", "needs one of"));
        assertTrue(hasError(parse(with(String.format(base, "\"fast\""))), "layers[0].params.speed", "number or a curve"));
        FxParser.Result clamped = parse(with(String.format(base, "{\"keys\": [[0, 0], [1, 50]]}")));
        assertTrue(hasWarning(clamped, "layers[0].params.speed.keys[1]", "clamped"), clamped.problems().all().toString());
    }

    @Test
    void appearanceAndTextures() {
        assertTrue(hasError(parse(with("\"appearance\": {\"color\": [\"#fff\", \"red\"]}")), "appearance.color[1]", "not a colour"));
        assertTrue(hasError(parse(with("\"appearance\": {\"color\": [{\"t\": 0.5, \"color\": \"#fff\"}, {\"t\": 0.1, \"color\": \"#000\"}]}")),
                "appearance.color[1]", "go up"));
        assertTrue(hasError(parse(with("\"appearance\": {\"overlays\": [{\"blend\": \"screen\", \"texture\": \"a\"}]}")),
                "appearance.overlays[0].blend", "expected one of"));
        assertTrue(hasError(parse(with("\"appearance\": {\"overlays\": [{}]}")), "appearance.overlays[0].texture", "required"));
        assertTrue(hasError(parse(with("\"trigger\": {\"type\": \"mask\"}")), "trigger.texture", "required"));
        assertTrue(hasError(parse(with("\"trigger\": {\"type\": \"mask\", \"texture\": \"../../secret\"}")), "trigger.texture",
                "not a valid texture name"));
        FxParser.Result png = parse(with("\"trigger\": {\"type\": \"mask\", \"texture\": \"m.png\"}"));
        assertTrue(png.ok());
        assertTrue(png.def().textures.containsKey("m"));
        StringBuilder overlays = new StringBuilder();
        StringBuilder emitters = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            emitters.append(i > 0 ? "," : "").append("{\"sprite\": \"s").append(i).append("\"}");
        }
        for (int i = 0; i < 4; i++) {
            overlays.append(i > 0 ? "," : "").append("{\"texture\": \"o").append(i).append("\"}");
        }
        FxParser.Result many = parse(with("\"emitters\": [" + emitters + "], \"appearance\": {\"overlays\": [" + overlays
                + "], \"mask\": {\"texture\": \"m1\"}}, \"trigger\": {\"type\": \"mask\", \"texture\": \"m2\"}, "
                + "\"layers\": [{\"module\": \"drag\"}]"));
        // 8 sprites + 4 overlays + a mask + a trigger mask: the most the format can name, within the limit.
        assertTrue(many.ok(), many.problems().all().toString());
        assertEquals(14, many.def().textures.size());
        assertEquals(FxDef.TextureRole.SPRITE, many.def().textures.get("s0"));
        assertEquals(FxDef.TextureRole.MASK, many.def().textures.get("m2"));
        FxParser.Result nine = parse(with("\"emitters\": [" + emitters + ",{\"sprite\": \"s9\"}]"));
        assertTrue(hasWarning(nine, "emitters", "more than"));
        assertEquals(FxLimits.MAX_EMITTERS, nine.def().emitters.size());
    }
    @Test
    void emittersAndSounds() {
        assertTrue(hasError(parse(with("\"emitters\": [{}]")), "emitters[0]", "exactly one"));
        assertTrue(hasError(parse(with("\"emitters\": [{\"particle\": \"minecraft:flame\", \"sprite\": \"s\"}]")),
                "emitters[0]", "exactly one"));
        assertTrue(hasError(parse(with("\"emitters\": [{\"particle\": \"Flame Thing\"}]")), "emitters[0].particle", "not a valid id"));
        assertTrue(hasError(parse(with("\"emitters\": [{\"particle\": \"flame\", \"zone\": \"sky\"}]")), "emitters[0].zone",
                "expected one of"));
        FxParser.Result flame = parse(with("\"emitters\": [{\"particle\": \"flame\"}]"));
        assertTrue(flame.ok());
        assertEquals("minecraft:flame", flame.def().emitters.getFirst().particle());
        assertTrue(hasError(parse(with("\"sounds\": [{\"volume\": 1}]")), "sounds[0].sound", "required"));
        assertTrue(hasError(parse(with("\"sounds\": [{\"sound\": \"a:b\", \"at\": \"middle\"}]")), "sounds[0].at", "expected one of"));
        FxParser.Result timed = parse(with("\"sounds\": [{\"sound\": \"a:b\", \"at\": 0.5}]"));
        assertTrue(timed.ok());
        assertEquals("time", timed.def().sounds.getFirst().at());
    }

    @Test
    void fractureRegionAndModifiers() {
        assertTrue(hasError(parse(with("\"fracture\": {\"region\": [0.5, 0.5, 0.4, 0.9]}")), "fracture.region", "u1 > u0"));
        assertTrue(hasError(parse(with("\"fracture\": {\"region\": [0, 0, 1]}")), "fracture.region", "exactly 4"));
        assertTrue(hasError(parse(with("\"modifiers\": {\"edge\": {\"direction\": \"up\"}}")), "modifiers.edge.direction",
                "expected one of"));
        assertTrue(hasWarning(parse(with("\"modifiers\": {\"edge\": {\"enter\": false, \"exit\": false}}")), "modifiers.edge",
                "does nothing"));
        assertTrue(hasError(parse(with("\"trigger\": {\"direction\": [0, 0]}")), "trigger.direction", "must not be"));
        FxParser.Result many = parse(with("\"fracture\": {\"mode\": \"voronoi\", \"count\": 5000}"));
        assertEquals(FxLimits.MAX_VORONOI_CELLS, many.def().fracture.count());
    }

    @Test
    void tooLarge() {
        String big = with("\"description\": \"" + "x".repeat(FxLimits.MAX_JSON_BYTES) + "\"");
        FxParser.Result r = parse(big);
        assertFalse(r.ok());
        assertTrue(hasError(r, "", "too large"));
    }

    /** Random damage to the examples: the parser never throws, and a result is either usable or has errors. */
    @Test
    void fuzzedExamplesNeverThrow() throws IOException {
        Random random = new Random(1234);
        String[] junk = {"\"x\"", "-1", "1e9", "[]", "{}", "null", "true", "[[[", "}", ",", "\"#zzz\"", "0.5"};
        for (Path file : examples()) {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            for (int i = 0; i < 300; i++) {
                StringBuilder s = new StringBuilder(json);
                int edits = 1 + random.nextInt(4);
                for (int e = 0; e < edits; e++) {
                    int pos = random.nextInt(s.length());
                    switch (random.nextInt(3)) {
                        case 0 -> s.deleteCharAt(pos);
                        case 1 -> s.insert(pos, junk[random.nextInt(junk.length)]);
                        default -> s.setCharAt(pos, (char) (32 + random.nextInt(90)));
                    }
                }
                FxParser.Result r = parse(s.toString());
                assertNotNull(r.problems());
                if (!r.ok()) {
                    assertNull(r.def());
                    assertFalse(r.problems().errors().isEmpty());
                }
            }
        }
    }

    @Test
    void schemaOf() {
        assertEquals(2, FxParser.schemaOf(MINIMAL));
        assertEquals(1, FxParser.schemaOf("{\"schema\": 1}"));
        assertEquals(1, FxParser.schemaOf("{}"));
        assertEquals(1, FxParser.schemaOf("not json"));
    }
}
