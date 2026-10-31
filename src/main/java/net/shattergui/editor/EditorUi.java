package net.shattergui.editor;

import net.minecraft.network.chat.Component;
import net.shattergui.fx.FxDef;
import net.shattergui.fx.FxSchema;
import net.shattergui.ui.Draw;
import net.shattergui.ui.Icon;
import net.shattergui.ui.Ui;

import java.util.Locale;

/**
 * Small shared pieces of the effect editor: translations, numbers in human units (seconds instead of ticks, degrees
 * instead of radians, blocks per second, percentages) and back, module icons and colours, a checkerboard.
 */
public final class EditorUi {
    private EditorUi() {
    }

    public static Component tr(String key, Object... args) {
        return Component.translatable("ui.shatter_gui.fx." + key, args);
    }

    // --- units ---

    /** Display value for a stored one. */
    public static double toDisplay(FxSchema.Unit unit, double v) {
        return switch (unit) {
            case FRACTION -> v * 100;
            case SPEED, RATE -> v * 20;
            case ACCEL -> v * 400;
            case ANGLE -> Math.toDegrees(v);
            case TURN -> Math.toDegrees(v) * 20;
            case TICKS -> v / 20;
            default -> v;
        };
    }

    public static double fromDisplay(FxSchema.Unit unit, double v) {
        return switch (unit) {
            case FRACTION -> v / 100;
            case SPEED, RATE -> v / 20;
            case ACCEL -> v / 400;
            case ANGLE -> Math.toRadians(v);
            case TURN -> Math.toRadians(v / 20);
            case TICKS -> v * 20;
            default -> v;
        };
    }

    /** "1.25 s", "40 %", "0.80 bl/s", "×1.50", … */
    public static String format(FxSchema.Unit unit, double stored) {
        double v = toDisplay(unit, stored);
        String number = switch (unit) {
            case COUNT, PIXELS -> Long.toString(Math.round(v));
            case FRACTION, ANGLE -> trim(String.format(Locale.ROOT, Math.abs(v) >= 10 ? "%.0f" : "%.1f", v));
            case TICKS -> trim(String.format(Locale.ROOT, "%.2f", v));
            default -> trim(String.format(Locale.ROOT, Math.abs(v) >= 100 ? "%.0f" : Math.abs(v) >= 10 ? "%.1f" : "%.3f", v));
        };
        String key = switch (unit) {
            case FRACTION -> "unit.percent";
            case FACTOR -> "unit.factor";
            case BLOCKS -> "unit.blocks";
            case SPEED -> "unit.speed";
            case ACCEL -> "unit.accel";
            case ANGLE -> "unit.degrees";
            case TURN -> "unit.turn";
            case TICKS -> "unit.seconds";
            case RATE -> "unit.rate";
            case PIXELS -> "unit.pixels";
            default -> null;
        };
        return key == null ? number : tr(key, number).getString();
    }

    private static String trim(String s) {
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return "-0".equals(s) ? "0" : s;
    }

