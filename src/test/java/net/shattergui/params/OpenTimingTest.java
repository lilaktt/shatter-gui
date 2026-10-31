package net.shattergui.params;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Openings are short: at most 0.35 s for quick effects and 0.5 s for large ones with the default opening speed. */
class OpenTimingTest {
    @Test
    void defaultsAreShort() {
        float speed = Params.OPEN_SPEED.def();
        assertEquals(1.5f, speed);
        for (int natural : new int[]{6, 9, 12, 18, 40, 400}) {
            assertTrue(OpenTiming.ticks(natural, true, speed) <= 7, "quick " + natural + " ≤ 0.35 s");
            assertTrue(OpenTiming.ticks(natural, false, speed) <= 10, "large " + natural + " ≤ 0.5 s");
        }
    }

    @Test
    void openSpeedIsAMultiplierWithinLimits() {
        assertEquals(15, OpenTiming.ticks(40, false, 1f));
        assertEquals(30, OpenTiming.ticks(40, false, 0.5f));
        assertEquals(5, OpenTiming.ticks(40, false, 3f));
        // Never shorter than the minimum, also with a very short effect (reduce motion) or the fastest setting.
        assertEquals(OpenTiming.MIN_TICKS, OpenTiming.ticks(2, true, 3f));
        assertEquals(0.5f, Params.OPEN_SPEED.min());
        assertEquals(3f, Params.OPEN_SPEED.max());
    }
}
