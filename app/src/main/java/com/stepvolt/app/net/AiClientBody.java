package com.stepvolt.app.net;

import com.stepvolt.app.data.Stats;
import com.stepvolt.app.health.Insights;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduz um resumo do período no corpo enviado a /api/ai/insights.
 *
 * Só sobem números agregados — nada de série crua, nada que identifique a
 * pessoa além do token que o Retrofit já manda. Quanto menor o corpo, mais
 * barato e mais privado fica o pedido.
 */
public final class AiClientBody {

    private AiClientBody() {}

    public static Map<String, Object> build(Insights.Snapshot s, String periodLabel) {
        Map<String, Object> body = new HashMap<>();
        body.put("period", periodLabel == null ? "período" : periodLabel);
        body.put("from", s.windowStart);
        body.put("to", s.windowEnd);
        body.put("steps", s.steps);
        body.put("energy", round(s.energy, 2));
        body.put("sampleCount", s.sampleCount);
        body.put("locale", "pt-BR");

        Map<String, Object> metrics = new LinkedHashMap<>();
        putStats(metrics, "bpm", s.bpm);
        putStats(metrics, "spo2", s.spo2);
        putStats(metrics, "temp", s.temp);
        body.put("metrics", metrics);
        return body;
    }

    private static void putStats(Map<String, Object> target, String key, Stats st) {
        if (st == null || st.isEmpty()) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", st.count);
        m.put("avg", round(st.avg, 1));
        m.put("min", round(st.min, 1));
        m.put("max", round(st.max, 1));
        m.put("stdev", round(st.stdev, 2));
        m.put("slopePerMinute", round(st.slopePerMinute, 4));
        target.put(key, m);
    }

    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
