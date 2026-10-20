package com.stepvolt.app.ui.fragments;

import android.widget.LinearLayout;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.ble.Metric;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.data.Point;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.data.SessionRecord;
import com.stepvolt.app.data.Stats;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.ui.views.SparklineView;
import com.stepvolt.app.util.Fmt;

import java.util.List;
import java.util.Locale;

/**
 * Detalhe de uma sessão gravada no aparelho.
 *
 * Diferente da tela de atividade (que mostra o que o servidor guardou), esta
 * lê o banco local — então funciona offline e mostra a série completa, não a
 * versão reduzida que sobe para a nuvem.
 */
public class SessionDetailsFragment extends BaseScreenFragment {

    public static final String ARG_SESSION_ID = "sessionId";

    private LinearLayout body;
    private long sessionId;

    @Override
    protected String screenTitle() {
        return "Sessão";
    }

    @Override
    protected boolean showsBack() {
        return true;
    }

    @Override
    protected void buildContent() {
        sessionId = getArguments() != null ? getArguments().getLong(ARG_SESSION_ID, 0) : 0;

        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        addBlock(body, 4);

        if (sessionId <= 0) {
            body.addView(UiKit.emptyState(requireContext(), "Sessão não encontrada",
                    "Volte ao histórico e escolha uma sessão da lista."), UiKit.matchWrap(requireContext(), 0));
            return;
        }
        load();
    }

    private void load() {
        final Repo repo = Repo.get(requireContext());
        repo.sessionsAsync(200, sessions -> {
            if (!isAdded()) return;
            SessionRecord found = null;
            if (sessions != null) {
                for (SessionRecord s : sessions) if (s.id == sessionId) found = s;
            }
            if (found == null) {
                body.addView(UiKit.emptyState(requireContext(), "Sessão não encontrada",
                        "Ela pode ter sido apagada junto com o histórico."),
                        UiKit.matchWrap(requireContext(), 0));
                return;
            }
            render(repo, found);
        });
    }

    private void render(Repo repo, SessionRecord s) {
        setSubtitle(Fmt.date(s.startedAt) + " · " + Fmt.clock(s.startedAt)
                + (s.endedAt > 0 ? " às " + Fmt.clock(s.endedAt) : " (em andamento)"));

        LinearLayout head = UiKit.panel(requireContext());
        head.addView(UiKit.sectionTitle(requireContext(), "Resumo"));
        head.addView(UiKit.keyValue(requireContext(), "Duração", Fmt.duration(s.durationMs())));
        head.addView(UiKit.keyValue(requireContext(), "Passos", Fmt.intGrouped(s.steps)));
        head.addView(UiKit.keyValue(requireContext(), "Energia gerada",
                Fmt.energy(s.energy) + " mWh"));
        if (s.bpmAvg > 0) {
            head.addView(UiKit.keyValue(requireContext(), "Batimentos",
                    String.format(Locale.getDefault(), "%.0f bpm em média (%.0f–%.0f)",
                            s.bpmAvg, s.bpmMin, s.bpmMax)));
        }
        if (s.spo2Avg > 0) {
            head.addView(UiKit.keyValue(requireContext(), "Oxigenação",
                    String.format(Locale.getDefault(), "%.0f%% em média (mínima %.0f%%)",
                            s.spo2Avg, s.spo2Min)));
        }
        head.addView(UiKit.keyValue(requireContext(), "Medições", String.valueOf(s.sampleCount)));
        head.addView(UiKit.keyValue(requireContext(), "Tênis",
                s.deviceName == null || s.deviceName.isEmpty() ? "—" : s.deviceName));
        head.addView(UiKit.keyValue(requireContext(), "No servidor",
                s.synced ? "enviada" : "na fila de envio"));
        body.addView(head, UiKit.matchWrap(requireContext(), 0));

        if (!s.synced) {
            body.addView(UiKit.ghostButton(requireContext(), "Tentar enviar agora", v -> {
                AppState.get().telemetry.syncPendingSessions();
                toast("Envio solicitado. A lista atualiza assim que o servidor responder.");
            }), UiKit.matchWrap(requireContext(), 12));
        }

        for (Metric m : SensorCatalog.all()) {
            if (m.cumulative) continue;
            renderSeries(repo, s, m);
        }
    }

    private void renderSeries(Repo repo, SessionRecord s, Metric m) {
        repo.sessionSeriesAsync(s.id, m.key, points -> {
            if (!isAdded() || points == null || points.size() < 2) return;
            Stats st = Stats.of(points);

            LinearLayout panel = UiKit.panel(requireContext());

            LinearLayout row = UiKit.row(requireContext());
            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(UiKit.sectionTitle(requireContext(), m.label));
            col.addView(UiKit.caption(requireContext(), String.format(Locale.getDefault(),
                    "%d leituras · mínimo %s · máximo %s",
                    st.count, Fmt.decimals(st.min, m.decimals), Fmt.decimals(st.max, m.decimals))));
            row.addView(col, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(UiKit.readout(requireContext(),
                    Fmt.decimals(st.avg, m.decimals) + (m.unit.isEmpty() ? "" : " " + m.unit),
                    22, m.color));
            panel.addView(row);

            float[] values = reduce(points, 160);
            SparklineView spark = new SparklineView(requireContext());
            spark.setAccent(m.color);
            spark.setCapacity(values.length);
            spark.setValues(values);
            LinearLayout.LayoutParams lp = UiKit.matchWrap(requireContext(), 14);
            lp.height = dp(64);
            panel.addView(spark, lp);

            body.addView(panel, UiKit.matchWrap(requireContext(), 12));
        });
    }

    /** Uma sessão longa tem milhares de pontos; a curva não precisa de todos. */
    private float[] reduce(List<Point> points, int max) {
        int step = Math.max(1, points.size() / max);
        int size = (points.size() + step - 1) / step;
        float[] out = new float[size];
        int i = 0;
        for (int p = 0; p < points.size() && i < size; p += step) {
            out[i++] = (float) points.get(p).value;
        }
        return out;
    }
}
