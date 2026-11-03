package net.shattergui.fx;

import net.shattergui.params.BundledParams;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The effects shipped with the mod: they read cleanly, match their settings, and stay gentle enough. */
class BundledEffectsTest {
    private static String json(String id) throws IOException {
        try (InputStream in = BundledEffectsTest.class.getResourceAsStream("/assets/shatter_gui/effects/" + id + ".json")) {
            assertNotNull(in, id + ".json is in the mod");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static FxDef parse(String id) throws IOException {
        FxParser.Result r = FxParser.parse(json(id));
        assertTrue(r.ok(), id + ": " + r.problems().all());
        assertTrue(r.problems().all().isEmpty(), id + " has no warnings: " + r.problems().all());
        return r.def();
    }

    private static FxSimulation sim(FxDef def, boolean reduceMotion) {
        Random random = new Random(7);
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, 1.06f, 176, random);
        return new FxSimulation(def, cut, 0.9f, 0.85f, new FxEngineTest.FakeHost(), random, reduceMotion ? 0.65f : 1f,
                reduceMotion, 1f);
    }

    @Test
    void allReadCleanlyAndMatchTheirSettings() throws IOException {
        assertEquals(27, BundledParams.ids().size());
        for (String id : BundledParams.ids()) {
            FxDef def = parse(id);
            assertEquals(id, def.id);
            assertEquals(BundledParams.fileDuration(id), def.duration, id + ": duration setting default = file");
            assertTrue(def.openable, id + " can open screens");
            assertTrue(def.textures.isEmpty(), id + " needs no textures");
            assertTrue(def.description.length() > 10, id + " has a description");
        }
    }

    @Test
    void aQuickerOpeningEndsGatheredToo() throws IOException {
        for (String id : BundledParams.ids()) {
            FxSimulation backward = sim(parse(id), false);
            backward.reverse(6);
            // 6 ticks of motion and one at rest, gathered, before the real screen takes over.
            assertEquals(7, backward.length(), id);
            for (int t = 0; t < 7; t++) {
                assertTrue(backward.alive(), id + " still gathering at " + t);
                backward.tick();
            }
            backward.tick();
            assertTrue(!backward.alive() || !backward.sprites().isEmpty(), id + " over after its 7 ticks");
            for (FxSimulation.Piece p : backward.pieces()) {
                assertEquals((p.u - 0.5f) * 0.9f, p.x, 1e-3f, id + " piece back in x");
                assertEquals((0.5f - p.v) * 0.85f, p.y, 1e-3f, id + " piece back in y");
            }
        }
    }

    @Test
    void runForwardAndBackwardWithFiniteNumbers() throws IOException {
        for (String id : BundledParams.ids()) {
            FxDef def = parse(id);
            for (boolean reduce : new boolean[]{false, true}) {
                FxSimulation forward = sim(def.forReducedMotion(reduce), reduce);
                FxSimulation backward = sim(def.forReducedMotion(reduce), reduce);
                backward.reverse();
                for (int t = 0; t <= forward.duration + 2; t++) {
                    forward.tick();
                    backward.tick();
                    for (FxSimulation.Piece p : backward.pieces()) {
                        assertTrue(Float.isFinite(p.x) && Float.isFinite(p.y) && Float.isFinite(p.z), id + " finite");
                        assertTrue(!backward.gone(p), id + ": no piece is removed while gathering");
                    }
                }
                assertTrue(!forward.alive() || !forward.sprites().isEmpty(), id + " ends");
                // Gathered: every piece is back where it was cut, whole and opaque.
                for (FxSimulation.Piece p : backward.pieces()) {
                    float u = (p.u - 0.5f) * 0.9f;
                    float v = (0.5f - p.v) * 0.85f;
                    assertEquals(u, p.x, 1e-3f, id + " piece back in x");
                    assertEquals(v, p.y, 1e-3f, id + " piece back in y");
                    assertEquals(1f, p.scale, 1e-3f, id + " full size");
                }
            }
        }
    }

    /** At most three sharp flashes per second, per piece and over the whole interface. */
    @Test
    void flashesAtMostThreePerSecond() throws IOException {
        for (String id : BundledParams.ids()) {
            FxDef def = parse(id);
            FxSimulation s = sim(def, false);
            int[] perPiece = new int[s.pieces().size()];
            boolean[] high = new boolean[s.pieces().size()];
            int screenFlashes = 0;
            boolean screenHigh = false;
            for (int t = 0; t <= s.duration; t++) {
                s.tick();
                float sum = 0;
                for (FxSimulation.Piece p : s.pieces()) {
                    float e = s.emissive(p, 1f);
                    sum += e;
                    boolean h = e > 0.6f;
                    if (h && !high[p.index]) {
                        perPiece[p.index]++;
                    }
                    high[p.index] = h;
                }
                boolean h = sum / Math.max(1, s.pieces().size()) > 0.5f;
                if (h && !screenHigh) {
                    screenFlashes++;
                }
                screenHigh = h;
            }
            float seconds = Math.max(1f, s.duration / 20f);
            for (int n : perPiece) {
                assertTrue(n / seconds <= 3f, id + ": " + n + " flashes of one piece in " + seconds + " s");
            }
            assertTrue(screenFlashes / seconds <= 3f, id + ": " + screenFlashes + " screen flashes in " + seconds + " s");
        }
    }

    /** With reduce motion nothing flickers: a piece only fades out, never back in. */
    @Test
    void reducedMotionDoesNotFlicker() throws IOException {
        for (String id : BundledParams.ids()) {
            FxDef def = parse(id).forReducedMotion(true);
            FxSimulation s = sim(def, true);
            float[] last = new float[s.pieces().size()];
            java.util.Arrays.fill(last, 1f);
            for (int t = 0; t <= s.duration; t++) {
                s.tick();
                for (FxSimulation.Piece p : s.pieces()) {
                    float a = (s.color(p, 1f) >>> 24) / 255f;
                    assertTrue(a <= last[p.index] + 0.02f, id + ": piece " + p.index + " comes back (" + last[p.index] + " → " + a + ")");
                    last[p.index] = a;
                }
            }
        }
    }

    /**
     * The quick everyday effects stay light: 0.3–0.9 s, at most 60 pieces and 40 particles, no smoke, no glow (so no
     * flash), a calmer version or the engine's own calming for reduce motion, and they open by gathering.
     */
    @Test
    void quickEffectsStayLight() throws IOException {
        List<String> smoke = List.of("minecraft:smoke", "minecraft:large_smoke", "minecraft:campfire_cosy_smoke",
                "minecraft:campfire_signal_smoke", "minecraft:poof", "minecraft:cloud", "minecraft:white_smoke");
        int quick = 0;
        for (String id : BundledParams.ids()) {
            if (!BundledParams.quick(id)) {
                continue;
            }
            quick++;
            FxDef def = parse(id);
            assertTrue(def.duration >= 6 && def.duration <= 18, id + ": " + def.duration + " ticks, not 0.3–0.9 s");
            FxSimulation s = sim(def, false);
            assertTrue(s.pieces().size() <= 60, id + ": " + s.pieces().size() + " pieces");
            float particles = 0;
            for (FxDef.Emitter e : def.emitters) {
                assertTrue(e.particle() == null || !smoke.contains(e.particle()), id + ": no smoke, got " + e.particle());
                particles += e.burst() + e.rate().at(0.5f, 0.5f) * def.duration * (e.to() - e.from());
            }
            assertTrue(particles <= 40, id + ": about " + particles + " particles");
            for (int t = 0; t <= s.duration; t++) {
                s.tick();
                for (FxSimulation.Piece p : s.pieces()) {
                    assertTrue(s.emissive(p, 1f) < 0.05f, id + ": no glow, so no flash");
                }
            }
        }
        assertTrue(quick >= 12, "at least 12 quick effects, got " + quick);
    }

    @Test
    void glitchHasACalmerVersion() throws IOException {
        FxDef def = parse("glitch");
        assertTrue(def.hasReducedVariant());
        assertTrue(def.forReducedMotion(true) != def);
        assertTrue(def.forReducedMotion(false) == def);
    }
}
