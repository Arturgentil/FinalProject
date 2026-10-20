package com.stepvolt.app.plan;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PlanGeneratorTest {

    private static PlanGenerator.Week week(long steps, double energy, int days) {
        PlanGenerator.Week w = new PlanGenerator.Week();
        w.steps = steps; w.energyMwh = energy; w.activeDays = days;
        return w;
    }

    @Test
    public void withoutHistoryUsesDailyGoals() {
        PlanGenerator.Plan p = PlanGenerator.generate(new ArrayList<PlanGenerator.Week>(), 8000, 500);
        assertFalse(p.basedOnHistory);
        assertEquals(40000, p.weeklySteps);
        assertEquals(7, p.days.size());
        int rest = 0;
        for (PlanGenerator.DayTarget d : p.days) if (d.rest) rest++;
        assertEquals(2, rest);
    }

    @Test
    public void historyRaisesGoalByAboutTenPercent() {
        List<PlanGenerator.Week> h = new ArrayList<>();
        h.add(week(50000, 3000, 5));
        h.add(week(50000, 3000, 5));
        h.add(week(50000, 3000, 5));
        PlanGenerator.Plan p = PlanGenerator.generate(h, 8000, 500);
        assertTrue(p.basedOnHistory);
        assertEquals(55000, p.weeklySteps);
        assertEquals(5, p.activeDays);
    }

    @Test
    public void weakLastWeekLowersGoal() {
        List<PlanGenerator.Week> h = new ArrayList<>();
        h.add(week(20000, 1000, 2));
        h.add(week(60000, 3000, 5));
        h.add(week(60000, 3000, 5));
        PlanGenerator.Plan p = PlanGenerator.generate(h, 8000, 500);
        double avg = (20000 + 60000 + 60000) / 3.0;
        assertEquals(Math.round(avg * 0.9), p.weeklySteps);
    }

    @Test
    public void strongLastWeekRaisesOnlyALittle() {
        List<PlanGenerator.Week> h = new ArrayList<>();
        h.add(week(90000, 4000, 6));
        h.add(week(40000, 2000, 4));
        h.add(week(40000, 2000, 4));
        PlanGenerator.Plan p = PlanGenerator.generate(h, 8000, 500);
        double avg = (90000 + 40000 + 40000) / 3.0;
        assertEquals(Math.round(avg * 1.05), p.weeklySteps);
    }

    @Test
    public void emptyWeeksAreIgnored() {
        List<PlanGenerator.Week> h = new ArrayList<>();
        h.add(week(0, 0, 0));
        PlanGenerator.Plan p = PlanGenerator.generate(h, 8000, 500);
        assertFalse(p.basedOnHistory);
    }

    @Test
    public void dayTargetsSumToWeeklyGoalApproximately() {
        List<PlanGenerator.Week> h = new ArrayList<>();
        h.add(week(50000, 3000, 5));
        PlanGenerator.Plan p = PlanGenerator.generate(h, 8000, 500);
        long sum = 0;
        for (PlanGenerator.DayTarget d : p.days) sum += d.steps;
        assertTrue(Math.abs(sum - p.weeklySteps) <= p.activeDays);
    }
}
