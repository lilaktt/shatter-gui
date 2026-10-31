package net.shattergui.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NaN and infinite mouse positions mean "no mouse"; repeated UI failures are logged once. */
class MouseGuardTest {
    @Test
    void brokenPositionsAreNoMouse() {
        assertTrue(MouseGuard.valid(12.5, 300));
        assertTrue(MouseGuard.valid(-4, -4));
        assertFalse(MouseGuard.valid(Double.NaN, 3));
        assertFalse(MouseGuard.valid(3, Double.NaN));
        assertFalse(MouseGuard.valid(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertFalse(MouseGuard.valid(Double.NEGATIVE_INFINITY, 0));
        assertFalse(MouseGuard.valid(1e12, 0));
        // An infinite double cast to int (what the game hands to rendering).
        int inf = (int) Double.POSITIVE_INFINITY;
        int negInf = (int) Double.NEGATIVE_INFINITY;
        int nan = (int) Double.NaN;
        assertFalse(MouseGuard.valid(inf, inf));
        assertFalse(MouseGuard.valid(negInf, 10));
        assertTrue(MouseGuard.valid(nan, nan), "NaN cast to int is 0, a real position");
        assertEquals(MouseGuard.NONE, MouseGuard.sanitize(inf));
        assertEquals(MouseGuard.NONE, MouseGuard.sanitize(negInf));
        assertEquals(240, MouseGuard.sanitize(240));
        // "No mouse" is outside every widget.
        Widget w = new Widget() {
            @Override
            protected void draw(Ui ui) {
            }
        };
        w.x = 0;
        w.y = 0;
        w.w = 4000;
        w.h = 4000;
        assertFalse(w.contains(MouseGuard.NONE, MouseGuard.NONE));
    }

    @Test
    void failuresAreLoggedOncePerKind() {
        List<String> lines = new ArrayList<>();
        FailureLog log = new FailureLog((where, error) -> lines.add(where));
        RuntimeException a = new IllegalStateException("boom");
        for (int frame = 0; frame < 100; frame++) {
            log.report("draw Slider", a);
        }
        log.report("draw Slider", new IllegalStateException("other"));
        log.report("draw Button", a);
        assertEquals(3, lines.size());
        assertEquals(102, log.count());
        assertEquals(3, log.kinds().size());
    }
}
