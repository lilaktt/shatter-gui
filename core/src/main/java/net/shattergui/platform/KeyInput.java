package net.shattergui.platform;

public interface KeyInput {
    int key();

    boolean hasShiftDown();

    boolean hasControlDown();

    boolean isSelection();

    boolean isConfirmation();

    boolean isUp();

    boolean isDown();

    boolean isLeft();

    boolean isRight();

    boolean isSelectAll();

    boolean isCopy();

    boolean isCut();

    boolean isPaste();
}
