package com.stepvolt.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.stepvolt.app.AppState;
import com.stepvolt.app.R;
import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.core.Telemetry;
import com.stepvolt.app.data.DailyDao;
import com.stepvolt.app.data.DayStat;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.ui.SplashActivity;
import com.stepvolt.app.util.Fmt;

import java.util.List;

/**
 * Widget da tela inicial: passos e energia de hoje, com a meta, e o estado do
 * treino quando há um em andamento. Lê o resumo por dia do banco local, então
 * funciona mesmo com o app fechado e o tênis desconectado (mostra o que já foi
 * registrado hoje).
 */
public class StepVoltWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        update(ctx, mgr, ids);
    }

    /** Chamado pelo serviço de sessão e pelo app para atualizar os widgets já colocados. */
    public static void refresh(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, StepVoltWidget.class));
            if (ids != null && ids.length > 0) update(ctx, mgr, ids);
        } catch (Exception ignored) {
            // widget é conveniência: nunca derruba o app
        }
    }

    private static void update(final Context ctx, final AppWidgetManager mgr, final int[] ids) {
        final long now = System.currentTimeMillis();
        Repo.get(ctx).daily.rangeAsync(now, now, days -> {
            long steps = 0; double energy = 0;
            if (days != null) for (DayStat d : days) { steps += d.steps; energy += d.energy; }
            long goal = Prefs.dailyStepGoal(ctx);

            String status = "Sem treino em andamento";
            Telemetry t = AppState.get() == null ? null : AppState.get().telemetry;
            if (t != null && t.isSessionRunning()) {
                status = (t.isPaused() ? "Pausado · " : "Treinando · ") + Fmt.stopwatch(t.sessionElapsedMs());
            }

            for (int id : ids) {
                RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_stepvolt);
                v.setTextViewText(R.id.widgetSteps, Fmt.intGrouped(steps));
                v.setTextViewText(R.id.widgetGoal, "de " + Fmt.intGrouped(goal) + " passos");
                v.setTextViewText(R.id.widgetEnergy, Fmt.energy(energy) + " mWh gerados");
                v.setTextViewText(R.id.widgetStatus, status);
                v.setProgressBar(R.id.widgetProgress, 100,
                        (int) Math.min(100, goal > 0 ? steps * 100 / goal : 0), false);
                Intent open = new Intent(ctx, SplashActivity.class);
                PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                v.setOnClickPendingIntent(R.id.widgetRoot, pi);
                mgr.updateAppWidget(id, v);
            }
        });
    }
}
