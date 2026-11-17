package net.shattergui.shatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReassembleTest {
    @Test
    void enoughLivePiecesThatAreStillVisible() {
        assertTrue(Reassemble.eligible(120, 120, 1.0));
        assertTrue(Reassemble.eligible(60, 120, 0.35));
        assertFalse(Reassemble.eligible(59, 120, 1.0), "too few pieces left");
        assertFalse(Reassemble.eligible(120, 120, 0.3), "too faded");
        assertFalse(Reassemble.eligible(0, 0, 1.0), "nothing to gather");
    }

    @Test
    void durationFollowsOpenSpeedAndEffectSpeed() {
        assertEquals(12, Reassemble.duration(1.5f, 1f));
        assertEquals(18, Reassemble.duration(1f, 1f));
        assertEquals(9, Reassemble.duration(1f, 0.5f), "effect speed x2 halves it");
        assertEquals(Reassemble.MIN_TICKS, Reassemble.duration(3f, 0.25f));
    }

    @Test
    void easingStartsAndEndsGently() {
        assertEquals(0f, Reassemble.ease(0f));
        assertEquals(1f, Reassemble.ease(1f));
        assertEquals(0.5f, Reassemble.ease(0.5f), 1e-6f);
        assertTrue(Reassemble.ease(0.1f) < 0.1f, "slow start");
        assertTrue(Reassemble.ease(0.9f) > 0.9f, "slow end");
        float last = 0f;
        for (int i = 1; i <= 100; i++) {
            float e = Reassemble.ease(i / 100f);
            assertTrue(e >= last);
            last = e;
        }
    }

    @Test
    void gonePiecesAppearInTheLastThird() {
        assertEquals(0f, Reassemble.appear(0.5f));
        assertEquals(0f, Reassemble.appear(2f / 3f), 1e-6f);
        assertTrue(Reassemble.appear(0.8f) > 0f && Reassemble.appear(0.8f) < 1f);
        assertEquals(1f, Reassemble.appear(1f), 1e-6f);
    }

    @Test
    void fadedPiecesBecomeOpaqueOnTheWay() {
        assertEquals(0.4f, Reassemble.alpha(0.4f, 0f), 1e-6f);
        assertEquals(0.7f, Reassemble.alpha(0.4f, 0.5f), 1e-6f);
        assertEquals(1f, Reassemble.alpha(0.4f, 1f), 1e-6f);
    }

    @Test
    void theRealScreenTakesOverInAFewTicks() {
        assertEquals(1f, Reassemble.fade(0));
        assertEquals(0.5f, Reassemble.fade(Reassemble.FADE_TICKS / 2.0), 1e-6f);
        assertEquals(0f, Reassemble.fade(Reassemble.FADE_TICKS));
    }

    @Test
    void lightGoesToFullOnThePlane() {
        int dark = (3 << 20) | (2 << 4);
        assertEquals(dark, Reassemble.light(dark, 0f));
        assertEquals((15 << 20) | (15 << 4), Reassemble.light(dark, 1f));
    }
}
