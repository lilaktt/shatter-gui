package net.shattergui.effect.modifier;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.shattergui.shatter.ShardPlane;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Locale;

/**
 * Modifier: the GUI enters from beyond an edge or corner of the screen, or leaves towards one. Holds only the
 * direction and the shape of the move (offset, roll, scale, fade); the effect decides when and how fast (it passes
 * the eased progress). Used by the fly effect for screens without a container; not tied to it, so any effect can use
 * it for its entry or exit.
 *
 * @param dx   -1, 0 or 1: left / none / right
 * @param dy   -1, 0 or 1: up / none / down (screen directions)
 * @param roll whether the GUI is slightly rolled at the edge (off with reduceMotion)
 */
public record EdgeTransition(int dx, int dy, boolean roll) {
    /** Entry: size at the edge. Exit: size when it leaves the screen. */
    private static final float ENTRY_START_SCALE = 0.85f;
    private static final float EXIT_END_SCALE = 0.6f;
    /** Roll at the edge, radians. */
    private static final float ROLL = 0.25f;
    /** Exit: how far the centre flies, in image sizes (the flat image is the whole screen). */
    private static final double EXIT_DISTANCE = 1.2;

    private static final int[][] EDGES = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private static final int[][] EDGES_AND_CORNERS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    /**
     * Text-entry and reading screens never move in or out from an edge: they open often, usually to type at once,
     * and a flying chat or sign is distracting.
     */
    private static final List<Class<? extends Screen>> STILL_SCREENS = List.of(ChatScreen.class,
            AbstractSignEditScreen.class, BookEditScreen.class, BookViewScreen.class, BookSignScreen.class,
            AbstractCommandBlockEditScreen.class, JigsawBlockEditScreen.class, StructureBlockEditScreen.class);

    /** "top", "bottom", "left", "right", or anything else for a random edge or corner. */
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

    /** False for chat, sign, book and command screens (see {@link #STILL_SCREENS}). */
    public static boolean allows(Screen screen) {
        for (Class<? extends Screen> type : STILL_SCREENS) {
            if (type.isInstance(screen)) {
                return false;
            }
        }
        return true;
    }

    // --- entry: e = 0 beyond the edge, 1 in place ---

    /** The flat GUI: pushes nothing, applies the entry to the current pose around the GUI centre. */
    public void applyFlatEntry(Matrix3x2fStack pose, float centerX, float centerY, int width, int height, float e) {
        // From one screen size beyond the edge: the whole GUI starts off screen.
        float out = 1f - e;
        pose.translate(centerX + dx * width * out, centerY + dy * height * out);
        pose.rotate(rollAt(out));
        pose.scale(Mth.lerp(e, ENTRY_START_SCALE, 1f));
        pose.translate(-centerX, -centerY);
    }

    /**
     * A plane in the world (Spatial GUI): the transform to put in front of its own, in camera-relative world space;
     * {@code planeCenter} is where its centre is, relative to the camera.
     */
    public Matrix4f worldEntry(Vector3fc planeCenter, Camera camera, double aspect, float e) {
        // From two half view widths/heights beside the plane, at its distance: it starts outside the frame.
        float out = 1f - e;
        double halfHeight = planeCenter.length() * Math.tan(Math.toRadians(camera.getFov()) / 2);
        double halfWidth = halfHeight * aspect;
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        Vector3fc forward = camera.forwardVector();
        // Screen right is -left, screen down is -up.
        float right = (float) (dx * 2 * halfWidth * out);
        float down = (float) (dy * 2 * halfHeight * out);
        Vector3f center = new Vector3f(planeCenter)
                .sub(left.x() * right, left.y() * right, left.z() * right)
                .sub(up.x() * down, up.y() * down, up.z() * down);
        return new Matrix4f().translation(center).rotate(rollAt(out), forward.x(), forward.y(), forward.z())
                .scale(Mth.lerp(e, ENTRY_START_SCALE, 1f))
                .translate(-planeCenter.x(), -planeCenter.y(), -planeCenter.z());
    }

    // --- exit: e = 0 in place, 1 gone ---

    /** Where the centre of the GUI ends up, off the screen, for a GUI on {@code plane} centred at {@code center}. */
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
