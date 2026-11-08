package net.shattergui.editor;

public record Selection(Kind kind, int index) {
    public enum Kind {
        EFFECT, APPEARANCE, EDGE, LAYER, EMITTER, SOUND
    }

    public static final Selection EFFECT = new Selection(Kind.EFFECT, 0);
    public static final Selection APPEARANCE = new Selection(Kind.APPEARANCE, 0);
    public static final Selection EDGE = new Selection(Kind.EDGE, 0);

    public String list() {
        return listOf(kind);
    }

    public static String listOf(Kind kind) {
        return switch (kind) {
            case LAYER -> "layers";
            case EMITTER -> "emitters";
            case SOUND -> "sounds";
            default -> null;
        };
    }

    public String path() {
        return switch (kind) {
            case EFFECT -> "";
            case APPEARANCE -> "appearance";
            case EDGE -> "modifiers.edge";
            default -> list() + "[" + index + "]";
        };
    }
}
