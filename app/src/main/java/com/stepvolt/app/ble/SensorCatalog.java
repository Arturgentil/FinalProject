package com.stepvolt.app.ble;

import com.stepvolt.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catálogo das grandezas que o tênis envia.
 *
 * Para suportar um sensor novo no app inteiro, acrescente um Metric aqui e
 * mande a chave correspondente no JSON do ESP32. Cartões, gráficos, histórico,
 * exportação e a análise passam a incluir a grandeza automaticamente.
 */
public final class SensorCatalog {

    public static final String BPM = "bpm";
    public static final String SPO2 = "spo2";
    public static final String STEPS = "steps";
    public static final String ENERGY = "energy";
    public static final String TEMP = "temp";
    public static final String CADENCE = "cadence";
    public static final String IMPACT = "impact";
    public static final String BATTERY = "battery";

    // Geração de energia (protocolo v2)
    public static final String VOLT = "volt";
    public static final String CURRENT = "curr";
    public static final String POWER = "pwr";
    // Marcha
    public static final String CONTACT = "contact";
    // Pressão por zona do pé, em N. "l" = pé esquerdo, "r" = direito.
    public static final String PL_HEEL = "pl_h";
    public static final String PL_MID = "pl_m";
    public static final String PL_TOE = "pl_t";
    public static final String PR_HEEL = "pr_h";
    public static final String PR_MID = "pr_m";
    public static final String PR_TOE = "pr_t";
    // Sinais de segurança vindos do próprio tênis (1 = ativo)
    public static final String FALL = "fall";
    public static final String SOS = "sos";

    private static final List<Metric> ALL = new ArrayList<>();

    static {
        ALL.add(new Metric(BPM, new String[]{"hr", "heart", "bpmavg"},
                "Batimentos", "bpm", 0xFFFF5C7A, R.drawable.ic_nav_health,
                40, 200, 55, 160, false, 0));

        ALL.add(new Metric(SPO2, new String[]{"ox", "oxygen", "sp02"},
                "Oxigenação", "%", 0xFF4CC9F0, R.drawable.ic_drop,
                85, 100, 94, 100, false, 0));

        ALL.add(new Metric(STEPS, new String[]{"step", "passos"},
                "Passos", "", 0xFFFFB347, R.drawable.ic_nav_shoe,
                0, 12000, 0, 12000, true, 0));

        ALL.add(new Metric(ENERGY, new String[]{"mwh", "e", "energia"},
                "Energia gerada", "mWh", 0xFF00F5A0, R.drawable.ic_bolt,
                0, 800, 0, 800, true, 1));

        ALL.add(new Metric(TEMP, new String[]{"temperature", "temperatura", "t"},
                "Temperatura", "°C", 0xFFFFD166, R.drawable.ic_info,
                20, 45, 30, 37.8f, false, 1));

        ALL.add(new Metric(CADENCE, new String[]{"cad", "spm"},
                "Cadência", "passos/min", 0xFF8B5CFF, R.drawable.ic_sensor,
                0, 220, 0, 200, false, 0));

        ALL.add(new Metric(IMPACT, new String[]{"force", "pressure", "impacto"},
                "Impacto do passo", "N", 0xFF4CC9F0, R.drawable.ic_sensor,
                0, 1500, 0, 1200, false, 0));

        ALL.add(new Metric(BATTERY, new String[]{"batt", "bat", "bateria"},
                "Bateria do tênis", "%", 0xFF9A9AB8, R.drawable.ic_bolt,
                0, 100, 15, 100, false, 0));

        ALL.add(new Metric(VOLT, new String[]{"voltage", "tensao"},
                "Tensão do gerador", "V", 0xFF00F5A0, R.drawable.ic_bolt,
                0, 12, 0, 12, false, 2));

        ALL.add(new Metric(CURRENT, new String[]{"current", "corrente"},
                "Corrente do gerador", "mA", 0xFFFFB347, R.drawable.ic_bolt,
                0, 200, 0, 200, false, 1));

        ALL.add(new Metric(POWER, new String[]{"power", "potencia"},
                "Potência gerada", "mW", 0xFF00F5A0, R.drawable.ic_bolt,
                0, 500, 0, 500, false, 1));

        ALL.add(new Metric(CONTACT, new String[]{"gct", "contato"},
                "Tempo de contato", "ms", 0xFF8B5CFF, R.drawable.ic_sensor,
                100, 500, 150, 400, false, 0));

        String[] zones = {PL_HEEL, PL_MID, PL_TOE, PR_HEEL, PR_MID, PR_TOE};
        String[] zoneNames = {"Pressão calcanhar (esq.)", "Pressão meio do pé (esq.)", "Pressão antepé (esq.)",
                "Pressão calcanhar (dir.)", "Pressão meio do pé (dir.)", "Pressão antepé (dir.)"};
        for (int i = 0; i < zones.length; i++) {
            ALL.add(new Metric(zones[i], null, zoneNames[i], "N", 0xFF4CC9F0, R.drawable.ic_sensor,
                    0, 1500, 0, 1500, false, 0, true));
        }
        ALL.add(new Metric(FALL, null, "Queda detectada no tênis", "", 0xFFFF5C7A, R.drawable.ic_alert,
                0, 1, 0, 1, false, 0, true));
        ALL.add(new Metric(SOS, null, "Botão SOS do tênis", "", 0xFFFF5C7A, R.drawable.ic_alert,
                0, 1, 0, 1, false, 0, true));
    }

    private SensorCatalog() {}

    /** Grandezas com cartão próprio nas listas (exclui as de uso interno). */
    public static List<Metric> all() {
        List<Metric> out = new ArrayList<>();
        for (Metric m : ALL) if (!m.hidden) out.add(m);
        return Collections.unmodifiableList(out);
    }

    public static boolean isHidden(String key) {
        Metric m = byKey(key);
        return m != null && m.hidden;
    }

    /** Grandezas instantâneas (as que fazem sentido em medidor ao vivo). */
    public static List<Metric> instantaneous() {
        List<Metric> out = new ArrayList<>();
        for (Metric m : ALL) if (!m.cumulative && !m.hidden && !BATTERY.equals(m.key)) out.add(m);
        return out;
    }

    public static Metric byKey(String key) {
        if (key == null) return null;
        for (Metric m : ALL) if (m.key.equals(key)) return m;
        return null;
    }

    /** Resolve a chave crua vinda do firmware para a chave canônica do app. */
    public static String canonical(String jsonKey) {
        for (Metric m : ALL) if (m.matches(jsonKey)) return m.key;
        return jsonKey == null ? null : jsonKey.toLowerCase();
    }
}
