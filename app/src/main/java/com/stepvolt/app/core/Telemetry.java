package com.stepvolt.app.core;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.stepvolt.app.ble.BleManager;
import com.stepvolt.app.ble.BleState;
import com.stepvolt.app.ble.SensorCatalog;
import com.stepvolt.app.ble.SensorSample;
import com.stepvolt.app.ble.SessionService;
import com.stepvolt.app.energy.ShoeWear;
import com.stepvolt.app.gps.GeoMath;
import com.stepvolt.app.gps.LocationTracker;
import com.stepvolt.app.safety.FallDetector;
import com.stepvolt.app.safety.SosManager;
import com.stepvolt.app.data.AlertRecord;
import com.stepvolt.app.data.Point;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.data.SessionRecord;
import com.stepvolt.app.event.EventCategory;
import com.stepvolt.app.event.EventEngine;
import com.stepvolt.app.event.EventSeverity;
import com.stepvolt.app.health.AlertEngine;
import com.stepvolt.app.models.ActivityRecord;
import com.stepvolt.app.net.ApiClient;
import com.stepvolt.app.net.SessionManager;
import com.stepvolt.app.util.Fmt;
import com.stepvolt.app.util.Notifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * O centro dos dados ao vivo.
 *
 * Na versão anterior quem escutava o Bluetooth era o fragmento do Dashboard:
 * sair da tela parava a coleta e a sessão em andamento se perdia. Aqui a coleta
 * vive no nível do app — as telas apenas se inscrevem para desenhar. Cada pacote
 * que chega é, na ordem: guardado em memória para os gráficos ao vivo, gravado
 * no banco local, somado na sessão (se houver uma aberta) e comparado com os
 * limites de alerta.
 */
public class Telemetry implements BleManager.Listener {

    /** Quantos pontos cada grandeza mantém em memória para o gráfico ao vivo. */
    private static final int LIVE_BUFFER = 180;

    public interface Listener {
        default void onLiveData(SensorSample sample) {}
        default void onSessionChanged(boolean running) {}
        default void onAlertRaised(AlertRecord alert) {}
        default void onConnectionChanged(BleState state, String detail) {}
        /** Novo ponto de GPS gravado na sessão em andamento. */
        default void onRouteUpdated(GeoMath.Fix fix, double distanceM) {}
        /** Queda suspeita: a tela abre a contagem regressiva do SOS. */
        default void onFallSuspected(double impactN, String source) {}
        /** Botão SOS pressionado no próprio tênis. */
        default void onShoeSos() {}
    }

    private final Context ctx;
    private final BleManager ble;
    private final Repo repo;
    private final AlertEngine alertEngine = new AlertEngine();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private final Map<String, Double> latest = new LinkedHashMap<>();
    private final Map<String, Long> latestAt = new HashMap<>();
    private final Map<String, Deque<Point>> liveBuffers = new LinkedHashMap<>();

    // Sessão em andamento
    private long sessionId = 0;
    private long sessionStartedAt = 0;
    private long sessionSteps = 0;
    private boolean paused = false;
    private long pausedAt = 0;
    private long pausedTotalMs = 0;
    private long lastFinishedSessionId = 0;
    private double sessionEnergy = 0;
    private int sessionSamples = 0;

    // Rastreia a transição pra virar evento só uma vez por mudança real de
    // estado (onStateChanged dispara em toda troca, ex.: SCANNING→CONNECTING,
    // que não é conexão nem desconexão).
    private boolean wasLive = false;

    // v3: GPS, queda, desgaste do tênis
    private final LocationTracker location;
    private final FallDetector fallDetector = new FallDetector();
    private ShoeWear.BatteryTracker batteryTracker;
    private String trackedShoe;
    private String sessionKind = "walk";
    private final List<GeoMath.Fix> routeFixes = new ArrayList<>();
    private double routeDistanceM = 0;
    private GeoMath.Fix routeLast;
    private boolean lastShoeFall = false;
    private boolean shoeHasOwnFallDetection = false;
    private boolean lastShoeSos = false;

