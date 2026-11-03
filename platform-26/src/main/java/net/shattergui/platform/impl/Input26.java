package net.shattergui.platform.impl;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.shattergui.platform.CharInput;
import net.shattergui.platform.KeyInput;

/** The game's input events in core's terms; every answer is the game's own. */
public final class Input26 {
    private Input26() {
    }

    /** A key press. */
    public record Key(KeyEvent event) implements KeyInput {
        @Override
        public int key() {
            return event.key();
        }

        @Override
        public boolean hasShiftDown() {
            return event.hasShiftDown();
        }

        @Override
        public boolean hasControlDown() {
            return event.hasControlDown();
        }

        @Override
        public boolean isSelection() {
            return event.isSelection();
        }

        @Override
        public boolean isConfirmation() {
            return event.isConfirmation();
        }

        @Override
        public boolean isUp() {
            return event.isUp();
        }

        @Override
        public boolean isDown() {
            return event.isDown();
        }

        @Override
        public boolean isLeft() {
            return event.isLeft();
        }

        @Override
        public boolean isRight() {
            return event.isRight();
        }

        @Override
        public boolean isSelectAll() {
            return event.isSelectAll();
        }

        @Override
        public boolean isCopy() {
            return event.isCopy();
        }

        @Override
        public boolean isCut() {
            return event.isCut();
        }

        @Override
        public boolean isPaste() {
            return event.isPaste();
        }
    }

    /** A typed character. */
    public record Char(CharacterEvent event) implements CharInput {
        @Override
        public int codepoint() {
            return event.codepoint();
        }

        @Override
        public String codepointAsString() {
            return event.codepointAsString();
        }
    }
}
