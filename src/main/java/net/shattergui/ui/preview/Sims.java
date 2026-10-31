package net.shattergui.ui.preview;

/** Picks the 2D stand-in for a scene's close effect. */
final class Sims {
    private Sims() {
    }

    static Sim create(Scene scene, Sim.Stage stage, long seed) {
        if (scene.fx() != null) {
            return new FxSim(stage, scene.params(), seed, scene.fx().def, scene.fx().textures::get, scene.fx().bundled, false);
        }
        Sim sim = scene.base().equals("fly") ? new FlySim(stage, scene.params(), seed, scene.hasSource()
                ? FlySim.Mode.INTO_SOURCE : scene.edgeAllowed() ? FlySim.Mode.THROUGH_EDGE : FlySim.Mode.IN_PLACE)
                : base(scene.base(), stage, scene.params(), seed);
        if (scene.custom() != null && !scene.custom().particles().isEmpty()) {
            return new ExtrasSim(sim, scene.custom(), seed + 7);
        }
        return sim;
    }

    /** The stand-in of a code effect other than fly (glass for anything unknown). */
    static Sim base(String base, Sim.Stage stage, net.shattergui.params.ParamValues params, long seed) {
        return switch (base) {
            case "burn" -> new BurnSim(stage, params, seed);
            case "dissolve" -> new DissolveSim(stage, params, seed);
            case "vortex" -> new VortexSim(stage, params, seed);
            default -> new GlassSim(stage, params, seed);
        };
    }

    /**
     * The stand-in of an "open:<id>" open effect: the effect played backwards, or null if it has no opening (unknown,
     * fly, a schema-2 effect that cannot open).
     */
    static Sim opening(String open, Sim.Stage stage, net.shattergui.params.ParamValues params, long seed) {
        String id = net.shattergui.config.ShatterConfig.openDataId(open);
        net.shattergui.effect.ShatterEffect fx = id != null ? net.shattergui.effect.ShatterEffects.opening(id) : null;
        if (fx instanceof net.shattergui.fx.game.DataEffect data) {
            return new FxSim(stage, params, seed, data.def, data.textures::get, data.bundled, true);
        }
        if (fx instanceof net.shattergui.effect.ReversedEffect reversed) {
            return new ReversedSim(base(reversed.base().id(), stage, reversed.prepare(params), seed));
        }
        return null;
    }
}
