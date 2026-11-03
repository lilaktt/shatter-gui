package net.shattergui.platform;

/** A typed character. */
public interface CharInput {
    int codepoint();

    String codepointAsString();
}
