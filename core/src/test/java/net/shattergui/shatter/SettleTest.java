package net.shattergui.shatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettleTest {
    @Test
    void motionlessOnABlockSettlesAfterAFewTicks() {
        int still = 0;
        for (int tick = 1; tick < Settle.STILL_TICKS; tick++) {
            still = Settle.stillTicks(still, false);
            assertFalse(Settle.settled(still, true, 20 + tick, false), "not yet after " + tick + " ticks");
        }
        still = Settle.stillTicks(still, false);
        assertTrue(Settle.settled(still, true, 30, false));
    }

    @Test
    void motionlessInTheAirIsNotSettledUntilOld() {
        assertFalse(Settle.settled(50, false, Settle.AGE_TICKS - 1, false));
        assertTrue(Settle.settled(50, false, Settle.AGE_TICKS, false));
    }

    @Test
    void movingIsNotSettledUntilOld() {
        int still = Settle.stillTicks(10, true);
        assertEquals(0, still);
        assertFalse(Settle.settled(still, true, 40, false));
        assertTrue(Settle.settled(still, false, Settle.AGE_TICKS + 5, false));
    }

    @Test
    void insideABlockNeverSettles() {
        assertFalse(Settle.settled(100, true, 30, true));
        assertFalse(Settle.settled(100, true, Settle.AGE_TICKS * 3, true));
    }

    @Test
    void motionThresholds() {
        assertFalse(Settle.moved(0, 0, 0, 0f));
        assertFalse(Settle.moved(5.0e-5, 0, 0, 0f));
        assertTrue(Settle.moved(0, -2.0e-4, 0, 0f));
        assertTrue(Settle.moved(0, 0, 0, turn(0.05)));
        assertFalse(Settle.moved(0, 0, 0, turn(0.005)));
    }

    private static float turn(double degrees) {
        double[] start = normalize(new double[]{0.31, -0.62, 0.18, 0.69});
        double half = Math.toRadians(degrees) / 2;
        double[] axis = normalize(new double[]{0.3, 0.8, -0.5, 0});
        double[] step = {axis[0] * Math.sin(half), axis[1] * Math.sin(half), axis[2] * Math.sin(half), Math.cos(half)};
        double[] end = multiply(step, start);
        float sin = Settle.halfTurnSin((float) start[0], (float) start[1], (float) start[2], (float) start[3],
                (float) end[0], (float) end[1], (float) end[2], (float) end[3]);
        assertEquals(Math.sin(half), sin, 2e-6);
        assertEquals(sin, Settle.halfTurnSin((float) start[0], (float) start[1], (float) start[2], (float) start[3],
                (float) -end[0], (float) -end[1], (float) -end[2], (float) -end[3]), 1e-6);
        return sin;
    }

    private static double[] multiply(double[] a, double[] b) {
        return new double[]{
                a[3] * b[0] + b[3] * a[0] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] + b[3] * a[1] + a[2] * b[0] - a[0] * b[2],
                a[3] * b[2] + b[3] * a[2] + a[0] * b[1] - a[1] * b[0],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    private static double[] normalize(double[] q) {
        double n = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        return new double[]{q[0] / n, q[1] / n, q[2] / n, q[3] / n};
    }

    @Test
    void recheckOnlyAtTheMomentsThatCanChangeIt() {
        assertTrue(Settle.recheck(Settle.STILL_TICKS, false, 20));
        assertFalse(Settle.recheck(Settle.STILL_TICKS + 1, false, 20));
        assertTrue(Settle.recheck(0, true, Settle.AGE_TICKS));
        assertTrue(Settle.recheck(0, true, Settle.AGE_TICKS + 7));
        assertFalse(Settle.recheck(0, true, Settle.AGE_TICKS - 1));
    }

    @Test
    void biasMovesAlongTheLineOfSight() {
        float x = 1.5f;
        float y = -1.2f;
        float z = -4f;
        float k = Settle.towardsCamera(x, y, z, Settle.BIAS);
        float before = (float) Math.sqrt(x * x + y * y + z * z);
        float after = before * k;
        assertEquals(Settle.BIAS, before - after, 1e-5);
        assertEquals(x / z, x * k / (z * k), 1e-6);
        assertEquals(y / z, y * k / (z * k), 1e-6);
        assertEquals(1f, Settle.towardsCamera(0.01f, 0f, -0.02f, Settle.BIAS));
        assertEquals(1f, Settle.towardsCamera(1f, 1f, 1f, 0f));
    }
}
