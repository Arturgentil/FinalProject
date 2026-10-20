package com.stepvolt.app.core;

import android.content.Context;

import com.stepvolt.app.data.DayStat;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.util.Fmt;
import com.stepvolt.app.util.Notifier;

import java.util.List;

/**
 * Avisa uma vez por dia quando a meta diária de passos (ou de energia) é
 * batida, enquanto o app está recebendo dados do tênis. Respeita o ajuste
 * Notificações > "Meta atingida". Lê o resumo do dia a cada ~20 s no máximo.
 */
public final class GoalWatcher {

    private GoalWatcher() {}

    private static long lastCheck = 0;
    private static final long EVERY_MS = 20_000;
    private static final String K_DAY = "goal_notified_day";
    private static final String K_STEPS = "goal_notified_steps";
    private static final String K_ENERGY = "goal_notified_energy";

    /** O que avisar agora. Regra pura, para teste. */
    public static final class Decision {
        public final boolean steps;
        public final boolean energy;
        Decision(boolean steps, boolean energy) { this.steps = steps; this.energy = energy; }
    }

    public static Decision decide(long todaySteps, double todayEnergy, long stepGoal, double energyGoal,
                                  boolean alreadySteps, boolean alreadyEnergy, boolean enabled) {
        if (!enabled) return new Decision(false, false);
        boolean s = !alreadySteps && stepGoal > 0 && todaySteps >= stepGoal;
        boolean e = !alreadyEnergy && energyGoal > 0 && todayEnergy >= energyGoal;
        return new Decision(s, e);
    }

    /** Chame depois de gravar passos/energia. É barato: na maioria das chamadas só retorna. */
    public static void onUsage(final Context ctx, long now) {
        if (now - lastCheck < EVERY_MS) return;
        lastCheck = now;
        if (!Prefs.getBool(ctx, "notif_goal", true)) return;

        final String today = com.stepvolt.app.data.DailyDao.dayKey(now);
        final boolean sameDay = today.equals(Prefs.getString(ctx, K_DAY, ""));
        final boolean doneSteps = sameDay && Prefs.getBool(ctx, K_STEPS, false);
        final boolean doneEnergy = sameDay && Prefs.getBool(ctx, K_ENERGY, false);
        if (doneSteps && doneEnergy) return;

        Repo.get(ctx).daily.rangeAsync(now, now, days -> {
            long steps = 0; double energy = 0;
            if (days != null) for (DayStat d : (List<DayStat>) days) { steps += d.steps; energy += d.energy; }
            Decision d = decide(steps, energy, Prefs.dailyStepGoal(ctx), Prefs.dailyEnergyGoal(ctx),
                    doneSteps, doneEnergy, true);
            if (!d.steps && !d.energy) return;
            Prefs.setString(ctx, K_DAY, today);
            if (d.steps) {
                Prefs.setBool(ctx, K_STEPS, true);
                Notifier.showMilestone(ctx, "Meta de passos batida!",
                        "Voc\u00ea chegou a " + Fmt.intGrouped(steps) + " passos hoje.");
            }
            if (d.energy) {
                Prefs.setBool(ctx, K_ENERGY, true);
                Notifier.showMilestone(ctx, "Meta de energia batida!",
                        "O t\u00eanis j\u00e1 gerou " + Fmt.energy(energy) + " mWh hoje.");
            }
        });
    }
}
