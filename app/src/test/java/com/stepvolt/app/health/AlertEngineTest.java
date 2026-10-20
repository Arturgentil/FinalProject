package com.stepvolt.app.health;

import com.stepvolt.app.ble.SensorSample;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AlertEngineTest {

    private static SensorSample sample(String key, double value) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put(key, value);
        return new SensorSample(0, m, "{}");
    }

    private static AlertEngine.Limits limits() {
        return new AlertEngine.Limits();
    }

    @Test
    public void needsThreeConsecutiveReadingsBeforeFiring() {
        AlertEngine e = new AlertEngine();
        assertTrue(e.check(limits(), sample("bpm", 190), 1000).isEmpty());
        assertTrue(e.check(limits(), sample("bpm", 191), 2000).isEmpty());
        List<AlertEngine.Fired> out = e.check(limits(), sample("bpm", 192), 3000);
        assertEquals(1, out.size());
        assertEquals("bpm_high", out.get(0).key);
    }

    @Test
    public void normalReadingResetsStreak() {
        AlertEngine e = new AlertEngine();
        e.check(limits(), sample("bpm", 190), 1000);
        e.check(limits(), sample("bpm", 190), 2000);
        e.check(limits(), sample("bpm", 100), 3000);
        assertTrue(e.check(limits(), sample("bpm", 190), 4000).isEmpty());
    }

    @Test
    public void silenceWindowPreventsSpam() {
        AlertEngine e = new AlertEngine();
        for (int i = 0; i < 3; i++) e.check(limits(), sample("bpm", 190), 1000L * i);
        int fired = 0;
        for (int i = 3; i < 60; i++) fired += e.check(limits(), sample("bpm", 190), 1000L * i).size();
        assertEquals(0, fired);
        assertEquals(1, e.check(limits(), sample("bpm", 190), 3 * 60 * 1000L + 5000).size());
    }

    @Test
    public void disabledAlertsNeverFire() {
        AlertEngine e = new AlertEngine();
        AlertEngine.Limits l = limits();
        l.enabled = false;
        for (int i = 0; i < 10; i++) assertTrue(e.check(l, sample("bpm", 250), i * 1000L).isEmpty());
    }

    @Test
    public void lowOxygenFiresAsCritical() {
        AlertEngine e = new AlertEngine();
        List<AlertEngine.Fired> out = null;
        for (int i = 0; i < 3; i++) out = e.check(limits(), sample("spo2", 88), i * 1000L);
        assertEquals(1, out.size());
        assertEquals(2, out.get(0).record.severity);
        assertEquals("spo2_low", out.get(0).key);
    }

    @Test
    public void zeroReadingsAreTreatedAsNoSignal() {
        AlertEngine e = new AlertEngine();
        for (int i = 0; i < 5; i++) assertTrue(e.check(limits(), sample("bpm", 0), i * 1000L).isEmpty());
        for (int i = 0; i < 5; i++) assertTrue(e.check(limits(), sample("spo2", 0), i * 1000L).isEmpty());
    }

    @Test
    public void respectsUserLimits() {
        AlertEngine e = new AlertEngine();
        AlertEngine.Limits l = limits();
        l.bpmMax = 150;
        List<AlertEngine.Fired> out = null;
        for (int i = 0; i < 3; i++) out = e.check(l, sample("bpm", 160), i * 1000L);
        assertEquals(1, out.size());
        assertEquals(150.0, out.get(0).record.threshold, 1e-9);
    }

    @Test
    public void lowBatteryFiresAsInfo() {
        AlertEngine e = new AlertEngine();
        List<AlertEngine.Fired> out = null;
        for (int i = 0; i < 3; i++) out = e.check(limits(), sample("battery", 9), i * 1000L);
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).record.severity);
    }
}
