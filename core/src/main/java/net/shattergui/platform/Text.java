package net.shattergui.platform;

/**
 * A piece of text the game can draw: a translation (resolved when it is drawn, in the current language), literal text,
 * or a mix. The platform wraps its own text type; core builds it only through these methods.
 */
public interface Text {
    static Text translatable(String key, Object... args) {
        return Platform.get().texts().translatable(key, args);
    }

    static Text literal(String text) {
        return Platform.get().texts().literal(text);
    }

    static Text empty() {
        return Platform.get().texts().empty();
    }

    /** The text as it reads now (translated). */
    String getString();

    /** A copy that can be changed without changing this one. */
    Text copy();

    /** Adds to the end of this text and returns it. */
    Text append(String text);

    /** Adds to the end of this text and returns it. */
    Text append(Text text);

    /** Makes this text bold and returns it. */
    Text bold();

    /** The translation key if this is a translation, otherwise null. */
    String translationKey();
}
