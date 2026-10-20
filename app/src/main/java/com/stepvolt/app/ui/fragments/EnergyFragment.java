package com.stepvolt.app.ui.fragments;

import android.content.Context;
import android.widget.LinearLayout;

import com.stepvolt.app.R;
import com.stepvolt.app.data.DailyDao;
import com.stepvolt.app.data.DayStat;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.data.SessionRecord;
import com.stepvolt.app.energy.EnergyEquivalents;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.ui.views.BarChartView;
import com.stepvolt.app.util.Fmt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Energia gerada pelo tênis: histórico por dia ou semana, comparação com o
 * período anterior, equivalências do dia a dia e eficiência (mWh por 1.000
 * passos), inclusive por tipo de treino.
 */
public class EnergyFragment extends BaseScreenFragment {

    private static final String[] PERIODS = {"7 dias", "30 dias", "90 dias"};
    private static final int[] DAYS = {7, 30, 90};

    private int period = 0;
    private LinearLayout body;

    @Override protected String screenTitle() { return "Energia"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        addBlock(UiKit.chips(requireContext(), PERIODS, period, i -> { period = i; load(); }), 0);
        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        addBlock(body, 14);
        load();
    }

    private static long startOfDay(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void load() {
        final int n = DAYS[period];
        final long today = startOfDay(System.currentTimeMillis());
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(today);
        cal.add(Calendar.DAY_OF_YEAR, -(n - 1));
        final long from = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -n);
        final long prevFrom = cal.getTimeInMillis();
        final long prevTo = from - 1;

        final Repo repo = Repo.get(requireContext());
        repo.daily.rangeAsync(from, today, current -> {
            if (!isAdded()) return;
            repo.daily.rangeAsync(prevFrom, prevTo, previous -> {
                if (!isAdded()) return;
                repo.sessionsAsync(500, sessions -> {
                    if (!isAdded()) return;
                    render(n, from, current == null ? new ArrayList<DayStat>() : current,
                            previous == null ? new ArrayList<DayStat>() : previous,
                            sessions == null ? new ArrayList<SessionRecord>() : sessions);
                });
            });
        });
    }

