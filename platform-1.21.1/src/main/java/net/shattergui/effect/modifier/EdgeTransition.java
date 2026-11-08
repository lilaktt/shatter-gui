package net.shattergui.effect.modifier;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.shattergui.shatter.ShardPlane;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Locale;

public record EdgeTransition(int dx, int dy, boolean roll) {
    private static final float ENTRY_START_SCALE = 0.85f;
    private static final float EXIT_END_SCALE = 0.6f;
    private static final float ROLL = 0.25f;
    private static final double EXIT_DISTANCE = 1.2;

    private static final int[][] EDGES = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private static final int[][] EDGES_AND_CORNERS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    private static final List<Class<? extends Screen>> STILL_SCREENS = List.of(ChatScreen.class,
            AbstractSignEditScreen.class, BookEditScreen.class, BookViewScreen.class,
            AbstractCommandBlockEditScreen.class, JigsawBlockEditScreen.class, StructureBlockEditScreen.class);

    public static EdgeTransition pick(String direction, RandomSource random, boolean roll) {
        int[] dir = switch (direction) {
            case "top" -> EDGES[0];
            case "bottom" -> EDGES[1];
            case "left" -> EDGES[2];
            case "right" -> EDGES[3];
            default -> EDGES_AND_CORNERS[random.nextInt(EDGES_AND_CORNERS.length)];
        };
        return new EdgeTransition(dir[0], dir[1], roll);
    }

    public static boolean allows(Screen screen) {
        for (Class<? extends Screen> type : STILL_SCREENS) {
            if (type.isInstance(screen)) {
                return false;
            }
        }
        return true;
    }

    public void applyFlatEntry(PoseStack pose, float centerX, float centerY, int width, int height, float e) {
        float out = 1f - e;
        pose.translate(centerX + dx * width * out, centerY + dy * height * out, 0f);
        pose.mulPose(Axis.ZP.rotation(rollAt(out)));
        float scale = Mth.lerp(e, ENTRY_START_SCALE, 1f);
        pose.scale(scale, scale, 1f);
        pose.translate(-centerX, -centerY, 0f);
    }

    public Matrix4f worldEntry(Vector3fc planeCenter, Camera camera, double aspect, float e) {
        float out = 1f - e;
        double halfHeight = planeCenter.length() * Math.tan(Math.toRadians(net.shattergui.shatter.CameraFov.of(camera)) / 2);
        double halfWidth = halfHeight * aspect;
        Vector3fc left = camera.getLeftVector();
        Vector3fc up = camera.getUpVector();
        Vector3fc forward = camera.getLookVector();
        float right = (float) (dx * 2 * halfWidth * out);
        float down = (float) (dy * 2 * halfHeight * out);
        Vector3f center = new Vector3f(planeCenter)
                .sub(left.x() * right, left.y() * right, left.z() * right)
                .sub(up.x() * down, up.y() * down, up.z() * down);
        return new Matrix4f().translation(center).rotate(rollAt(out), forward.x(), forward.y(), forward.z())
                .scale(Mth.lerp(e, ENTRY_START_SCALE, 1f))
                .translate(-planeCenter.x(), -planeCenter.y(), -planeCenter.z());
    }

    public Vector3d exitPoint(ShardPlane plane, Vector3d center) {
        return plane.toWorld(0.5 + dx * EXIT_DISTANCE, 0.5 + dy * EXIT_DISTANCE).sub(plane.toWorld(0.5, 0.5)).add(center);
    }

    public float exitScale(float e) {
        return Mth.lerp(e, 1f, EXIT_END_SCALE);
    }

    public float exitAlpha(float e) {
        float t = Math.clamp((e - 0.35f) / 0.65f, 0f, 1f);
        return 1f - t * t * (3 - 2 * t);
    }

    private float rollAt(float out) {
        if (!roll) {
            return 0f;
        }
        return ROLL * out * (dx != 0 ? dx : dy);
    }

    public String describe() {
        return String.format(Locale.ROOT, "edge %d,%d", dx, dy);
    }
}
