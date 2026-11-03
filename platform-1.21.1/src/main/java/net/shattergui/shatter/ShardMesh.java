package net.shattergui.shatter;

import net.minecraft.util.RandomSource;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.debug.ShatterProfiler;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the GUI area of a captured image into jittered-grid triangles placed on a {@link ShardPlane}. Only geometry:
 * the effect gives the shards their motion.
 */
public final class ShardMesh {
    private static final float JITTER = 0.35f;

    /**
     * @param center       world position of the middle of the GUI area
     * @param halfDiagonal half the world-space diagonal of the GUI area
     */
    public record Layout(List<ShardInstance> shards, Vector3d center, double halfDiagonal) {
    }

    private ShardMesh() {
    }

    /** A grid of roughly {@code cells} cells, following the world aspect ratio of the GUI area. */
    public static Layout build(ShardSource source, int cells) {
        RandomSource random = source.random();
        ShardPlane plane = source.plane();
        ImageRect bounds = source.bounds();

        long gridStart = ShatterProfiler.now();
        double aspect = bounds.width() / bounds.height() * plane.aspect();
        int cols = Math.max(1, Math.round((float) Math.sqrt(cells * aspect)));
        int rows = Math.max(1, Math.round((float) cells / cols));

        double cellW = bounds.width() / cols;
        double cellH = bounds.height() / rows;
        double[][] vx = new double[cols + 1][rows + 1];
        double[][] vy = new double[cols + 1][rows + 1];
        for (int i = 0; i <= cols; i++) {
            for (int j = 0; j <= rows; j++) {
                double x = bounds.x0() + i * cellW;
                double y = bounds.y0() + j * cellH;
                // Border vertices stay put so the outline remains straight.
                if (i > 0 && i < cols) {
                    x += (random.nextFloat() * 2 - 1) * JITTER * cellW;
                }
                if (j > 0 && j < rows) {
                    y += (random.nextFloat() * 2 - 1) * JITTER * cellH;
                }
                vx[i][j] = x;
                vy[i][j] = y;
            }
        }
        ShatterProfiler.step("mesh.grid (" + cols + "x" + rows + ")", gridStart);

        long shardsStart = ShatterProfiler.now();
        Vector3d center = plane.toWorld((bounds.x0() + bounds.x1()) / 2, (bounds.y0() + bounds.y1()) / 2);
        double halfDiagonal = plane.toWorld(bounds.x0(), bounds.y0()).distance(plane.toWorld(bounds.x1(), bounds.y1())) / 2;

        List<ShardInstance> shards = new ArrayList<>(cols * rows * 2);
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                double[] a = {vx[i][j], vy[i][j]};
                double[] b = {vx[i + 1][j], vy[i + 1][j]};
                double[] c = {vx[i + 1][j + 1], vy[i + 1][j + 1]};
                double[] d = {vx[i][j + 1], vy[i][j + 1]};
                // Alternate the diagonal so the cracks do not all run the same way.
                double[][][] tris = random.nextBoolean()
                        ? new double[][][]{{a, b, c}, {a, c, d}}
                        : new double[][][]{{a, b, d}, {b, c, d}};
                // No CPU copy of the image, so fully transparent triangles are kept; they draw nothing.
                for (double[][] tri : tris) {
                    shards.add(createShard(tri, plane, source));
                }
            }
        }
        ShatterProfiler.step("mesh.createShards (" + shards.size() + ")", shardsStart);
        return new Layout(shards, center, halfDiagonal);
    }

    /**
     * A regular grid of {@code cols x rows} squares over the GUI area, without jitter. Each square is two triangles,
     * next to each other in the list (square k = shards 2k and 2k+1), so an effect can move them as one.
     */
    public static Layout buildSquares(ShardSource source, int cols, int rows) {
        ShardPlane plane = source.plane();
        ImageRect bounds = source.bounds();
        long start = ShatterProfiler.now();
        double cellW = bounds.width() / cols;
        double cellH = bounds.height() / rows;
        List<ShardInstance> shards = new ArrayList<>(cols * rows * 2);
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                double x0 = bounds.x0() + i * cellW;
                double y0 = bounds.y0() + j * cellH;
                double[] a = {x0, y0};
                double[] b = {x0 + cellW, y0};
                double[] c = {x0 + cellW, y0 + cellH};
                double[] d = {x0, y0 + cellH};
                shards.add(createShard(new double[][]{a, b, c}, plane, source));
                shards.add(createShard(new double[][]{a, c, d}, plane, source));
            }
        }
        Vector3d center = plane.toWorld((bounds.x0() + bounds.x1()) / 2, (bounds.y0() + bounds.y1()) / 2);
        double halfDiagonal = plane.toWorld(bounds.x0(), bounds.y0()).distance(plane.toWorld(bounds.x1(), bounds.y1())) / 2;
        ShatterProfiler.step("mesh.squares (" + cols + "x" + rows + ", " + shards.size() + " triangles)", start);
        return new Layout(shards, center, halfDiagonal);
    }

    /** One triangle given in image fractions ({x, y} per corner), for effects that cut the image themselves. */
    public static ShardInstance triangle(double[][] tri, ShardSource source) {
        return createShard(tri, source.plane(), source);
    }

    private static ShardInstance createShard(double[][] tri, ShardPlane plane, ShardSource source) {
        Vector3d[] world = new Vector3d[3];
        Vector3d centroid = new Vector3d();
        float[] uv = new float[6];
        for (int k = 0; k < 3; k++) {
            world[k] = plane.toWorld(tri[k][0], tri[k][1]);
            centroid.add(world[k]);
            uv[k * 2] = plane.u(tri[k][0]);
            uv[k * 2 + 1] = plane.v(tri[k][1]);
        }
        centroid.div(3);

        Vector3f[] corners = new Vector3f[3];
        for (int k = 0; k < 3; k++) {
            corners[k] = new Vector3f((float) (world[k].x - centroid.x), (float) (world[k].y - centroid.y),
                    (float) (world[k].z - centroid.z));
        }

        // Default lifetime and fade are the glass settings; effects with their own timing set the lifetime themselves.
        RandomSource random = source.random();
        ParamValues params = source.params();
        int lifetime = Math.max(1, Math.round(params.i(Params.GLASS_LIFETIME) * params.durationScale()
                * (0.8f + random.nextFloat() * 0.4f)));
        ShardInstance shard = new ShardInstance(centroid, corners, uv, plane.normal(), lifetime,
                params.f(Params.GLASS_FADE_START), random.nextFloat());
        shard.noClip = source.level() != null && ShardInstance.collides(source.level(), centroid.x, centroid.y, centroid.z);
        return shard;
    }
}
