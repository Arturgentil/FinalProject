package com.stepvolt.app.event;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.stepvolt.app.data.Repo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Barramento central de eventos do sistema.
 *
 * A ideia da spec é simples de enunciar e fácil de perder na prática: todo
 * evento do app — um limite de biometria passado, o tênis que desconectou,
 * mais pra frente uma queda ou um SOS — deveria nascer aqui, não em cada tela
 * por conta própria. É isso que permite o Safety Engine, o Black Box e as
 * automações reagirem à mesma fonte única mais adiante, em vez de cada
 * detector reinventar sua própria noção de "isso eu já avisei faz pouco".
 *
 * O que este arquivo faz, e só isso:
 *   - decide se um evento é repetição recente do mesmo tipo (cooldown/dedup);
 *   - grava no banco local (fonte de verdade offline-first, sem depender de
 *     rede ou do backend);
 *   - avisa quem estiver ouvindo.
 *
 * O que ele NÃO faz nesta fase: disparar notificação do Android por conta
 * própria. Quem já tinha esse comportamento (AlertEngine, via Notifier)
 * continua responsável por ele, para não duplicar aviso do mesmo evento.
 * Uma política central de notificação ("todo evento >= tal severidade
 * notifica, com prioridade/cooldown/escalonamento configuráveis") é trabalho
 * da fase de Automação/Notificações, sobre este barramento — não deste
 * arquivo.
 */
public class EventEngine {

    public interface Listener {
        void onEvent(AppEvent event);
    }

    /** Depois de quanto tempo o mesmo `type` pode disparar de novo. */
    private static final long DEFAULT_COOLDOWN_MS = 3 * 60 * 1000L;

    private static volatile EventEngine instance;

    public static EventEngine get(Context context) {
        if (instance == null) {
            synchronized (EventEngine.class) {
                if (instance == null) instance = new EventEngine(context.getApplicationContext());
            }
        }
        return instance;
    }

    private final Repo repo;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Long> lastFired = new ConcurrentHashMap<>();

    private EventEngine(Context context) {
        this.repo = Repo.get(context);
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Atalho para eventos determinísticos (confiança 1) sem metadado extra. */
    public AppEvent raise(EventCategory category, EventSeverity severity, String type, String message) {
        return raise(category, severity, type, message, 1f, "app", null);
    }

    /**
     * Registra um evento. Devolve null quando ele foi filtrado por cooldown
     * (mesmo `type` disparado há menos de {@link #DEFAULT_COOLDOWN_MS}) —
     * SAFETY, EMERGENCY e severidade CRITICAL nunca são filtrados, porque
     * suprimir um segundo aviso real de queda ou SOS é bem pior do que
     * repetir um aviso.
     *
     * @param confidence   0 a 1. Use 1 para leitura direta de sensor/estado;
     *                     use menos para detecção probabilística.
     * @param source       de onde veio (ex.: "AlertEngine"), só para depuração.
     * @param metadataJson JSON livre com contexto extra, ou null.
     */
    public AppEvent raise(EventCategory category, EventSeverity severity, String type,
                           String message, float confidence, String source, String metadataJson) {
        if (category == null || severity == null || type == null || message == null) return null;

        long now = System.currentTimeMillis();
        if (!bypassesCooldown(category, severity)) {
            Long last = lastFired.get(type);
            if (last != null && now - last < DEFAULT_COOLDOWN_MS) return null;
        }
        lastFired.put(type, now);

        final AppEvent e = new AppEvent();
        e.ts = now;
        e.type = type;
        e.category = category;
        e.severity = severity;
        e.confidence = Math.max(0f, Math.min(1f, confidence));
        e.source = (source == null || source.isEmpty()) ? "app" : source;
        e.message = message;
        e.metadata = metadataJson;
        e.status = EventStatus.ACTIVE;

        repo.insertEvent(e);
        main.post(() -> {
            for (Listener l : listeners) l.onEvent(e);
        });
        return e;
    }

    /** Regra de bypass de cooldown, isolada como método estático de pacote para dar para testar sem Context/Android. */
    static boolean bypassesCooldown(EventCategory category, EventSeverity severity) {
        return category == EventCategory.SAFETY
                || category == EventCategory.EMERGENCY
                || severity == EventSeverity.CRITICAL;
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    public void recentAsync(int limit, Repo.Result<List<AppEvent>> cb) {
        repo.eventsAsync(limit, cb);
    }

    public void recentByCategoryAsync(EventCategory category, int limit, Repo.Result<List<AppEvent>> cb) {
        repo.eventsByCategoryAsync(category, limit, cb);
    }

    public void unresolvedCountAsync(Repo.Result<Integer> cb) {
        repo.unresolvedEventCountAsync(cb);
    }

    public void acknowledge(long eventId) {
        repo.setEventStatus(eventId, EventStatus.ACKNOWLEDGED);
    }

    public void resolve(long eventId) {
        repo.setEventStatus(eventId, EventStatus.RESOLVED);
    }

    public void clearAll() {
        repo.clearEvents();
        lastFired.clear();
    }
}
