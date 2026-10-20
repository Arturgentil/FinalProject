package com.stepvolt.app.ble;

/**
 * Descrição de uma grandeza que o tênis pode enviar. Adicionar um sensor novo
 * é acrescentar uma linha em {@link SensorCatalog} — o resto do app (cartões,
 * gráficos, histórico, alertas, análise) passa a mostrar a grandeza sozinho.
 */
public class Metric {

    /** Como a chave chega no JSON do ESP32. */
    public final String key;
    /** Outros nomes aceitos para a mesma grandeza (firmwares antigos). */
    public final String[] aliases;
    public final String label;
    public final String unit;
    public final int color;
    public final int iconRes;
    /** Faixa usada nos medidores e gráficos. */
    public final float min;
    public final float max;
    /** Faixa considerada habitual em repouso/uso normal (para contexto, não diagnóstico). */
    public final float usualLow;
    public final float usualHigh;
    /** true = o valor é um acréscimo desde o último pacote (passos, energia). */
    public final boolean cumulative;
    public final int decimals;
    /** true = grandeza de uso interno (ex.: pressão por zona do pé): não ganha cartão nas listas gerais. */
    public final boolean hidden;

    public Metric(String key, String[] aliases, String label, String unit, int color, int iconRes,
                  float min, float max, float usualLow, float usualHigh,
                  boolean cumulative, int decimals) {
        this(key, aliases, label, unit, color, iconRes, min, max, usualLow, usualHigh,
                cumulative, decimals, false);
    }

    public Metric(String key, String[] aliases, String label, String unit, int color, int iconRes,
                  float min, float max, float usualLow, float usualHigh,
                  boolean cumulative, int decimals, boolean hidden) {
        this.hidden = hidden;
        this.key = key;
        this.aliases = aliases == null ? new String[0] : aliases;
        this.label = label;
        this.unit = unit;
        this.color = color;
        this.iconRes = iconRes;
        this.min = min;
        this.max = max;
        this.usualLow = usualLow;
        this.usualHigh = usualHigh;
        this.cumulative = cumulative;
        this.decimals = decimals;
    }

    public boolean matches(String jsonKey) {
        if (jsonKey == null) return false;
        String k = jsonKey.toLowerCase();
        if (key.equals(k)) return true;
        for (String a : aliases) if (a.equals(k)) return true;
        return false;
    }

    /** Onde o valor cai dentro da faixa do medidor, entre 0 e 1. */
    public float fraction(double value) {
        if (max <= min) return 0f;
        double f = (value - min) / (max - min);
        return (float) Math.max(0, Math.min(1, f));
    }

    public boolean isOutsideUsual(double value) {
        return value < usualLow || value > usualHigh;
    }
}
