package com.stepvolt.app.ui.fragments;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.stepvolt.app.R;
import com.stepvolt.app.ai.AiClient;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.health.Insights;
import com.stepvolt.app.net.AiResponse;
import com.stepvolt.app.ui.Navigator;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.util.Anim;

import java.util.Calendar;
import java.util.List;

/**
 * Aba de análise.
 *
 * O que acontece aqui é interpretação, não diagnóstico, e a tela é construída
 * para que essa diferença fique óbvia: o aviso fica no topo, antes de qualquer
 * resultado; cada cartão diz o que os números mostram e o que costuma
 * explicá-los; e a origem da análise (servidor ou aparelho) aparece sempre,
 * para ninguém confundir um texto gerado com uma opinião clínica.
 */
public class AiFragment extends BaseScreenFragment {

    private static final String[] PERIODS = {"Hoje", "7 dias", "30 dias"};

    private int period = 0;
    private boolean loading = false;

    private LinearLayout resultArea;
    private ProgressBar spinner;
    private TextView txtSource;
    private TextView btnRun;

    @Override
    protected String screenTitle() {
        return "Análise";
    }

    @Override
    protected void buildContent() {
        setSubtitle("Seus números, explicados em palavras");
        addAction(R.drawable.ic_settings, "Ajustes", v -> nav().navigate(Navigator.SETTINGS));

        // Primeiro o aviso, depois a análise. A ordem é proposital.
        addBlock(UiKit.note(requireContext(), getString(R.string.ai_disclaimer), 0xFFFFD166), 4);

        addBlock(UiKit.chips(requireContext(), PERIODS, period, index -> {
            period = index;
            run();
        }), 14);

        btnRun = UiKit.primaryButton(requireContext(), "Gerar análise", v -> run());
        addBlock(btnRun, 14);

        spinner = new ProgressBar(requireContext());
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        addBlock(spinner, 14);

        txtSource = UiKit.caption(requireContext(), "");
        txtSource.setVisibility(View.GONE);
        addBlock(txtSource, 14);

        resultArea = new LinearLayout(requireContext());
        resultArea.setOrientation(LinearLayout.VERTICAL);
        addBlock(resultArea, 0);

        addBlock(UiKit.linkRow(requireContext(), R.drawable.ic_history,
                "Ver o histórico completo", "Sessões gravadas, com gráficos", 0xFF8B5CFF,
                v -> nav().navigate(Navigator.HISTORY)), 16);

        run();
    }

    // ------------------------------------------------------------------

    private long fromTs() {
        if (period == 0) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        }
        return System.currentTimeMillis() - (period == 1 ? 7 : 30) * 86_400_000L;
    }

    private String periodLabel() {
        return period == 0 ? "hoje" : (period == 1 ? "últimos 7 dias" : "últimos 30 dias");
    }

    private void run() {
        if (loading || !isAdded()) return;
        loading = true;

        resultArea.removeAllViews();
        txtSource.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        btnRun.setAlpha(0.5f);
        btnRun.setText("Analisando…");

        final long from = fromTs();
        final long to = System.currentTimeMillis();

        // O resumo é calculado fora da thread principal: 30 dias de amostras
        // podem ser dezenas de milhares de linhas.
        final android.content.Context appCtx = requireContext().getApplicationContext();
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            final Insights.Snapshot snapshot = Insights.snapshot(appCtx, from, to);
            main.post(() -> {
                if (!isAdded()) return;
                AiClient.analyse(appCtx, snapshot, periodLabel(),
                        (response, fromServer, error) -> {
                            if (!isAdded()) return;
                            render(response, fromServer, error, snapshot);
                        });
            });
        }).start();
    }

    private void render(AiResponse r, boolean fromServer, String error, Insights.Snapshot s) {
        loading = false;
        spinner.setVisibility(View.GONE);
        btnRun.setAlpha(1f);
        btnRun.setText("Gerar de novo");
        resultArea.removeAllViews();

        if (s == null || s.sampleCount == 0) {
            resultArea.addView(UiKit.emptyState(requireContext(),
                    "Sem medições no período",
                    "A análise precisa de dados do tênis. Conecte o tênis, grave uma sessão e volte aqui."),
                    UiKit.matchWrap(requireContext(), 0));
            resultArea.addView(UiKit.ghostButton(requireContext(), "Ir para o tênis",
                    v -> nav().navigate(Navigator.DEVICE)), UiKit.matchWrap(requireContext(), 12));
            return;
        }

        txtSource.setVisibility(View.VISIBLE);
        String origin;
        if (fromServer && "model".equals(r.source)) {
            origin = "Análise gerada no servidor"
                    + (r.model != null && !r.model.isEmpty() ? " (" + r.model + ")" : "")
                    + " a partir de " + s.sampleCount + " medições.";
        } else if (fromServer) {
            origin = "Análise calculada no servidor a partir de " + s.sampleCount + " medições.";
        } else {
            origin = "Análise feita no próprio aparelho, a partir de " + s.sampleCount + " medições.";
        }
        if (error != null) origin += " " + error;
        txtSource.setText(origin);

        if (r.summary != null && !r.summary.isEmpty()
                && !r.summary.equals("Leitura de " + s.sampleCount + " medições feitas pelo tênis.")) {
            LinearLayout head = UiKit.panel(requireContext());
            head.addView(UiKit.sectionTitle(requireContext(), "Resumo do período"));
            head.addView(UiKit.caption(requireContext(), r.summary),
                    UiKit.matchWrap(requireContext(), 8));
            resultArea.addView(head, UiKit.matchWrap(requireContext(), 0));
        }

        List<AiResponse.Insight> insights = r.insights;
        View[] cards = new View[insights.size()];
        for (int i = 0; i < insights.size(); i++) {
            AiResponse.Insight in = insights.get(i);
            LinearLayout panel = UiKit.panel(requireContext());

            LinearLayout head = UiKit.row(requireContext());
            View bar = new View(requireContext());
            bar.setBackgroundColor(toneColor(in.tone));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(3), dp(18));
            barLp.rightMargin = dp(10);
            barLp.topMargin = dp(3);
            head.addView(bar, barLp);
            head.addView(UiKit.sectionTitle(requireContext(), in.title == null ? "Leitura" : in.title));
            panel.addView(head);

            panel.addView(UiKit.caption(requireContext(), in.body == null ? "" : in.body),
                    UiKit.matchWrap(requireContext(), 10));

            resultArea.addView(panel, UiKit.matchWrap(requireContext(), 12));
            cards[i] = panel;
        }
        Anim.revealStaggered(cards, 60);

        String extra = r.disclaimer != null && !r.disclaimer.isEmpty()
                ? r.disclaimer : getString(R.string.ai_disclaimer);
        resultArea.addView(UiKit.caption(requireContext(), extra),
                UiKit.matchWrap(requireContext(), 16));

        if (!Prefs.getBool(requireContext(), Prefs.AI_ENABLED, true)) {
            resultArea.addView(UiKit.note(requireContext(),
                    "O envio dos resumos ao servidor está desligado em Ajustes, então a análise "
                            + "acontece só neste aparelho.", 0xFF4CC9F0),
                    UiKit.matchWrap(requireContext(), 12));
        }
    }

    private int toneColor(String tone) {
        if ("attention".equals(tone)) return 0xFFFFD166;
        if ("positive".equals(tone)) return 0xFF00F5A0;
        return 0xFF4CC9F0;
    }
}
