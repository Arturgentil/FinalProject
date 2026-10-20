package com.stepvolt.app.biomech;

import org.junit.Test;

import static org.junit.Assert.*;

public class PressureAnalysisTest {

    @Test
    public void percentagesSumToHundred() {
        PressureAnalysis.Foot f = new PressureAnalysis.Foot(500, 100, 400);
        assertEquals(100.0, f.heelPct + f.midPct + f.toePct, 1e-9);
        assertEquals(50.0, f.heelPct, 1e-9);
    }

    @Test
    public void strikeClassification() {
        assertEquals(PressureAnalysis.Strike.HEEL, new PressureAnalysis.Foot(600, 100, 300).strike());
        assertEquals(PressureAnalysis.Strike.FORE, new PressureAnalysis.Foot(200, 100, 700).strike());
        assertEquals(PressureAnalysis.Strike.MID, new PressureAnalysis.Foot(350, 300, 350).strike());
    }

    @Test
    public void emptyFootIsUnknown() {
        PressureAnalysis.Foot f = new PressureAnalysis.Foot(0, 0, 0);
        assertFalse(f.present);
        assertEquals(PressureAnalysis.Strike.UNKNOWN, f.strike());
    }

    @Test
    public void symmetryIndexAndLevels() {
        assertEquals(0.0, PressureAnalysis.symmetryIndex(500, 500), 1e-9);
        assertEquals(20.0, PressureAnalysis.symmetryIndex(550, 450), 1e-9);
        assertEquals(PressureAnalysis.SymmetryLevel.GOOD, PressureAnalysis.symmetryLevel(5));
        assertEquals(PressureAnalysis.SymmetryLevel.ATTENTION, PressureAnalysis.symmetryLevel(15));
        assertEquals(PressureAnalysis.SymmetryLevel.HIGH, PressureAnalysis.symmetryLevel(25));
    }

    @Test
    public void negativeReadingsAreClamped() {
        PressureAnalysis.Foot f = new PressureAnalysis.Foot(-5, 100, 100);
        assertEquals(0.0, f.heelN, 1e-9);
    }

    @Test
    public void meanPositiveIgnoresZeros() {
        assertEquals(200.0, PressureAnalysis.meanPositive(new double[]{0, 100, 300, 0}), 1e-9);
        assertEquals(0.0, PressureAnalysis.meanPositive(new double[]{0, 0}), 1e-9);
    }
}
