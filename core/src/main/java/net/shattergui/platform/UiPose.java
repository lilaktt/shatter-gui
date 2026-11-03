package net.shattergui.platform;

/** The 2D transform of the GUI being drawn (a matrix stack). */
public interface UiPose {
    void pushMatrix();

    void popMatrix();

    void translate(float x, float y);

    void scale(float x, float y);

    /** Rotation in radians. */
    void rotate(float angle);

    /** A copy of the current matrix, for {@link #restore}. */
    Object snapshot();

    /** Back to a matrix taken with {@link #snapshot} (the stack depth is not changed). */
    void restore(Object snapshot);
}
