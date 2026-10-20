package com.stepvolt.app.ui.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.stepvolt.app.models.Challenge;
import com.stepvolt.app.models.LeaderboardEntry;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.ApiUtil;
import com.stepvolt.app.net.OkResponse;
import com.stepvolt.app.net.SessionManager;
import com.stepvolt.app.ui.UiKit;
import com.stepvolt.app.util.Fmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Desafios e ranking. O ranking compara você com quem você segue; os desafios
 * têm prazo e uma medida (passos, energia ou distância). Quem calcula o
 * progresso é o servidor, a partir das atividades enviadas — o app só mostra.
 */
public class ChallengesFragment extends BaseScreenFragment {

    private static final String[] TABS = {"Ranking", "Desafios"};
    private static final String[] PERIODS = {"7 dias", "30 dias"};
    private static final String[] PERIOD_KEYS = {"week", "month"};
    private static final String[] METRICS = {"Energia", "Passos", "Distância"};
    private static final String[] METRIC_KEYS = {"energy", "steps", "distance"};

    private int tab = 0, period = 0, metric = 0;
    private LinearLayout body;

    @Override protected String screenTitle() { return "Desafios e ranking"; }
    @Override protected boolean showsBack() { return true; }

    @Override
    protected void buildContent() {
        addBlock(UiKit.chips(requireContext(), TABS, tab, i -> { tab = i; load(); }), 0);
        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        addBlock(body, 12);
        load();
    }

    private void load() {
        body.removeAllViews();
        Context c = requireContext();
        if (!SessionManager.isLoggedIn(c)) {
            body.addView(UiKit.emptyState(c, "Entre na sua conta",
                    "Desafios e ranking comparam você com outras pessoas, então precisam de uma conta."), UiKit.matchWrap(c, 0));
            return;
        }
        if (tab == 0) loadRanking(); else loadChallenges();
    }

    // ------------------------------------------------------------------
    // Ranking
    // ------------------------------------------------------------------

    private void loadRanking() {
        final Context c = requireContext();
        body.addView(UiKit.chips(c, METRICS, metric, i -> { metric = i; load(); }), UiKit.matchWrap(c, 0));
        body.addView(UiKit.chips(c, PERIODS, period, i -> { period = i; load(); }), UiKit.matchWrap(c, 8));
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, UiKit.matchWrap(c, 14));
        list.addView(UiKit.caption(c, "Carregando…"));

