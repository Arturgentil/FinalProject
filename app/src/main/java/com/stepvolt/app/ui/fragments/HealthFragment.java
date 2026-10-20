package com.stepvolt.app.ui.fragments;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.stepvolt.app.R;
import com.stepvolt.app.ble.Metric;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.data.Point;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.data.Stats;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.ui.views.BarsView;
import com.stepvolt.app.ui.views.SparklineView;
import com.stepvolt.app.util.Fmt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Leitura dos dados de saúde por período.
 *
 * O ponto desta tela é comparação: o mesmo número visto em três janelas de
 * tempo conta histórias diferentes. Por isso tudo aqui responde ao filtro de
 * período no topo, e cada grandeza mostra mínimo, média, máximo e a direção em
 * que vem andando — não só o último valor.
 */
public class HealthFragment extends BaseScreenFragment {

    private static final String[] PERIODS = {"Hoje", "7 dias", "30 dias"};

    private int period = 0;

    private LinearLayout summaryRow;
    private LinearLayout blocks;
    private TextView txtRange;

    @Override
    protected String screenTitle() {
        return "Saúde";
    }

    @Override
    protected void buildContent() {
        addAction(R.drawable.ic_history, "Histórico", v -> nav().navigate(Navigator.HISTORY));
        addAction(R.drawable.ic_alert, "Avisos", v -> nav().navigate(Navigator.ALERTS));

        addBlock(UiKit.chips(requireContext(), PERIODS, period, index -> {
            period = index;
            reload();
        }), 4);

        txtRange = UiKit.caption(requireContext(), "");
        addBlock(txtRange, 12);

        summaryRow = UiKit.row(requireContext());
        addBlock(summaryRow, 12);

        blocks = new LinearLayout(requireContext());
        blocks.setOrientation(LinearLayout.VERTICAL);
        addBlock(blocks, 0);

        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (blocks != null) reload();
    }

    // ------------------------------------------------------------------

    private long fromTs() {
        Calendar c = Calendar.getInstance();
        if (period == 0) {
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        }
        int days = period == 1 ? 7 : 30;
        return System.currentTimeMillis() - days * 86_400_000L;
    }

    /** Tamanho do balde de agregação: 1 h para hoje, 1 dia para as janelas longas. */
    private long bucketMs() {
        return period == 0 ? 3_600_000L : 86_400_000L;
    }

    private void reload() {
        final long from = fromTs();
        final long to = System.currentTimeMillis();
        final Repo repo = Repo.get(requireContext());

        setSubtitle(period == 0 ? "Desde a meia-noite"
                : (period == 1 ? "Últimos 7 dias" : "Últimos 30 dias"));
        txtRange.setText(Fmt.date(from) + " → " + Fmt.date(to));

        summaryRow.removeAllViews();
        blocks.removeAllViews();

        repo.knownMetricsAsync(from, metrics -> {
            if (!isAdded()) return;
            if (metrics == null || metrics.isEmpty()) {
                blocks.addView(UiKit.emptyState(requireContext(), "Nada medido neste período",
                        getString(R.string.empty_no_device)), UiKit.matchWrap(requireContext(), 12));
                blocks.addView(UiKit.ghostButton(requireContext(), "Ir para o tênis",
                        v -> nav().navigate(Navigator.DEVICE)), UiKit.matchWrap(requireContext(), 12));
                return;
            }
            buildSummary(repo, from, to);
            buildStepsChart(repo, from, to);
            for (String key : orderMetrics(metrics)) {
                buildMetricBlock(repo, key, from, to);
            }
        });
    }

    /** Grandezas conhecidas primeiro, na ordem do catálogo; as novas vêm depois. */
    private List<String> orderMetrics(List<String> found) {
        List<String> out = new ArrayList<>();
        for (Metric m : SensorCatalog.all()) {
            if (m.cumulative) continue;
            if (found.contains(m.key)) out.add(m.key);
        }
        for (String k : found) {
            if (!out.contains(k) && SensorCatalog.byKey(k) == null) out.add(k);
        }
        return out;
    }

