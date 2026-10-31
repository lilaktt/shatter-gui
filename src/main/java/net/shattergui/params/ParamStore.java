package net.shattergui.params;

/** Something parameter values can be read from and written to (the config, a custom effect being edited). */
public interface ParamStore {
    <T> T get(Param<T> param);

    /** Sanitizes (clamps, validates) the input first; returns the stored value. */
    <T> T set(Param<T> param, Object raw);
}
