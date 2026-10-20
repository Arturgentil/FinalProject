package com.stepvolt.app.safety;

import org.junit.Test;

import static org.junit.Assert.*;

public class FallDetectorTest {

    private int triggers;

    private FallDetector detector() {
        triggers = 0;
        FallDetector d = new FallDetector(1800, 8000, 120000);
        d.setListener((at, impact) -> triggers++);
        return d;
    }

    @Test
    public void impactFollowedByStillnessTriggers() {
        FallDetector d = detector();
        d.onSample(1000, 2500, 0);
        for (long t = 2000; t <= 12000; t += 1000) d.onSample(t, 0, 0);
        assertEquals(1, triggers);
    }

    @Test
    public void impactFollowedByWalkingDoesNotTrigger() {
        FallDetector d = detector();
        d.onSample(1000, 2500, 1);
        for (long t = 2000; t <= 12000; t += 1000) d.onSample(t, 600, 2);
        assertEquals(0, triggers);
    }

    @Test
    public void normalStepsNeverTrigger() {
        FallDetector d = detector();
        for (long t = 0; t < 60000; t += 1000) d.onSample(t, 900, 2);
        assertEquals(0, triggers);
    }

    @Test
    public void cooldownPreventsRepeatedAlarms() {
        FallDetector d = detector();
        d.onSample(1000, 2500, 0);
        for (long t = 2000; t <= 12000; t += 1000) d.onSample(t, 0, 0);
        d.onSample(20000, 2500, 0);
        for (long t = 21000; t <= 31000; t += 1000) d.onSample(t, 0, 0);
        assertEquals(1, triggers);
    }

    @Test
    public void secondFallAfterCooldownTriggersAgain() {
        FallDetector d = detector();
        d.onSample(1000, 2500, 0);
        for (long t = 2000; t <= 12000; t += 1000) d.onSample(t, 0, 0);
        d.onSample(200000, 2500, 0);
        for (long t = 201000; t <= 211000; t += 1000) d.onSample(t, 0, 0);
        assertEquals(2, triggers);
    }

    @Test
    public void belowThresholdImpactIsIgnored() {
        FallDetector d = detector();
        d.onSample(1000, 1700, 0);
        for (long t = 2000; t <= 12000; t += 1000) d.onSample(t, 0, 0);
        assertEquals(0, triggers);
    }
}
