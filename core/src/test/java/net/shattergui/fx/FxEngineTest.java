package net.shattergui.fx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Curves, fracture and the simulation, without the game. */
class FxEngineTest {
    /** A host without a world: masks are a horizontal gradient, particles and sounds are counted. */
    static final class FakeHost implements FxSimulation.Host {
        int particles;
        final List<String> sounds = new ArrayList<>();
        boolean masks = true;

        @Override
        public float[] point(FxDef.Point.Kind kind) {
            return kind == FxDef.Point.Kind.SOURCE ? new float[]{0, -1, 1} : null;
        }

        @Override
        public float sampleMask(String texture, float u, float v) {
            return masks ? Math.clamp(u, 0f, 1f) : Float.NaN;
        }

        @Override
        public float[] gravity() {
            return new float[]{0, -1, 0};
        }

        @Override
        public boolean hasSprite(String texture) {
            return true;
        }

        @Override
        public void spawnParticle(FxDef.Emitter emitter, float x, float y, float z, float vx, float vy, float vz, float size) {
            particles++;
        }

        @Override
        public void playSound(String sound, float volume, float pitch) {
            sounds.add(sound);
        }
    }

    private static FxDef parse(String json) {
        FxParser.Result r = FxParser.parse(json);
        assertTrue(r.ok(), r.problems().all().toString());
        return r.def();
    }

