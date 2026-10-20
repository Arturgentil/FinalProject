package com.stepvolt.app.ui.fragments;

import android.content.Context;
import android.widget.LinearLayout;

import com.stepvolt.app.R;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.ble.SensorSample;
import com.stepvolt.app.biomech.PressureAnalysis;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.data.Point;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.ui.views.FootPressureView;
import com.stepvolt.app.util.Fmt;

import java.util.List;

/**
 * Biomecânica do pé: mapa de pressão por zona, simetria entre os pés, cadência
 * e tempo de contato. Mostra o AO VIVO quando o tênis está enviando pressão e
 * a MÉDIA dos últimos 7 dias quando não está.
 *
 * É uma leitura de como a carga se distribui. Não diagnostica pisada nem
 * lesão — o texto da tela diz isso.
 */
public class BiomechanicsFragment extends BaseScreenFragment implements Telemetry.Listener {

    private static final String[] ZONES = {
            SensorCatalog.PL_HEEL, SensorCatalog.PL_MID, SensorCatalog.PL_TOE,
            SensorCatalog.PR_HEEL, SensorCatalog.PR_MID, SensorCatalog.PR_TOE};

    private static final long WEEK_MS = 7L * 24 * 3600 * 1000;

    private boolean live = true;
    private LinearLayout body;
    private final double[] weekMean = new double[6];
    private boolean weekLoaded = false;

    @Override protected String screenTitle() { return "Biomecânica do pé"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        addBlock(body, 0);
        loadWeek();
        render();
    }

    private void loadWeek() {
        final Repo repo = Repo.get(requireContext());
        final long now = System.currentTimeMillis();
        final int[] pending = {ZONES.length};
        for (int i = 0; i < ZONES.length; i++) {
            final int idx = i;
            repo.seriesAsync(ZONES[i], now - WEEK_MS, now, 5000, series -> {
                double sum = 0; int cnt = 0;
                if (series != null) for (Point p : series) if (p.value > 0) { sum += p.value; cnt++; }
                weekMean[idx] = cnt == 0 ? 0 : sum / cnt;
                if (--pending[0] == 0) {
                    weekLoaded = true;
                    if (isAdded()) render();
                }
            });
        }
    }

    private double[] liveMeans() {
        double[] m = new double[6];
        Telemetry t = telemetry();
        for (int i = 0; i < ZONES.length; i++) m[i] = PressureAnalysis.meanPositive(toDouble(t.liveValues(ZONES[i])));
        return m;
    }

