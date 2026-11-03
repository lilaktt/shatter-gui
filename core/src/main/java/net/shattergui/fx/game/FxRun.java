package net.shattergui.fx.game;

import net.shattergui.fx.Fracture;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxSimulation;
import net.shattergui.params.BundledParams;
import net.shattergui.params.OpenTiming;
import net.shattergui.params.ParamValues;

import java.util.List;
import java.util.Random;

/** How a schema-2 effect runs with the player's settings; shared by the effect in the world and the previews. */
public final class FxRun {
    private FxRun() {
    }

    /**
     * The simulation of {@code def} with the settings: a shipped effect's own length, strength and particles on top of
     * the general ones, and played backwards for opening a screen.
     */
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
            // An opening is quicker than the effect (capped, and openSpeed on top): the same motion, sampled.
            sim.reverse(OpenTiming.playback(sim.duration, OpenTiming.quick(def.id, bundled, def.duration), params));
        }
        return sim;
    }
}
