package com.stepvolt.app.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.stepvolt.app.R;
import com.stepvolt.app.data.AlertRecord;
import com.stepvolt.app.ui.MainActivity;

/** Canais e notificações do app: a sessão em andamento e os avisos de saúde. */
public final class Notifier {

    public static final String CH_SESSION = "stepvolt_session";
    public static final String CH_ALERTS = "stepvolt_alerts";
    public static final int ID_SESSION = 1001;
    public static final int ID_SOS = 1002;
    public static final String ACTION_STOP_SESSION = "com.stepvolt.app.STOP_SESSION";

    private Notifier() {}

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel session = new NotificationChannel(
                CH_SESSION, ctx.getString(R.string.chan_session_name), NotificationManager.IMPORTANCE_LOW);
        session.setDescription(ctx.getString(R.string.chan_session_desc));
        session.setShowBadge(false);
        nm.createNotificationChannel(session);

        NotificationChannel alerts = new NotificationChannel(
                CH_ALERTS, ctx.getString(R.string.chan_alerts_name), NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription(ctx.getString(R.string.chan_alerts_desc));
        nm.createNotificationChannel(alerts);
    }

    private static PendingIntent openApp(Context ctx) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(ctx, 0, intent, flags);
    }

    public static Notification sessionNotification(Context ctx, String title, String text) {
        return new NotificationCompat.Builder(ctx, CH_SESSION)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openApp(ctx))
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_stop, "Parar treino", stopSessionIntent(ctx))
                .build();
    }

    private static PendingIntent stopSessionIntent(Context ctx) {
        Intent i = new Intent(ctx, com.stepvolt.app.ble.SessionService.class).setAction(ACTION_STOP_SESSION);
        return PendingIntent.getService(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void showAlert(Context ctx, AlertRecord alert) {
        Notification n = new NotificationCompat.Builder(ctx, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle(alert.severityLabel() + " · StepVolt")
                .setContentText(alert.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(alert.message))
                .setContentIntent(openApp(ctx))
                .setAutoCancel(true)
                .setPriority(alert.severity >= AlertRecord.CRITICAL
                        ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .build();
        try {
            NotificationManagerCompat.from(ctx).notify((int) (System.currentTimeMillis() % 100000), n);
        } catch (SecurityException ignored) {
            // Sem a permissão POST_NOTIFICATIONS: o aviso continua na aba de avisos.
        }
    }

    // ------------------------------------------------------------------
    // SOS
    // ------------------------------------------------------------------

    public static void showSosCountdown(Context ctx, int seconds, String reason) {
        ensureChannels(ctx);
        Notification n = new NotificationCompat.Builder(ctx, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle("SOS em " + seconds + " s")
                .setContentText(reason + ". Toque para cancelar se estiver bem.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(openApp(ctx))
                .setFullScreenIntent(openApp(ctx), true)
                .build();
        try {
            NotificationManagerCompat.from(ctx).notify(ID_SOS, n);
        } catch (SecurityException ignored) {}
    }

    public static void cancelSosCountdown(Context ctx) {
        NotificationManagerCompat.from(ctx).cancel(ID_SOS);
    }

    public static void showSosResult(Context ctx, String title, String body) {
        ensureChannels(ctx);
        Notification n = new NotificationCompat.Builder(ctx, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp(ctx))
                .setAutoCancel(true)
                .build();
        try {
            NotificationManagerCompat.from(ctx).notify(ID_SOS + 1, n);
        } catch (SecurityException ignored) {}
    }

    /** Aviso simples e discreto (meta batida). Usa o canal de avisos, sem som insistente. */
    public static void showMilestone(Context ctx, String title, String body) {
        ensureChannels(ctx);
        Notification n = new NotificationCompat.Builder(ctx, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openApp(ctx))
                .setAutoCancel(true)
                .build();
        try {
            NotificationManagerCompat.from(ctx).notify(ID_SOS + 2 + (int) (System.currentTimeMillis() % 1000), n);
        } catch (SecurityException ignored) {}
    }
}
