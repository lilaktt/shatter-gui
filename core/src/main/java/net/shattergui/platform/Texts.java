package net.shattergui.platform;

public interface Texts {
    Text translatable(String key, Object... args);

    Text literal(String text);

    Text empty();

    boolean has(String key);

    String getOrDefault(String key);

    UiFont font();
}