    private void buildSummary(Repo repo, long from, long to) {
        final long stepGoal = Prefs.dailyStepGoal(requireContext());

        repo.totalAsync(SensorCatalog.STEPS, from, to, steps -> {
            if (!isAdded()) return;
            long s = Math.round(steps);
            String meta = period == 0
                    ? "meta " + Fmt.intGrouped(stepGoal)
                    : "≈ " + Fmt.intGrouped(Math.round(s / (double) (period == 1 ? 7 : 30))) + "/dia";
            LinearLayout tile = UiKit.statTile(requireContext(), R.drawable.ic_nav_shoe,
                    Fmt.intGrouped(s), "", "Passos · " + meta, 0xFFFFB347, R.drawable.tint_stride);
            summaryRow.addView(tile, weighted(summaryRow.getChildCount()));
        });

        repo.totalAsync(SensorCatalog.ENERGY, from, to, energy -> {
            if (!isAdded()) return;
            LinearLayout tile = UiKit.statTile(requireContext(), R.drawable.ic_bolt,
                    Fmt.energy(energy), "mWh", "Energia gerada", 0xFF00F5A0, R.drawable.tint_volt);
            summaryRow.addView(tile, weighted(summaryRow.getChildCount()));
        });
    }

    private LinearLayout.LayoutParams weighted(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) lp.leftMargin = dp(10);
        return lp;
    }

    /** Passos somados por balde — a única grandeza cumulativa que ganha gráfico próprio. */
    private void buildStepsChart(Repo repo, long from, long to) {
        repo.bucketsAsync(SensorCatalog.STEPS, from, to, bucketMs(), true, points -> {
            if (!isAdded() || points == null || points.isEmpty()) return;

            LinearLayout panel = UiKit.panel(requireContext());
            panel.addView(UiKit.sectionTitle(requireContext(),
                    period == 0 ? "Passos por hora" : "Passos por dia"));

            float[] values = new float[points.size()];
            String[] labels = new String[points.size()];
            int step = Math.max(1, points.size() / 6);
            for (int i = 0; i < points.size(); i++) {
                Point p = points.get(i);
                values[i] = (float) p.value;
                boolean show = i % step == 0 || i == points.size() - 1;
                labels[i] = show ? (period == 0 ? hourLabel(p.ts) : Fmt.dateShort(p.ts)) : "";
            }

            BarsView bars = new BarsView(requireContext());
            bars.setAccent(0xFFFFB347);
            bars.setData(values, labels);
            LinearLayout.LayoutParams lp = UiKit.matchWrap(requireContext(), 14);
            lp.height = dp(92);
            panel.addView(bars, lp);

            double sum = 0, peak = 0;
            long peakTs = 0;
            for (Point p : points) {
                sum += p.value;
                if (p.value > peak) { peak = p.value; peakTs = p.ts; }
            }
            if (peak > 0) {
                panel.addView(UiKit.caption(requireContext(), String.format(Locale.getDefault(),
                        "Pico de %s passos %s · média de %s por %s com registro",
                        Fmt.intGrouped(Math.round(peak)),
                        period == 0 ? "às " + hourLabel(peakTs) : "em " + Fmt.dateShort(peakTs),
                        Fmt.intGrouped(Math.round(sum / points.size())),
                        period == 0 ? "hora" : "dia")),
                        UiKit.matchWrap(requireContext(), 10));
            }
            blocks.addView(panel, UiKit.matchWrap(requireContext(), 12));
        });
    }

    private String hourLabel(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return String.format(Locale.getDefault(), "%02dh", c.get(Calendar.HOUR_OF_DAY));
    }

    /** Um painel por grandeza instantânea: estatísticas, curva do período e tendência. */
    private void buildMetricBlock(Repo repo, String key, long from, long to) {
        final Metric m = SensorCatalog.byKey(key);
        final String label = m != null ? m.label : key;
        final String unit = m != null ? m.unit : "";
        final int accent = m != null ? m.color : 0xFF9A9AB8;
        final int decimals = m != null ? m.decimals : 1;

        repo.seriesAsync(key, from, to, 4000, series -> {
            if (!isAdded() || series == null || series.isEmpty()) return;
            Stats st = Stats.of(series);

            LinearLayout panel = UiKit.panel(requireContext());

            LinearLayout head = UiKit.row(requireContext());
            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(UiKit.sectionTitle(requireContext(), label));
            col.addView(UiKit.caption(requireContext(),
                    st.count + " leituras · " + st.trendLabel()));
            head.addView(col, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(UiKit.readout(requireContext(),
                    Fmt.decimals(st.avg, decimals) + (unit.isEmpty() ? "" : " " + unit), 24, accent));
            panel.addView(head);

            // A curva usa os baldes do período para não desenhar 4 mil pontos.
            repo.bucketsAsync(key, from, to, bucketMs(), false, buckets -> {
                if (!isAdded() || buckets == null || buckets.size() < 2) return;
                float[] values = new float[buckets.size()];
                for (int i = 0; i < buckets.size(); i++) values[i] = (float) buckets.get(i).value;
                SparklineView spark = new SparklineView(requireContext());
                spark.setAccent(accent);
                spark.setCapacity(values.length);
                spark.setValues(values);
                LinearLayout.LayoutParams lp = UiKit.matchWrap(requireContext(), 12);
                lp.height = dp(56);
                panel.addView(spark, 1, lp);
            });

            LinearLayout statsRow = UiKit.row(requireContext());
            statsRow.addView(miniStat("Mínimo", Fmt.decimals(st.min, decimals)), weighted(0));
            statsRow.addView(miniStat("Média", Fmt.decimals(st.avg, decimals)), weighted(1));
            statsRow.addView(miniStat("Máximo", Fmt.decimals(st.max, decimals)), weighted(2));
            panel.addView(statsRow, UiKit.matchWrap(requireContext(), 14));

            String reading = describe(m, st, decimals, unit);
            if (reading != null) {
                panel.addView(UiKit.caption(requireContext(), reading),
                        UiKit.matchWrap(requireContext(), 12));
            }

            blocks.addView(panel, UiKit.matchWrap(requireContext(), 12));
        });
    }

    private View miniStat(String label, String value) {
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundResource(R.drawable.bg_inset);
        col.setPadding(dp(12), dp(10), dp(12), dp(10));
        col.addView(UiKit.caption(requireContext(), label));
        TextView v = UiKit.readout(requireContext(), value, 17,
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary));
        col.addView(v, UiKit.matchWrap(requireContext(), 2));
        return col;
    }

    /**
     * Frase de leitura em linguagem comum. Descreve o que os números mostram e
     * o que costuma explicá-los — sem afirmar causa nem sugerir diagnóstico.
     */
    private String describe(Metric m, Stats st, int decimals, String unit) {
        if (m == null || st.isEmpty()) return null;

        StringBuilder b = new StringBuilder();
        double spread = st.max - st.min;
        b.append("Variou ").append(Fmt.decimals(spread, decimals))
                .append(unit.isEmpty() ? "" : " " + unit).append(" entre o menor e o maior valor. ");

        if (st.max > m.usualHigh) {
            b.append("O máximo passou da faixa habitual de uso (")
                    .append(Fmt.decimals(m.usualHigh, decimals)).append(unit.isEmpty() ? "" : " " + unit)
                    .append("). ");
        } else if (st.min < m.usualLow) {
            b.append("O mínimo ficou abaixo da faixa habitual (")
                    .append(Fmt.decimals(m.usualLow, decimals)).append(unit.isEmpty() ? "" : " " + unit)
                    .append("). ");
        }

        if (Math.abs(st.slopePerMinute) > 0.0001 && st.count > 20) {
            b.append(st.slopePerMinute > 0 ? "A tendência do período é de alta." : "A tendência do período é de queda.");
        }
        return b.toString().trim();
    }
}
