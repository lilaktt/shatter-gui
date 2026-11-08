package net.shattergui.fx.game;

import net.shattergui.fx.Fracture;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxSimulation;
import net.shattergui.params.BundledParams;
import net.shattergui.params.OpenTiming;
import net.shattergui.params.ParamValues;

import java.util.List;
import java.util.Random;

public final class FxRun {
    private FxRun() {
    }

    public static FxSimulation simulate(FxDef def, boolean bundled, boolean reversed, List<Fracture.Piece> cut, float width,
                                        float height, FxSimulation.Host host, Random random, ParamValues params) {
        BundledParams.Set own = bundled ? BundledParams.of(def.id) : null;
        float durationScale = params.durationScale();
        float particleScale = params.particleScale();
        if (own != null) {
            durationScale *= params.i(own.duration()) / (float) BundledParams.fileDuration(def.id);
            particleScale *= params.f(own.particles());
        }
        FxSimulation sim = new FxSimulation(def, cut, width, height, host, random, durationScale, params.reduceMotion(),
                particleScale);
        if (own != null) {
            sim.strength(params.f(own.intensity()));
        }
        if (reversed) {
            sim.reverse(OpenTiming.playback(sim.duration, OpenTiming.quick(def.id, bundled, def.duration), params));
        }
        return sim;
    }
}
