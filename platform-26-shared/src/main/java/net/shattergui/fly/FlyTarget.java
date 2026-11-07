package net.shattergui.fly;

import net.minecraft.world.phys.Vec3;
import net.shattergui.effect.modifier.EdgeTransition;

import java.util.Locale;

/**
 * Where the fly effect flies to or from: the block or entity the screen was opened from ({@link #point}), or, only for
 * the fly effect on screens without one, an edge of the screen ({@link #edge}).
 */
public record FlyTarget(Vec3 point, EdgeTransition edge) {
    public static FlyTarget point(Vec3 point) {
        return new FlyTarget(point, null);
    }

    public static FlyTarget edge(EdgeTransition edge) {
        return new FlyTarget(null, edge);
    }

    public boolean isEdge() {
        return edge != null;
    }

    /** For the profile log. */
    public String describe() {
        return isEdge() ? edge.describe() : String.format(Locale.ROOT, "point %.1f %.1f %.1f", point.x, point.y, point.z);
    }
}
