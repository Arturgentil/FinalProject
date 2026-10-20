package com.stepvolt.app.health;

import android.content.Context;

import com.stepvolt.app.ble.Metric;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.data.Point;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.data.Stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leitura dos dados feita no próprio celular, sem depender de servidor nenhum.
 *
 * É o que aparece na aba Análise quando não há internet ou quando o serviço de
 * análise não está configurado, e também é o resumo que vai junto com o pedido
 * ao servidor — mandar números agregados em vez de milhares de amostras.
 */
public class Insights {

    public static class Card {
        public final String title;
        public final String body;
        public final int accent;

        public Card(String title, String body, int accent) {
            this.title = title;
            this.body = body;
            this.accent = accent;
        }
    }

    /** Números do dia, prontos para a tela e para mandar ao servidor. */
    public static class Snapshot {
        public long windowStart;
        public long windowEnd;
        public long steps;
        public double energy;
        public Stats bpm = new Stats();
        public Stats spo2 = new Stats();
        public Stats temp = new Stats();
        public int sampleCount;

        public String toCompactJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"passos\":").append(steps);
            sb.append(",\"energia_mwh\":").append(round(energy, 1));
            appendStats(sb, "bpm", bpm);
            appendStats(sb, "spo2", spo2);
            appendStats(sb, "temp", temp);
            sb.append(",\"amostras\":").append(sampleCount);
            sb.append("}");
            return sb.toString();
        }

        private static void appendStats(StringBuilder sb, String name, Stats s) {
            if (s == null || s.isEmpty()) return;
            sb.append(",\"").append(name).append("\":{")
                    .append("\"media\":").append(round(s.avg, 1))
                    .append(",\"min\":").append(round(s.min, 1))
                    .append(",\"max\":").append(round(s.max, 1))
                    .append(",\"variacao\":").append(round(s.stdev, 1))
                    .append(",\"tendencia_por_min\":").append(round(s.slopePerMinute, 3))
                    .append("}");
        }

        private static double round(double v, int places) {
            double f = Math.pow(10, places);
            return Math.round(v * f) / f;
        }
    }

    /** Monta o retrato de um período. Chame fora da thread principal. */
    public static Snapshot snapshot(Context ctx, long fromTs, long toTs) {
        Repo repo = Repo.get(ctx);
        Snapshot s = new Snapshot();
        s.windowStart = fromTs;
        s.windowEnd = toTs;
        s.steps = Math.round(repo.total(SensorCatalog.STEPS, fromTs, toTs));
        s.energy = repo.total(SensorCatalog.ENERGY, fromTs, toTs);

        List<Point> bpm = repo.series(SensorCatalog.BPM, fromTs, toTs, 5000);
        List<Point> spo2 = repo.series(SensorCatalog.SPO2, fromTs, toTs, 5000);
        List<Point> temp = repo.series(SensorCatalog.TEMP, fromTs, toTs, 5000);
        s.bpm = Stats.of(bpm);
        s.spo2 = Stats.of(spo2);
        s.temp = Stats.of(temp);
        s.sampleCount = bpm.size() + spo2.size() + temp.size();
        return s;
    }

    /** Cartões de leitura local, em linguagem simples e sem diagnóstico. */
    public static List<Card> read(Context ctx, Snapshot s) {
        return read(s, Prefs.dailyStepGoal(ctx), Prefs.spo2Min(ctx));
    }

    /** Versão sem Android: recebe as metas em vez de ler as preferências. */
    public static List<Card> read(Snapshot s, long goal, int spo2Limit) {
        List<Card> cards = new ArrayList<>();
        if (s == null) return cards;

        if (s.steps > 0) {
            int pct = (int) Math.min(999, Math.round(s.steps * 100.0 / Math.max(1, goal)));
            String body = String.format(Locale.getDefault(),
                    "Você somou %d passos, %d%% da sua meta diária de %d. "
                            + "Isso gerou %.1f mWh no tênis.", s.steps, pct, goal, s.energy);
            cards.add(new Card("Movimento do dia", body, 0xFFFFB347));
        }

        if (!s.bpm.isEmpty()) {
            Metric m = SensorCatalog.byKey(SensorCatalog.BPM);
            StringBuilder b = new StringBuilder(String.format(Locale.getDefault(),
                    "Média de %.0f bpm, indo de %.0f a %.0f ao longo do período.",
                    s.bpm.avg, s.bpm.min, s.bpm.max));
            if (s.bpm.stdev > 18) {
                b.append(" A variação foi grande, o que costuma aparecer quando há trechos de esforço e de descanso na mesma sessão.");
            } else if (s.bpm.stdev < 5 && s.bpm.count > 30) {
                b.append(" A leitura ficou bem constante durante todo o período.");
            }
            if (m != null && s.bpm.max > m.usualHigh) {
                b.append(String.format(Locale.getDefault(),
                        " O pico de %.0f bpm passou da faixa habitual de uso.", s.bpm.max));
            }
            cards.add(new Card("Batimentos", b.toString(), 0xFFFF5C7A));
        }

        if (!s.spo2.isEmpty()) {
            StringBuilder b = new StringBuilder(String.format(Locale.getDefault(),
                    "Oxigenação média de %.0f%%, com mínima de %.0f%%.", s.spo2.avg, s.spo2.min));
            if (s.spo2.min < spo2Limit) {
                b.append(" Houve leitura abaixo do limite que você configurou — vale conferir se o sensor estava bem encostado no pé.");
            }
            cards.add(new Card("Oxigenação", b.toString(), 0xFF4CC9F0));
        }

        if (!s.temp.isEmpty()) {
            cards.add(new Card("Temperatura", String.format(Locale.getDefault(),
                    "Entre %.1f e %.1f °C, média de %.1f °C.", s.temp.min, s.temp.max, s.temp.avg),
                    0xFFFFD166));
        }

        if (s.energy > 0) {
            // Bateria de celular comum ≈ 15,4 Wh = 15.400 mWh.
            double phoneChargePct = com.stepvolt.app.energy.EnergyEquivalents.phoneCharges(s.energy) * 100.0;
            cards.add(new Card("Energia gerada", String.format(Locale.getDefault(),
                    "Os %.1f mWh do período equivalem a cerca de %.3f%% da carga de um celular comum. "
                            + "O valor cresce com a cadência e com o peso aplicado a cada passo.",
                    s.energy, phoneChargePct), 0xFF00F5A0));
        }

        if (cards.isEmpty()) {
            cards.add(new Card("Ainda sem dados",
                    "Conecte o tênis e inicie uma sessão. Assim que os primeiros pacotes chegarem, "
                            + "a leitura do período aparece aqui.", 0xFF9A9AB8));
        }
        return cards;
    }
}
