package net.shattergui.fx;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The collide module without the game: a host with a flat floor and a budget of checks per tick. */
class FxCollideTest {
    static final float FLOOR = -1.2f;

    /** A floor at y = FLOOR (a "block" below it); {@code budget} checks per tick, counted like the world's. */
    static final class FloorHost implements FxSimulation.Host {
        int moves;
        int budget = Integer.MAX_VALUE;
        int left = Integer.MAX_VALUE;

        void newTick() {
            left = budget;
        }

        @Override
        public float[] point(FxDef.Point.Kind kind) {
            return null;
        }

        @Override
        public float sampleMask(String texture, float u, float v) {
            return Float.NaN;
        }

        @Override
        public float[] gravity() {
            return new float[]{0, -1, 0};
        }

        @Override
        public boolean hasSprite(String texture) {
            return false;
        }

        @Override
        public void spawnParticle(FxDef.Emitter emitter, float x, float y, float z, float vx, float vy, float vz, float size) {
        }

        @Override
        public void playSound(String sound, float volume, float pitch) {
        }

        @Override
        public int move(float[] s, float radius, float bounce, float friction) {
            if (left < 3) {
                return SKIPPED;
            }
            left -= 3;
            moves++;
            float ny = s[1] + s[4];
            s[0] += s[3];
            s[2] += s[5];
            if (ny - radius >= FLOOR) {
                s[1] = ny;
                return MOVED;
            }
            // Like BlockCollision: settles on the face it hit.
            s[1] = FLOOR + radius + 0.001f;
            boolean landed = s[4] < 0;
            s[4] = -s[4] * bounce;
            if (landed) {
                s[3] *= 1 - friction;
                s[5] *= 1 - friction;
                if (Math.abs(s[4]) < 0.02f) {
                    s[4] = 0;
                }
                return LANDED;
            }
            return HIT;
        }

        @Override
        public int inside(float x, float y, float z) {
            if (left < 1) {
                return -1;
            }
            left--;
            return y < FLOOR ? 1 : 0;
        }
    }

    private static FxDef parse(String json) {
        FxParser.Result r = FxParser.parse(json);
        assertTrue(r.ok(), r.problems().all().toString());
        return r.def();
    }

    private static String effect(String collide, int count) {
        return "{\"schema\": 2, \"id\": \"fall\", \"duration\": 120, \"fracture\": {\"mode\": \"grid\", \"count\": " + count + "},"
                + " \"trigger\": {\"type\": \"none\", \"spread\": 0}, \"layers\": ["
                + "{\"module\": \"gravity\", \"params\": {\"strength\": 0.04}}" + collide + "]}";
    }

