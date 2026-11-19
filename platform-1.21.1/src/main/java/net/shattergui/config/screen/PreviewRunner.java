package net.shattergui.config.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.RandomSource;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.FlyEffect;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.effect.modifier.EdgeTransition;
import net.shattergui.fly.FlyTarget;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardManager;

public final class PreviewRunner {
    private static final int MAX_TICKS = 20 * 8;
    private static final int START_TICKS = 3;
    private static final RandomSource RANDOM = RandomSource.create();

    private static Screen editor;
    private static int age;

    private PreviewRunner() {
    }

    public static boolean start(Screen screen, ShatterEffect effect, ParamValues params) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        ParamValues own = effect.prepare(params);
        FlyTarget target = effect.base() instanceof FlyEffect
                ? FlyTarget.edge(EdgeTransition.pick(own.s(Params.FLY_EDGE_DIRECTION), RANDOM, !own.reduceMotion()))
                : null;
        boolean spatial = SpatialGuiCompat.isShowing(screen);
        editor = screen;
        age = 0;
        mc.setScreen(null);
        SpatialGuiCompat.startClose(screen, spatial, effect, target, params, true);
        ShatterProfiler.value("preview", effect.id() + (spatial ? " on the Spatial GUI plane" : " on the flat editor"));
        return true;
    }

    public static boolean running() {
        return editor != null;
    }

    public static void tick() {
        if (editor == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        age++;
        boolean over = age > START_TICKS && !ShardManager.hasPreview() || age > MAX_TICKS || mc.level == null;
        if (!over) {
            return;
        }
        Screen screen = editor;
        editor = null;
        if (mc.screen == null && mc.level != null) {
            mc.setScreen(screen);
        }
    }
}
