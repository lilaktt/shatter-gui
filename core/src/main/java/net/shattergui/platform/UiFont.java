package net.shattergui.platform;

import java.util.List;

public interface UiFont {
    int width(String text);

    int width(Text text);

    int width(TextLine line);

    String plainSubstrByWidth(String text, int width);

    List<TextLine> split(Text text, int width);
}
