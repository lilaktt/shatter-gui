package net.shattergui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The open/close state machine of pop-ups. */
class RevealTest {
    private static final float FRAME = 1f / 60f;

    private static void run(Reveal r, float seconds) {
        for (float t = 0; t < seconds; t += FRAME) {
            r.tick(FRAME);
        }
    }

    @Test
    void opensAndCloses() {
        Reveal r = new Reveal();
        assertEquals(Reveal.State.CLOSED, r.state());
        assertFalse(r.visible());
        assertTrue(r.open());
        assertEquals(Reveal.State.OPENING, r.state());
        run(r, Reveal.OPEN_SECONDS + 0.05f);
        assertEquals(Reveal.State.OPEN, r.state());
        assertEquals(1f, r.progress());
        assertTrue(r.close());
        assertEquals(Reveal.State.CLOSING, r.state());
        assertTrue(r.visible(), "still drawn while closing");
        run(r, Reveal.CLOSE_SECONDS + 0.05f);
        assertEquals(Reveal.State.CLOSED, r.state());
        assertEquals(0f, r.progress());
    }

    @Test
    void clickOnTheHeaderOfAnOpenListClosesIt() {
        Reveal r = new Reveal();
        r.toggle();
        run(r, 1f);
        r.toggle();
        assertEquals(Reveal.State.CLOSING, r.state(), "a second click closes, it does not open again");
        assertFalse(r.wantsOpen());
    }

    @Test
    void secondClickDuringAnimationTurnsRoundFromWhereItIs() {
        Reveal r = new Reveal();
        r.open();
        run(r, Reveal.OPEN_SECONDS / 2);
        float mid = r.progress();
        assertTrue(mid > 0.3f && mid < 0.8f, "half open: " + mid);
        r.toggle();
        assertEquals(Reveal.State.CLOSING, r.state());
        assertEquals(mid, r.progress(), 1e-6f, "no restart: same progress");
        r.tick(FRAME);
        assertTrue(r.progress() < mid, "now going back");
        float back = r.progress();
        r.toggle();
        assertEquals(Reveal.State.OPENING, r.state());
        assertEquals(back, r.progress(), 1e-6f, "turned round again without a jump");
        assertTrue(Math.abs(r.eased() - Anim.easeOut(back)) < 1e-6f);
    }

    @Test
    void closingIsShorterThanOpening() {
        assertTrue(Reveal.CLOSE_SECONDS < Reveal.OPEN_SECONDS);
        Reveal r = new Reveal();
        r.open();
        int openFrames = 0;
        while (r.tick(FRAME) != Reveal.State.OPEN) {
            openFrames++;
        }
        r.close();
        int closeFrames = 0;
        while (r.tick(FRAME) != Reveal.State.CLOSED) {
            closeFrames++;
        }
        assertTrue(closeFrames < openFrames, closeFrames + " frames to close, " + openFrames + " to open");
    }

    @Test
    void repeatedRequestsChangeNothing() {
        Reveal r = new Reveal();
        assertFalse(r.close(), "closing a closed one");
        r.open();
        run(r, 0.05f);
        float p = r.progress();
        assertFalse(r.open(), "opening an opening one does not restart it");
        assertEquals(p, r.progress());
        r.close();
        assertFalse(r.close());
        r.reset();
        assertEquals(Reveal.State.CLOSED, r.state());
        assertEquals(0f, r.progress());
        assertEquals(Reveal.State.CLOSED, r.tick(Float.NaN), "a bad frame time is ignored");
    }

    @Test
    void closeRightAfterOpenWithoutAFrameEndsClosed() {
        Reveal r = new Reveal();
        r.open();
        r.close();
        assertEquals(Reveal.State.CLOSED, r.state(), "nothing was shown yet, nothing to animate");
    }
}
