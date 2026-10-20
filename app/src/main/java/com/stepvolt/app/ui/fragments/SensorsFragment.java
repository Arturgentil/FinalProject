package com.stepvolt.app.ui.fragments;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.ble.BleState;
import com.stepvolt.app.ble.Metric;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.ble.SensorSample;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.data.AlertRecord;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.util.Fmt;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Estado cru de cada sensor, em tempo real.
 *
 * A lista não é fixa: ela é o catálogo do app somado a tudo que o firmware
 * mandou nesta execução. Se amanhã o ESP32 começar a enviar uma chave nova no
 * JSON, ela aparece aqui sozinha, com nome, valor e gráfico — sem precisar
 * mexer no app. É isso que torna a arquitetura expansível na prática.
 */
public class SensorsFragment extends BaseScreenFragment implements Telemetry.Listener {

    private LinearLayout list;
    private LinearLayout throughputPanel;
    private LinearLayout rawPanel;

    private final LinkedList<String> rawLines = new LinkedList<>();
    private boolean showRaw = false;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            renderThroughput();
            ticker.postDelayed(this, 2000);
        }
    };

    @Override
    protected String screenTitle() {
        return "Sensores";
    }

    @Override
    protected boolean showsBack() {
        return true;
    }

    @Override
    protected void buildContent() {
        setSubtitle("O que cada grandeza está medindo agora");
        addAction(R.drawable.ic_bluetooth, "Tênis", v -> nav().navigate(Navigator.DEVICE));

        throughputPanel = UiKit.panel(requireContext());
        addBlock(throughputPanel, 4);

        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        addBlock(list, 0);

        rawPanel = UiKit.panel(requireContext());
        addBlock(rawPanel, 12);

        renderThroughput();
        renderList();
        renderRaw();
    }

    @Override
    public void onResume() {
        super.onResume();
        AppState.get().telemetry.addListener(this);
        ticker.post(tick);
        renderList();
    }

    @Override
    public void onPause() {
        super.onPause();
        AppState.get().telemetry.removeListener(this);
        ticker.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------

    private void renderThroughput() {
        if (!isAdded() || throughputPanel == null) return;
        throughputPanel.removeAllViews();

        boolean connected = AppState.get().bleManager.isConnected();
        long packets = AppState.get().bleManager.getPacketCount();
        long last = AppState.get().bleManager.getLastPacketAt();
        long since = AppState.get().bleManager.getConnectedSince();

        LinearLayout row = UiKit.row(requireContext());
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UiKit.sectionTitle(requireContext(),
                connected ? "Recebendo dados" : "Sem ligação com o tênis"));

        String detail;
        if (!connected) {
            detail = "Conecte o tênis para ver as leituras chegando.";
        } else if (since > 0 && packets > 0) {
            double seconds = Math.max(1, (System.currentTimeMillis() - since) / 1000.0);
            detail = String.format(Locale.getDefault(),
                    "%s pacotes · %.1f por segundo · último %s",
                    Fmt.intGrouped(packets), packets / seconds, Fmt.ago(last));
        } else {
            detail = "Ligado, aguardando o primeiro pacote.";
        }
        col.addView(UiKit.caption(requireContext(), detail));
        row.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        throughputPanel.addView(row);
    }

    private void renderList() {
        if (!isAdded() || list == null) return;
        Telemetry t = AppState.get().telemetry;
        list.removeAllViews();

        List<String> keys = new ArrayList<>();
        for (Metric m : SensorCatalog.all()) keys.add(m.key);
        for (String k : t.liveMetrics()) if (!keys.contains(k) && !SensorCatalog.isHidden(k)) keys.add(k);

        boolean anyLive = false;
        for (String key : keys) {
            Double value = t.latest(key);
            if (value != null) anyLive = true;
            list.addView(sensorCard(key, value, t), UiKit.matchWrap(requireContext(), 12));
        }

        if (!anyLive) {
            list.addView(UiKit.note(requireContext(),
                    "Os sensores abaixo estão previstos pelo app, mas nenhum valor chegou ainda "
                            + "nesta execução. Chaves novas enviadas pelo firmware aparecem aqui "
                            + "automaticamente.", 0xFF4CC9F0), 0);
        }
    }

    private View sensorCard(String key, Double value, Telemetry t) {
        Metric m = SensorCatalog.byKey(key);
        String label = m != null ? m.label : key;
        String unit = m != null ? m.unit : "";
        int accent = m != null ? m.color : 0xFF8B5CFF;
        int decimals = m != null ? m.decimals : 2;

        String valueText = value == null ? "—" : Fmt.decimals(value, decimals)
                + (unit.isEmpty() ? "" : " " + unit);

        long at = t.latestAt(key);
        StringBuilder meta = new StringBuilder();
        if (value == null) {
            meta.append(m == null ? "grandeza enviada pelo tênis" : "aguardando leitura");
        } else {
            meta.append(Fmt.ago(at));
            if (m != null) {
                meta.append(" · faixa habitual ")
                        .append(Fmt.decimals(m.usualLow, decimals)).append("–")
                        .append(Fmt.decimals(m.usualHigh, decimals));
                if (m.isOutsideUsual(value)) meta.append(" · fora da faixa");
            }
            if (m == null) meta.append(" · nova para o app");
        }

        float[] series = t.liveValues(key);
        LinearLayout card = UiKit.metricRow(requireContext(), label, valueText, meta.toString(),
                accent, series.length > 1 ? series : null);

        if (m != null && m.cumulative) {
            card.addView(UiKit.caption(requireContext(),
                    "Valor acumulado: cada pacote traz o quanto somou desde o anterior."),
                    UiKit.matchWrap(requireContext(), 10));
        }
        return card;
    }

    private void renderRaw() {
        if (!isAdded() || rawPanel == null) return;
        rawPanel.removeAllViews();

        LinearLayout head = UiKit.row(requireContext());
        head.addView(UiKit.sectionTitle(requireContext(), "Fluxo bruto"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView toggle = UiKit.caption(requireContext(), showRaw ? "ocultar" : "mostrar");
        toggle.setTextColor(0xFF00F5A0);
        toggle.setClickable(true);
        toggle.setOnClickListener(v -> {
            showRaw = !showRaw;
            renderRaw();
        });
        head.addView(toggle);
        rawPanel.addView(head);

        if (!showRaw) {
            rawPanel.addView(UiKit.caption(requireContext(),
                    "O JSON exatamente como o ESP32 envia. Útil para conferir o firmware."),
                    UiKit.matchWrap(requireContext(), 8));
            return;
        }

        if (rawLines.isEmpty()) {
            rawPanel.addView(UiKit.caption(requireContext(),
                    "Nenhum pacote recebido desde que esta tela abriu."),
                    UiKit.matchWrap(requireContext(), 8));
            return;
        }

        for (String line : rawLines) {
            TextView tv = new TextView(requireContext());
            tv.setText(line);
            tv.setTextColor(0xFF7A7A99);
            tv.setTextSize(11f);
            tv.setTypeface(Typeface.MONOSPACE);
            rawPanel.addView(tv, UiKit.matchWrap(requireContext(), 6));
        }
    }

    // ------------------------------------------------------------------
    // Telemetry.Listener
    // ------------------------------------------------------------------

    @Override
    public void onLiveData(SensorSample sample) {
        if (!isAdded()) return;
        if (sample.raw != null) {
            rawLines.addFirst(Fmt.clockWithSeconds(sample.timestamp) + "  " + sample.raw);
            while (rawLines.size() > 20) rawLines.removeLast();
        }
        renderList();
        if (showRaw) renderRaw();
    }

    @Override
    public void onConnectionChanged(BleState state, String detail) {
        if (!isAdded()) return;
        renderThroughput();
    }

    @Override
    public void onAlertRaised(AlertRecord alert) {
        // Os avisos têm tela própria.
    }
}
