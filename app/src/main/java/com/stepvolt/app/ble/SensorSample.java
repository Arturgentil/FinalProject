package com.stepvolt.app.ble;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Um pacote lido do tênis. Guarda qualquer grandeza numérica que tenha vindo no
 * JSON, inclusive chaves que o app ainda não conhece — assim um firmware novo
 * nunca perde dado, mesmo antes de o app ganhar um cartão para ele.
 */
public class SensorSample {

    public final long timestamp;
    public final String raw;
    private final Map<String, Double> values;

    public SensorSample(long timestamp, Map<String, Double> values, String raw) {
        this.timestamp = timestamp;
        this.values = values == null ? new LinkedHashMap<String, Double>() : values;
        this.raw = raw;
    }

    public boolean has(String key) { return values.containsKey(key); }

    public double get(String key, double def) {
        Double v = values.get(key);
        return v == null ? def : v;
    }

    public Double getOrNull(String key) { return values.get(key); }

    public Map<String, Double> values() { return Collections.unmodifiableMap(values); }

    public boolean isEmpty() { return values.isEmpty(); }

    // Atalhos das grandezas usadas com mais frequência na UI.
    public int bpm() { return (int) Math.round(get(SensorCatalog.BPM, 0)); }
    public int spo2() { return (int) Math.round(get(SensorCatalog.SPO2, 0)); }
    public long stepsDelta() { return Math.round(get(SensorCatalog.STEPS, 0)); }
    public double energyDelta() { return get(SensorCatalog.ENERGY, 0); }
}
