package net.shattergui.fx;

import net.shattergui.params.BundledParams;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frames of the shipped effects, drawn here the way the game and the preview draw them: the interface one block in
 * front of the camera, every corner turned in 3D ({@link FxSimulation#rotate}) and divided by its depth. Every pair of
 * effects must look different at 20, 40, 60 and 80 % of their time: by the opacity mask (its bounding box and how much
 * of it differs) or by the picture itself (a pattern of the interface tinted by the effect's colour). Two effects that
 * nearly match in all four moments ({@link #SAME}) fail the test.
 *
 * <p>Set the environment variable {@code SHATTER_FRAMES_DIR} to a folder of effect files to get the same table for
 * them too (with the old preview's projection: a tumble squeezed the width by its cosine, perspective from the centre).
 */
class EffectFramesTest {
    static final int W = 120;
    static final int H = 80;
    /** Plane area shown: x in ±0.9, y in ±0.6 blocks at the interface's depth. */
    private static final float VIEW_X = 0.9f;
    private static final float VIEW_Y = 0.6f;
    private static final float WIDTH = 0.9f;
    private static final float HEIGHT = 0.85f;
    static final float[] MOMENTS = {0.2f, 0.4f, 0.6f, 0.8f};

    /** A frame: opacity 0..1 and brightness of the tinted pattern 0..1 per pixel. */
    record Frame(float[] alpha, float[] picture) {
        int[] box() {
            int x0 = W;
            int y0 = H;
            int x1 = -1;
            int y1 = -1;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (alpha[y * W + x] > 0.5f) {
                        x0 = Math.min(x0, x);
                        y0 = Math.min(y0, y);
                        x1 = Math.max(x1, x + 1);
                        y1 = Math.max(y1, y + 1);
                    }
                }
            }
            return x1 < 0 ? new int[]{0, 0, 0, 0} : new int[]{x0, y0, x1, y1};
        }
    }

    /**
     * How two effects differ at one moment, each 0..1: the masks (1 − intersection over union), the pictures (mean
     * difference over the union), and the bounding boxes (largest change of an edge or of the size, over the larger
     * box). Two empty frames do not differ.
     */
    record Difference(float mask, float picture, float box) {
        float score() {
            return Math.max(mask, Math.max(picture, box));
        }
    }

    /**
     * Below this on average over the moments two effects count as nearly the same. The closest shipped pair is at
     * about 0.4; a renamed copy is at 0.
     */
    static final float SAME = 0.3f;

    private static String json(String id) throws IOException {
        try (InputStream in = EffectFramesTest.class.getResourceAsStream("/assets/shatter_gui/effects/" + id + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Frame[] frames(FxDef def, boolean legacy) {
        Random random = new Random(7);
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, WIDTH / HEIGHT, 176, random);
        FxSimulation sim = new FxSimulation(def, cut, WIDTH, HEIGHT, new FxEngineTest.FakeHost(), random, 1f, false, 1f);
        Frame[] out = new Frame[MOMENTS.length];
        for (int m = 0; m < MOMENTS.length; m++) {
            int tick = Math.max(1, Math.round(sim.duration * MOMENTS[m]));
            while (sim.time() < tick) {
                sim.tick();
            }
            out[m] = draw(sim, legacy);
        }
        return out;
    }

    private static Frame draw(FxSimulation sim, boolean legacy) {
        float[] alpha = new float[W * H];
        float[] picture = new float[W * H];
        float[] edge = sim.edgeOffset(1f);
        for (FxSimulation.Piece p : sim.pieces()) {
            int color = sim.color(p, 1f);
            float a = (color >>> 24) / 255f;
            float scale = sim.scale(p, 1f);
            if (a <= 0.004f || scale <= 0.001f) {
                continue;
            }
            float shade = ((color >> 16 & 0xFF) + (color >> 8 & 0xFF) + (color & 0xFF)) / (3 * 255f);
            float[] t = p.triangles;
            for (int i = 0; i < t.length; i += 6) {
                float[] sx = new float[3];
                float[] sy = new float[3];
                for (int c = 0; c < 3; c++) {
                    float lx = (t[i + c * 2] - p.u) * WIDTH * scale;
                    float ly = -(t[i + c * 2 + 1] - p.v) * HEIGHT * scale;
                    float x;
                    float y;
                    float z;
                    if (legacy) {
                        // The old preview: the tumble only squeezed the width, depth from the piece centre.
                        float fx = (float) Math.cos(p.tilt);
                        float cos = (float) Math.cos(p.angle);
                        float sin = (float) Math.sin(p.angle);
                        x = (lx * fx) * cos - ly * sin;
                        y = (lx * fx) * sin + ly * cos;
                        z = 0;
                    } else {
                        float[] v = FxSimulation.rotate(lx, ly, 0, p.tiltAxis, p.tilt, p.angle);
                        x = v[0];
                        y = v[1];
                        z = v[2];
                    }
                    float persp = 1f / Math.max(0.3f, 1f - (p.z + z));
                    float px = (p.x + edge[0] + x) * persp;
                    float py = (p.y + edge[1] + y) * persp;
                    sx[c] = (px / VIEW_X * 0.5f + 0.5f) * W;
                    sy[c] = (0.5f - py / VIEW_Y * 0.5f) * H;
                }
                fill(sx, sy, t, i, a, shade, alpha, picture);
            }
        }
        return new Frame(alpha, picture);
    }

    /** Rasterises one triangle: opacity over what is there, and a checker pattern of the interface's uv. */
    private static void fill(float[] sx, float[] sy, float[] uv, int i, float a, float shade, float[] alpha,
                             float[] picture) {
        int x0 = Math.max(0, (int) Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
        int x1 = Math.min(W - 1, (int) Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
        int y1 = Math.min(H - 1, (int) Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        float d = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2]);
        if (Math.abs(d) < 1e-6f) {
            return;
        }
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float w0 = ((sy[1] - sy[2]) * (px - sx[2]) + (sx[2] - sx[1]) * (py - sy[2])) / d;
                float w1 = ((sy[2] - sy[0]) * (px - sx[2]) + (sx[0] - sx[2]) * (py - sy[2])) / d;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) {
                    continue;
                }
                float u = w0 * uv[i] + w1 * uv[i + 2] + w2 * uv[i + 4];
                float v = w0 * uv[i + 1] + w1 * uv[i + 3] + w2 * uv[i + 5];
                // A pattern that tells the halves and quarters of the interface apart (so a mirror image differs).
                float pattern = 0.25f + 0.5f * u * 0.6f + 0.25f * v + (((int) (u * 6) + (int) (v * 5)) % 2) * 0.15f;
                int k = y * W + x;
                picture[k] = picture[k] * (1 - a) + pattern * shade * a;
                alpha[k] = alpha[k] + a * (1 - alpha[k]);
            }
        }
    }

    /** Mean difference over the moments: how far apart two effects look as a whole. */
    static float distance(Frame[] a, Frame[] b) {
        float sum = 0;
        for (int m = 0; m < MOMENTS.length; m++) {
            sum += compare(a[m], b[m]).score();
        }
        return sum / MOMENTS.length;
    }

    static Difference compare(Frame a, Frame b) {
        float inter = 0;
        float union = 0;
        float picture = 0;
        for (int k = 0; k < W * H; k++) {
            inter += Math.min(a.alpha[k], b.alpha[k]);
            union += Math.max(a.alpha[k], b.alpha[k]);
            picture += Math.abs(a.picture[k] - b.picture[k]);
        }
        if (union < 1f) {
            return new Difference(0, 0, 0);
        }
        int[] ba = a.box();
        int[] bb = b.box();
        float size = Math.max(1, Math.max(Math.max(ba[2] - ba[0], bb[2] - bb[0]), Math.max(ba[3] - ba[1], bb[3] - bb[1])));
        float edges = 0;
        for (int i = 0; i < 4; i++) {
            edges = Math.max(edges, Math.abs(ba[i] - bb[i]));
        }
        edges = Math.max(edges, Math.abs((ba[2] - ba[0]) - (bb[2] - bb[0])));
        edges = Math.max(edges, Math.abs((ba[3] - ba[1]) - (bb[3] - bb[1])));
        return new Difference(1 - inter / union, Math.min(1f, picture / union / 0.5f), Math.min(1f, edges / size));
    }

    /** For each effect: its frames' box (w×h, % of the view) and coverage, and its closest other effect. */
    static String table(Map<String, Frame[]> frames, List<String> same) {
        StringBuilder out = new StringBuilder();
        List<String> pairs = new ArrayList<>();
        out.append(String.format(Locale.ROOT, "%-10s | %-35s | %-22s%n", "effect", "box w×h % / coverage % at 20/40/60/80 %",
                "closest (score)"));
        List<String> ids = new ArrayList<>(frames.keySet());
        for (String a : ids) {
            StringBuilder moments = new StringBuilder();
            for (Frame f : frames.get(a)) {
                int[] box = f.box();
                float cover = 0;
                for (float v : f.alpha) {
                    cover += v;
                }
                moments.append(String.format(Locale.ROOT, "%d×%d/%d ", Math.round((box[2] - box[0]) * 100f / W),
                        Math.round((box[3] - box[1]) * 100f / H), Math.round(cover * 100 / (W * H))));
            }
            String closest = "";
            float best = Float.MAX_VALUE;
            for (String b : ids) {
                if (b.equals(a)) {
                    continue;
                }
                float score = distance(frames.get(a), frames.get(b));
                if (a.compareTo(b) < 0) {
                    pairs.add(String.format(Locale.ROOT, "%.3f %s–%s", score, a, b));
                }
                if (score < SAME && a.compareTo(b) < 0) {
                    same.add(a + " ≈ " + b);
                }
                if (score < best) {
                    best = score;
                    closest = b;
                }
            }
            out.append(String.format(Locale.ROOT, "%-10s | %-35s | %s (%.3f)%n", a, moments.toString().trim(), closest, best));
        }
        pairs.sort(null);
        out.append("closest pairs: ").append(pairs.subList(0, Math.min(12, pairs.size()))).append('\n');
        return out.toString();
    }

    @Test
    void everyPairOfShippedEffectsLooksDifferent() throws IOException {
        Map<String, Frame[]> frames = new LinkedHashMap<>();
        for (String id : BundledParams.ids()) {
            FxParser.Result r = FxParser.parse(json(id));
            assertTrue(r.ok(), id + ": " + r.problems().all());
            frames.put(id, frames(r.def(), false));
        }
        List<String> same = new ArrayList<>();
        String table = table(frames, same);
        System.out.println("[EffectFrames] shipped effects:\n" + table);
        String extra = System.getenv("SHATTER_FRAMES_DIR");
        if (extra != null) {
            Map<String, Frame[]> old = new LinkedHashMap<>();
            try (var files = Files.list(Path.of(extra))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    FxParser.Result r = FxParser.parse(Files.readString(f));
                    if (r.ok()) {
                        old.put(r.def().id, frames(r.def(), true));
                    }
                }
            }
            List<String> oldSame = new ArrayList<>();
            System.out.println("[EffectFrames] " + extra + " (old preview projection):\n" + table(old, oldSame)
                    + "nearly the same: " + oldSame);
        }
        assertTrue(same.isEmpty(), "effects that nearly match at 20/40/60/80 %: " + same + "\n" + table);
    }

    /**
     * The largest change of the shape (opaque where alpha > 0.5) against the whole interface in the first half of the
     * time: 1 − intersection over union. Fading alone leaves it near 0 until the very end.
     */
    static float motionBeforeFade(FxDef def, boolean legacy) {
        Random random = new Random(7);
        List<Fracture.Piece> cut = Fracture.cut(def.fracture, WIDTH / HEIGHT, 176, random);
        FxSimulation sim = new FxSimulation(def, cut, WIDTH, HEIGHT, new FxEngineTest.FakeHost(), random, 1f, false, 1f);
        Frame whole = draw(sim, legacy);
        float most = 0;
        int half = Math.max(1, Math.round(sim.duration * 0.5f));
        while (sim.time() < half) {
            sim.tick();
            Frame now = draw(sim, legacy);
            float inter = 0;
            float union = 0;
            for (int k = 0; k < W * H; k++) {
                boolean a = whole.alpha[k] > 0.5f;
                boolean b = now.alpha[k] > 0.5f;
                inter += a && b ? 1 : 0;
                union += a || b ? 1 : 0;
            }
            most = Math.max(most, union == 0 ? 0 : 1 - inter / union);
        }
        return most;
    }

    /** Least change of shape at half time for a quick effect. */
    static final float MOTION = 0.15f;

    @Test
    void quickEffectsMoveBeforeTheyFade() throws IOException {
        StringBuilder table = new StringBuilder();
        List<String> still = new ArrayList<>();
        for (String id : BundledParams.ids()) {
            if (!BundledParams.quick(id)) {
                continue;
            }
            float motion = motionBeforeFade(FxParser.parse(json(id)).def(), false);
            table.append(String.format(Locale.ROOT, "%s %.3f, ", id, motion));
            if (motion < MOTION) {
                still.add(id);
            }
        }
        String extra = System.getenv("SHATTER_FRAMES_DIR");
        if (extra != null) {
            table.append(" | old: ");
            for (String id : List.of("flip", "recede", "door")) {
                FxParser.Result r = FxParser.parse(Files.readString(Path.of(extra, id + ".json")));
                table.append(String.format(Locale.ROOT, "%s %.3f, ", id, motionBeforeFade(r.def(), true)));
            }
        }
        System.out.println("[EffectFrames] largest change of shape in the first half: " + table);
        assertTrue(still.isEmpty(), "quick effects that barely move before fading (only a fade): " + still + " " + table);
    }

    @Test
    void cardFlipRecedeAndDoorAreWhatTheySay() throws IOException {
        Map<String, Frame[]> f = new LinkedHashMap<>();
        for (String id : List.of("flip", "recede", "door")) {
            f.put(id, frames(FxParser.parse(json(id)).def(), false));
        }
        int[] full = f.get("flip")[0].box();
        // Card flip: narrows about the vertical centre line, the height stays (perspective makes the near edge taller).
        int[] flip = f.get("flip")[3].box();
        assertTrue(flip[2] - flip[0] < (full[2] - full[0]) * 0.7f, "flip narrows: " + (flip[2] - flip[0]));
        // Turning about its centre line: the near half grows in perspective, but the middle stays near the middle.
        assertTrue(Math.abs((flip[0] + flip[2]) - (full[0] + full[2])) / 2f <= (full[2] - full[0]) * 0.15f,
                "flip stays centred: " + (flip[0] + flip[2]) / 2f + " vs " + (full[0] + full[2]) / 2f);
        assertTrue(flip[3] - flip[1] >= (full[3] - full[1]) * 0.9f, "flip keeps its height");
        // Recede: small (about a twentieth across) and darker.
        int[] recede = f.get("recede")[3].box();
        assertTrue(recede[2] - recede[0] < (full[2] - full[0]) * 0.3f, "recede is far away: " + (recede[2] - recede[0]));
        // Door: the hinge (left edge) stays, the free edge swings in, perspective shortens the far side.
        int[] door = f.get("door")[2].box();
        assertTrue(Math.abs(door[0] - full[0]) <= 3, "door keeps its hinges at the left edge: " + door[0] + " vs " + full[0]);
        assertTrue(door[2] - door[0] < (full[2] - full[0]) * 0.75f, "door swings away: " + (door[2] - door[0]));
    }
}
