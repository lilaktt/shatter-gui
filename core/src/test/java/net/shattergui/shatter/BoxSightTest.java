package net.shattergui.shatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxSightTest {
    private static boolean player(double px, double py, double pz) {
        return BoxSight.blocked(0.5, 1.6, 4.0, px, py, pz, 0.2, 0.0, -0.3, 0.8, 1.8, 0.3);
    }

    @Test
    void aPieceBehindTheBoxIsHidden() {
        assertTrue(player(0.5, 1.6, -1.5));
        assertTrue(player(0.5, 0.5, -1.5), "lower body");
    }

    @Test
    void aPieceInFrontOfTheBoxIsNot() {
        assertFalse(player(0.5, 1.6, 2.0));
        assertFalse(player(0.5, 1.6, 0.31), "just in front");
    }

    @Test
    void aPieceBesideTheBoxIsNot() {
        assertFalse(player(3.0, 1.6, -1.5));
        assertFalse(player(0.5, 4.0, -1.5), "above, seen past the head");
    }

    @Test
    void aPieceInsideTheBoxCounts() {
        assertTrue(player(0.5, 1.0, 0.0));
    }

    @Test
    void theCameraInsideTheBoxDoesNotHide() {
        assertFalse(BoxSight.blocked(0.5, 1.0, 0.0, 0.5, 1.0, -3.0, 0.2, 0.0, -0.3, 0.8, 1.8, 0.3));
    }

    @Test
    void anAxisParallelRayOutsideTheSlabMisses() {
        assertFalse(BoxSight.blocked(2.0, 1.0, 4.0, 2.0, 1.0, -4.0, 0.2, 0.0, -0.3, 0.8, 1.8, 0.3));
        assertTrue(BoxSight.blocked(0.5, 1.0, 4.0, 0.5, 1.0, -4.0, 0.2, 0.0, -0.3, 0.8, 1.8, 0.3));
    }
}
