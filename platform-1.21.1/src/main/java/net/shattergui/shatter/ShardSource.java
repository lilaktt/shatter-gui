package net.shattergui.shatter;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import org.joml.Vector3f;

public record ShardSource(ShardPlane plane, ImageRect bounds, Vector3f forward, ParamValues params,
                          ClientLevel level, RandomSource random, FlyTarget target) {
}
