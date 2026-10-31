package net.shattergui.shatter;

import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Where a captured image sits in the world when it shatters. Image points are given as fractions of the image,
 * {@code x} left to right and {@code y} top to bottom, both 0..1.
 */
public interface ShardPlane {
    /** World position of an image point. */
    Vector3d toWorld(double x, double y);

    /** World width divided by world height of the whole image. */
    double aspect();

    /** Unit normal of the plane, facing the camera. */
    Vector3f normal();

    /** Texture u of an image point. */
    default float u(double x) {
        return (float) x;
    }

    /** Texture v of an image point. Captures are framebuffer copies, whose rows start at the bottom. */
    default float v(double y) {
        return (float) (1 - y);
    }
}
