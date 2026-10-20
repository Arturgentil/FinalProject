package com.stepvolt.app.health;

import com.stepvolt.app.data.Point;
import com.stepvolt.app.data.Stats;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class InsightsTest {

    private static Stats stats(double... values) {
        List<Point> pts = new ArrayList<>();
        for (int i = 0; i < values.length; i++) pts.add(new Point(i * 1000L, values[i]));
        return Stats.of(pts);
    }

    @Test
    public void emptySnapshotSaysNoData() {
        List<Insights.Card> cards = Insights.read(new Insights.Snapshot(), 8000, 92);
        assertEquals(1, cards.size());
        assertEquals("Ainda sem dados", cards.get(0).title);
    }

    @Test
    public void stepsCardShowsPercentOfGoal() {
        Insights.Snapshot s = new Insights.Snapshot();
        s.steps = 4000;
        s.energy = 120;
        List<Insights.Card> cards = Insights.read(s, 8000, 92);
        boolean found = false;
        for (Insights.Card c : cards) {
            if (c.title.equals("Movimento do dia")) {
                found = true;
                assertTrue(c.body.contains("50%"));
            }
        }
        assertTrue(found);
    }

    @Test
    public void energyCardUsesRealPhoneBatteryScale() {
        Insights.Snapshot s = new Insights.Snapshot();
        s.energy = 154; // 1% de 15.400 mWh
        for (Insights.Card c : Insights.read(s, 8000, 92)) {
            if (c.title.equals("Energia gerada")) {
                assertTrue(c.body, c.body.contains("1.000%") || c.body.contains("1,000%"));
                return;
            }
        }
        fail("cartão de energia ausente");
    }

    @Test
    public void lowOxygenMentionsConfiguredLimit() {
        Insights.Snapshot s = new Insights.Snapshot();
        s.spo2 = stats(95, 90, 96);
        boolean mentioned = false;
        for (Insights.Card c : Insights.read(s, 8000, 92)) {
            if (c.title.equals("Oxigenação") && c.body.contains("abaixo do limite")) mentioned = true;
        }
        assertTrue(mentioned);
    }

    @Test
    public void neverDiagnoses() {
        Insights.Snapshot s = new Insights.Snapshot();
        s.bpm = stats(60, 190, 70, 200);
        s.spo2 = stats(80, 85);
        for (Insights.Card c : Insights.read(s, 8000, 92)) {
            String t = c.body.toLowerCase();
            assertFalse(t.contains("diagnóstico"));
            assertFalse(t.contains("você tem"));
            assertFalse(t.contains("doença"));
        }
    }
}