    private static double[] toDouble(float[] f) {
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) d[i] = f[i];
        return d;
    }

    private static boolean any(double[] v) {
        for (double x : v) if (x > 0) return true;
        return false;
    }

    private void render() {
        Context c = requireContext();
        body.removeAllViews();

        double[] liveM = liveMeans();
        boolean haveLive = any(liveM);
        double[] m = haveLive && live ? liveM : weekMean;
        boolean haveData = any(m);
        boolean showingLive = haveLive && live;

        if (!haveData) {
            if (!weekLoaded) {
                body.addView(UiKit.caption(c, "Carregando…"), UiKit.matchWrap(c, 0));
            } else {
                body.addView(UiKit.emptyState(c, "Sem leitura de pressão",
                        "O tênis precisa enviar a pressão do calcanhar, do meio e do antepé de cada pé. "
                                + "Isso exige os sensores de pressão e o firmware v2 deste projeto. "
                                + "Assim que chegarem leituras, o mapa aparece aqui."), UiKit.matchWrap(c, 0));
            }
            return;
        }

        if (haveLive) {
            body.addView(UiKit.chips(c, new String[]{"Ao vivo", "Média de 7 dias"}, live ? 0 : 1, i -> {
                live = i == 0;
                render();
            }), UiKit.matchWrap(c, 0));
        }
        setSubtitle(showingLive ? "Últimos passos, ao vivo" : "Média dos últimos 7 dias");

        PressureAnalysis.Foot left = new PressureAnalysis.Foot(m[0], m[1], m[2]);
        PressureAnalysis.Foot right = new PressureAnalysis.Foot(m[3], m[4], m[5]);

        FootPressureView view = new FootPressureView(c);
        view.setFeet(left, right);
        body.addView(view, UiKit.matchWrap(c, 14));
        body.addView(UiKit.caption(c, "Cor: azul = pouca carga, vermelho = muita. O número é a % da carga do pé naquela zona."),
                UiKit.matchWrap(c, 4));

        // Padrão de pisada por pé
        body.addView(UiKit.sectionTitle(c, "Como a carga se distribui"), UiKit.matchWrap(c, 20));
        body.addView(footRow("Pé esquerdo", left), UiKit.matchWrap(c, 8));
        body.addView(footRow("Pé direito", right), UiKit.matchWrap(c, 8));

        // Simetria
        if (left.present && right.present) {
            double index = PressureAnalysis.symmetryIndex(left.totalN(), right.totalN());
            boolean leftHeavier = left.totalN() >= right.totalN();
            PressureAnalysis.SymmetryLevel lvl = PressureAnalysis.symmetryLevel(index);
            int color = lvl == PressureAnalysis.SymmetryLevel.GOOD ? 0xFF00F5A0
                    : lvl == PressureAnalysis.SymmetryLevel.ATTENTION ? 0xFFFFB347 : 0xFFFF5C7A;
            body.addView(UiKit.sectionTitle(c, "Simetria entre os pés"), UiKit.matchWrap(c, 20));
            LinearLayout tile = UiKit.statTile(c, R.drawable.ic_sensor, String.format(java.util.Locale.US, "%.0f", index),
                    "%", "Diferença de carga entre os pés", color, R.drawable.tint_stride);
            body.addView(tile, UiKit.matchWrap(c, 8));
            body.addView(UiKit.note(c, PressureAnalysis.symmetryText(index, leftHeavier), color), UiKit.matchWrap(c, 8));
        } else {
            body.addView(UiKit.caption(c, "Para calcular a simetria, o app precisa de leitura dos dois pés."), UiKit.matchWrap(c, 16));
        }

        // Cadência e contato
        Telemetry t = telemetry();
        Double cadence = t.latest(SensorCatalog.CADENCE);
        Double contact = t.latest(SensorCatalog.CONTACT);
        Double impact = t.latest(SensorCatalog.IMPACT);
        if (cadence != null || contact != null || impact != null) {
            body.addView(UiKit.sectionTitle(c, "Marcha agora"), UiKit.matchWrap(c, 20));
            LinearLayout r = UiKit.row(c);
            addTile(r, cadence == null ? "—" : String.valueOf(Math.round(cadence)), "passos/min", "Cadência");
            addTile(r, contact == null ? "—" : String.valueOf(Math.round(contact)), "ms", "Tempo de contato");
            body.addView(r, UiKit.matchWrap(c, 8));
            if (impact != null) {
                body.addView(UiKit.keyValue(c, "Impacto do último passo", Fmt.decimals(impact, 0) + " N"), UiKit.matchWrap(c, 8));
            }
        }

        body.addView(UiKit.note(c, "Esta tela descreve como a carga se distribui no pé. Ela não diagnostica o tipo de pisada nem lesões. "
                + "Se sentir dor ou desconforto, procure um profissional de saúde.", 0xFF9A9AB8), UiKit.matchWrap(c, 20));
    }

    private LinearLayout footRow(String name, PressureAnalysis.Foot f) {
        Context c = requireContext();
        LinearLayout p = UiKit.panel(c);
        p.addView(UiKit.label(c, name));
        if (!f.present) {
            p.addView(UiKit.caption(c, "Sem leitura deste pé."));
            return p;
        }
        p.addView(UiKit.caption(c, PressureAnalysis.strikeLabel(f.strike())
                + String.format(java.util.Locale.US, " — calcanhar %.0f%%, meio %.0f%%, antepé %.0f%%", f.heelPct, f.midPct, f.toePct)));
        return p;
    }

    private void addTile(LinearLayout row, String value, String unit, String label) {
        Context c = requireContext();
        LinearLayout t = UiKit.statTile(c, R.drawable.ic_sensor, value, unit, label, 0xFF8B5CFF, R.drawable.tint_oxy);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) lp.leftMargin = UiKit.dp(c, 10);
        row.addView(t, lp);
    }

    @Override public void onResume() { super.onResume(); telemetry().addListener(this); }
    @Override public void onPause() { super.onPause(); telemetry().removeListener(this); }

    private long lastRender = 0;
    @Override
    public void onLiveData(SensorSample sample) {
        if (!isAdded() || !live) return;
        long now = System.currentTimeMillis();
        if (now - lastRender < 1000) return; // não redesenha a cada pacote
        lastRender = now;
        render();
    }
}
