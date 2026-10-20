package com.stepvolt.app.energy;

import org.junit.Test;

import static org.junit.Assert.*;

public class ShoeWearTest {

    @Test
    public void levelsFollowDistance() {
        assertEquals(ShoeWear.Level.GOOD, ShoeWear.level(100, 700));
        assertEquals(ShoeWear.Level.ATTENTION, ShoeWear.level(500, 700));
        assertEquals(ShoeWear.Level.SOON, ShoeWear.level(650, 700));
        assertEquals(ShoeWear.Level.REPLACE, ShoeWear.level(700, 700));
    }

    @Test
    public void batteryTrackerCountsOnlyDischarge() {
        ShoeWear.BatteryTracker t = new ShoeWear.BatteryTracker(0);
        t.onReading(100);
        t.onReading(80);   // -20
        t.onReading(100);  // recarga: não conta
        t.onReading(90);   // -10
        assertEquals(30.0, t.dischargedPoints(), 1e-9);
        assertEquals(0.3, t.equivalentCycles(), 1e-9);
    }

    @Test
    public void batteryTrackerIgnoresInvalidReadings() {
        ShoeWear.BatteryTracker t = new ShoeWear.BatteryTracker(0);
        t.onReading(-1);
        t.onReading(150);
        t.onReading(50);
        t.onReading(40);
        assertEquals(10.0, t.dischargedPoints(), 1e-9);
    }
}