        ApiClient.get(c).getLeaderboard(PERIOD_KEYS[period], METRIC_KEYS[metric]).enqueue(new Callback<List<LeaderboardEntry>>() {
            @Override public void onResponse(Call<List<LeaderboardEntry>> call, Response<List<LeaderboardEntry>> r) {
                if (!isAdded()) return;
                list.removeAllViews();
                if (!r.isSuccessful() || r.body() == null) {
                    if (ApiUtil.handleAuthError(requireContext(), r, nav())) return;
                    list.addView(UiKit.caption(requireContext(), ApiUtil.parseError(r)));
                    return;
                }
                if (r.body().size() <= 1) {
                    list.addView(UiKit.emptyState(requireContext(), "Siga outras pessoas para comparar",
                            "O ranking mostra você e quem você segue. Procure amigos na aba de busca."));
                }
                for (LeaderboardEntry e : r.body()) list.addView(entryRow(e, METRIC_KEYS[metric]), UiKit.matchWrap(requireContext(), 8));
            }
            @Override public void onFailure(Call<List<LeaderboardEntry>> call, Throwable t) {
                if (!isAdded()) return;
                list.removeAllViews();
                list.addView(UiKit.caption(requireContext(), "Sem conexão com o servidor."));
            }
        });
    }

    private LinearLayout entryRow(LeaderboardEntry e, String metricKey) {
        Context c = requireContext();
        LinearLayout p = UiKit.panel(c);
        LinearLayout row = UiKit.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView rank = UiKit.readout(c, "#" + e.rank, 18, e.rank == 1 ? 0xFFFFB347 : 0xFF9A9AB8);
        row.addView(rank, new LinearLayout.LayoutParams(UiKit.dp(c, 48), LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(UiKit.label(c, (e.isMe ? "Você" : e.name)));
        if (!e.isMe && e.username != null) t.addView(UiKit.caption(c, "@" + e.username));
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(UiKit.readout(c, formatValue(e.value, metricKey), 16, e.isMe ? 0xFF00F5A0 : 0xFFF2F2F7));
        p.addView(row);
        return p;
    }

    private static String formatValue(double v, String metricKey) {
        if ("steps".equals(metricKey)) return Fmt.intGrouped(Math.round(v));
        if ("distance".equals(metricKey)) return Fmt.decimals(v, 1) + " km";
        return Fmt.energy(v) + " mWh";
    }

    // ------------------------------------------------------------------
    // Desafios
    // ------------------------------------------------------------------

    private void loadChallenges() {
        final Context c = requireContext();
        body.addView(UiKit.primaryButton(c, "Criar desafio", v -> createDialog()), UiKit.matchWrap(c, 0));
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, UiKit.matchWrap(c, 14));
        list.addView(UiKit.caption(c, "Carregando…"));

        ApiClient.get(c).getChallenges().enqueue(new Callback<List<Challenge>>() {
            @Override public void onResponse(Call<List<Challenge>> call, Response<List<Challenge>> r) {
                if (!isAdded()) return;
                list.removeAllViews();
                if (!r.isSuccessful() || r.body() == null) {
                    if (ApiUtil.handleAuthError(requireContext(), r, nav())) return;
                    list.addView(UiKit.caption(requireContext(), ApiUtil.parseError(r)));
                    return;
                }
                if (r.body().isEmpty()) {
                    list.addView(UiKit.emptyState(requireContext(), "Nenhum desafio ainda",
                            "Crie um desafio e quem você segue pode entrar."));
                }
                for (Challenge ch : r.body()) list.addView(challengeCard(ch), UiKit.matchWrap(requireContext(), 10));
            }
            @Override public void onFailure(Call<List<Challenge>> call, Throwable t) {
                if (!isAdded()) return;
                list.removeAllViews();
                list.addView(UiKit.caption(requireContext(), "Sem conexão com o servidor."));
            }
        });
    }

    private LinearLayout challengeCard(final Challenge ch) {
        final Context c = requireContext();
        LinearLayout p = UiKit.panel(c);
        p.addView(UiKit.sectionTitle(c, ch.title));
        boolean finished = "finished".equals(ch.status);
        p.addView(UiKit.caption(c, Challenge.metricLabel(ch.metric)
                + (ch.targetValue > 0 ? " · meta " + formatValue(ch.targetValue, ch.metric) : "")
                + " · " + ch.participants + " participante" + (ch.participants == 1 ? "" : "s")
                + (finished ? " · encerrado" : "")), UiKit.matchWrap(c, 2));
        if (ch.ownerName != null && !ch.mine) p.addView(UiKit.caption(c, "Criado por " + ch.ownerName), UiKit.matchWrap(c, 2));
        if (ch.joined) p.addView(UiKit.keyValue(c, "Seu progresso", formatValue(ch.myValue, ch.metric)), UiKit.matchWrap(c, 10));

        LinearLayout actions = UiKit.row(c);
        actions.addView(UiKit.ghostButton(c, "Ver ranking", v -> showLeaderboard(ch)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (!finished) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = UiKit.dp(c, 8);
            actions.addView(ch.joined
                    ? UiKit.dangerButton(c, "Sair", v -> leave(ch))
                    : UiKit.primaryButton(c, "Participar", v -> join(ch)), lp);
        }
        p.addView(actions, UiKit.matchWrap(c, 12));
        return p;
    }

    private void join(Challenge ch) {
        ApiClient.get(requireContext()).joinChallenge(ch.id).enqueue(new Callback<Challenge>() {
            @Override public void onResponse(Call<Challenge> call, Response<Challenge> r) {
                if (!isAdded()) return;
                if (r.isSuccessful()) load(); else Toast.makeText(requireContext(), ApiUtil.parseError(r), Toast.LENGTH_LONG).show();
            }
            @Override public void onFailure(Call<Challenge> call, Throwable t) { if (isAdded()) toast("Sem conexão."); }
        });
    }

    private void leave(Challenge ch) {
        ApiClient.get(requireContext()).leaveChallenge(ch.id).enqueue(new Callback<OkResponse>() {
            @Override public void onResponse(Call<OkResponse> call, Response<OkResponse> r) {
                if (!isAdded()) return;
                if (r.isSuccessful()) load(); else Toast.makeText(requireContext(), ApiUtil.parseError(r), Toast.LENGTH_LONG).show();
            }
            @Override public void onFailure(Call<OkResponse> call, Throwable t) { if (isAdded()) toast("Sem conexão."); }
        });
    }

    private void showLeaderboard(final Challenge ch) {
        ApiClient.get(requireContext()).getChallengeLeaderboard(ch.id).enqueue(new Callback<List<LeaderboardEntry>>() {
            @Override public void onResponse(Call<List<LeaderboardEntry>> call, Response<List<LeaderboardEntry>> r) {
                if (!isAdded()) return;
                if (!r.isSuccessful() || r.body() == null) {
                    Toast.makeText(requireContext(), ApiUtil.parseError(r), Toast.LENGTH_LONG).show();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (LeaderboardEntry e : r.body()) {
                    sb.append('#').append(e.rank).append("  ").append(e.isMe ? "Você" : e.name)
                            .append("  —  ").append(formatValue(e.value, ch.metric)).append('\n');
                }
                if (sb.length() == 0) sb.append("Ninguém participa ainda.");
                new AlertDialog.Builder(requireContext()).setTitle(ch.title).setMessage(sb.toString().trim())
                        .setPositiveButton("Fechar", null).show();
            }
            @Override public void onFailure(Call<List<LeaderboardEntry>> call, Throwable t) { if (isAdded()) toast("Sem conexão."); }
        });
    }

    private void createDialog() {
        final Context c = requireContext();
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(c, 20);
        form.setPadding(pad, UiKit.dp(c, 8), pad, 0);

        final EditText title = new EditText(c);
        title.setHint("Nome do desafio");
        final EditText days = new EditText(c);
        days.setHint("Duração em dias (1 a 90)");
        days.setInputType(InputType.TYPE_CLASS_NUMBER);
        days.setText("7");
        final EditText target = new EditText(c);
        target.setHint("Meta (opcional)");
        target.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final int[] sel = {metric};
        final TextView metricBtn = UiKit.ghostButton(c, "Medida: " + METRICS[metric], null);
        metricBtn.setOnClickListener(v -> {
            sel[0] = (sel[0] + 1) % METRICS.length;
            metricBtn.setText("Medida: " + METRICS[sel[0]]);
        });
        form.addView(title); form.addView(days); form.addView(target);
        form.addView(metricBtn, UiKit.matchWrap(c, 10));

        new AlertDialog.Builder(c).setTitle("Novo desafio").setView(form)
                .setPositiveButton("Criar", (d, w) -> {
                    String t = title.getText().toString().trim();
                    int n;
                    try { n = Integer.parseInt(days.getText().toString().trim()); } catch (NumberFormatException e) { n = 0; }
                    if (t.length() < 3) { toast("Dê um nome ao desafio."); return; }
                    if (n < 1 || n > 90) { toast("A duração deve ser de 1 a 90 dias."); return; }
                    Map<String, Object> body = new HashMap<>();
                    body.put("title", t);
                    body.put("metric", METRIC_KEYS[sel[0]]);
                    body.put("days", n);
                    try {
                        double tv = Double.parseDouble(target.getText().toString().trim());
                        if (tv > 0) body.put("targetValue", tv);
                    } catch (NumberFormatException ignored) {}
                    ApiClient.get(requireContext()).createChallenge(body).enqueue(new Callback<Challenge>() {
                        @Override public void onResponse(Call<Challenge> call, Response<Challenge> r) {
                            if (!isAdded()) return;
                            if (r.isSuccessful()) { toast("Desafio criado."); load(); }
                            else Toast.makeText(requireContext(), ApiUtil.parseError(r), Toast.LENGTH_LONG).show();
                        }
                        @Override public void onFailure(Call<Challenge> call, Throwable t2) { if (isAdded()) toast("Sem conexão."); }
                    });
                })
                .setNegativeButton("Cancelar", null).show();
    }
}