    /** A typed number in display units → stored value; null if it is not a number. */
    public static Double parse(FxSchema.Unit unit, String text) {
        String clean = text.replace(',', '.').replaceAll("[^0-9.+-]", "");
        if (clean.isEmpty() || "-".equals(clean) || ".".equals(clean)) {
            return null;
        }
        try {
            double v = Double.parseDouble(clean);
            return Double.isFinite(v) ? fromDisplay(unit, v) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A field's help with its range in human units and (when {@code withDefault}: the default is a plain number) its
     * default — "From 0 % to 100 %, default 35 %".
     */
    public static Component withRange(Component help, FxSchema.Field field, boolean withDefault) {
        Component range = withDefault
                ? Component.translatable("ui.shatter_gui.range", format(field.unit(), field.min()), format(field.unit(), field.max()),
                        format(field.unit(), field.def()))
                : tr("tip.range", format(field.unit(), field.min()), format(field.unit(), field.max()));
        return help.copy().append("\n").append(range);
    }

    /** Slider step in stored units. */
    public static double step(FxSchema.Field field) {
        if (field.whole() || field.unit() == FxSchema.Unit.COUNT) {
            return 1;
        }
        return (field.max() - field.min()) / 400.0;
    }

    // --- modules ---

    public static Icon icon(FxDef.Module module) {
        return switch (module) {
            case STAY -> Icon.ANCHOR;
            case LAUNCH -> Icon.BURST;
            case GRAVITY -> Icon.ARROW_DOWN;
            case DRAG -> Icon.BRAKE;
            case ATTRACT -> Icon.MAGNET;
            case DRIFT -> Icon.WIND;
            case SPIN -> Icon.SPIN;
            case SCALE -> Icon.SCALE;
            case FOLLOW_PATH -> Icon.PATH;
            case FORCE -> Icon.CHEVRON_RIGHT;
            case COLLIDE -> Icon.COLLIDE;
        };
    }

    public static int color(FxDef.Module module) {
        return switch (module) {
            case STAY -> 0xFF8A8FA3;
            case LAUNCH -> 0xFFF97316;
            case GRAVITY -> 0xFF3B82F6;
            case DRAG -> 0xFF64748B;
            case ATTRACT -> 0xFFA855F7;
            case DRIFT -> 0xFF14B8A6;
            case SPIN -> 0xFFEC4899;
            case SCALE -> 0xFF22C55E;
            case FOLLOW_PATH -> 0xFFEAB308;
            case FORCE -> 0xFF06B6D4;
            case COLLIDE -> 0xFFB45309;
        };
    }

    public static final int EMITTER_COLOR = 0xFFFACC15;
    public static final int SOUND_COLOR = 0xFF38BDF8;

    /** The module's translated name ("Launch" / "Поштовх"). */
    public static Component moduleName(FxDef.Module module) {
        return tr("module." + FxSchema.moduleName(module));
    }

    public static Component moduleDescription(FxDef.Module module) {
        return tr("module." + FxSchema.moduleName(module) + ".desc");
    }

    public static Component paramName(String key) {
        return tr("param." + key);
    }

    public static Component paramHelp(String key) {
        return tr("param." + key + ".desc");
    }

    // --- drawing ---

    /** A grey checkerboard (behind colours with transparency). */
    public static void checker(Ui ui, int x, int y, int w, int h, int cell) {
        Draw.rect(ui, x, y, w, h, 0xFF9A9A9A);
        for (int cy = 0; cy * cell < h; cy++) {
            for (int cx = (cy & 1); cx * cell < w; cx += 2) {
                int x0 = x + cx * cell;
                int y0 = y + cy * cell;
                Draw.rect(ui, x0, y0, Math.min(cell, x + w - x0), Math.min(cell, y + h - y0), 0xFFD0D0D0);
            }
        }
    }

    /** Line segments (x0, y0, x1, y1, …) of the given width, drawn as thin quads in one batch. */
    public static void lines(Ui ui, float[] points, int count, float width, int color) {
        if (count < 2) {
            return;
        }
        float[] data = new float[(count - 1) * 6 * 4];
        int[] colors = new int[(count - 1) * 6];
        int n = 0;
        float half = width / 2f;
        for (int i = 0; i < count - 1; i++) {
            float x0 = points[i * 2];
            float y0 = points[i * 2 + 1];
            float x1 = points[i * 2 + 2];
            float y1 = points[i * 2 + 3];
            float dx = x1 - x0;
            float dy = y1 - y0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-4f) {
                continue;
            }
            float nx = -dy / len * half;
            float ny = dx / len * half;
            float[] quad = {x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny};
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int k : order) {
                data[n * 4] = quad[k * 2];
                data[n * 4 + 1] = quad[k * 2 + 1];
                colors[n] = color;
                n++;
            }
        }
        Draw.triangles(ui, null, data, colors, n);
    }

    /** A horizontal colour gradient over the rectangle, one column per pixel (or per 2 for wide ones). */
    public static void hGradient(Ui ui, int x, int y, int w, int h, java.util.function.IntUnaryOperator colorAtColumn) {
        int step = w > 200 ? 2 : 1;
        for (int i = 0; i < w; i += step) {
            Draw.rect(ui, x + i, y, Math.min(step, w - i), h, colorAtColumn.applyAsInt(i));
        }
    }
}