    private static FxSimulation run(FxDef def, FakeHost host, int extraTicks) {
        Random random = new Random(42);
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, 1.06f, 176, random);
        FxSimulation sim = new FxSimulation(def, cut, 0.9f, 0.85f, host, random, 1f, false, 1f);
        for (int i = 0; i < sim.duration + extraTicks; i++) {
            sim.tick();
            for (FxSimulation.Piece p : sim.pieces()) {
                assertTrue(Float.isFinite(p.x) && Float.isFinite(p.y) && Float.isFinite(p.z), "finite position");
            }
        }
        return sim;
    }

    // --- curves ---

    private static Value value(String json) {
        FxProblems problems = new FxProblems();
        Value v = Value.read(new Node(com.google.gson.JsonParser.parseString(json), "v", problems), 0, -10, 10);
        assertFalse(problems.hasErrors(), problems.all().toString());
        return v;
    }

    @Test
    void curvesHitTheirEnds() {
        for (Easing e : Easing.values()) {
            if (e == Easing.PULSE || e == Easing.STEP) {
                continue;
            }
            assertEquals(0f, e.apply(0f), 1e-4f, e.configName());
            assertEquals(1f, e.apply(1f), 1e-4f, e.configName());
        }
        assertEquals(0f, Easing.PULSE.apply(0f), 1e-4f);
        assertEquals(1f, Easing.PULSE.apply(0.5f), 1e-4f);
        Value curve = value("{\"curve\": \"easeIn\", \"from\": 2, \"to\": 4}");
        assertEquals(2f, curve.at(0, 0), 1e-4f);
        assertEquals(2.5f, curve.at(0.5f, 0), 1e-4f);
        assertEquals(4f, curve.at(1, 0), 1e-4f);
    }

    @Test
    void bezierAndKeys() {
        Value linear = value("{\"bezier\": [0.333, 0.333, 0.667, 0.667], \"from\": 0, \"to\": 1}");
        for (float t = 0; t <= 1; t += 0.125f) {
            assertEquals(t, linear.at(t, 0), 0.01f);
        }
        Value keys = value("{\"keys\": [[0, 0], [0.5, 2], [1, 1]]}");
        assertEquals(1f, keys.at(0.25f, 0), 1e-4f);
        assertEquals(2f, keys.at(0.5f, 0), 1e-4f);
        assertEquals(1.5f, keys.at(0.75f, 0), 1e-4f);
        assertEquals(1f, keys.at(1.2f, 0), 1e-4f);
    }

    @Test
    void randomJitterAndClamp() {
        Value range = value("{\"random\": [1, 3]}");
        assertEquals(1f, range.at(0.3f, 0f), 1e-4f);
        assertEquals(3f, range.at(0.3f, 1f), 1e-4f);
        Value jitter = value("{\"value\": 2, \"jitter\": 0.5}");
        for (float r = 0; r < 1; r += 0.05f) {
            float v = jitter.at(0, r);
            assertTrue(v >= 1f - 1e-4f && v <= 3f + 1e-4f, "jittered " + v);
        }
        Value big = value("{\"curve\": \"linear\", \"from\": 0, \"to\": 10}");
        assertTrue(big.at(1, 0) <= 10f);
    }

    @Test
    void gradient() {
        FxProblems problems = new FxProblems();
        Gradient g = Gradient.read(new Node(com.google.gson.JsonParser.parseString("[\"#000000\", \"#ffffff\"]"), "c", problems),
                Gradient.WHITE);
        assertFalse(problems.hasErrors());
        int mid = g.at(0.5f);
        assertEquals(128, (mid >> 16) & 0xFF, 1);
        assertEquals(0xFF000000, g.at(0) );
    }

    // --- fracture ---

    @Test
    void everyModeCoversTheInterfaceOnce() {
        String[] modes = {
                "{\"mode\": \"grid\", \"count\": 60}", "{\"mode\": \"grid\", \"cols\": 7, \"rows\": 5, \"split\": true}",
                "{\"mode\": \"voronoi\", \"count\": 120, \"jitter\": 1}", "{\"mode\": \"strips\", \"count\": 12, \"segments\": 4}",
                "{\"mode\": \"strips\", \"direction\": \"vertical\", \"count\": 9}", "{\"mode\": \"radial\", \"count\": 16, \"rings\": 5}",
                "{\"mode\": \"radial\", \"count\": 10, \"center\": [0.2, 0.8]}", "{\"mode\": \"pixels\", \"size\": 8}",
                "{\"mode\": \"voronoi\", \"count\": 40, \"region\": [0.2, 0.2, 0.7, 0.9]}",
                "{\"mode\": \"pixels\", \"size\": 4, \"region\": [0, 0.5, 1, 1]}"};
        for (String mode : modes) {
            FxDef def = parse("{\"schema\": 2, \"id\": \"t\", \"layers\": [], \"fracture\": " + mode + "}");
            List<Fracture.Piece> pieces = Fracture.cut(def.fracture, 1.06f, 176, new Random(7));
            assertFalse(pieces.isEmpty(), mode);
            assertEquals(1.0, Fracture.area(pieces), 0.01, mode + ": the pieces cover the interface exactly once");
            int triangles = pieces.stream().mapToInt(Fracture.Piece::triangleCount).sum();
            assertTrue(triangles <= FxLimits.MAX_TRIANGLES && pieces.size() <= FxLimits.MAX_PIECES, mode);
        }
    }

    @Test
    void limitsHold() {
        FxDef def = parse("{\"schema\": 2, \"id\": \"t\", \"layers\": [], \"fracture\": {\"mode\": \"pixels\", \"size\": 2}}");
        List<Fracture.Piece> pieces = Fracture.cut(def.fracture, 1.06f, 4000, new Random(1));
        assertTrue(pieces.size() <= FxLimits.MAX_PIECES);
        FxDef radial = parse("{\"schema\": 2, \"id\": \"t\", \"layers\": [], \"fracture\": {\"mode\": \"radial\", \"count\": 1500, \"rings\": 12}}");
        List<Fracture.Piece> rings = Fracture.cut(radial.fracture, 1f, 176, new Random(1));
        assertTrue(rings.stream().mapToInt(Fracture.Piece::triangleCount).sum() <= FxLimits.MAX_TRIANGLES);
    }

    // --- simulation ---

    @Test
    void examplesRunToTheEnd() throws IOException {
        for (Path file : FxParserTest.examples()) {
            FxDef def = parse(Files.readString(file, StandardCharsets.UTF_8));
            FakeHost host = new FakeHost();
            FxSimulation sim = run(def, host, FxLimits.MAX_PARTICLE_LIFE + 5);
            assertFalse(sim.alive(), file + " must end");
            assertTrue(sim.pieces().stream().allMatch(p -> p.finished), file + ": every piece finishes");
            assertEquals(def.sounds.size(), host.sounds.size(), file + ": every sound plays once");
            sim.dispose();
        }
        assertEquals(0, FxSimulation.liveSpritesTotal(), "sprites are released");
    }

    @Test
    void maskTriggerOrdersPiecesByBrightness() {
        FxDef def = parse("{\"schema\": 2, \"id\": \"t\", \"layers\": [], \"trigger\": {\"type\": \"mask\", \"texture\": \"m\", \"spread\": 0.8}}");
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, 1, 176, new Random(3));
        FxSimulation sim = new FxSimulation(def, cut, 1, 1, new FakeHost(), new Random(3), 1, false, 1);
        for (FxSimulation.Piece a : sim.pieces()) {
            for (FxSimulation.Piece b : sim.pieces()) {
                if (a.u + 0.05f < b.u) {
                    assertTrue(a.start <= b.start, "darker (left) parts of the mask start first");
                }
            }
        }
        FakeHost noMask = new FakeHost();
        noMask.masks = false;
        FxSimulation fallback = new FxSimulation(def, cut, 1, 1, noMask, new Random(3), 1, false, 1);
        assertTrue(fallback.maskMissing(), "a missing mask falls back to noise and says so");
    }

    @Test
    void attractPullsIn() {
        FxDef def = parse("{\"schema\": 2, \"id\": \"t\", \"duration\": 60, \"trigger\": {\"type\": \"none\"},"
                + "\"layers\": [{\"module\": \"attract\", \"params\": {\"to\": \"center\", \"strength\": 0.004}}, {\"module\": \"drag\", \"params\": {\"amount\": 0.1}}]}");
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, 1, 176, new Random(5));
        FxSimulation sim = new FxSimulation(def, cut, 1, 1, new FakeHost(), new Random(5), 1, false, 1);
        double before = sim.pieces().stream().mapToDouble(p -> Math.hypot(p.x, p.y)).average().orElse(0);
        for (int i = 0; i < 30; i++) {
            sim.tick();
        }
        double after = sim.pieces().stream().mapToDouble(p -> Math.hypot(p.x, p.y)).average().orElse(0);
        assertTrue(after < before * 0.7, "pulled towards the centre: " + before + " → " + after);
    }

    @Test
    void reduceMotionAndParticleScale() {
        String json = "{\"schema\": 2, \"id\": \"t\", \"duration\": 40, \"trigger\": {\"type\": \"none\"},"
                + "\"layers\": [{\"module\": \"launch\", \"params\": {\"speed\": 0.1}}],"
                + "\"emitters\": [{\"particle\": \"minecraft:flame\", \"rate\": 2}]}";
        FxDef def = parse(json);
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, 1, 176, new Random(9));
        FakeHost full = new FakeHost();
        FakeHost calm = new FakeHost();
        FxSimulation a = new FxSimulation(def, cut, 1, 1, full, new Random(9), 1, false, 1);
        FxSimulation b = new FxSimulation(def, cut, 1, 1, calm, new Random(9), 0.65f, true, 0.25f);
        for (int i = 0; i < 5; i++) {
            a.tick();
            b.tick();
        }
        double speedA = a.pieces().stream().mapToDouble(p -> Math.hypot(p.vx, p.vy)).average().orElse(0);
        double speedB = b.pieces().stream().mapToDouble(p -> Math.hypot(p.vx, p.vy)).average().orElse(0);
        assertEquals(speedA * 0.5, speedB, speedA * 0.05, "reduce motion halves the pushes");
        assertTrue(b.duration < a.duration, "and shortens the effect");
        assertTrue(calm.particles < full.particles, "fewer particles with a lower particle scale");
    }

    @Test
    void spriteCapsAndHurry() {
        String json = "{\"schema\": 2, \"id\": \"t\", \"duration\": 100, \"layers\": [],"
                + "\"emitters\": [{\"sprite\": \"s\", \"rate\": 50, \"life\": 200, \"max\": 400}]}";
        FxDef def = parse(json);
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, 1, 176, new Random(2));
        List<FxSimulation> sims = new ArrayList<>();
        for (int s = 0; s < 5; s++) {
            FxSimulation sim = new FxSimulation(def, cut, 1, 1, new FakeHost(), new Random(s), 1, false, 1);
            for (int i = 0; i < 20; i++) {
                sim.tick();
            }
            assertTrue(sim.sprites().size() <= FxLimits.MAX_SPRITES_PER_WAVE);
            sims.add(sim);
        }
        assertTrue(FxSimulation.liveSpritesTotal() <= FxLimits.MAX_SPRITES_TOTAL, "global cap");
        FxSimulation first = sims.getFirst();
        first.hurry(3);
        for (int i = 0; i < 5; i++) {
            first.tick();
        }
        assertTrue(first.sprites().isEmpty(), "hurried: no new particles and the old ones gone");
        for (FxSimulation sim : sims) {
            sim.dispose();
        }
        assertEquals(0, FxSimulation.liveSpritesTotal());
    }

    @Test
    void edgeModifierMovesEverythingOut() {
        FxDef def = parse("{\"schema\": 2, \"id\": \"t\", \"duration\": 40, \"layers\": [{\"module\": \"stay\"}],"
                + "\"modifiers\": {\"edge\": {\"direction\": \"right\", \"exit\": true, \"exitStart\": 0.5}}}");
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, 1, 176, new Random(4));
        FxSimulation sim = new FxSimulation(def, cut, 1, 1, new FakeHost(), new Random(4), 1, false, 1);
        for (int i = 0; i < 20; i++) {
            sim.tick();
        }
        assertEquals(0f, sim.edgeOffset(1)[0], 1e-4f, "not before exitStart");
        for (int i = 0; i < 20; i++) {
            sim.tick();
        }
        assertTrue(sim.edgeOffset(1)[0] > 1f, "off to the right at the end");
    }

    @Test
    void followPathSpline() {
        float[] end = FxSimulation.spline(new float[][]{{1, 0, 0}, {1, 1, 0}}, 1f);
        assertEquals(1f, end[0], 1e-4f);
        assertEquals(1f, end[1], 1e-4f);
        float[] start = FxSimulation.spline(new float[][]{{1, 0, 0}}, 0f);
        assertEquals(0f, start[0], 1e-4f);
    }
}
