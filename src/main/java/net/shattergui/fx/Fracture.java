package net.shattergui.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cuts the interface into pieces, in GUI fractions (u right, v down, both 0..1). A piece is one or more triangles that
 * move together (a Voronoi cell, a grid cell…). Modes: grid, voronoi, strips, radial, pixels. With a region, only that
 * rectangle is cut as asked; the rest becomes a few large pieces marked as outside.
 */
public final class Fracture {
    /** One piece: its centroid and triangles (6 floats each: u0 v0 u1 v1 u2 v2), and whether it lies in the region. */
    public record Piece(float cu, float cv, float[] triangles, boolean inRegion) {
        public int triangleCount() {
            return triangles.length / 6;
        }
    }

    private Fracture() {
    }

    /**
     * @param aspect      world width / height of the interface (cells are made round in world space)
     * @param guiPixelsW  the interface's width in GUI pixels (for pixel mode)
     */
    public static List<Piece> cut(FxDef.Fracture spec, float aspect, float guiPixelsW, Random random) {
        float[] r = spec.region() != null ? spec.region() : new float[]{0, 0, 1, 1};
        List<Piece> pieces = new ArrayList<>();
        switch (spec.mode()) {
            case "voronoi" -> voronoi(spec, r, aspect, random, pieces);
            case "strips" -> {
                int count = Math.max(1, spec.count());
                int segments = spec.segments();
                if (spec.vertical()) {
                    grid(r, count, segments, spec.jitter() * 0.6f, spec.jitter() * 0.25f, false, random, pieces);
                } else {
                    grid(r, segments, count, spec.jitter() * 0.25f, spec.jitter() * 0.6f, false, random, pieces);
                }
            }
            case "radial" -> radial(spec, r, aspect, random, pieces);
            case "pixels" -> {
                float size = spec.size();
                float regionW = (r[2] - r[0]) * guiPixelsW;
                float regionH = (r[3] - r[1]) * guiPixelsW / Math.max(0.01f, aspect);
                int cols = Math.max(1, Math.round(regionW / size));
                int rows = Math.max(1, Math.round(regionH / size));
                while (cols * rows > FxLimits.MAX_PIECES) {
                    cols = Math.max(1, cols * 9 / 10);
                    rows = Math.max(1, rows * 9 / 10);
                }
                grid(r, cols, rows, 0, 0, false, random, pieces);
            }
            default -> {
                int cols = spec.cols();
                int rows = spec.rows();
                if (cols <= 0 || rows <= 0) {
                    float regionAspect = aspect * (r[2] - r[0]) / (r[3] - r[1]);
                    cols = Math.max(1, Math.round((float) Math.sqrt(spec.count() * regionAspect)));
                    rows = Math.max(1, Math.round((float) spec.count() / cols));
                }
                grid(r, cols, rows, spec.jitter(), spec.jitter(), spec.split(), random, pieces);
            }
        }
        if (spec.region() != null) {
            outside(r, pieces);
        }
        return limit(pieces);
    }

    /** Keeps whole pieces up to the triangle and piece limits. */
    private static List<Piece> limit(List<Piece> pieces) {
        List<Piece> out = new ArrayList<>(pieces.size());
        int triangles = 0;
        for (Piece piece : pieces) {
            if (out.size() >= FxLimits.MAX_PIECES || triangles + piece.triangleCount() > FxLimits.MAX_TRIANGLES) {
                break;
            }
            out.add(piece);
            triangles += piece.triangleCount();
        }
        return out;
    }

    // --- grid, strips, pixels ---

