package com.stepvolt.app.health;

import android.content.Context;

import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.ble.SensorSample;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.data.AlertRecord;
import com.stepvolt.app.event.EventCategory;
import com.stepvolt.app.event.EventEngine;
import com.stepvolt.app.event.EventSeverity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compara cada pacote do tênis com os limites que o usuário definiu em Ajustes.
 *
 * Duas decisões importantes:
 *  - um aviso só repete depois de um intervalo de silêncio, senão um batimento
 *    alto por dois minutos viraria cem notificações;
 *  - a medida precisa passar do limite em algumas leituras seguidas antes de
 *    virar aviso, porque um pico isolado quase sempre é ruído do sensor.
 *
 * Os textos falam do número medido e do limite escolhido. Em nenhum momento o
 * app diagnostica nada — quem interpreta sintoma é profissional de saúde.
 *
 * Desde o Event Engine, cada aviso aqui também vira um AppEvent (categoria
 * BIOMETRIC ou POWER). O caminho antigo (AlertRecord/Notifier/AlertsFragment)
 * continua exatamente como estava — este é aditivo, não uma substituição.
 */
public class AlertEngine {

    private static final long SILENCE_MS = 3 * 60 * 1000L;
    private static final int CONSECUTIVE_NEEDED = 3;

    /** Limites configurados pelo usuário, separados para a regra poder ser testada sem Android. */
    public static class Limits {
        public boolean enabled = true;
        public int bpmMax = 180;
        public int bpmMin = 40;
        public int spo2Min = 92;
        public float tempMax = 38.5f;

        public static Limits fromPrefs(Context ctx) {
            Limits l = new Limits();
            l.enabled = Prefs.alertsOn(ctx);
            l.bpmMax = Prefs.bpmMax(ctx);
            l.bpmMin = Prefs.bpmMin(ctx);
            l.spo2Min = Prefs.spo2Min(ctx);
            l.tempMax = Prefs.tempMax(ctx);
            return l;
        }
    }

    /** Um aviso disparado, com a chave da regra que o gerou. */
    public static class Fired {
        public final String key;
        public final AlertRecord record;
        Fired(String key, AlertRecord record) { this.key = key; this.record = record; }
    }

    private final Map<String, Long> lastFired = new HashMap<>();
    private final Map<String, Integer> streak = new HashMap<>();

    public List<AlertRecord> evaluate(Context ctx, SensorSample sample) {
        List<AlertRecord> out = new ArrayList<>();
        for (Fired f : check(Limits.fromPrefs(ctx), sample, System.currentTimeMillis())) {
            out.add(f.record);
            publishEvent(ctx, f);
        }
        return out;
    }

    /**
     * Regra pura: compara o pacote com os limites e devolve os avisos que
     * devem disparar agora. Não mexe em banco, notificação nem Prefs.
     */
    public List<Fired> check(Limits limits, SensorSample sample, long now) {
        List<Fired> out = new ArrayList<>();
        if (limits == null || !limits.enabled || sample == null) return out;

        Double bpm = sample.getOrNull(SensorCatalog.BPM);
        if (bpm != null && bpm > 0) {
            int max = limits.bpmMax;
            int min = limits.bpmMin;
            if (bpm > max) {
                add(out, now, "bpm_high", SensorCatalog.BPM, bpm, max, AlertRecord.CRITICAL,
                        "Batimentos em " + Math.round(bpm) + " bpm, acima do seu limite de " + max + ".");
            } else if (bpm < min) {
                add(out, now, "bpm_low", SensorCatalog.BPM, bpm, min, AlertRecord.WARNING,
                        "Batimentos em " + Math.round(bpm) + " bpm, abaixo do seu limite de " + min + ".");
            } else {
                reset("bpm_high");
                reset("bpm_low");
            }
        }

        Double spo2 = sample.getOrNull(SensorCatalog.SPO2);
        if (spo2 != null && spo2 > 0) {
            int min = limits.spo2Min;
            if (spo2 < min) {
                add(out, now, "spo2_low", SensorCatalog.SPO2, spo2, min, AlertRecord.CRITICAL,
                        "Oxigenação medida em " + Math.round(spo2) + "%, abaixo do limite de " + min + "%.");
            } else {
                reset("spo2_low");
            }
        }

        Double temp = sample.getOrNull(SensorCatalog.TEMP);
        if (temp != null && temp > 0) {
            float max = limits.tempMax;
            if (temp > max) {
                add(out, now, "temp_high", SensorCatalog.TEMP, temp, max, AlertRecord.WARNING,
                        String.format(java.util.Locale.getDefault(),
                                "Temperatura em %.1f °C, acima do limite de %.1f °C.", temp, max));
            } else {
                reset("temp_high");
            }
        }

        Double batt = sample.getOrNull(SensorCatalog.BATTERY);
        if (batt != null && batt > 0 && batt < 15) {
            add(out, now, "battery_low", SensorCatalog.BATTERY, batt, 15, AlertRecord.INFO,
                    "Bateria do tênis em " + Math.round(batt) + "%. Vale recarregar.");
        } else if (batt != null && batt >= 15) {
            reset("battery_low");
        }

        return out;
    }

    private void add(List<Fired> out, long now, String key, String metric, double value,
                     double threshold, int severity, String message) {
        int n = streak.containsKey(key) ? streak.get(key) + 1 : 1;
        streak.put(key, n);
        if (n < CONSECUTIVE_NEEDED) return;

        Long last = lastFired.get(key);
        if (last != null && now - last < SILENCE_MS) return;
        lastFired.put(key, now);

        AlertRecord a = new AlertRecord();
        a.ts = now;
        a.metric = metric;
        a.value = value;
        a.threshold = threshold;
        a.severity = severity;
        a.message = message;
        a.seen = false;
        out.add(new Fired(key, a));
    }

    /**
     * Mesmo aviso, publicado no barramento central de eventos (além do caminho
     * antigo, que Notifier/AlertsFragment/HomeFragment/DeviceFragment/
     * SensorsFragment já leem). CRITICAL aqui vira HIGH — a severidade
     * CRITICAL do Event Engine fica reservada para SAFETY/EMERGENCY (queda
     * confirmada, SOS), não para uma leitura de biometria fora do limite.
     */
    private void publishEvent(Context ctx, Fired f) {
        EventCategory category = SensorCatalog.BATTERY.equals(f.record.metric)
                ? EventCategory.POWER : EventCategory.BIOMETRIC;
        EventSeverity mapped = f.record.severity == AlertRecord.CRITICAL ? EventSeverity.HIGH
                : f.record.severity == AlertRecord.WARNING ? EventSeverity.MEDIUM : EventSeverity.LOW;
        EventEngine.get(ctx).raise(category, mapped, f.key, f.record.message);
    }

    private void reset(String key) {
        streak.remove(key);
    }
}
