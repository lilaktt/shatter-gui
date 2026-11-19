package net.shattergui.platform.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import net.shattergui.platform.Text;
import net.shattergui.platform.TextLine;
import net.shattergui.platform.Texts;
import net.shattergui.platform.UiFont;

import java.util.ArrayList;
import java.util.List;

final class Texts121 implements Texts, UiFont {
    record Line(FormattedCharSequence sequence) implements TextLine {
    }

    @Override
    public Text translatable(String key, Object... args) {
        return ComponentText.translatable(key, args);
    }

    @Override
    public Text literal(String text) {
        return ComponentText.literal(text);
    }

    @Override
    public Text empty() {
        return ComponentText.empty();
    }

    @Override
    public boolean has(String key) {
        return Language.getInstance().has(key);
    }

    @Override
    public String getOrDefault(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    @Override
    public UiFont font() {
        return this;
    }

    @Override
    public int width(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    @Override
    public int width(Text text) {
        return Minecraft.getInstance().font.width(ComponentText.unwrap(text));
    }

    @Override
    public int width(TextLine line) {
        return Minecraft.getInstance().font.width(((Line) line).sequence());
    }

    @Override
    public String plainSubstrByWidth(String text, int width) {
        return Minecraft.getInstance().font.plainSubstrByWidth(text, width);
    }

    @Override
    public List<TextLine> split(Text text, int width) {
        List<FormattedCharSequence> lines = Minecraft.getInstance().font.split(ComponentText.unwrap(text), width);
        List<TextLine> out = new ArrayList<>(lines.size());
        for (FormattedCharSequence line : lines) {
            out.add(new Line(line));
        }
        return out;
    }
}