    private static FxSimulation sim(FxDef def, FloorHost host) {
        Random random = new Random(7);
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, 1.06f, 176, random);
        return new FxSimulation(def, cut, 0.9f, 0.85f, host, random, 1f, false, 1f);
    }

    private static void run(FxSimulation sim, FloorHost host, int ticks) {
        for (int i = 0; i < ticks; i++) {
            host.newTick();
            sim.tick();
        }
    }

    @Test
    void landsAndNeverSinksBelowTheFloor() {
        FloorHost host = new FloorHost();
        FxSimulation sim = sim(parse(effect(", {\"module\": \"collide\", \"params\": {\"bounce\": 0}}", 40)), host);
        for (int t = 0; t < 100; t++) {
            host.newTick();
            sim.tick();
            for (FxSimulation.Piece p : sim.pieces()) {
                assertTrue(p.y - p.radius >= FLOOR - 1e-4f, "piece " + p.index + " below the floor at tick " + t + ": " + p.y);
            }
        }
        for (FxSimulation.Piece p : sim.pieces()) {
            assertEquals(FLOOR, p.y - p.radius, 0.05f, "piece " + p.index + " lies on the floor");
            assertTrue(p.asleep, "piece " + p.index + " fell asleep");
        }
    }

    @Test
    void bouncesUpAndStillNeverSinks() {
        FloorHost host = new FloorHost();
        FxSimulation sim = sim(parse(effect(", {\"module\": \"collide\", \"params\": {\"bounce\": 0.8, \"friction\": 0}}", 12)), host);
        boolean wentUp = false;
        for (int t = 0; t < 110; t++) {
            host.newTick();
            sim.tick();
            for (FxSimulation.Piece p : sim.pieces()) {
                assertTrue(p.y - p.radius >= FLOOR - 1e-4f, "below the floor: " + p.y);
                wentUp |= p.vy > 0.05f;
            }
        }
        assertTrue(wentUp, "pieces bounced back up");
    }

    @Test
    void sleepingPiecesAreNotChecked() {
        FloorHost host = new FloorHost();
        FxSimulation sim = sim(parse(effect(", {\"module\": \"collide\", \"params\": {\"bounce\": 0}}", 20)), host);
        run(sim, host, 80);
        assertTrue(sim.pieces().stream().allMatch(p -> p.asleep), "all asleep");
        int before = host.moves;
        run(sim, host, 20);
        assertEquals(before, host.moves, "no checks for sleeping pieces");
    }

    @Test
    void withoutTheModulePiecesFallThroughAsBefore() {
        FloorHost host = new FloorHost();
        FxSimulation sim = sim(parse(effect("", 20)), host);
        run(sim, host, 80);
        assertEquals(0, host.moves);
        assertTrue(sim.pieces().stream().allMatch(p -> p.y < FLOOR - 1), "fell through");
    }

    @Test
    void overTheBudgetPiecesWaitInsteadOfPassingThrough() {
        FloorHost host = new FloorHost();
        host.budget = 9;
        FxSimulation sim = sim(parse(effect(", {\"module\": \"collide\"}", 40)), host);
        for (int t = 0; t < 100; t++) {
            host.newTick();
            sim.tick();
            assertTrue(host.left >= 0);
            for (FxSimulation.Piece p : sim.pieces()) {
                assertTrue(p.y - p.radius >= FLOOR - 1e-4f, "below the floor at tick " + t + ": y " + p.y + " r " + p.radius
                        + " py " + p.py + " vy " + p.vy + " asleep " + p.asleep + " clip " + p.clip + " scale " + p.scale);
            }
        }
    }

    @Test
    void collidingPiecesAreLimited() {
        FloorHost host = new FloorHost();
        FxSimulation sim = sim(parse("{\"schema\": 2, \"id\": \"many\", \"fracture\": {\"mode\": \"pixels\", \"size\": 2},"
                + " \"layers\": [{\"module\": \"collide\"}]}"), host);
        assertTrue(sim.pieces().size() > FxLimits.MAX_COLLIDING_PIECES, "enough pieces: " + sim.pieces().size());
        assertEquals(FxLimits.MAX_COLLIDING_PIECES, sim.collidingCount());
    }

    @Test
    void restFadeHidesPiecesThatStopped() {
        FloorHost host = new FloorHost();
        FxSimulation sim = sim(parse(effect(", {\"module\": \"collide\", \"params\": {\"bounce\": 0, \"rest\": \"fade\"}}", 20)), host);
        run(sim, host, 90);
        for (FxSimulation.Piece p : sim.pieces()) {
            assertTrue(p.asleep);
            assertEquals(0, sim.color(p, 1f) >>> 24, "faded");
        }
        FxSimulation lie = sim(parse(effect(", {\"module\": \"collide\", \"params\": {\"bounce\": 0}}", 20)), host);
        run(lie, host, 90);
        assertFalse(lie.pieces().stream().anyMatch(p -> (lie.color(p, 1f) >>> 24) == 0), "lying pieces still show");
    }

    @Test
    void parserReadsRestAndRejectsUnknown() {
        FxDef def = parse(effect(", {\"module\": \"collide\", \"params\": {\"rest\": \"fade\", \"bounce\": 5}}", 4));
        FxDef.Layer layer = def.layers.get(1);
        assertEquals("fade", layer.rest());
        assertEquals(1f, layer.value("bounce").at(0, 0), 1e-6f, "clamped");
        assertEquals("lie", parse(effect(", {\"module\": \"collide\"}", 4)).layers.get(1).rest());
    }
}
