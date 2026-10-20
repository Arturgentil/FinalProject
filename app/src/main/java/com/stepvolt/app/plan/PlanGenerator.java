package com.stepvolt.app.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * Monta a meta da semana a partir do que a pessoa de fato fez nas últimas
 * semanas. Regra simples e explicável: parte da média recente e propõe um
 * degrau pequeno (no máximo +10%), nunca um salto — e recua se a semana
 * passada ficou muito abaixo do esperado.
 */
public final class PlanGenerator {

    private PlanGenerator() {}

    public static final class Week {
        public long steps;
        public double energyMwh;
        public int activeDays;
    }

    public static final class DayTarget {
        public final int dayIndex; // 0 = segunda
        public final String label;
        public final long steps;
        public final boolean rest;
        public DayTarget(int dayIndex, String label, long steps, boolean rest) {
            this.dayIndex = dayIndex; this.label = label; this.steps = steps; this.rest = rest;
        }
    }

    public static final class Plan {
        public long weeklySteps;
        public double weeklyEnergyMwh;
        public int activeDays;
        public String reasoning;
        public List<DayTarget> days = new ArrayList<>();
        public boolean basedOnHistory;
    }

    private static final String[] LABELS = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};

    /** history: semanas completas mais recentes primeiro (índice 0 = semana passada). */
    public static Plan generate(List<Week> history, long defaultDailySteps, double defaultDailyEnergy) {
        Plan plan = new Plan();

        List<Week> usable = new ArrayList<>();
        if (history != null) {
            for (Week w : history) if (w != null && w.steps > 0) usable.add(w);
        }

        if (usable.isEmpty()) {
            plan.basedOnHistory = false;
            plan.activeDays = 5;
            plan.weeklySteps = defaultDailySteps * 5;
            plan.weeklyEnergyMwh = defaultDailyEnergy * 5;
            plan.reasoning = "Ainda não há semanas completas no histórico, então o plano parte das suas metas diárias, com 5 dias ativos e 2 de descanso.";
            fillDays(plan, defaultDailySteps);
            return plan;
        }

        int n = Math.min(3, usable.size());
        double avgSteps = 0, avgEnergy = 0, avgDays = 0;
        for (int i = 0; i < n; i++) {
            avgSteps += usable.get(i).steps;
            avgEnergy += usable.get(i).energyMwh;
            avgDays += usable.get(i).activeDays;
        }
        avgSteps /= n; avgEnergy /= n; avgDays /= n;

        double growth = 1.10;
        String why;
        Week last = usable.get(0);
        if (last.steps < avgSteps * 0.6) {
            growth = 0.90;
            why = "A última semana ficou bem abaixo da sua média, então a meta recua um pouco para retomar sem pressão.";
        } else if (last.steps > avgSteps * 1.3) {
            growth = 1.05;
            why = "A última semana foi forte; a meta sobe só um pouco para consolidar em vez de forçar.";
        } else {
            why = "Meta baseada na média das suas últimas " + n + " semanas, com um degrau de cerca de 10%.";
        }

        plan.basedOnHistory = true;
        plan.weeklySteps = Math.round(avgSteps * growth);
        plan.weeklyEnergyMwh = avgEnergy * growth;
        plan.activeDays = (int) Math.max(3, Math.min(6, Math.round(avgDays)));
        if (plan.activeDays == 0) plan.activeDays = 5;
        plan.reasoning = why;

        long perActive = plan.weeklySteps / plan.activeDays;
        fillDays(plan, perActive);
        return plan;
    }

    /** Distribui os dias ativos e de descanso da semana; usado também para redesenhar um plano já salvo. */
    public static List<DayTarget> layoutDays(int activeDays, long perActiveDay) {
        Plan p = new Plan();
        p.activeDays = activeDays;
        fillDays(p, perActiveDay);
        return p.days;
    }

    private static void fillDays(Plan plan, long perActiveDay) {
        // Distribui os dias de descanso espalhados, não todos juntos no fim.
        boolean[] active = new boolean[7];
        int count = plan.activeDays;
        int[] order = {0, 2, 4, 1, 5, 3, 6};
        for (int i = 0; i < count && i < 7; i++) active[order[i]] = true;
        for (int d = 0; d < 7; d++) {
            plan.days.add(new DayTarget(d, LABELS[d], active[d] ? perActiveDay : 0, !active[d]));
        }
    }
}
