package net.shattergui.shatter;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SightLineTest {
    private static SightLine.Blocks world(int[]... solid) {
        Set<String> cells = new HashSet<>();
        for (int[] c : solid) {
            cells.add(c[0] + "," + c[1] + "," + c[2]);
        }
        return (x, y, z) -> cells.contains(x + "," + y + "," + z);
    }

    @Test
    void aWallBetweenHides() {
        SightLine.Blocks wall = world(new int[]{0, 0, 3});
        assertTrue(SightLine.blocked(0.5, 0.5, 0.5, 0.5, 0.5, 6.5, wall));
        assertTrue(SightLine.blocked(0.5, 0.5, 6.5, 0.5, 0.5, 0.5, wall), "both directions");
    }

    @Test
    void openAirDoesNot() {
        SightLine.Blocks wall = world(new int[]{1, 0, 3});
        assertFalse(SightLine.blocked(0.5, 0.5, 0.5, 0.5, 0.5, 6.5, wall));
        assertFalse(SightLine.blocked(0.5, 0.5, 0.5, 0.5, 0.5, 2.9, world(new int[]{0, 0, 3})), "the wall is beyond the piece");
    }

    @Test
    void theCameraAndThePieceCellsDoNotCount() {
        SightLine.Blocks both = world(new int[]{0, 0, 0}, new int[]{0, 0, 5});
        assertFalse(SightLine.blocked(0.5, 0.5, 0.5, 0.5, 0.5, 5.5, both));
    }

    @Test
    void diagonalsAndNegativeCoordinates() {
        SightLine.Blocks roof = world(new int[]{-3, 2, -3});
        assertTrue(SightLine.blocked(-0.5, 0.5, -0.5, -5.5, 4.5, -5.5, roof));
        assertFalse(SightLine.blocked(-0.5, 0.5, -0.5, -5.5, 0.5, -5.5, roof));
    }

    @Test
    void samePointIsNeverBlocked() {
        assertFalse(SightLine.blocked(2.5, 2.5, 2.5, 2.5, 2.5, 2.5, (x, y, z) -> true));
    }
}
