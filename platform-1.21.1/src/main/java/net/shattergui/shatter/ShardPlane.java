package net.shattergui.shatter;

import org.joml.Vector3d;
import org.joml.Vector3f;

public interface ShardPlane {
    Vector3d toWorld(double x, double y);

    double aspect();

    Vector3f normal();

    default float u(double x) {
        return (float) x;
    }

    default float v(double y) {
        return (float) (1 - y);
    }
}