    private static void grid(float[] r, int cols, int rows, float jitterU, float jitterV, boolean split, Random random,
                             List<Piece> out) {
        float cw = (r[2] - r[0]) / cols;
        float ch = (r[3] - r[1]) / rows;
        float[][] gu = new float[cols + 1][rows + 1];
        float[][] gv = new float[cols + 1][rows + 1];
        for (int i = 0; i <= cols; i++) {
            for (int j = 0; j <= rows; j++) {
                float u = r[0] + i * cw;
                float v = r[1] + j * ch;
                if (i > 0 && i < cols) {
                    u += (random.nextFloat() * 2 - 1) * jitterU * 0.5f * cw;
                }
                if (j > 0 && j < rows) {
                    v += (random.nextFloat() * 2 - 1) * jitterV * 0.5f * ch;
                }
                gu[i][j] = u;
                gv[i][j] = v;
            }
        }
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                float[] a = {gu[i][j], gv[i][j]};
                float[] b = {gu[i + 1][j], gv[i + 1][j]};
                float[] c = {gu[i + 1][j + 1], gv[i + 1][j + 1]};
                float[] d = {gu[i][j + 1], gv[i][j + 1]};
                boolean flip = random.nextBoolean();
                float[] t1 = flip ? tri(a, b, c) : tri(a, b, d);
                float[] t2 = flip ? tri(a, c, d) : tri(b, c, d);
                if (split) {
                    out.add(piece(t1, true));
                    out.add(piece(t2, true));
                } else {
                    out.add(piece(concat(t1, t2), true));
                }
            }
        }
    }

    // --- voronoi ---

    private static void voronoi(FxDef.Fracture spec, float[] r, float aspect, Random random, List<Piece> out) {
        float regionAspect = aspect * (r[2] - r[0]) / (r[3] - r[1]);
        int gc = Math.max(1, Math.round((float) Math.sqrt(spec.count() * regionAspect)));
        int gr = Math.max(1, Math.round((float) spec.count() / gc));
        // Seeds in isotropic space: x = u·aspect, y = v.
        List<float[]> seeds = new ArrayList<>();
        for (int i = 0; i < gc; i++) {
            for (int j = 0; j < gr; j++) {
                float u = r[0] + (r[2] - r[0]) * (i + 0.5f + (random.nextFloat() - 0.5f) * spec.jitter()) / gc;
                float v = r[1] + (r[3] - r[1]) * (j + 0.5f + (random.nextFloat() - 0.5f) * spec.jitter()) / gr;
                seeds.add(new float[]{u * aspect, v});
            }
        }
        List<float[]> box = List.of(new float[]{r[0] * aspect, r[1]}, new float[]{r[2] * aspect, r[1]},
                new float[]{r[2] * aspect, r[3]}, new float[]{r[0] * aspect, r[3]});
        for (float[] seed : seeds) {
            List<float[]> cell = box;
            for (float[] other : seeds) {
                if (other == seed) {
                    continue;
                }
                // Keep the side closer to the seed: (p - mid) · (other - seed) <= 0.
                float nx = other[0] - seed[0];
                float ny = other[1] - seed[1];
                float mx = (other[0] + seed[0]) / 2;
                float my = (other[1] + seed[1]) / 2;
                cell = clip(cell, nx, ny, -(nx * mx + ny * my));
                if (cell.size() < 3) {
                    break;
                }
            }
            if (cell.size() >= 3) {
                out.add(polygon(cell, aspect, true));
            }
        }
    }

    /** Sutherland–Hodgman: keeps the part of the polygon where a·x + b·y + c <= 0. */
    static List<float[]> clip(List<float[]> poly, float a, float b, float c) {
        List<float[]> out = new ArrayList<>(poly.size() + 2);
        for (int i = 0; i < poly.size(); i++) {
            float[] p = poly.get(i);
            float[] q = poly.get((i + 1) % poly.size());
            float dp = a * p[0] + b * p[1] + c;
            float dq = a * q[0] + b * q[1] + c;
            if (dp <= 0) {
                out.add(p);
            }
            if ((dp <= 0) != (dq <= 0)) {
                float t = dp / (dp - dq);
                out.add(new float[]{p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t});
            }
        }
        return out;
    }

    /** A polygon (isotropic coordinates) as a piece: a triangle fan, back in GUI fractions. */
    private static Piece polygon(List<float[]> poly, float aspect, boolean inRegion) {
        float[] tris = new float[(poly.size() - 2) * 6];
        int k = 0;
        float[] p0 = poly.get(0);
        for (int i = 1; i < poly.size() - 1; i++) {
            float[] p1 = poly.get(i);
            float[] p2 = poly.get(i + 1);
            tris[k++] = p0[0] / aspect;
            tris[k++] = p0[1];
            tris[k++] = p1[0] / aspect;
            tris[k++] = p1[1];
            tris[k++] = p2[0] / aspect;
            tris[k++] = p2[1];
        }
        return piece(tris, inRegion);
    }

    // --- radial ---

    private static void radial(FxDef.Fracture spec, float[] r, float aspect, Random random, List<Piece> out) {
        float cu = spec.center().kind() == FxDef.Point.Kind.FIXED ? spec.center().u() : 0.5f;
        float cv = spec.center().kind() == FxDef.Point.Kind.FIXED ? spec.center().v() : 0.5f;
        int rings = spec.rings();
        int sectors = Math.max(3, spec.count());
        while (sectors * rings > FxLimits.MAX_PIECES && sectors > 3) {
            sectors--;
        }
        float cx = cu * aspect;
        float cy = cv;
        float maxR = 0;
        float[][] corners = {{r[0], r[1]}, {r[2], r[1]}, {r[2], r[3]}, {r[0], r[3]}};
        for (float[] corner : corners) {
            maxR = Math.max(maxR, (float) Math.hypot(corner[0] * aspect - cx, corner[1] - cy));
        }
        maxR *= 1.02f;
        float[][][] vertex = new float[rings + 1][sectors][];
        for (int k = 1; k <= rings; k++) {
            for (int j = 0; j < sectors; j++) {
                float radius = maxR * k / rings;
                if (k < rings) {
                    radius += (random.nextFloat() - 0.5f) * spec.jitter() * 0.8f * maxR / rings;
                }
                double angle = Math.PI * 2 * (j + (random.nextFloat() - 0.5f) * spec.jitter() * 0.6f) / sectors;
                vertex[k][j] = new float[]{cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius};
            }
        }
        List<float[]> rect = List.of(new float[]{r[0] * aspect, r[1]}, new float[]{r[2] * aspect, r[1]},
                new float[]{r[2] * aspect, r[3]}, new float[]{r[0] * aspect, r[3]});
        for (int k = 0; k < rings; k++) {
            for (int j = 0; j < sectors; j++) {
                int n = (j + 1) % sectors;
                List<float[]> cell = k == 0
                        ? List.of(new float[]{cx, cy}, vertex[1][j], vertex[1][n])
                        : List.of(vertex[k][j], vertex[k + 1][j], vertex[k + 1][n], vertex[k][n]);
                // Clip to the region rectangle (four half-planes).
                List<float[]> clipped = clipToRect(cell, rect);
                if (clipped.size() >= 3) {
                    out.add(polygon(clipped, aspect, true));
                }
            }
        }
    }

    private static List<float[]> clipToRect(List<float[]> poly, List<float[]> rect) {
        float x0 = rect.get(0)[0];
        float y0 = rect.get(0)[1];
        float x1 = rect.get(2)[0];
        float y1 = rect.get(2)[1];
        List<float[]> p = clip(poly, -1, 0, x0);
        p = p.size() < 3 ? p : clip(p, 1, 0, -x1);
        p = p.size() < 3 ? p : clip(p, 0, -1, y0);
        p = p.size() < 3 ? p : clip(p, 0, 1, -y1);
        return p;
    }

    // --- outside the region ---

    private static void outside(float[] r, List<Piece> out) {
        float[][] bands = {
                {0, 0, 1, r[1]}, {0, r[3], 1, 1}, {0, r[1], r[0], r[3]}, {r[2], r[1], 1, r[3]}};
        for (float[] b : bands) {
            if (b[2] - b[0] < 1e-3f || b[3] - b[1] < 1e-3f) {
                continue;
            }
            boolean wide = b[2] - b[0] >= b[3] - b[1];
            int n = 2;
            for (int i = 0; i < n; i++) {
                float u0 = wide ? b[0] + (b[2] - b[0]) * i / n : b[0];
                float u1 = wide ? b[0] + (b[2] - b[0]) * (i + 1) / n : b[2];
                float v0 = wide ? b[1] : b[1] + (b[3] - b[1]) * i / n;
                float v1 = wide ? b[3] : b[1] + (b[3] - b[1]) * (i + 1) / n;
                float[] a = {u0, v0};
                float[] bb = {u1, v0};
                float[] c = {u1, v1};
                float[] d = {u0, v1};
                out.add(piece(concat(tri(a, bb, c), tri(a, c, d)), false));
            }
        }
    }

    // --- helpers ---

    private static float[] tri(float[] a, float[] b, float[] c) {
        return new float[]{a[0], a[1], b[0], b[1], c[0], c[1]};
    }

    private static float[] concat(float[] a, float[] b) {
        float[] out = new float[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** A piece with its area-weighted centroid. */
    static Piece piece(float[] tris, boolean inRegion) {
        double area = 0;
        double cu = 0;
        double cv = 0;
        for (int i = 0; i < tris.length; i += 6) {
            double a = Math.abs((tris[i + 2] - tris[i]) * (tris[i + 5] - tris[i + 1])
                    - (tris[i + 4] - tris[i]) * (tris[i + 3] - tris[i + 1])) / 2;
            area += a;
            cu += a * (tris[i] + tris[i + 2] + tris[i + 4]) / 3;
            cv += a * (tris[i + 1] + tris[i + 3] + tris[i + 5]) / 3;
        }
        if (area <= 0) {
            cu = (tris[0] + tris[2] + tris[4]) / 3;
            cv = (tris[1] + tris[3] + tris[5]) / 3;
            area = 1;
        }
        return new Piece((float) (cu / area), (float) (cv / area), tris, inRegion);
    }

    /** Total area of the pieces (1 when they cover the whole interface without overlap; for tests). */
    public static double area(List<Piece> pieces) {
        double area = 0;
        for (Piece piece : pieces) {
            float[] t = piece.triangles();
            for (int i = 0; i < t.length; i += 6) {
                area += Math.abs((t[i + 2] - t[i]) * (t[i + 5] - t[i + 1]) - (t[i + 4] - t[i]) * (t[i + 3] - t[i + 1])) / 2;
            }
        }
        return area;
    }
}
