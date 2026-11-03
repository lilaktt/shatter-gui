package net.shattergui.platform;

/** Text and translations of the running game. */
public interface Texts {
    /** A translation with arguments ({@link Text} arguments keep their formatting). */
    Text translatable(String key, Object... args);

    Text literal(String text);

    Text empty();

    /** Whether the current language (or its English fallback) has the key. */
    boolean has(String key);

    /** The translation of the key in the current language, or the key itself. */
    String getOrDefault(String key);

    /** The game's font. */
    UiFont font();
}
