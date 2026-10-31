package net.shattergui.editor;

/** What the inspector shows: the effect's settings, its look, the edge flight, or one layer, emitter or sound. */
public record Selection(Kind kind, int index) {
    public enum Kind {
        EFFECT, APPEARANCE, EDGE, LAYER, EMITTER, SOUND
    }

    public static final Selection EFFECT = new Selection(Kind.EFFECT, 0);
    public static final Selection APPEARANCE = new Selection(Kind.APPEARANCE, 0);
    public static final Selection EDGE = new Selection(Kind.EDGE, 0);

    /** The list in the effect's JSON for list kinds ("layers", "emitters", "sounds"), else null. */
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

    /** Path of the selected item ("layers[2]"), or of the section ("" for the effect, "appearance", "modifiers.edge"). */
    public String path() {
        return switch (kind) {
            case EFFECT -> "";
            case APPEARANCE -> "appearance";
            case EDGE -> "modifiers.edge";
            default -> list() + "[" + index + "]";
        };
    }
}
