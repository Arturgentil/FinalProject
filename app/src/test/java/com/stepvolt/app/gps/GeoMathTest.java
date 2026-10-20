package com.stepvolt.app.gps;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class GeoMathTest {

    @Test
    public void haversineKnownDistance() {
        // 0,001° de latitude ≈ 111,2 m
        double d = GeoMath.haversine(-15.0, -47.0, -14.999, -47.0);
        assertEquals(111.2, d, 1.0);
    }

    private static List<GeoMath.Fix> straightLine(int n, long stepMs, double stepLat) {
        List<GeoMath.Fix> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new GeoMath.Fix(i * stepMs, -15.0 + i * stepLat, -47.0, 1000 + i, 5f));
        }
        return out;
    }

    @Test
    public void walkingLineAddsUpDistanceAndPace() {
        // 60 pontos a cada 10 s, ~14 m cada (1,4 m/s): ~830 m em ~590 s
        GeoMath.Track t = GeoMath.analyze(straightLine(60, 10000, 0.000126));
        assertTrue(t.distanceM > 800 && t.distanceM < 880);
        double pace = t.paceSecPerKm();
        assertTrue(pace > 650 && pace < 800);
        assertTrue(t.elevationGainM > 40);
    }

    @Test
    public void lowAccuracyPointsAreRejected() {
        List<GeoMath.Fix> fixes = new ArrayList<>();
        fixes.add(new GeoMath.Fix(0, -15.0, -47.0, 1000, 5f));
        fixes.add(new GeoMath.Fix(10000, -14.99, -47.0, 1000, 80f)); // longe e impreciso
        fixes.add(new GeoMath.Fix(20000, -14.99987, -47.0, 1000, 5f));
        GeoMath.Track t = GeoMath.analyze(fixes);
        assertEquals(1, t.rejectedPoints);
        assertTrue(t.distanceM < 20);
    }

    @Test
    public void impossibleJumpIsRejected() {
        List<GeoMath.Fix> fixes = new ArrayList<>();
        fixes.add(new GeoMath.Fix(0, -15.0, -47.0, 1000, 5f));
        fixes.add(new GeoMath.Fix(1000, -14.9, -47.0, 1000, 5f)); // 11 km em 1 s
        GeoMath.Track t = GeoMath.analyze(fixes);
        assertEquals(0.0, t.distanceM, 1e-9);
        assertEquals(1, t.rejectedPoints);
    }

    @Test
    public void standingStillAddsNoDistance() {
        List<GeoMath.Fix> fixes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            // jitter de ~1 m
            fixes.add(new GeoMath.Fix(i * 2000L, -15.0 + (i % 2) * 0.00001, -47.0, 1000, 8f));
        }
        GeoMath.Track t = GeoMath.analyze(fixes);
        assertEquals(0.0, t.distanceM, 1e-9);
        assertEquals(0.0, t.paceSecPerKm(), 1e-9);
    }

    @Test
    public void emptyAndSinglePointAreSafe() {
        assertEquals(0.0, GeoMath.analyze(null).distanceM, 1e-9);
        List<GeoMath.Fix> one = new ArrayList<>();
        one.add(new GeoMath.Fix(0, -15, -47, 0, 5f));
        assertEquals(0.0, GeoMath.analyze(one).distanceM, 1e-9);
    }

    @Test
    public void formatPace() {
        assertEquals("5:42", GeoMath.formatPace(342));
        assertEquals("6:05", GeoMath.formatPace(365));
        assertEquals("—", GeoMath.formatPace(0));
        assertEquals("—", GeoMath.formatPace(99999));
    }

    @Test
    public void mwhPerKmNeedsEnoughDistance() {
        assertEquals(0.0, GeoMath.mwhPerKm(50, 50), 1e-9);
        assertEquals(25.0, GeoMath.mwhPerKm(50, 2000), 1e-9);
    }
}
