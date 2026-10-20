package com.stepvolt.app.event;

/**
 * Um evento do sistema: de um aviso de biometria a, mais pra frente, uma
 * queda confirmada ou um SOS. Os campos seguem a especificação (event_id,
 * type, severity, confidence, timestamp, duration, source, metadata, status)
 * mais a categoria, que a spec trata separadamente.
 */
public class AppEvent {

    /** Id local (SQLite). 0 até ser gravado. */
    public long id;

    /** Quando aconteceu, em millis desde epoch. */
    public long ts;

    /**
     * Chave estável em snake_case (ex.: "bpm_high", "device_disconnected").
     * É por ela que o cooldown/dedup decide se um evento é "o mesmo de novo".
     */
    public String type;

    public EventCategory category;
    public EventSeverity severity;

    /** 0 a 1. 1 = certeza (ex.: bateria lida do sensor). Menor para detecção probabilística (ex.: queda). */
    public float confidence = 1f;

    /** Quanto tempo o evento durou, quando isso faz sentido (0 = instantâneo). */
    public long durationMs;

    /** Quem gerou o evento (ex.: "AlertEngine", "Telemetry", "Manual"). Só para depuração/auditoria. */
    public String source;

    /** Texto pronto para mostrar na UI. Nunca um diagnóstico — só o fato medido. */
    public String message;

    /** JSON livre com dados extra do evento (ex.: valor e limite que dispararam um aviso). Pode ser null. */
    public String metadata;

    public EventStatus status = EventStatus.ACTIVE;

    public int color() {
        return severity.color();
    }

    public String severityLabel() {
        return severity.label();
    }

    public String categoryLabel() {
        return category.label();
    }
}
