package net.shattergui.params;

public interface ParamStore {
    <T> T get(Param<T> param);

    <T> T set(Param<T> param, Object raw);
}