    private void render(int n, long from, List<DayStat> found, List<DayStat> previous, List<SessionRecord> sessions) {
        Context c = requireContext();
        body.removeAllViews();

        double total = 0, prevTotal = 0, best = 0;
        long steps = 0;
        String bestDay = null;
        for (DayStat d : found) {
            total += d.energy; steps += d.steps;
            if (d.energy > best) { best = d.energy; bestDay = d.day; }
        }
        for (DayStat d : previous) prevTotal += d.energy;

        if (total <= 0) {
            body.addView(UiKit.emptyState(c, "Sem energia registrada neste período",
                    "A energia aparece aqui conforme o tênis gera e o app recebe os dados. Conecte o tênis e caminhe um pouco."),
                    UiKit.matchWrap(c, 0));
            return;
        }

        // Totais
        LinearLayout r1 = UiKit.row(c);
        addTile(r1, Fmt.energy(total), "mWh", "Energia no período");
        addTile(r1, Fmt.intGrouped(steps), "", "Passos");
        body.addView(r1, UiKit.matchWrap(c, 0));

        double eff = EnergyEquivalents.mwhPer1000Steps(total, steps);
        LinearLayout r2 = UiKit.row(c);
        addTile(r2, eff > 0 ? Fmt.energy(eff) : "—", "mWh", "Por 1.000 passos");
        addTile(r2, Fmt.energy(total / n), "mWh", "Média por dia");
        body.addView(r2, UiKit.matchWrap(c, 10));

        // Comparação com o período anterior
        if (prevTotal > 0) {
            double delta = (total - prevTotal) / prevTotal * 100.0;
            String text = String.format(Locale.US, "%s%.0f%% em relação aos %d dias anteriores (%s mWh).",
                    delta >= 0 ? "+" : "−", Math.abs(delta), n, Fmt.energy(prevTotal));
            body.addView(UiKit.note(c, text, delta >= 0 ? 0xFF00F5A0 : 0xFFFFB347), UiKit.matchWrap(c, 14));
        } else {
            body.addView(UiKit.caption(c, "Sem dados do período anterior para comparar."), UiKit.matchWrap(c, 12));
        }

        // Gráfico
        body.addView(UiKit.sectionTitle(c, n > 30 ? "Energia por semana (mWh)" : "Energia por dia (mWh)"), UiKit.matchWrap(c, 22));
        List<DayStat> filled = DailyDao.fillGaps(found, from, n);
        float[] vals;
        String[] labels;
        if (n > 30) {
            int weeks = (int) Math.ceil(filled.size() / 7.0);
            vals = new float[weeks];
            labels = new String[weeks];
            for (int i = 0; i < filled.size(); i++) vals[i / 7] += (float) filled.get(i).energy;
            for (int w = 0; w < weeks; w++) labels[w] = "S" + (w + 1);
        } else {
            vals = new float[filled.size()];
            labels = new String[filled.size()];
            for (int i = 0; i < filled.size(); i++) {
                vals[i] = (float) filled.get(i).energy;
                String d = filled.get(i).day; // yyyy-MM-dd
                labels[i] = d.substring(8) ;
            }
        }
        BarChartView chart = new BarChartView(c);
        chart.setAccent(0xFFFFB347);
        chart.setData(vals, labels);
        body.addView(chart, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 170)));

        if (bestDay != null) {
            body.addView(UiKit.caption(c, "Melhor dia: " + bestDay.substring(8) + "/" + bestDay.substring(5, 7)
                    + " com " + Fmt.energy(best) + " mWh."), UiKit.matchWrap(c, 8));
        }

        // Equivalências
        body.addView(UiKit.sectionTitle(c, "O que isso representa"), UiKit.matchWrap(c, 22));
        for (EnergyEquivalents.Item it : EnergyEquivalents.describe(total)) {
            LinearLayout row = UiKit.panel(c);
            row.addView(UiKit.label(c, it.title));
            row.addView(UiKit.caption(c, it.detail));
            body.addView(row, UiKit.matchWrap(c, 8));
        }
        body.addView(UiKit.caption(c, "Valores de referência típicos, para dar noção de escala."), UiKit.matchWrap(c, 6));

        // Eficiência por tipo de treino
        renderByKind(from, sessions);
    }

    private void renderByKind(long from, List<SessionRecord> sessions) {
        Context c = requireContext();
        Map<String, double[]> byKind = new LinkedHashMap<>(); // [steps, energy, count]
        for (SessionRecord s : sessions) {
            if (s.startedAt < from || s.steps <= 0) continue;
            String k = s.kind == null ? "walk" : s.kind;
            double[] acc = byKind.get(k);
            if (acc == null) { acc = new double[3]; byKind.put(k, acc); }
            acc[0] += s.steps; acc[1] += s.energy; acc[2] += 1;
        }
        if (byKind.isEmpty()) return;

        body.addView(UiKit.sectionTitle(c, "Eficiência por tipo de treino"), UiKit.matchWrap(c, 22));
        for (Map.Entry<String, double[]> e : byKind.entrySet()) {
            double[] a = e.getValue();
            double eff = EnergyEquivalents.mwhPer1000Steps(a[1], (long) a[0]);
            LinearLayout row = UiKit.panel(c);
            row.addView(UiKit.keyValue(c, kindLabel(e.getKey()) + " (" + (int) a[2] + " treino"
                    + (a[2] > 1 ? "s" : "") + ")", Fmt.energy(eff) + " mWh / 1.000 passos"));
            body.addView(row, UiKit.matchWrap(c, 8));
        }
        body.addView(UiKit.caption(c, "Mostra em que tipo de treino o tênis rende mais energia por passo."), UiKit.matchWrap(c, 6));
    }

    private static String kindLabel(String k) {
        if ("run".equals(k)) return "Corrida";
        if ("hike".equals(k)) return "Trilha";
        if ("other".equals(k)) return "Outro";
        return "Caminhada";
    }

    private void addTile(LinearLayout row, String value, String unit, String label) {
        Context c = requireContext();
        LinearLayout t = UiKit.statTile(c, R.drawable.ic_bolt, value, unit, label, 0xFFFFB347, R.drawable.tint_volt);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) lp.leftMargin = UiKit.dp(c, 10);
        row.addView(t, lp);
    }
}
