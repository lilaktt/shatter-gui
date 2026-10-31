package net.shattergui.params;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Locale;

/**
 * One typed setting of an effect (or of the "general" group): name, type, range, default and translation key. Every
 * value that reaches an effect goes through {@link #sanitize}, which clamps it into range and replaces anything
 * unusable (wrong type, NaN, unknown choice) with the default, so no input can produce a fatal value.
 */
public abstract sealed class Param<T> permits Param.Float, Param.Int, Param.Bool, Param.Choice {
    private final String group;
    private final String id;
    private final T def;
    /** Group whose translations it uses (the effects shipped as data share one set), or null for its own. */
    private String langGroup;

    Param(String group, String id, T def) {
        this.group = group;
        this.id = id;
        this.def = def;
    }

    public String group() {
        return group;
    }

    public String id() {
        return id;
    }

    public T def() {
        return def;
    }

    /** Translation key of the name; {@code + ".tooltip"} for the description. */
    public String langKey() {
        return "param.shatter_gui." + (langGroup != null ? langGroup : group) + "." + id;
    }

    /** Uses the translations of another group (same name and description for many effects). */
    void sharedLang(String group) {
        langGroup = group;
    }

    /** Any input (JSON number or text, editor text, a value of the right type) → a valid value. */
    public abstract T sanitize(Object raw);

    public JsonElement toJson(T value) {
        return value instanceof Number n ? new JsonPrimitive(n) : value instanceof Boolean b ? new JsonPrimitive(b)
                : new JsonPrimitive(String.valueOf(value));
    }

    public T fromJson(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return def;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return sanitize(primitive.isNumber() ? primitive.getAsNumber() : primitive.isBoolean()
                ? (Object) primitive.getAsBoolean() : primitive.getAsString());
    }

    @Override
    public String toString() {
        return group + "." + id;
    }

    /** A number with a range. */
    public static final class Float extends Param<java.lang.Float> {
        private final float min;
        private final float max;

        Float(String group, String id, float min, float max, float def) {
            super(group, id, def);
            this.min = min;
            this.max = max;
        }

        public float min() {
            return min;
        }

        public float max() {
            return max;
        }

        @Override
        public java.lang.Float sanitize(Object raw) {
            double value;
            if (raw instanceof Number n) {
                value = n.doubleValue();
            } else if (raw instanceof String s) {
                try {
                    value = Double.parseDouble(s.trim().replace(',', '.'));
                } catch (NumberFormatException e) {
                    return def();
                }
            } else {
                return def();
            }
            if (!Double.isFinite(value)) {
                return def();
            }
            return (float) Math.clamp(value, min, max);
        }
    }

    /** A whole number with a range. */
    public static final class Int extends Param<Integer> {
        private final int min;
        private final int max;

        Int(String group, String id, int min, int max, int def) {
            super(group, id, def);
            this.min = min;
            this.max = max;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        @Override
        public Integer sanitize(Object raw) {
            double value;
            if (raw instanceof Number n) {
                value = n.doubleValue();
            } else if (raw instanceof String s) {
                try {
                    value = Double.parseDouble(s.trim().replace(',', '.'));
                } catch (NumberFormatException e) {
                    return def();
                }
            } else {
                return def();
            }
            if (!Double.isFinite(value)) {
                return def();
            }
            return (int) Math.clamp(Math.round(value), min, max);
        }
    }

    public static final class Bool extends Param<Boolean> {
        Bool(String group, String id, boolean def) {
            super(group, id, def);
        }

        @Override
        public Boolean sanitize(Object raw) {
            if (raw instanceof Boolean b) {
                return b;
            }
            if (raw instanceof String s) {
                if (s.equalsIgnoreCase("true")) {
                    return true;
                }
                if (s.equalsIgnoreCase("false")) {
                    return false;
                }
            }
            return def();
        }
    }

    /** One of a fixed list of names. */
    public static final class Choice extends Param<String> {
        private final List<String> choices;

        Choice(String group, String id, String def, String... choices) {
            super(group, id, def);
            this.choices = List.of(choices);
        }

        public List<String> choices() {
            return choices;
        }

        @Override
        public String sanitize(Object raw) {
            if (raw instanceof String s) {
                String name = s.trim().toLowerCase(Locale.ROOT);
                if (choices.contains(name)) {
                    return name;
                }
            }
            return def();
        }

        /** Translation key of one choice. */
        public String choiceKey(String choice) {
            return langKey() + "." + choice;
        }
    }
}
