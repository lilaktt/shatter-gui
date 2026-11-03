package net.shattergui.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxParser;
import net.shattergui.fx.FxSchema;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The editor's document: paths, edits, undo/redo with merging, lists, parsing, and robustness against odd data. */
class FxDocTest {
    private static FxDoc doc() {
        return new FxDoc(Templates.json("glass"));
    }

    @Test
    void pathsReadAndWrite() {
        FxDoc doc = doc();
        assertEquals("launch", doc.str("layers[0].module", null));
        assertEquals(0.5f, doc.num("layers[0].params.spread", 0), 1e-6);
        doc.set("layers[0].params.spread", 0.75f);
        assertEquals(0.75f, doc.num("layers[0].params.spread", 0), 1e-6);
        // Missing parts are created.
        doc.set("modifiers.edge.direction", "left");
        assertEquals("left", doc.str("modifiers.edge.direction", null));
        doc.set("appearance.overlays[0].texture", "lines");
        assertEquals("lines", doc.str("appearance.overlays[0].texture", null));
        doc.remove("modifiers.edge");
        assertFalse(doc.has("modifiers.edge"));
        // Wrong types read as the default.
        assertEquals(7f, doc.num("layers[0].module", 7), 0);
        assertEquals("x", doc.str("duration", "x"));
        assertNull(doc.get("layers[99].module"));
    }

    @Test
    void numbersAreClean() {
        assertEquals("3", FxDoc.number(3.0).toString());
        assertEquals("0.3", FxDoc.number(0.1f + 0.2f).toString());
        assertEquals("0", FxDoc.number(Double.NaN).toString());
    }

    @Test
    void undoRedoAndMerging() {
        FxDoc doc = doc();
        assertFalse(doc.canUndo());
        // A drag: many edits of one field are one step.
        for (int i = 0; i < 20; i++) {
            doc.set("duration", 30 + i);
        }
        doc.breakMerge();
        doc.set("name", "Other");
        assertTrue(doc.dirty());
        assertTrue(doc.undo());
        assertEquals("Glass", doc.str("name", null));
        assertEquals(49, doc.num("duration", 0), 0);
        assertTrue(doc.undo());
        assertEquals(36, doc.num("duration", 0), 0);
        assertFalse(doc.undo());
        assertFalse(doc.dirty());
        assertTrue(doc.redo());
        assertTrue(doc.redo());
        assertEquals("Other", doc.str("name", null));
        assertFalse(doc.redo());
        // A new edit clears the redo history.
        doc.undo();
        doc.set("description", "x");
        assertFalse(doc.canRedo());
        // No change, no step.
        int version = doc.version();
        doc.set("description", "x");
        assertEquals(version, doc.version());
    }

    @Test
    void lists() {
        FxDoc doc = doc();
        int added = doc.append("layers", JsonParser.parseString("{\"module\": \"drag\"}"));
        // The glass template has 4 layers (burst, fall, tumble, land).
        assertEquals(4, added);
        assertEquals("drag", doc.str("layers[4].module", null));
        doc.move("layers", 4, 0);
        assertEquals("drag", doc.str("layers[0].module", null));
        assertEquals("launch", doc.str("layers[1].module", null));
        int copy = doc.duplicate("layers", 1);
        assertEquals(2, copy);
        assertEquals("launch", doc.str("layers[2].module", null));
        assertEquals(6, doc.size("layers"));
        doc.remove("layers[0]");
        assertEquals(5, doc.size("layers"));
        // Out-of-range moves and duplicates do nothing.
        int version = doc.version();
        doc.move("layers", 0, 40);
        assertEquals(-1, doc.duplicate("layers", 40));
        assertEquals(version, doc.version());
        // A list created from nothing.
        doc.append("sounds", JsonParser.parseString("{\"sound\": \"minecraft:block.glass.break\"}"));
        assertEquals(2, doc.size("sounds"));
    }

    @Test
    void parsingAndProblemsByPath() {
        FxDoc doc = doc();
        assertTrue(doc.result().ok());
        doc.set("layers[1].module", "teleport");
        assertFalse(doc.result().ok());
        assertFalse(doc.problems("layers[1]").isEmpty());
        assertTrue(doc.problems("layers[0]").isEmpty());
        assertFalse(doc.problems("").isEmpty());
        assertTrue(FxDoc.isUnder("layers[1].params.speed", "layers[1]"));
        assertFalse(FxDoc.isUnder("layers[10]", "layers[1]"));
        doc.undo();
        assertTrue(doc.result().ok());
    }

