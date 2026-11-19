package net.shattergui.config.screen;

import net.shattergui.params.Param;
import net.shattergui.params.Params;
import net.shattergui.platform.Text;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ParamUi {
    enum Unit {
        SECONDS(20.0, "ui.shatter_gui.unit.seconds", 2),
        PERCENT(0.01, "ui.shatter_gui.unit.percent", 0),
        TIMES(1.0, "ui.shatter_gui.unit.times", 2),
        BLOCKS_PER_SECOND(1 / 20.0, "ui.shatter_gui.unit.blocks_per_second", 1),
        BLOCKS_PER_SECOND2(1 / 400.0, "ui.shatter_gui.unit.blocks_per_second2", 0),
        DEGREES_PER_SECOND(Math.PI / 180 / 20, "ui.shatter_gui.unit.degrees_per_second", 0),
        TURNS(1.0, "ui.shatter_gui.unit.turns", 2),
        PIXELS(1.0, "ui.shatter_gui.unit.pixels", 0),
        COUNT(1.0, "ui.shatter_gui.unit.count", 0);

        final double factor;
        final String key;
        final int decimals;

        Unit(double factor, String key, int decimals) {
            this.factor = factor;
            this.key = key;
            this.decimals = decimals;
        }
    }

    private static final Map<Param<?>, Unit> UNITS = new HashMap<>();

    static {
        UNITS.put(Params.PARTICLE_DENSITY, Unit.TIMES);
        UNITS.put(Params.EFFECT_SPEED, Unit.TIMES);
        UNITS.put(Params.OPEN_SPEED, Unit.TIMES);
        UNITS.put(Params.SOUND_VOLUME, Unit.PERCENT);
        UNITS.put(Params.MAX_SHARDS, Unit.COUNT);
        UNITS.put(Params.GLASS_COLUMNS, Unit.COUNT);
        UNITS.put(Params.GLASS_ROWS, Unit.COUNT);
        UNITS.put(Params.GLASS_LIFETIME, Unit.SECONDS);
        UNITS.put(Params.GLASS_FADE_START, Unit.PERCENT);
        UNITS.put(Params.GLASS_GRAVITY, Unit.BLOCKS_PER_SECOND2);
        UNITS.put(Params.GLASS_AIR_DRAG, Unit.PERCENT);
        UNITS.put(Params.GLASS_BURST, Unit.BLOCKS_PER_SECOND);
        UNITS.put(Params.GLASS_SPIN, Unit.DEGREES_PER_SECOND);
        UNITS.put(Params.GLASS_BOUNCE, Unit.PERCENT);
        UNITS.put(Params.GLASS_SOUND, Unit.PERCENT);
        UNITS.put(Params.BURN_DURATION, Unit.SECONDS);
        UNITS.put(Params.BURN_SMOKE_DURATION, Unit.SECONDS);
        UNITS.put(Params.BURN_FLAME_DENSITY, Unit.TIMES);
        UNITS.put(Params.BURN_SMOKE_DENSITY, Unit.PERCENT);
        UNITS.put(Params.BURN_SOUND, Unit.PERCENT);
        UNITS.put(Params.FLY_DURATION, Unit.SECONDS);
        UNITS.put(Params.FLY_SOUND, Unit.PERCENT);
        UNITS.put(Params.DISSOLVE_DURATION, Unit.SECONDS);
        UNITS.put(Params.DISSOLVE_PIXEL_SIZE, Unit.PIXELS);
        UNITS.put(Params.DISSOLVE_SOUND, Unit.PERCENT);
        UNITS.put(Params.VORTEX_DURATION, Unit.SECONDS);
        UNITS.put(Params.VORTEX_TURNS, Unit.TURNS);
        UNITS.put(Params.VORTEX_PULL, Unit.TIMES);
        UNITS.put(Params.VORTEX_SPIN, Unit.DEGREES_PER_SECOND);
        UNITS.put(Params.VORTEX_SOUND, Unit.PERCENT);
    }

    private ParamUi() {
    }

    static Unit unit(Param<?> param) {
        Unit unit = UNITS.get(param);
        if (unit != null) {
            return unit;
        }
        return param instanceof Param.Int ? Unit.COUNT : Unit.TIMES;
    }

    static String format(Param<?> param, double stored) {
        Unit unit = unit(param);
        double shown = stored / unit.factor;
        String number = unit.decimals == 0 ? Long.toString(Math.round(shown))
                : String.format(Locale.ROOT, "%." + unit.decimals + "f", shown);
        return Text.translatable(unit.key, number).getString();
    }

    static Double parse(Param<?> param, String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.')) * unit(param).factor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static double step(Param<?> param) {
        if (param instanceof Param.Int) {
            return 1;
        }
        if (param instanceof Param.Float f) {
            return (f.max() - f.min()) / 100.0;
        }
        return 0;
    }
}
