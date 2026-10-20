package com.stepvolt.app.ui.fragments;

import android.content.Context;
import android.widget.LinearLayout;

import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.data.DailyDao;
import com.stepvolt.app.data.DayStat;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.plan.PlanGenerator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.ui.views.BarChartView;
import com.stepvolt.app.util.Fmt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Meta da semana, montada a partir do que a pessoa realmente fez nas últimas
 * semanas. O plano fica fixo durante a semana (não muda a cada abertura da
 * tela) e só é refeito na segunda-feira ou quando a pessoa pede.
 */
public class PlansFragment extends BaseScreenFragment {

    private LinearLayout body;

    @Override protected String screenTitle() { return "Planos e metas"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        addBlock(body, 0);
        load(false);
    }

    private void load(final boolean forceNew) {
        final Context c = requireContext();
        final long weekStart = DailyDao.weekStart(System.currentTimeMillis());
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(weekStart);
        cal.add(Calendar.DAY_OF_YEAR, -28);
        final long histFrom = cal.getTimeInMillis();

        Repo.get(c).daily.rangeAsync(histFrom, System.currentTimeMillis(), days -> {
            if (!isAdded()) return;
            if (days == null) days = new ArrayList<>();
            render(weekStart, histFrom, days, forceNew);
        });
    }

    private void render(long weekStart, long histFrom, List<DayStat> days, boolean forceNew) {
        Context c = requireContext();
        body.removeAllViews();

        // Semanas completas anteriores, a mais recente primeiro.
        List<PlanGenerator.Week> history = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            long from = weekStart - (long) (w + 1) * 7 * 86_400_000L;
            PlanGenerator.Week week = new PlanGenerator.Week();
            for (int d = 0; d < 7; d++) {
                String key = DailyDao.dayKey(from + d * 86_400_000L + 3_600_000L);
                for (DayStat s : days) if (s.day.equals(key)) {
                    week.steps += s.steps; week.energyMwh += s.energy;
                    if (s.steps >= 1000) week.activeDays++;
                }
            }
            history.add(week);
        }

        long storedWeek = Prefs.getLong(c, Prefs.PLAN_WEEK_START, 0);
        PlanGenerator.Plan plan;
        if (!forceNew && storedWeek == weekStart && Prefs.getLong(c, Prefs.PLAN_WEEKLY_STEPS, 0) > 0) {
            plan = new PlanGenerator.Plan();
            plan.weeklySteps = Prefs.getLong(c, Prefs.PLAN_WEEKLY_STEPS, 0);
            plan.weeklyEnergyMwh = Prefs.getFloat(c, Prefs.PLAN_WEEKLY_ENERGY, 0);
            plan.activeDays = (int) Prefs.getLong(c, Prefs.PLAN_ACTIVE_DAYS, 5);
            plan.reasoning = Prefs.getString(c, Prefs.PLAN_REASONING, "");
            plan.basedOnHistory = true;
            plan.days = PlanGenerator.layoutDays(plan.activeDays,
                    plan.weeklySteps / Math.max(1, plan.activeDays));
        } else {
            plan = PlanGenerator.generate(history, Prefs.dailyStepGoal(c), Prefs.dailyEnergyGoal(c));
            Prefs.setLong(c, Prefs.PLAN_WEEK_START, weekStart);
            Prefs.setLong(c, Prefs.PLAN_WEEKLY_STEPS, plan.weeklySteps);
            Prefs.setFloat(c, Prefs.PLAN_WEEKLY_ENERGY, (float) plan.weeklyEnergyMwh);
            Prefs.setLong(c, Prefs.PLAN_ACTIVE_DAYS, plan.activeDays);
            Prefs.setString(c, Prefs.PLAN_REASONING, plan.reasoning);
        }

        // Progresso da semana
        long doneSteps = 0; double doneEnergy = 0;
        float[] actual = new float[7];
        for (int d = 0; d < 7; d++) {
            String key = DailyDao.dayKey(weekStart + d * 86_400_000L + 3_600_000L);
            for (DayStat s : days) if (s.day.equals(key)) {
                actual[d] = s.steps; doneSteps += s.steps; doneEnergy += s.energy;
            }
        }

        body.addView(UiKit.sectionTitle(c, "Meta desta semana"));
        LinearLayout r = UiKit.row(c);
        addTile(r, Fmt.intGrouped(plan.weeklySteps), "passos", "Meta de passos");
        addTile(r, Fmt.energy(plan.weeklyEnergyMwh), "mWh", "Meta de energia");
        body.addView(r, UiKit.matchWrap(c, 10));

        double pct = plan.weeklySteps > 0 ? Math.min(100.0, doneSteps * 100.0 / plan.weeklySteps) : 0;
        body.addView(UiKit.keyValue(c, "Progresso", Fmt.intGrouped(doneSteps) + " de " + Fmt.intGrouped(plan.weeklySteps)
                + " passos (" + Math.round(pct) + "%)"), UiKit.matchWrap(c, 14));
        body.addView(UiKit.keyValue(c, "Energia até agora", Fmt.energy(doneEnergy) + " mWh"), UiKit.matchWrap(c, 6));

        body.addView(UiKit.sectionTitle(c, "Dia a dia"), UiKit.matchWrap(c, 20));
        float[] targets = new float[7];
        String[] labels = new String[7];
        for (PlanGenerator.DayTarget t : plan.days) {
            targets[t.dayIndex] = t.steps;
            labels[t.dayIndex] = t.rest ? t.label + "·" : t.label;
        }
        BarChartView chart = new BarChartView(c);
        chart.setAccent(0xFF00F5A0);
        chart.setData(actual, labels);
        long perActive = plan.weeklySteps / Math.max(1, plan.activeDays);
        chart.setGoal(perActive);
        body.addView(chart, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 170)));
        body.addView(UiKit.caption(c, "Barras: passos de cada dia. Linha tracejada: meta de um dia ativo ("
                + Fmt.intGrouped(perActive) + "). Dias com ponto (·) são de descanso."), UiKit.matchWrap(c, 6));

        body.addView(UiKit.note(c, plan.reasoning, 0xFF4CC9F0), UiKit.matchWrap(c, 16));

        body.addView(UiKit.primaryButton(c, "Usar " + Fmt.intGrouped(perActive) + " passos como meta diária", v -> {
            Prefs.setInt(requireContext(), Prefs.DAILY_STEP_GOAL, (int) perActive);
            toast("Meta diária atualizada.");
        }), UiKit.matchWrap(c, 20));
        body.addView(UiKit.ghostButton(c, "Gerar o plano de novo", v -> load(true)), UiKit.matchWrap(c, 10));
    }

    private void addTile(LinearLayout row, String value, String unit, String label) {
        Context c = requireContext();
        LinearLayout t = UiKit.statTile(c, com.stepvolt.app.R.drawable.ic_target, value, unit, label,
                0xFF00F5A0, com.stepvolt.app.R.drawable.tint_volt);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) lp.leftMargin = UiKit.dp(c, 10);
        row.addView(t, lp);
    }
}
