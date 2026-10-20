package com.stepvolt.app.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.stepvolt.app.R;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.data.Point;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.data.SessionRecord;
import com.stepvolt.app.energy.EnergyEquivalents;
import com.stepvolt.app.export.PdfReport;
import com.stepvolt.app.gps.GeoMath;
import com.stepvolt.app.models.ActivityRecord;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.ui.views.LineChartView;
import com.stepvolt.app.ui.views.RouteMapView;
import com.stepvolt.app.util.Fmt;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Resumo logo depois do treino: rota, números principais, energia e compartilhamento. */
public class WorkoutSummaryFragment extends BaseScreenFragment {

    private long sessionId;
    private LinearLayout body;

    @Override protected String screenTitle() { return "Resumo do treino"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        sessionId = getArguments() != null ? getArguments().getLong("sessionId", 0) : 0;
        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        addBlock(body, 4);
        if (sessionId <= 0) {
            body.addView(UiKit.emptyState(requireContext(), "Treino não encontrado",
                    "Abra o histórico e escolha uma sessão."), UiKit.matchWrap(requireContext(), 0));
            return;
        }
        Repo repo = Repo.get(requireContext());
        repo.sessionAsync(sessionId, session -> {
            if (!isAdded()) return;
            if (session == null) {
                body.addView(UiKit.emptyState(requireContext(), "Treino não encontrado",
                        "Essa sessão não existe mais neste aparelho."), UiKit.matchWrap(requireContext(), 0));
                return;
            }
            repo.route.pointsAsync(sessionId, route -> {
                if (!isAdded()) return;
                repo.sessionSeriesAsync(sessionId, SensorCatalog.BPM, bpm -> {
                    if (!isAdded()) return;
                    render(session, route == null ? new ArrayList<GeoMath.Fix>() : route,
                            bpm == null ? new ArrayList<Point>() : bpm);
                });
            });
        });
    }

    private static String kindLabel(String k) {
        if ("run".equals(k)) return "Corrida";
        if ("hike".equals(k)) return "Trilha";
        if ("other".equals(k)) return "Treino";
        return "Caminhada";
    }

