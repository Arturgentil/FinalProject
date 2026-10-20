package com.stepvolt.app.ui.fragments;

import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.ble.BleState;
import com.stepvolt.app.ble.Metric;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.data.AlertRecord;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.util.Fmt;

import java.util.List;
import java.util.Locale;

/**
 * Avisos disparados pelos limites configurados.
 *
 * Cada linha diz o número medido, o limite que ele passou e quando isso
 * aconteceu — nunca o que aquilo significa para a saúde de alguém. A tela
 * também leva direto aos limites, porque um aviso que incomoda deve ser
 * ajustável no mesmo fluxo em que aparece.
 */
public class AlertsFragment extends BaseScreenFragment implements Telemetry.Listener {

    private LinearLayout list;
    private LinearLayout limitsPanel;

    @Override
    protected String screenTitle() {
        return "Avisos";
    }

    @Override
    protected boolean showsBack() {
        return true;
    }

    @Override
    protected void buildContent() {
        addAction(R.drawable.ic_settings, "Limites", v -> nav().navigate(Navigator.SETTINGS));

        limitsPanel = UiKit.panel(requireContext());
        addBlock(limitsPanel, 4);

        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        addBlock(list, 0);

        renderLimits();
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        AppState.get().telemetry.addListener(this);
        if (list != null) {
            renderLimits();
            load();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        AppState.get().telemetry.removeListener(this);
        // Sair da tela marca tudo como visto: a pessoa já leu.
        Repo.get(requireContext()).markAlertsSeen();
    }

    // ------------------------------------------------------------------

    private void renderLimits() {
        limitsPanel.removeAllViews();
        boolean on = Prefs.alertsOn(requireContext());

        limitsPanel.addView(UiKit.sectionTitle(requireContext(),
                on ? "Limites ativos" : "Avisos desligados"));

        if (!on) {
            limitsPanel.addView(UiKit.caption(requireContext(),
                    "Nenhum aviso novo será gerado enquanto estiverem desligados em Ajustes."),
                    UiKit.matchWrap(requireContext(), 8));
        } else {
            limitsPanel.addView(UiKit.keyValue(requireContext(), "Batimentos acima de",
                    Prefs.bpmMax(requireContext()) + " bpm"));
            limitsPanel.addView(UiKit.keyValue(requireContext(), "Batimentos abaixo de",
                    Prefs.bpmMin(requireContext()) + " bpm"));
            limitsPanel.addView(UiKit.keyValue(requireContext(), "Oxigenação abaixo de",
                    Prefs.spo2Min(requireContext()) + "%"));
            limitsPanel.addView(UiKit.keyValue(requireContext(), "Temperatura acima de",
                    String.format(Locale.getDefault(), "%.1f °C", Prefs.tempMax(requireContext()))));
            limitsPanel.addView(UiKit.caption(requireContext(),
                    "Um aviso só dispara depois de três leituras seguidas fora do limite, "
                            + "e cada tipo se repete no máximo a cada três minutos."),
                    UiKit.matchWrap(requireContext(), 12));
        }

        limitsPanel.addView(UiKit.ghostButton(requireContext(), "Ajustar limites",
                v -> nav().navigate(Navigator.SETTINGS)), UiKit.matchWrap(requireContext(), 14));
    }

    private void load() {
        Repo.get(requireContext()).alertsAsync(200, records -> {
            if (!isAdded()) return;
            list.removeAllViews();

            if (records == null || records.isEmpty()) {
                list.addView(UiKit.emptyState(requireContext(), "Nenhum aviso",
                        getString(R.string.empty_no_alerts)), UiKit.matchWrap(requireContext(), 12));
                return;
            }

            setSubtitle(records.size() + (records.size() == 1 ? " aviso registrado" : " avisos registrados"));

            String lastDay = null;
            for (AlertRecord a : records) {
                String day = Fmt.date(a.ts);
                if (!day.equals(lastDay)) {
                    lastDay = day;
                    list.addView(UiKit.sectionTitle(requireContext(), day),
                            UiKit.matchWrap(requireContext(), 18));
                }
                list.addView(alertRow(a), UiKit.matchWrap(requireContext(), 10));
            }

            list.addView(UiKit.dangerButton(requireContext(), "Limpar todos os avisos", v -> {
                Repo.get(requireContext()).clearAlerts();
                toast("Avisos apagados.");
                load();
            }), UiKit.matchWrap(requireContext(), 20));
        });
    }

    private LinearLayout alertRow(AlertRecord a) {
        LinearLayout panel = UiKit.panel(requireContext());
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout head = UiKit.row(requireContext());

        android.view.View bar = new android.view.View(requireContext());
        bar.setBackgroundColor(a.color());
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(3), dp(30));
        barLp.rightMargin = dp(12);
        head.addView(bar, barLp);

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(requireContext());
        title.setText(a.severityLabel() + " · " + metricLabel(a.metric));
        title.setTextColor(a.color());
        title.setTextSize(12);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(title);

        TextView msg = new TextView(requireContext());
        msg.setText(a.message);
        msg.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        msg.setTextSize(14);
        msg.setLineSpacing(dp(2), 1f);
        col.addView(msg, UiKit.matchWrap(requireContext(), 4));

        col.addView(UiKit.caption(requireContext(),
                Fmt.clockWithSeconds(a.ts) + " · " + Fmt.ago(a.ts)),
                UiKit.matchWrap(requireContext(), 6));

        head.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(head);

        if (!a.seen) {
            TextView badge = UiKit.caption(requireContext(), "novo");
            badge.setTextColor(0xFF00F5A0);
            panel.addView(badge, UiKit.matchWrap(requireContext(), 8));
        }
        return panel;
    }

    private String metricLabel(String key) {
        Metric m = SensorCatalog.byKey(key);
        return m != null ? m.label : (key == null ? "leitura" : key);
    }

    // ------------------------------------------------------------------

    @Override
    public void onAlertRaised(AlertRecord alert) {
        if (!isAdded()) return;
        load();
    }

    @Override
    public void onConnectionChanged(BleState state, String detail) {
        // Nada a redesenhar: os avisos são históricos.
    }
}