    @Test
    void disabledLayersAndNames() {
        FxDoc doc = doc();
        doc.set("layers[1].enabled", false);
        doc.set("layers[1].name", "Gravity off");
        FxParser.Result result = doc.result();
        assertTrue(result.ok(), result.problems().all().toString());
        assertTrue(result.problems().warnings().isEmpty(), result.problems().all().toString());
        assertEquals(3, result.def().layers.size());
        doc.set("emitters[0].enabled", false);
        doc.set("sounds[0].enabled", false);
        assertEquals(0, doc.result().def().emitters.size());
        assertEquals(0, doc.result().def().sounds.size());
    }

    @Test
    void garbageNeverThrows() {
        FxDoc broken = new FxDoc("not json at all");
        assertNotNull(broken.root());
        assertFalse(broken.result().ok());
        FxDoc doc = doc();
        Random random = new Random(3);
        String[] paths = {"layers[0].params.speed", "duration", "fracture", "layers", "appearance.color[2]", "trigger.direction",
                "emitters[0].rate.keys[0]", "layers[0]", "sounds[5].sound", "name"};
        for (int i = 0; i < 500; i++) {
            String path = paths[random.nextInt(paths.length)];
            switch (random.nextInt(5)) {
                case 0 -> doc.set(path, random.nextFloat() * 1000 - 500);
                case 1 -> doc.set(path, "x" + random.nextInt(9));
                case 2 -> doc.set(path, random.nextBoolean());
                case 3 -> doc.remove(path);
                default -> {
                    if (random.nextBoolean()) {
                        doc.undo();
                    } else {
                        doc.redo();
                    }
                }
            }
            doc.result();
        }
    }

    @Test
    void templatesParseCleanly() {
        for (String id : Templates.IDS) {
            FxParser.Result result = FxParser.parse(Templates.json(id));
            assertTrue(result.ok(), id + ": " + result.problems().all());
            assertTrue(result.problems().warnings().isEmpty(), id + ": " + result.problems().all());
            assertTrue(result.def().textures.isEmpty(), id + " must not need textures");
        }
    }

    @Test
    void schemaRangesMatchTheParser() {
        // Every module parameter: a huge value is clamped to the schema's maximum, a tiny one to its minimum.
        for (var entry : FxSchema.MODULE_PARAMS.entrySet()) {
            for (FxSchema.Field field : entry.getValue()) {
                String module = FxSchema.moduleName(entry.getKey());
                String param = FxSchema.paramName(field);
                for (float probe : new float[]{field.max() + 1000, field.min() - 1000}) {
                    JsonObject root = JsonParser.parseString(Templates.json("empty")).getAsJsonObject();
                    String extra = entry.getKey() == FxDef.Module.FOLLOW_PATH ? ", \"points\": [[0.1, 0.1]]" : "";
                    root.add("layers", JsonParser.parseString("[{\"module\": \"" + module + "\", \"params\": {\"" + param + "\": "
                            + probe + extra + "}}]"));
                    FxParser.Result result = FxParser.parse(root.toString());
                    assertTrue(result.ok(), field.key() + ": " + result.problems().all());
                    float expected = probe > field.max() ? field.max() : field.min();
                    assertEquals(expected, result.def().layers.getFirst().value(param).at(0.5f, 0.5f), 1e-5, field.key());
                }
            }
        }
        // Emitter and appearance numbers.
        String json = "{\"schema\": 2, \"id\": \"x\", \"layers\": [], \"emitters\": [{\"particle\": \"flame\", \"rate\": 999, "
                + "\"life\": 999, \"size\": 9, \"spread\": 9}], \"appearance\": {\"emissive\": 9}}";
        FxParser.Result result = FxParser.parse(json);
        var emitter = result.def().emitters.getFirst();
        assertEquals(FxSchema.field("emitter.rate").max(), emitter.rate().at(0, 0), 1e-5);
        assertEquals(FxSchema.field("emitter.life").max(), emitter.life().at(0, 0), 1e-5);
        assertEquals(FxSchema.field("emitter.size").max(), emitter.size().at(0, 0), 1e-5);
        assertEquals(FxSchema.field("emitter.spread").max(), emitter.spread().at(0, 0), 1e-5);
        assertEquals(FxSchema.field("appearance.emissive").max(), result.def().appearance.emissive().at(0, 0), 1e-5);
    }

    @Test
    void freeIds() {
        assertEquals("my_effect", EffectFiles.freeId("My Effect", id -> false));
        assertEquals("glass_2", EffectFiles.freeId("glass", "glass"::equals));
        assertEquals("effect", EffectFiles.freeId("", id -> false));
        JsonObject root = new JsonObject();
        root.add("x", new JsonPrimitive(1));
        assertEquals("{\n  \"x\": 1\n}", FxDoc.pretty(root));
    }
}
