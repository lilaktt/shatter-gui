package net.shattergui.shatter;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import org.joml.Vector3f;

/**
 * A captured screen ready to be cut into shards.
 *
 * @param plane   where the image is in the world
 * @param bounds  the GUI area inside the image
 * @param forward camera view direction at the moment of closing (the burst pushes this way)
 * @param params  the settings the effect runs with (taken once, when it starts)
 * @param target  where the fly effect flies to (the block or entity the screen was opened from, or off an edge), or null
 */
public record ShardSource(ShardPlane plane, ImageRect bounds, Vector3f forward, ParamValues params,
                          ClientLevel level, RandomSource random, FlyTarget target) {
}
