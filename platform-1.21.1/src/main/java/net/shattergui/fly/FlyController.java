package net.shattergui.fly;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.shattergui.ScreenRules;
import net.shattergui.capture.ScreenCapture;
import net.shattergui.effect.ShatterEffects;
import net.shattergui.effect.ShatterEffect;
import net.shattergui.compat.spatial.SpatialGuiCompat;
import net.shattergui.config.ShatterConfig;
import net.shattergui.debug.ShatterProfiler;
import net.shattergui.effect.EffectSounds;
import net.shattergui.effect.FlyEffect;
import net.shattergui.effect.modifier.EdgeTransition;
import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;
import net.shattergui.shatter.ShardManager;

import java.util.List;

public final class FlyController {
    private static final long USE_WINDOW_MS = 1000;

    private static FlySource lastUse;
    private static long lastUseTime;
    private static FlySource openSource;
    private static String missingReason;
    private static boolean closedHadSource;
    private static String closedSourceKey;
    private static final RandomSource RANDOM = RandomSource.create();

    private FlyController() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && ShatterConfig.get().enabled) {
                remember(FlySource.of(level, hit.getBlockPos()));
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && ShatterConfig.get().enabled) {
                remember(FlySource.of(entity));
            }
            return InteractionResult.PASS;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!ShatterConfig.get().enabled) {
                return;
            }
            ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> !busy(s));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, x, y, button) -> !busy(s));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !busy(s));
        });
    }

    private static boolean busy(Screen s) {
        return FlyOpen.isAnimating(s) || OpenAssemble.hides(s);
    }

    private static void remember(FlySource source) {
        lastUse = source;
        lastUseTime = System.currentTimeMillis();
    }

    public static void onScreenOpened(Screen screen) {
        ShatterConfig config = ShatterConfig.get();
        if (!config.enabled) {
            return;
        }
        long start = ShatterProfiler.now();
        FlySource source = lastUse != null && System.currentTimeMillis() - lastUseTime <= USE_WINDOW_MS ? lastUse : null;
        lastUse = null;
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
            source = null;
        }
        openSource = source;

        List<String> keys = ScreenRules.keys(screen);
        ScreenRules.remember(screen, keys);
        String effect = ScreenRules.shouldShatter(screen, keys, config) ? ScreenRules.openEffectFor(keys, config)
                : ShatterConfig.OPEN_NONE;
        if (ShatterConfig.OPEN_NONE.equals(effect)) {
            return;
        }
        String dataId = ShatterConfig.openDataId(effect);
        if (dataId != null) {
            gather(screen, keys, effect, dataId, source, config, start);
            return;
        }
        ParamValues params = config.values();
        FlyTarget from;
        if (source != null) {
            from = FlyTarget.point(source.point());
        } else if (edgeFlightAllowed(screen, keys, config)) {
            from = FlyTarget.edge(EdgeTransition.pick(params.s(Params.FLY_EDGE_DIRECTION), RANDOM, !params.reduceMotion()));
        } else {
            return;
        }
        ShatterProfiler.beginOpen(keys.getFirst(), effect, source != null ? source.describe() : from.describe());
        if (source != null && !source.isValid()) {
            ShatterProfiler.openValue("open.skipped", "source already gone");
            return;
        }
        if (!ShardManager.admit(ScreenCapture.busy() ? 1 : 0, "fly-in of " + keys.getFirst())) {
            ShatterProfiler.openValue("open.skipped", "too many effects at once (skipNew)");
            return;
        }
        boolean spatial = SpatialGuiCompat.isShowing(screen) && SpatialGuiCompat.cancelOpenAnimation();
        int duration = Math.max(2, Math.round(params.ticks(Params.FLY_DURATION) / params.f(Params.OPEN_SPEED)));
        OpenAssemble.flyStarted(screen, duration);
        FlyOpen.start(screen, from, spatial, duration);
        EffectSounds.play(from.isEdge() ? SoundEvents.UI_TOAST_IN : SoundEvents.BUNDLE_REMOVE_ONE,
                params.volume(Params.FLY_SOUND), 1f);
        ShatterProfiler.openStep("open.trigger (" + (spatial ? "Spatial GUI plane, its open animation cancelled"
                : "flat GUI") + ", " + duration + " ticks)", start);
    }

    private static void gather(Screen screen, List<String> keys, String effect, String id, FlySource source,
                               ShatterConfig config, long start) {
        ShatterProfiler.beginOpen(keys.getFirst(), effect, source != null ? source.describe() : "no source");
        ShatterEffect fx = ShatterEffects.opening(id);
        if (fx == null) {
            ShatterProfiler.openValue("open.skipped", "effect " + id + " is not loaded or cannot open screens");
            return;
        }
        if (!ShardManager.admit(ScreenCapture.busy() ? 1 : 0, "opening of " + keys.getFirst())) {
            ShatterProfiler.openValue("open.skipped", "too many effects at once (skipNew)");
            return;
        }
        ParamValues params = fx.prepare(config.values());
        FlyTarget target = source != null && source.isValid() ? FlyTarget.point(source.point()) : null;
        if (SpatialGuiCompat.isShowing(screen)) {
            if (SpatialGuiCompat.tryGather(screen, fx, target, params)) {
                OpenAssemble.start(screen, true);
                ShatterProfiler.openStep("open.gather (" + id + " backwards on the Spatial GUI plane)", start);
            } else {
                ShatterProfiler.openValue("open.skipped", "Spatial GUI plane could not be taken over");
            }
            return;
        }
        OpenAssemble.start(screen, false);
        ScreenCapture.requestCapture(screen, fx, target, params, false);
        ShatterProfiler.openStep("open.gather (" + id + " backwards, capture next frame)", start);
    }

    public static boolean edgeFlightAllowed(Screen screen, List<String> keys, ShatterConfig config) {
        return EdgeTransition.allows(screen) && ScreenRules.effectFor(keys, config, openSourceKey()).base() instanceof FlyEffect;
    }

    public static String closedSourceKey() {
        return closedSourceKey;
    }

    public static String openSourceKey() {
        return openSource != null ? openSource.themeKey() : null;
    }

    public static boolean hadSource() {
        return closedHadSource;
    }

    public static FlySource onScreenClosed() {
        FlyOpen.stop();
        endOpening("closed");
        FlySource source = openSource;
        openSource = null;
        closedHadSource = source != null;
        closedSourceKey = source != null ? source.themeKey() : null;
        if (source == null) {
            missingReason = "no source (not opened by using a block or entity)";
            return null;
        }
        if (!source.isValid()) {
            missingReason = source.describe() + " is gone";
            return null;
        }
        missingReason = null;
        return source;
    }

    public static void endOpening(String why) {
        boolean was = OpenAssemble.active();
        OpenAssemble.stop();
        ScreenCapture.cancelPendingOpening();
        SpatialGuiCompat.cancelPendingOpen();
        int dropped = ShardManager.endOpenings();
        if (was || dropped > 0) {
            ShatterProfiler.openValue("open.ended", why + ": " + dropped + " gathering wave(s) dropped");
        }
    }

    public static String missingReason() {
        return missingReason;
    }
}
