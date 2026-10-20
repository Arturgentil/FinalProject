package com.stepvolt.app.energy;

import org.junit.Test;

import static org.junit.Assert.*;

public class EnergyEquivalentsTest {

    @Test
    public void phoneChargeUsesRealisticBatteryCapacity() {
        // 154 mWh de 15.400 mWh = 1%
        assertEquals(0.01, EnergyEquivalents.phoneCharges(154), 1e-9);
    }

    @Test
    public void ledBulbMinutes() {
        // 9 W = 9000 mW; 150 mWh -> 1 minuto
        assertEquals(1.0, EnergyEquivalents.ledBulbMinutes(150), 1e-9);
    }

    @Test
    public void efficiencyPerThousandSteps() {
        assertEquals(50.0, EnergyEquivalents.mwhPer1000Steps(100, 2000), 1e-9);
        assertEquals(0.0, EnergyEquivalents.mwhPer1000Steps(100, 0), 1e-9);
    }

    @Test
    public void describeIsEmptyForZeroEnergy() {
        assertTrue(EnergyEquivalents.describe(0).isEmpty());
    }

    @Test
    public void describeAlwaysIncludesIndicatorLed() {
        assertFalse(EnergyEquivalents.describe(5).isEmpty());
    }

    @Test
    public void formatMinutesScales() {
        assertEquals("30 s", EnergyEquivalents.formatMinutes(0.5));
        assertEquals("45 min", EnergyEquivalents.formatMinutes(45));
        assertEquals("3.0 h", EnergyEquivalents.formatMinutes(180));
    }
}
