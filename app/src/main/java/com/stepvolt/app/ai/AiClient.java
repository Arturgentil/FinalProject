package com.stepvolt.app.ai;

import android.content.Context;

import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.health.Insights;
import com.stepvolt.app.net.AiClientBody;
import com.stepvolt.app.net.AiResponse;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.SessionManager;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Ponte entre a tela de análise e o servidor.
 *
 * A regra aqui é não deixar a tela vazia por culpa da rede: se o servidor não
 * responder — ou se não houver provedor de IA configurado nele — o app monta a
 * mesma leitura a partir das estatísticas locais e diz claramente que foi feita
 * no aparelho. Em nenhum dos dois caminhos o texto vira diagnóstico.
 */
public class AiClient {

    public interface Callback2 {
        void onResult(AiResponse response, boolean fromServer, String error);
    }

    private AiClient() {}

    public static void analyse(final Context ctx, final Insights.Snapshot snapshot,
                               final String periodLabel, final Callback2 cb) {
        if (snapshot == null) {
            cb.onResult(local(ctx, null), false, null);
            return;
        }

        boolean online = SessionManager.isLoggedIn(ctx)
                && Prefs.getBool(ctx, Prefs.AI_ENABLED, true);
        if (!online) {
            cb.onResult(local(ctx, snapshot), false, null);
            return;
        }

        Map<String, Object> body = AiClientBody.build(snapshot, periodLabel);
        ApiClient.get(ctx).aiInsights(body).enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                AiResponse r = response.body();
                if (response.isSuccessful() && r != null && r.hasContent()) {
                    cb.onResult(r, true, null);
                } else {
                    cb.onResult(local(ctx, snapshot), false, null);
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
                cb.onResult(local(ctx, snapshot), false,
                        "Servidor fora de alcance — análise feita no próprio aparelho.");
            }
        });
    }

    /** Converte a leitura local em resposta com o mesmo formato do servidor. */
    private static AiResponse local(Context ctx, Insights.Snapshot s) {
        AiResponse r = new AiResponse();
        r.source = "device";
        List<Insights.Card> cards = Insights.read(ctx, s);
        for (Insights.Card c : cards) {
            AiResponse.Insight i = new AiResponse.Insight();
            i.title = c.title;
            i.body = c.body;
            i.tone = "info";
            r.insights.add(i);
        }
        if (s != null && s.sampleCount > 0) {
            r.summary = "Leitura de " + s.sampleCount + " medições feitas pelo tênis.";
        }
        return r;
    }
}
