package net.shattergui.config.screen;

import net.minecraft.network.chat.Component;
import net.shattergui.params.Param;
import net.shattergui.params.Params;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How the editor shows a parameter: in units people think in (seconds instead of ticks, percent, degrees per second,
 * blocks per second, ×) — the stored value stays in the registry's units. Typed values are read back the same way.
 */
final class ParamUi {
    enum Unit {
        /** Ticks shown as seconds. */
        SECONDS(20.0, "ui.shatter_gui.unit.seconds", 2),
        /** 0..1 shown as percent. */
        PERCENT(0.01, "ui.shatter_gui.unit.percent", 0),
        /** A multiplier. */
        TIMES(1.0, "ui.shatter_gui.unit.times", 2),
        /** Blocks per tick shown per second. */
        BLOCKS_PER_SECOND(1 / 20.0, "ui.shatter_gui.unit.blocks_per_second", 1),
        /** Blocks per tick² shown per second². */
        BLOCKS_PER_SECOND2(1 / 400.0, "ui.shatter_gui.unit.blocks_per_second2", 0),
        /** Radians per tick shown as degrees per second. */
        DEGREES_PER_SECOND(Math.PI / 180 / 20, "ui.shatter_gui.unit.degrees_per_second", 0),
        TURNS(1.0, "ui.shatter_gui.unit.turns", 2),
        PIXELS(1.0, "ui.shatter_gui.unit.pixels", 0),
        COUNT(1.0, "ui.shatter_gui.unit.count", 0);

        /** Stored value = shown value × factor. */
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

    /** Stored value → text with its unit ("1.50 s", "35 %", "×1.00"). */
    static String format(Param<?> param, double stored) {
        Unit unit = unit(param);
        double shown = stored / unit.factor;
        String number = unit.decimals == 0 ? Long.toString(Math.round(shown))
                : String.format(Locale.ROOT, "%." + unit.decimals + "f", shown);
        return Component.translatable(unit.key, number).getString();
    }

    /** Typed text (in shown units) → stored value, or null if it is not a number. */
    static Double parse(Param<?> param, String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.')) * unit(param).factor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Slider step in stored units: one tick, one whole number, or 1/100 of the range. */
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
