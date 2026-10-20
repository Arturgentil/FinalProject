package com.stepvolt.app.ble;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.stepvolt.app.AppState;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.util.Fmt;
import com.stepvolt.app.util.Notifier;

/**
 * Serviço em primeiro plano que segura a sessão enquanto ela está gravando.
 *
 * Sem isso, sair do app ou bloquear a tela derruba a ligação Bluetooth em
 * pouco tempo e a sessão morre pela metade — era exatamente o que acontecia na
 * versão anterior. A notificação persistente também é o jeito honesto de
 * mostrar que o app continua lendo o sensor em segundo plano.
 */
public class SessionService extends Service {

    public static final String ACTION_START = "com.stepvolt.app.SESSION_START";

    private static final long TICK_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateNotification();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && Notifier.ACTION_STOP_SESSION.equals(intent.getAction())) {
            // Botão "Parar treino" da notificação.
            AppState app = AppState.get();
            if (app != null && app.telemetry != null) app.telemetry.stopSession();
            com.stepvolt.app.widget.StepVoltWidget.refresh(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        startInForeground();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TICK_MS);
        return START_STICKY;
    }

    private void startInForeground() {
        android.app.Notification n = Notifier.sessionNotification(
                this, "Sessão em andamento", "Lendo os sensores do tênis…");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            // O tipo "location" só pode ser declarado com a permissão já concedida
            // (no Android 14+ declarar sem ela derruba o serviço).
            boolean gps = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (gps) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            startForeground(Notifier.ID_SESSION, n, type);
        } else {
            startForeground(Notifier.ID_SESSION, n);
        }
    }

    private void updateNotification() {
        AppState app = AppState.get();
        if (app == null || app.telemetry == null) return;
        Telemetry t = app.telemetry;
        if (!t.isSessionRunning()) {
            stopSelf();
            return;
        }
        StringBuilder sb = new StringBuilder(Fmt.stopwatch(t.sessionElapsedMs()));
        if (t.location().isRunning() && t.routeDistanceM() >= 10) {
            sb.append(" · ").append(Fmt.decimals(t.routeDistanceM() / 1000.0, 2)).append(" km");
            String pace = com.stepvolt.app.gps.GeoMath.formatPace(t.sessionPaceSecPerKm());
            if (!"—".equals(pace)) sb.append(" · ").append(pace).append("/km");
        }
        sb.append(" · ").append(Fmt.intGrouped(t.sessionSteps())).append(" passos")
                .append(" · ").append(Fmt.energy(t.sessionEnergy())).append(" mWh");
        String text = (t.isPaused() ? "Pausado · " : "") + sb;
        com.stepvolt.app.widget.StepVoltWidget.refresh(this);
        android.app.Notification n = Notifier.sessionNotification(this, "Sessão em andamento", text);
        androidx.core.app.NotificationManagerCompat nm =
                androidx.core.app.NotificationManagerCompat.from(this);
        try {
            nm.notify(Notifier.ID_SESSION, n);
        } catch (SecurityException ignored) {}
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
