package net.shattergui.platform.impl;

import net.minecraft.client.gui.screens.Screen;
import net.shattergui.platform.CharInput;
import net.shattergui.platform.KeyInput;

/**
 * 1.21.1's input (plain numbers) in core's terms, answering as 26.1's key events do (see {@link KeyInput}): modifiers
 * from the event's own bits; the edit shortcuts through 1.21.1's own checks (which read the keyboard's current state).
 */
public final class Input121 {
    private static final int SHIFT = 1;
    private static final int CONTROL = 2;

    private Input121() {
    }

    public record Key(int key, int scancode, int modifiers) implements KeyInput {
        @Override
        public boolean hasShiftDown() {
            return (modifiers & SHIFT) != 0;
        }

        @Override
        public boolean hasControlDown() {
            return (modifiers & CONTROL) != 0;
        }

        @Override
        public boolean isSelection() {
            return key == 257 || key == 32 || key == 335;
        }

        @Override
        public boolean isConfirmation() {
            return key == 257 || key == 335;
        }

        @Override
        public boolean isUp() {
            return key == 265;
        }

        @Override
        public boolean isDown() {
            return key == 264;
        }

        @Override
        public boolean isLeft() {
            return key == 263;
        }

        @Override
        public boolean isRight() {
            return key == 262;
        }

        @Override
        public boolean isSelectAll() {
            return Screen.isSelectAll(key);
        }

        @Override
        public boolean isCopy() {
            return Screen.isCopy(key);
        }

        @Override
        public boolean isCut() {
            return Screen.isCut(key);
        }

        @Override
        public boolean isPaste() {
            return Screen.isPaste(key);
        }
    }

    public record Char(char character) implements CharInput {
        @Override
        public int codepoint() {
            return character;
        }

        @Override
        public String codepointAsString() {
            return String.valueOf(character);
        }
    }
}
