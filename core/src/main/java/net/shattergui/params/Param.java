package net.shattergui.params;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Locale;

public abstract sealed class Param<T> permits Param.Float, Param.Int, Param.Bool, Param.Choice {
    private final String group;
    private final String id;
    private final T def;
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

    public String langKey() {
        return "param.shatter_gui." + (langGroup != null ? langGroup : group) + "." + id;
    }

    void sharedLang(String group) {
        langGroup = group;
    }

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

        public String choiceKey(String choice) {
            return langKey() + "." + choice;
        }
    }
}