    public Telemetry(Context context, BleManager bleManager) {
        this.ctx = context.getApplicationContext();
        this.ble = bleManager;
        this.repo = Repo.get(this.ctx);
        ble.addListener(this);
        Notifier.ensureChannels(this.ctx);
        this.location = new LocationTracker(this.ctx);
        this.location.addListener(new LocationTracker.Listener() {
            @Override
            public void onFix(GeoMath.Fix fix, float speedMs) { onGpsFix(fix, speedMs); }
        });
        this.fallDetector.setListener((at, impact) -> {
            EventEngine.get(this.ctx).raise(EventCategory.SAFETY, EventSeverity.HIGH,
                    "fall_suspected", "Possível queda detectada pelo impacto e pela imobilidade.");
            for (Listener l : listeners) l.onFallSuspected(impact, "app");
            if (Prefs.fallDetect(this.ctx)) {
                SosManager.get(this.ctx).startCountdown("possível queda detectada pelo app");
            }
        });
        // Amostras soltas com mais de 30 dias não interessam mais a ninguém.
        repo.pruneOlderThan(30);
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ------------------------------------------------------------------
    // Valores ao vivo
    // ------------------------------------------------------------------

    public Double latest(String metric) { return latest.get(metric); }

    public double latestOr(String metric, double def) {
        Double v = latest.get(metric);
        return v == null ? def : v;
    }

    public long latestAt(String metric) {
        Long t = latestAt.get(metric);
        return t == null ? 0 : t;
    }

    /** Grandezas que já chegaram nesta execução, na ordem em que apareceram. */
    public List<String> liveMetrics() {
        return new ArrayList<>(latest.keySet());
    }

    public List<Point> liveSeries(String metric) {
        Deque<Point> q = liveBuffers.get(metric);
        return q == null ? new ArrayList<Point>() : new ArrayList<>(q);
    }

    public float[] liveValues(String metric) {
        Deque<Point> q = liveBuffers.get(metric);
        if (q == null || q.isEmpty()) return new float[0];
        float[] out = new float[q.size()];
        int i = 0;
        for (Point p : q) out[i++] = (float) p.value;
        return out;
    }

    // ------------------------------------------------------------------
    // Sessão
    // ------------------------------------------------------------------

    public LocationTracker location() { return location; }
    public String sessionKind() { return sessionKind; }
    public double routeDistanceM() { return routeDistanceM; }
    public List<GeoMath.Fix> routeFixes() { return new ArrayList<>(routeFixes); }

    /** Ritmo médio da sessão em s/km, calculado com as mesmas regras do resumo final. */
    public double sessionPaceSecPerKm() {
        return GeoMath.analyze(routeFixes).paceSecPerKm();
    }

    public boolean isSessionRunning() { return sessionId > 0; }
    public long sessionId() { return sessionId; }
    public long sessionStartedAt() { return sessionStartedAt; }
    public long sessionSteps() { return sessionSteps; }
    public double sessionEnergy() { return sessionEnergy; }
    public int sessionSamples() { return sessionSamples; }

    public boolean isPaused() { return paused; }
    public long lastFinishedSessionId() { return lastFinishedSessionId; }

    /** Pausa a contagem da sessão: tempo, passos, energia e rota ficam parados. */
    public void pauseSession() {
        if (sessionId <= 0 || paused) return;
        paused = true;
        pausedAt = System.currentTimeMillis();
        for (Listener l : listeners) l.onSessionChanged(true);
    }

    public void resumeSession() {
        if (sessionId <= 0 || !paused) return;
        pausedTotalMs += System.currentTimeMillis() - pausedAt;
        paused = false;
        routeLast = null; // evita somar o salto feito durante a pausa
        for (Listener l : listeners) l.onSessionChanged(true);
    }

    public long sessionElapsedMs() {
        if (sessionStartedAt == 0) return 0;
        long now = paused ? pausedAt : System.currentTimeMillis();
        return Math.max(0, now - sessionStartedAt - pausedTotalMs);
    }

    /** Começa a gravar uma sessão. Retorna false se o tênis não está conectado. */
    public boolean startSession() { return startSession("walk", false); }

    /**
     * Começa uma sessão do tipo dado ("walk", "run", "hike", "other"). Com
     * useGps, liga o GPS junto — se faltar permissão ou o GPS estiver
     * desligado, a sessão começa mesmo assim, sem rota.
     */
    public boolean startSession(String kind, boolean useGps) {
        if (sessionId > 0) return true;
        if (!ble.isConnected()) return false;
        sessionKind = kind == null ? "walk" : kind;
        routeFixes.clear();
        routeDistanceM = 0;
        routeLast = null;

        sessionStartedAt = System.currentTimeMillis();
        paused = false;
        pausedTotalMs = 0;
        sessionSteps = 0;
        sessionEnergy = 0;
        sessionSamples = 0;
        trackedShoe = ble.getConnectedDeviceAddress();
        sessionId = repo.openSession(ble.getConnectedDeviceName(), trackedShoe, sessionKind);
        if (useGps) location.start();

        if (Prefs.getBool(ctx, Prefs.KEEP_ALIVE, true)) {
            Intent i = new Intent(ctx, SessionService.class).setAction(SessionService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        }
        for (Listener l : listeners) l.onSessionChanged(true);
        return true;
    }

    /** Encerra a sessão, fecha o registro local e tenta enviar ao servidor. */
    public void stopSession() {
        if (sessionId <= 0) return;
        final long finishedId = sessionId;
        final long steps = sessionSteps;
        final double energy = sessionEnergy;
        final long startedAt = sessionStartedAt;

        repo.closeSession(finishedId);
        lastFinishedSessionId = finishedId;
        paused = false;

        sessionId = 0;
        sessionStartedAt = 0;
        sessionSteps = 0;
        sessionEnergy = 0;
        sessionSamples = 0;

        location.stop();
        ctx.stopService(new Intent(ctx, SessionService.class));
        for (Listener l : listeners) l.onSessionChanged(false);

        if (steps <= 0 && energy <= 0) return; // sessão sem nada medido
        // Dá um instante para o closeSession terminar de calcular as médias.
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(this::syncPendingSessionsAuto, 800);
    }

    /**
     * Manda para a API as sessões que ainda não subiram. É chamado ao encerrar
     * uma sessão e também quando o app volta a ter rede — nada se perde se o
     * celular estava offline na hora.
     */
    /** Chamadas automáticas (fim de sessão, volta da rede): só enviam se "Envio automático" estiver ligado. */
    public void syncPendingSessionsAuto() {
        if (!Prefs.getBool(ctx, Prefs.SYNC_AUTO, true)) return;
        syncPendingSessions();
    }

    /** Envio pedido pela pessoa (botão): sempre envia. */
    public void syncPendingSessions() {
        if (!SessionManager.isLoggedIn(ctx)) return;
        repo.unsyncedSessionsAsync(sessions -> {
            if (sessions == null) return;
            for (final SessionRecord s : sessions) push(s);
        });
    }

    private void push(final SessionRecord s) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", Fmt.date(s.startedAt));
        body.put("time", Fmt.clock(s.startedAt));
        body.put("endTime", Fmt.clock(s.endedAt > 0 ? s.endedAt : System.currentTimeMillis()));
        body.put("steps", s.steps);
        body.put("energy", s.energy);
        body.put("duration", s.durationMinutes());
        body.put("distance", Math.round(s.distanceM / 10.0) / 100.0);
        body.put("kind", s.kind == null ? "walk" : s.kind);
        body.put("elevationGain", Math.round(s.elevGainM * 10.0) / 10.0);
        body.put("bpmAvg", (int) Math.round(s.bpmAvg));
        body.put("bpmSamples", downsample(repo.sessionSeries(s.id, SensorCatalog.BPM), 60));
        body.put("spo2Samples", downsample(repo.sessionSeries(s.id, SensorCatalog.SPO2), 60));

        ApiClient.get(ctx).createActivity(body).enqueue(new Callback<ActivityRecord>() {
            @Override
            public void onResponse(Call<ActivityRecord> call, Response<ActivityRecord> response) {
                if (response.isSuccessful() && response.body() != null) {
                    repo.markSynced(s.id, response.body().id);
                }
            }

            @Override
            public void onFailure(Call<ActivityRecord> call, Throwable t) {
                // Fica na fila: a próxima chamada de syncPendingSessions tenta de novo.
            }
        });
    }

    /** Reduz a série para caber num payload razoável, preservando o formato. */
    private List<Integer> downsample(List<Point> points, int maxPoints) {
        List<Integer> out = new ArrayList<>();
        if (points == null || points.isEmpty()) return out;
        int step = Math.max(1, points.size() / maxPoints);
        for (int i = 0; i < points.size(); i += step) {
            out.add((int) Math.round(points.get(i).value));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // BleManager.Listener
    // ------------------------------------------------------------------

    @Override
    public void onSample(SensorSample sample) {
        long now = sample.timestamp;

        for (Map.Entry<String, Double> e : sample.values().entrySet()) {
            String metric = e.getKey();
            double value = e.getValue();
            latest.put(metric, value);
            latestAt.put(metric, now);

            Deque<Point> buffer = liveBuffers.get(metric);
            if (buffer == null) {
                buffer = new ArrayDeque<>();
                liveBuffers.put(metric, buffer);
            }
            buffer.addLast(new Point(now, value));
            while (buffer.size() > LIVE_BUFFER) buffer.removeFirst();
        }

        repo.writeSample(now, sample.values(), paused ? 0 : sessionId);

        // Resumo do dia e desgaste do tênis (passos e energia valem mesmo fora de sessão).
        long stepsDelta = sample.stepsDelta();
        double energyDelta = sample.energyDelta();
        repo.daily.addUsage(now, stepsDelta, energyDelta);
        GoalWatcher.onUsage(ctx, now);
        String address = ble.getConnectedDeviceAddress();
        if (address != null) {
            repo.shoes.seen(address, ble.getConnectedDeviceName(), ble.getFirmwareVersion());
            repo.shoes.addSteps(address, stepsDelta, energyDelta);
            trackBattery(address, sample);
        }
        processSafetySignals(now, sample, stepsDelta);

        if (sessionId > 0 && !paused) {
            sessionSteps += sample.stepsDelta();
            sessionEnergy += sample.energyDelta();
            sessionSamples++;
        }

        for (AlertRecord a : alertEngine.evaluate(ctx, sample)) {
            repo.insertAlert(a);
            Notifier.showAlert(ctx, a);
            for (Listener l : listeners) l.onAlertRaised(a);
        }

        for (Listener l : listeners) l.onLiveData(sample);
    }

    @Override
    public void onStateChanged(BleState state, String detail) {
        boolean liveNow = state.isLive();
        if (liveNow && !wasLive) {
            EventEngine.get(ctx).raise(EventCategory.CONNECTIVITY, EventSeverity.LOW,
                    "device_connected", "Tênis conectado.");
        } else if (!liveNow && wasLive) {
            EventEngine.get(ctx).raise(EventCategory.CONNECTIVITY, EventSeverity.MEDIUM,
                    "device_disconnected", "Conexão com o tênis perdida.");
        }
        if (!liveNow && wasLive) resetShoeCapabilities();
        wasLive = liveNow;

        // Perder a ligação no meio de uma sessão não pode apagar o que já foi
        // medido: a sessão é encerrada e salva, e o usuário é avisado.
        if (!state.isLive() && !state.isBusy() && sessionId > 0) {
            stopSession();
        }
        for (Listener l : listeners) l.onConnectionChanged(state, detail);
    }

    // ------------------------------------------------------------------
    // v3: bateria, segurança e GPS
    // ------------------------------------------------------------------

    private void trackBattery(String address, SensorSample sample) {
        Double batt = sample.getOrNull(SensorCatalog.BATTERY);
        if (batt == null) return;
        if (batteryTracker == null || !address.equals(trackedShoeForBattery)) {
            com.stepvolt.app.data.Shoe shoe = repo.shoes.get(address);
            batteryTracker = new ShoeWear.BatteryTracker(shoe == null ? 0 : shoe.batteryDischarged);
            trackedShoeForBattery = address;
        }
        batteryTracker.onReading(batt);
        // Grava a cada ~20 leituras para não escrever em todo pacote.
        if (++batteryWrites % 20 == 0) {
            repo.shoes.updateBattery(address, batt, batteryTracker.dischargedPoints());
        }
    }

    private String trackedShoeForBattery;
    private int batteryWrites = 0;

    private void processSafetySignals(long now, SensorSample sample, long stepsDelta) {
        // Detecção no app: pico de impacto seguido de imobilidade.
        // Se o tênis manda o próprio sinal de queda (firmware com acelerômetro), a decisão
        // é dele; a regra do app, baseada só em pressão, fica para firmwares sem isso.
        if (sample.getOrNull(SensorCatalog.FALL) != null) shoeHasOwnFallDetection = true;
        Double impact = sample.getOrNull(SensorCatalog.IMPACT);
        if (impact != null && !shoeHasOwnFallDetection) fallDetector.onSample(now, impact, stepsDelta);

        // Sinais que vêm do próprio firmware (acelerômetro e botão SOS do tênis).
        boolean fall = sample.get(SensorCatalog.FALL, 0) >= 1;
        if (fall && !lastShoeFall) {
            EventEngine.get(ctx).raise(EventCategory.SAFETY, EventSeverity.HIGH,
                    "fall_suspected_shoe", "O tênis detectou uma possível queda.");
            for (Listener l : listeners) l.onFallSuspected(sample.get(SensorCatalog.IMPACT, 0), "shoe");
            if (Prefs.fallDetect(ctx)) {
                SosManager.get(ctx).startCountdown("possível queda detectada pelo tênis");
            }
        }
        lastShoeFall = fall;

        boolean sos = sample.get(SensorCatalog.SOS, 0) >= 1;
        if (sos && !lastShoeSos) {
            EventEngine.get(ctx).raise(EventCategory.EMERGENCY, EventSeverity.CRITICAL,
                    "shoe_sos", "Botão SOS do tênis pressionado.");
            for (Listener l : listeners) l.onShoeSos();
            // O botão físico é um pedido deliberado: abre a contagem mesmo com a detecção de queda desligada.
            SosManager.get(ctx).startCountdown("botão SOS do tênis pressionado");
        }
        lastShoeSos = sos;
    }

    /** Chamado quando a conexão cai: o próximo tênis pode ter outro firmware. */
    private void resetShoeCapabilities() {
        shoeHasOwnFallDetection = false;
        lastShoeFall = false;
        lastShoeSos = false;
        fallDetector.reset();
    }

    private void onGpsFix(GeoMath.Fix fix, float speedMs) {
        if (sessionId <= 0 || paused) return;
        SosManager.get(ctx).setLastFix(fix);
        repo.route.add(sessionId, fix, speedMs);
        routeFixes.add(fix);
        // Distância ao vivo: só soma o segmento se o ponto for aceito pelas
        // mesmas regras do resumo final (precisão, salto impossível, ruído).
        if (fix.accuracy <= GeoMath.MAX_ACCURACY_M) {
            if (routeLast != null) {
                long dt = fix.ts - routeLast.ts;
                double d = GeoMath.haversine(routeLast.lat, routeLast.lon, fix.lat, fix.lon);
                if (dt > 0 && d >= GeoMath.MIN_STEP_M && d / (dt / 1000.0) <= GeoMath.MAX_SPEED_MS) {
                    routeDistanceM += d;
                    routeLast = fix;
                }
            } else {
                routeLast = fix;
            }
        }
        for (Listener l : listeners) l.onRouteUpdated(fix, routeDistanceM);
    }
}
