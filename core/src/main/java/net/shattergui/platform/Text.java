package net.shattergui.platform;

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

    String getString();

    Text copy();

    Text append(String text);

    Text append(Text text);

    Text bold();

    String translationKey();
}