    private void render(SessionRecord s, List<GeoMath.Fix> route, List<Point> bpmSeries) {
        Context c = requireContext();
        body.removeAllViews();

        long durationMs = Math.max(0, s.endedAt - s.startedAt);
        GeoMath.Track track = GeoMath.analyze(route);
        double distanceM = s.distanceM > 0 ? s.distanceM : track.distanceM;

        setSubtitle(kindLabel(s.kind) + " · " + Fmt.date(s.startedAt) + " às " + Fmt.clock(s.startedAt));

        // Rota
        RouteMapView map = new RouteMapView(c);
        map.setRoute(route);
        map.setEmptyText(route.isEmpty() ? "Este treino não gravou rota" : "Poucos pontos de GPS");
        body.addView(map, UiKit.matchWrap(c, 0));

        // Números principais
        LinearLayout r1 = UiKit.row(c);
        addTile(r1, R.drawable.ic_target, distanceM >= 10 ? Fmt.decimals(distanceM / 1000.0, 2) : "—", "km", "Distância", 0xFF4CC9F0, R.drawable.tint_stride);
        addTile(r1, R.drawable.ic_history, Fmt.duration(durationMs), "", "Duração", 0xFF8B5CFF, R.drawable.tint_oxy);
        body.addView(r1, UiKit.matchWrap(c, 14));

        double pace = durationMs > 0 && distanceM >= 100 ? (durationMs / 1000.0) / (distanceM / 1000.0) : track.paceSecPerKm();
        LinearLayout r2 = UiKit.row(c);
        addTile(r2, R.drawable.ic_history, GeoMath.formatPace(pace), "/km", "Ritmo médio", 0xFF8B5CFF, R.drawable.tint_oxy);
        addTile(r2, R.drawable.ic_nav_shoe, Fmt.intGrouped(s.steps), "", "Passos", 0xFF00F5A0, R.drawable.tint_volt);
        body.addView(r2, UiKit.matchWrap(c, 10));

        LinearLayout r3 = UiKit.row(c);
        addTile(r3, R.drawable.ic_bolt, Fmt.energy(s.energy), "mWh", "Energia gerada", 0xFFFFB347, R.drawable.tint_volt);
        double perKm = GeoMath.mwhPerKm(s.energy, distanceM);
        addTile(r3, R.drawable.ic_bolt, perKm > 0 ? Fmt.energy(perKm) : "—", "mWh/km", "Energia por km", 0xFFFFB347, R.drawable.tint_volt);
        body.addView(r3, UiKit.matchWrap(c, 10));

        LinearLayout r4 = UiKit.row(c);
        addTile(r4, R.drawable.ic_nav_health, s.bpmAvg > 0 ? String.valueOf(Math.round(s.bpmAvg)) : "—", "bpm", "Batimentos médios", 0xFFFF5C7A, R.drawable.tint_pulse);
        addTile(r4, R.drawable.ic_target, track.elevationGainM > 0 ? String.valueOf(Math.round(track.elevationGainM)) : "—", "m", "Subida", 0xFF4CC9F0, R.drawable.tint_stride);
        body.addView(r4, UiKit.matchWrap(c, 10));

        // Batimentos ao longo do treino
        if (bpmSeries.size() >= 3) {
            body.addView(UiKit.sectionTitle(c, "Batimentos durante o treino"), UiKit.matchWrap(c, 22));
            LineChartView chart = new LineChartView(c);
            chart.setLineColor(0xFFFF5C7A);
            List<Float> vals = new ArrayList<>();
            for (Point p : bpmSeries) vals.add((float) p.value);
            chart.setData(vals);
            body.addView(chart, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 150)));
        }

        // Energia em comparações
        List<EnergyEquivalents.Item> eq = EnergyEquivalents.describe(s.energy);
        if (!eq.isEmpty()) {
            body.addView(UiKit.sectionTitle(c, "O que essa energia representa"), UiKit.matchWrap(c, 22));
            for (EnergyEquivalents.Item it : eq) {
                LinearLayout row = UiKit.panel(c);
                row.addView(UiKit.label(c, it.title));
                row.addView(UiKit.caption(c, it.detail));
                body.addView(row, UiKit.matchWrap(c, 8));
            }
            body.addView(UiKit.caption(c, "Comparações de ordem de grandeza, para dar noção de escala."), UiKit.matchWrap(c, 6));
        }

        if (track.rejectedPoints > 0) {
            body.addView(UiKit.caption(c, track.rejectedPoints + " ponto(s) de GPS foram descartados por baixa precisão ou salto impossível."),
                    UiKit.matchWrap(c, 12));
        }

        // Ações
        body.addView(UiKit.primaryButton(c, "Compartilhar resumo", v -> shareText(s, distanceM, pace)), UiKit.matchWrap(c, 22));
        if (s.remoteId != null) {
            body.addView(UiKit.ghostButton(c, "Publicar no feed", v -> publish(s)), UiKit.matchWrap(c, 10));
        } else {
            body.addView(UiKit.caption(c, "Este treino ainda não subiu para a conta. Quando subir, dá para publicar no feed."), UiKit.matchWrap(c, 10));
        }
        body.addView(UiKit.ghostButton(c, "Exportar em PDF", v -> exportPdf(s, route)), UiKit.matchWrap(c, 10));
        body.addView(UiKit.ghostButton(c, "Ver todos os detalhes", v -> {
            Bundle b = new Bundle();
            b.putLong(SessionDetailsFragment.ARG_SESSION_ID, s.id);
            nav().navigate(Navigator.SESSION_DETAILS, b);
        }), UiKit.matchWrap(c, 10));
    }

    private void addTile(LinearLayout row, int icon, String value, String unit, String label, int accent, int tint) {
        Context c = requireContext();
        LinearLayout t = UiKit.statTile(c, icon, value, unit, label, accent, tint);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) lp.leftMargin = UiKit.dp(c, 10);
        row.addView(t, lp);
    }

    private void shareText(SessionRecord s, double distanceM, double pace) {
        StringBuilder sb = new StringBuilder();
        sb.append(kindLabel(s.kind)).append(" no StepVolt — ").append(Fmt.date(s.startedAt)).append('\n');
        if (distanceM >= 10) sb.append("Distância: ").append(Fmt.decimals(distanceM / 1000.0, 2)).append(" km\n");
        sb.append("Duração: ").append(Fmt.duration(Math.max(0, s.endedAt - s.startedAt))).append('\n');
        if (pace > 0) sb.append("Ritmo: ").append(GeoMath.formatPace(pace)).append(" /km\n");
        sb.append("Passos: ").append(Fmt.intGrouped(s.steps)).append('\n');
        sb.append("Energia gerada pelo tênis: ").append(Fmt.energy(s.energy)).append(" mWh");
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, "Compartilhar resumo"));
    }

    private void publish(SessionRecord s) {
        ApiClient.get(requireContext()).shareActivity(s.remoteId).enqueue(new Callback<ActivityRecord>() {
            @Override public void onResponse(Call<ActivityRecord> call, Response<ActivityRecord> r) {
                if (!isAdded()) return;
                if (r.isSuccessful()) toast("Publicado no feed.");
                else Toast.makeText(requireContext(), ApiUtil.parseError(r), Toast.LENGTH_LONG).show();
            }
            @Override public void onFailure(Call<ActivityRecord> call, Throwable t) {
                if (isAdded()) toast("Sem conexão. Tente de novo mais tarde.");
            }
        });
    }

    private void exportPdf(SessionRecord s, List<GeoMath.Fix> route) {
        try {
            java.io.File f = PdfReport.sessionReport(requireContext(), s, route);
            com.stepvolt.app.export.Exporter.share(requireContext(), f, "application/pdf", "Resumo do treino");
        } catch (Exception e) {
            toast("Não foi possível gerar o PDF: " + e.getMessage());
        }
    }
}
