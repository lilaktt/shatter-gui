package net.shattergui.platform;

import java.util.List;

/** The game's font: measuring, cutting and wrapping text. */
public interface UiFont {
    int width(String text);

    int width(Text text);

    int width(TextLine line);

    /** The longest start of {@code text} that fits in {@code width}. */
    String plainSubstrByWidth(String text, int width);

    /** {@code text} wrapped to lines at most {@code width} wide. */
    List<TextLine> split(Text text, int width);
}
