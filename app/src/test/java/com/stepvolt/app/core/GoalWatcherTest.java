package com.stepvolt.app.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class GoalWatcherTest {

    @Test
    public void firesOnceWhenGoalReached() {
        GoalWatcher.Decision d = GoalWatcher.decide(8000, 100, 8000, 500, false, false, true);
        assertTrue(d.steps);
        assertFalse(d.energy);
        GoalWatcher.Decision again = GoalWatcher.decide(9000, 100, 8000, 500, true, false, true);
        assertFalse(again.steps);
    }

    @Test
    public void staysQuietBelowGoalOrWhenDisabled() {
        assertFalse(GoalWatcher.decide(7999, 499, 8000, 500, false, false, true).steps);
        assertFalse(GoalWatcher.decide(7999, 499, 8000, 500, false, false, true).energy);
        GoalWatcher.Decision off = GoalWatcher.decide(20000, 900, 8000, 500, false, false, false);
        assertFalse(off.steps);
        assertFalse(off.energy);
    }

    @Test
    public void bothGoalsIndependent() {
        GoalWatcher.Decision d = GoalWatcher.decide(9000, 600, 8000, 500, true, false, true);
        assertFalse(d.steps);
        assertTrue(d.energy);
    }

    @Test
    public void zeroGoalNeverFires() {
        GoalWatcher.Decision d = GoalWatcher.decide(100, 100, 0, 0, false, false, true);
        assertFalse(d.steps);
        assertFalse(d.energy);
    }
}
