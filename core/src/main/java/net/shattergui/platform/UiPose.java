package net.shattergui.platform;

public interface UiPose {
    void pushMatrix();

    void popMatrix();

    void translate(float x, float y);

    void scale(float x, float y);

    void rotate(float angle);

    Object snapshot();

    void restore(Object snapshot);
}
